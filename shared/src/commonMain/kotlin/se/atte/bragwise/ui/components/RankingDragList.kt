package se.atte.bragwise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.ranking_a11y_move_to_slot
import bragwise.shared.generated.resources.ranking_a11y_return_to_pool
import bragwise.shared.generated.resources.ranking_drop_here_hint
import bragwise.shared.generated.resources.ranking_slot_removed
import bragwise.shared.generated.resources.ranking_undo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.ui.LocalSnackbarHost
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.ui.windowHeightDp
import kotlin.math.roundToInt

// region Defaults

private object RankingDragDefaults {
    /**
     * Widget height cap as a fraction of the window. The two panes scroll internally
     * instead of growing, so a topN=8 ranking cannot push the submit CTA off screen.
     */
    const val PaneHeightFraction = 0.55f
    val PaneHeightMin = 320.dp
    val PaneHeightMax = 720.dp

    /** Keeps an emptied pool a viable drop target. */
    val PaneMinHeight = 120.dp
    val PaneGap = standardPaddingSmall

    /** Rows grow past this when a label wraps to two lines. */
    val RowMinHeight = 72.dp

    /** Always laid out (only painted when hovered) so a drag start cannot shift rows. */
    val GapIndicatorHeight = 6.dp

    /** Touch target of the ≡ drag handle. */
    val HandleTouchSize = 32.dp

    val AutoScrollEdgeZone = 48.dp

    /** Auto-scroll speed in dp per second at full edge depth. */
    val AutoScrollSpeed = 700.dp

    /** Widget maxWidth below which labels drop one type step. */
    val CompactLabelBreakpoint = 420.dp
}

// endregion

// region Private state types

internal sealed interface DragState {
    data object Idle : DragState
    data class Picking(val itemId: String) : DragState
    data class Hovering(val itemId: String, val target: DropTarget) : DragState
}

internal sealed interface DropTarget {
    data class Slot(val index: Int) : DropTarget
    data class Gap(val insertIndex: Int) : DropTarget
    data object Pool : DropTarget
}

internal data class PendingUndo(val slotIndex: Int, val itemId: String)

/**
 * Row geometry for hit-testing.
 *
 * [visible] is the clip-aware rect from `boundsInRoot()`: a row scrolled out of its
 * pane reports a zero/short rect, so `contains` correctly rejects it.
 * [full] is the unclipped rect (`positionInRoot()` + `size`). The top/middle/bottom
 * thirds MUST be computed from [full] — a row half-clipped at a pane edge (exactly
 * where auto-scroll parks the finger) would otherwise yield the wrong Gap index.
 */
internal data class RowBounds(val visible: Rect, val full: Rect)

private val PendingUndoSaver = listSaver<PendingUndo?, Any>(
    save = { undo -> if (undo == null) emptyList() else listOf(undo.slotIndex, undo.itemId) },
    restore = { list -> if (list.size < 2) null else PendingUndo(slotIndex = list[0] as Int, itemId = list[1] as String) },
)

// endregion

// region State holder

/**
 * Mutable drag state for one [RankingDragList]. All reads are exposed as
 * immutable [List] / value properties; mutations go through methods only.
 */
internal class RankingDragStateHolder(
    options: List<BetOption>,
    private val topN: Int,
    initialOrderedIds: List<String>,
    initialPendingUndo: PendingUndo? = null,
) {
    private val _slots = mutableStateListOf<BetOption?>()
    private val _pool = mutableStateListOf<BetOption>()
    private val _drag = mutableStateOf<DragState>(DragState.Idle)
    private val _pendingUndo = mutableStateOf<PendingUndo?>(null)

    val slots: List<BetOption?> get() = _slots
    val pool: List<BetOption> get() = _pool
    val drag: DragState get() = _drag.value
    val pendingUndo: PendingUndo? get() = _pendingUndo.value

    val slotBounds = mutableMapOf<Int, RowBounds>()
    var slotsPaneBounds: Rect = Rect.Zero
    var poolPaneBounds: Rect = Rect.Zero

    init {
        repeat(topN) { _slots.add(null) }
        reconcile(options = options, orderedIds = initialOrderedIds, excludeFromPool = initialPendingUndo?.itemId)
        if (initialPendingUndo != null) _pendingUndo.value = initialPendingUndo
    }

    fun reconcile(options: List<BetOption>, orderedIds: List<String>, excludeFromPool: String? = null) {
        while (_slots.size < topN) _slots.add(null)
        while (_slots.size > topN) _slots.removeAt(_slots.lastIndex)
        for (i in 0 until topN) {
            // "" is the empty-slot sentinel — preserves gaps through the
            // onReorder → reconcile round-trip so a pick stays where dropped.
            val id = orderedIds.getOrNull(i)?.takeIf { it.isNotEmpty() }
            _slots[i] = if (id != null) options.find { it.id == id } else null
        }
        val placed = orderedIds.filter { it.isNotEmpty() }.toSet()
        _pool.clear()
        options
            .filter { it.id !in placed && it.id != excludeFromPool }
            .sortedBy { it.label }
            .forEach { _pool.add(it) }
    }

    fun startDrag(itemId: String) {
        _drag.value = DragState.Picking(itemId)
    }

    fun hover(target: DropTarget?) {
        val current = _drag.value
        val itemId = when (current) {
            is DragState.Picking -> current.itemId
            is DragState.Hovering -> current.itemId
            DragState.Idle -> return
        }
        _drag.value = if (target != null) DragState.Hovering(itemId = itemId, target = target) else DragState.Picking(itemId)
    }

    fun cancelDrag() {
        _drag.value = DragState.Idle
    }

    /** Applies drag rules and returns the new ordered option id list (gaps as ""). */
    fun applyDrop(sourceItemId: String, target: DropTarget): List<String> {
        val sourceSlotIndex = _slots.indexOfFirst { it?.id == sourceItemId }.takeIf { it >= 0 }
        val isFromPool = _pool.any { it.id == sourceItemId }

        when {
            // Insert-between: place source at insertIndex and ripple items down
            // into the nearest empty slot below; if none, the last occupant
            // overflows back to the pool.
            target is DropTarget.Gap -> {
                val item = when {
                    sourceSlotIndex != null -> _slots[sourceSlotIndex].also { _slots[sourceSlotIndex] = null }!!
                    isFromPool -> _pool.first { it.id == sourceItemId }.also { _pool.remove(it) }
                    else -> null
                }
                if (item != null) {
                    val insertAt = target.insertIndex.coerceIn(0, topN - 1)
                    // Nearest empty slot at or below the insert point absorbs the shift.
                    var absorb = (insertAt until topN).firstOrNull { _slots[it] == null }
                    if (absorb == null) {
                        // No gap below — last slot's occupant returns to the pool.
                        _slots[topN - 1]?.let { insertSorted(it) }
                        absorb = topN - 1
                    }
                    for (i in absorb downTo insertAt + 1) _slots[i] = _slots[i - 1]
                    _slots[insertAt] = item
                }
            }
            // Pool → empty slot: anchor the item at exactly that slot.
            isFromPool && target is DropTarget.Slot && _slots[target.index] == null -> {
                val item = _pool.first { it.id == sourceItemId }
                _pool.remove(item)
                _slots[target.index] = item
            }
            // Pool → occupied slot: bump occupant back to pool, take its place.
            isFromPool && target is DropTarget.Slot && _slots[target.index] != null -> {
                val item = _pool.first { it.id == sourceItemId }
                val occupant = _slots[target.index]!!
                _pool.remove(item)
                _slots[target.index] = item
                insertSorted(occupant)
            }
            // Slot → pool: empty the source slot.
            sourceSlotIndex != null && target is DropTarget.Pool -> {
                val item = _slots[sourceSlotIndex]!!
                _slots[sourceSlotIndex] = null
                insertSorted(item)
            }
            // Slot → slot: swap occupants (target may be empty → moves the item).
            sourceSlotIndex != null && target is DropTarget.Slot && target.index != sourceSlotIndex -> {
                val sourceItem = _slots[sourceSlotIndex]
                _slots[sourceSlotIndex] = _slots[target.index]
                _slots[target.index] = sourceItem
            }
            else -> Unit
        }
        _drag.value = DragState.Idle
        return currentOrderedIds()
    }

    fun removeFromSlot(index: Int): PendingUndo {
        val item = _slots[index] ?: error("Slot $index is empty")
        _slots[index] = null
        val undo = PendingUndo(slotIndex = index, itemId = item.id)
        _pendingUndo.value = undo
        return undo
    }

    fun restoreUndo(options: List<BetOption>): List<String>? {
        val undo = _pendingUndo.value ?: return null
        val item = options.find { it.id == undo.itemId } ?: return null
        val current = _slots.getOrNull(undo.slotIndex)
        if (current != null) insertSorted(current)
        _slots[undo.slotIndex] = item
        _pendingUndo.value = null
        return currentOrderedIds()
    }

    fun commitUndo(options: List<BetOption>) {
        val undo = _pendingUndo.value ?: return
        val item = options.find { it.id == undo.itemId }
        if (item != null) insertSorted(item)
        _pendingUndo.value = null
    }

    /**
     * Pane-guarded hit test. A position outside both pane viewports is not a drop
     * target at all, which also rejects rows that scrolled out of their own pane.
     */
    fun findTargetAt(pos: Offset): DropTarget? {
        if (slotsPaneBounds != Rect.Zero && slotsPaneBounds.contains(pos)) {
            var nearestIndex: Int? = null
            var nearestDistance = Float.MAX_VALUE
            for ((index, bounds) in slotBounds) {
                // A row scrolled fully out of its pane reports a zero-height clipped
                // rect; it must not win the nearest-row fallback below.
                if (bounds.visible.isEmpty) continue
                if (bounds.visible.contains(pos)) return targetForRow(index, pos, bounds.full)
                val distance = when {
                    pos.y < bounds.visible.top -> bounds.visible.top - pos.y
                    pos.y > bounds.visible.bottom -> pos.y - bounds.visible.bottom
                    else -> 0f
                }
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestIndex = index
                }
            }
            // Inside the pane but on no row: the pane's own 8 dp padding and the 6 dp
            // gap indicator reserved above every slot are dead strips, and they sit
            // exactly where drag auto-scroll parks a finger at a pane edge. Snapping
            // to the nearest visible row keeps the drop from silently cancelling.
            return nearestIndex?.let { targetForRow(it, pos, slotBounds.getValue(it).full) }
        }
        if (poolPaneBounds != Rect.Zero && poolPaneBounds.contains(pos)) return DropTarget.Pool
        return null
    }

    /**
     * Empty slot: the whole row places directly into it. Filled slot: top third =
     * insert above, bottom third = insert below (ripples items down), middle third =
     * swap into this slot. Thirds come from the UNCLIPPED rect so a partially
     * scrolled-off row still maps the finger to the right third; a fallback position
     * above/below the row yields a negative/overlong relY, i.e. the adjacent gap.
     */
    private fun targetForRow(index: Int, pos: Offset, full: Rect): DropTarget {
        if (_slots.getOrNull(index) == null) return DropTarget.Slot(index)
        val relY = pos.y - full.top
        val third = full.height / 3f
        return when {
            relY < third -> DropTarget.Gap(index)
            relY > third * 2f -> DropTarget.Gap(index + 1)
            else -> DropTarget.Slot(index)
        }
    }

    private fun insertSorted(item: BetOption) {
        val insertIndex = _pool.indexOfFirst { it.label > item.label }.takeIf { it >= 0 } ?: _pool.size
        _pool.add(insertIndex, item)
    }

    /**
     * Slot order with empty slots as "" sentinels, trailing empties trimmed.
     * A fully-filled ranking is therefore dense (no ""), matching the wire
     * format; interior gaps are preserved so a pick stays where it was dropped.
     * Submit is gated on a full ranking (no gaps), so "" never reaches the
     * callable, scoring, or persistence.
     */
    internal fun currentOrderedIds(): List<String> {
        val ids = _slots.map { it?.id ?: "" }
        val lastFilled = ids.indexOfLast { it.isNotEmpty() }
        return if (lastFilled < 0) emptyList() else ids.subList(0, lastFilled + 1)
    }
}

// endregion

// region Public composable

/**
 * Two-pane drag-and-drop for Ranking bets (plan §4).
 *
 * Left pane: [topN] numbered placement slots.
 * Right pane: pool of remaining options, sorted A-Z.
 *
 * Drag rules:
 * - Pool → empty slot: anchors the item.
 * - Pool → occupied slot: bumps occupant back to pool (sorted).
 * - Slot → pool: empties the slot.
 * - Slot → slot: swaps occupants.
 *
 * Tap an occupied slot to remove it (undoable via snackbar for ~4 s).
 * Snackbar host is consumed from [LocalSnackbarHost].
 */
@Composable
fun RankingDragList(
    options: List<BetOption>,
    topN: Int,
    orderedOptionIds: List<String>,
    showFlag: Boolean,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var savedUndo by rememberSaveable(stateSaver = PendingUndoSaver) { mutableStateOf<PendingUndo?>(null) }

    val holder = remember(topN) {
        RankingDragStateHolder(
            options = options,
            topN = topN,
            initialOrderedIds = orderedOptionIds,
            initialPendingUndo = savedUndo,
        )
    }

    LaunchedEffect(options, orderedOptionIds) {
        holder.reconcile(
            options = options,
            orderedIds = orderedOptionIds,
            excludeFromPool = holder.pendingUndo?.itemId,
        )
    }

    var undoJob by remember { mutableStateOf<Job?>(null) }
    val removedText = stringResource(Res.string.ranking_slot_removed)
    val undoText = stringResource(Res.string.ranking_undo)

    fun launchSnackbar() {
        undoJob = scope.launch {
            val result = snackbarHost.showSnackbar(
                message = removedText,
                actionLabel = undoText,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                val newOrder = holder.restoreUndo(options)
                if (newOrder != null) {
                    savedUndo = null
                    onReorder(newOrder)
                }
            } else {
                holder.commitUndo(options)
                savedUndo = null
            }
        }
    }

    // Re-show snackbar after rotation if there was a pending undo
    LaunchedEffect(Unit) {
        if (savedUndo != null) launchSnackbar()
    }

    fun onTapSlot(index: Int) {
        undoJob?.cancel()
        val undo = holder.removeFromSlot(index)
        savedUndo = undo
        onReorder(holder.currentOrderedIds())
        launchSnackbar()
    }

    // Ghost item state — two Animatables to allow snap-back spring without the Offset VectorConverter
    var ghostItem by remember { mutableStateOf<BetOption?>(null) }
    var ghostSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var ghostSourceLeft by remember { mutableStateOf(0f) }
    var ghostSourceTop by remember { mutableStateOf(0f) }
    val ghostX = remember { Animatable(0f) }
    val ghostY = remember { Animatable(0f) }

    // Absolute finger position in root coords, kept in sync with the ongoing drag
    val fingerPos = remember { mutableStateOf(Offset.Zero) }

    // Ownership token for the live drag. Every filled row arms TWO detectors on the same
    // pointer: the row's long-press detector and the ≡ handle's immediate one. The loser
    // of that race still receives its own onDrag/onDragEnd/onDragCancel (its pointer
    // change was consumed by the winner), and an unguarded onDragCancel would kill the
    // live drag. Whoever calls handleDragStart last owns the token; stale callbacks no-op.
    var dragToken by remember { mutableStateOf(0) }

    fun handleDragStart(item: BetOption, sourceBounds: Rect, fingerInRoot: Offset): Int {
        ghostItem = item
        ghostSize = sourceBounds.size
        ghostSourceLeft = sourceBounds.left
        ghostSourceTop = sourceBounds.top
        fingerPos.value = fingerInRoot
        val centeredTopLeft = Offset(
            x = fingerInRoot.x - sourceBounds.width / 2f,
            y = fingerInRoot.y - sourceBounds.height / 2f,
        )
        scope.launch {
            ghostX.snapTo(centeredTopLeft.x)
            ghostY.snapTo(centeredTopLeft.y)
        }
        holder.startDrag(item.id)
        dragToken += 1
        return dragToken
    }

    fun handleDrag(token: Int, delta: Offset) {
        if (token != dragToken) return
        val newFinger = fingerPos.value + delta
        fingerPos.value = newFinger
        scope.launch {
            ghostX.snapTo(newFinger.x - ghostSize.width / 2f)
            ghostY.snapTo(newFinger.y - ghostSize.height / 2f)
        }
        holder.hover(holder.findTargetAt(newFinger))
    }

    /** Re-evaluates the hover target after auto-scroll moved rows under a still finger. */
    fun refreshHover() {
        holder.hover(holder.findTargetAt(fingerPos.value))
    }

    fun handleDragEnd(token: Int) {
        if (token != dragToken) return
        val current = holder.drag
        if (current is DragState.Hovering) {
            val newOrder = holder.applyDrop(sourceItemId = current.itemId, target = current.target)
            onReorder(newOrder)
            ghostItem = null
        } else {
            holder.cancelDrag()
            val snapSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            scope.launch {
                launch { ghostX.animateTo(targetValue = ghostSourceLeft, animationSpec = snapSpec) }
                ghostY.animateTo(targetValue = ghostSourceTop, animationSpec = snapSpec)
                ghostItem = null
            }
        }
    }

    fun handleDragCancel(token: Int) {
        if (token != dragToken) return
        holder.cancelDrag()
        ghostItem = null
    }

    // Root-space origin of this composable, used to convert ghost root coords → local offset.
    // ghostX/ghostY are stored in root coordinates (boundsInRoot of the dragged row).
    // Modifier.offset is relative to the local container, so we subtract the container origin.
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }

    // Bound the whole widget; each pane scrolls internally instead of growing.
    // heightIn MUST stay ABOVE the panes' verticalScroll in the modifier chain: a
    // scrollable measured with an infinite max height (which is what the hosting
    // LazyColumn hands down) throws "Vertically scrollable component was measured
    // with an infinity maximum height constraints".
    val paneMaxHeight = (windowHeightDp() * RankingDragDefaults.PaneHeightFraction)
        .coerceIn(RankingDragDefaults.PaneHeightMin, RankingDragDefaults.PaneHeightMax)

    BoxWithConstraints(
        modifier = modifier
            .heightIn(max = paneMaxHeight)
            .onGloballyPositioned { coords ->
                containerOrigin = coords.boundsInRoot().topLeft
            },
    ) {
        // Two equal panes on a phone leave ~150 dp per label — drop one type step.
        val compactLabels = maxWidth < RankingDragDefaults.CompactLabelBreakpoint

        // fillMaxWidth, NOT fillMaxSize: with a finite height cap in the chain above,
        // fillMaxSize would inflate every ranking card to the full cap.
        Box(modifier = Modifier.fillMaxWidth()) {
            SideBySideLayout(
                holder = holder,
                showFlag = showFlag,
                topN = topN,
                drag = holder.drag,
                compactLabels = compactLabels,
                fingerPos = fingerPos,
                onDragStart = ::handleDragStart,
                onDrag = ::handleDrag,
                onDragEnd = ::handleDragEnd,
                onDragCancel = ::handleDragCancel,
                onHoverRefresh = ::refreshHover,
                onTapSlot = ::onTapSlot,
                onDropFromPool = { itemId, slotIndex ->
                    val newOrder = holder.applyDrop(sourceItemId = itemId, target = DropTarget.Slot(slotIndex))
                    onReorder(newOrder)
                },
            )

            // Ghost overlay — floats above both panes during a drag.
            // ghostX/ghostY are in root coords; subtract containerOrigin to get local offset.
            val safeGhostItem = ghostItem
            if (safeGhostItem != null) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (ghostX.value - containerOrigin.x).roundToInt(),
                                y = (ghostY.value - containerOrigin.y).roundToInt(),
                            )
                        }
                        .zIndex(10f)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = standardPaddingSmall, vertical = 10.dp),
                ) {
                    OptionRowContent(option = safeGhostItem, showFlag = showFlag, compactLabels = compactLabels)
                }
            }
        }
    }
}

// endregion

// region Layouts

@Composable
private fun SideBySideLayout(
    holder: RankingDragStateHolder,
    showFlag: Boolean,
    topN: Int,
    drag: DragState,
    compactLabels: Boolean,
    fingerPos: androidx.compose.runtime.State<Offset>,
    onDragStart: (BetOption, Rect, Offset) -> Int,
    onDrag: (Int, Offset) -> Unit,
    onDragEnd: (Int) -> Unit,
    onDragCancel: (Int) -> Unit,
    onHoverRefresh: () -> Unit,
    onTapSlot: (Int) -> Unit,
    onDropFromPool: (itemId: String, slotIndex: Int) -> Unit,
) {
    val slotsScroll = rememberScrollState()
    val poolScroll = rememberScrollState()

    // Both panes wrap their content, so a full slots pane next to a nearly empty pool (or the
    // reverse, once all 8 slots are filled) looked lopsided. Each pane reports its measured
    // height and both take the taller of the two as their minimum — a fixpoint that settles in
    // one extra layout pass, bounded above by the widget's heightIn(max = paneMaxHeight).
    // Modifier.height(IntrinsicSize.Min/Max) is NOT an option here: intrinsic measurement of a
    // verticalScroll subtree measures with an infinite height and trips
    // checkScrollableContainerConstraints.
    val density = LocalDensity.current
    var slotsHeightPx by remember { mutableStateOf(0) }
    var poolHeightPx by remember { mutableStateOf(0) }
    val paneMinHeight = with(density) { maxOf(slotsHeightPx, poolHeightPx).toDp() }
        .coerceAtLeast(RankingDragDefaults.PaneMinHeight)

    AutoScrollOnDragNearEdge(
        holder = holder,
        dragging = drag !is DragState.Idle,
        fingerPos = fingerPos,
        slotsScroll = slotsScroll,
        poolScroll = poolScroll,
        onHoverRefresh = onHoverRefresh,
    )

    // Equal 50/50 panes across the full card width, always — no pager, no breakpoint.
    // No VerticalDivider: both panes already have a surfaceVariant background, so the
    // gap reads as the separator and costs no horizontal space.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RankingDragDefaults.PaneGap),
    ) {
        SlotsColumn(
            holder = holder,
            showFlag = showFlag,
            topN = topN,
            drag = drag,
            compactLabels = compactLabels,
            scrollState = slotsScroll,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            onTapSlot = onTapSlot,
            minHeight = paneMinHeight,
            onHeightMeasured = { slotsHeightPx = it },
            modifier = Modifier.weight(1f),
        )
        PoolList(
            holder = holder,
            showFlag = showFlag,
            topN = topN,
            drag = drag,
            compactLabels = compactLabels,
            scrollState = poolScroll,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            onDropToSlot = onDropFromPool,
            minHeight = paneMinHeight,
            onHeightMeasured = { poolHeightPx = it },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Scrolls whichever pane the finger hovers while a drag is held near that pane's top or
 * bottom edge, then re-evaluates the hover target because rows moved under a still finger.
 *
 * Frame-driven (`withFrameNanos` + `scrollBy`) rather than `animateScrollBy`, so no queued
 * animation can overshoot after the finger leaves the edge; the depth ramp replaces a hold
 * delay. Keyed on the `dragging` Boolean — not on `drag` — so a hover change does not
 * restart the loop; `holder.drag` is re-read inside the loop for the exit condition.
 */
@Composable
private fun AutoScrollOnDragNearEdge(
    holder: RankingDragStateHolder,
    dragging: Boolean,
    fingerPos: androidx.compose.runtime.State<Offset>,
    slotsScroll: ScrollState,
    poolScroll: ScrollState,
    onHoverRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    val edgeZonePx = with(density) { RankingDragDefaults.AutoScrollEdgeZone.toPx() }
    val pxPerSecond = with(density) { RankingDragDefaults.AutoScrollSpeed.toPx() }

    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        var lastNanos = 0L
        var hoverStale = false
        while (isActive && holder.drag !is DragState.Idle) {
            val nanos = withFrameNanos { it }
            // Deliberately refreshed at the TOP of the next frame, not right after scrollBy:
            // withFrameNanos resumes before the layout pass, so directly after a scroll the
            // row bounds in holder.slotBounds are still the pre-scroll ones.
            if (hoverStale) {
                onHoverRefresh()
                hoverStale = false
            }
            val seconds = if (lastNanos == 0L) 0f else (nanos - lastNanos) / 1_000_000_000f
            lastNanos = nanos
            if (seconds <= 0f) continue
            val pos = fingerPos.value
            val pane = when {
                holder.slotsPaneBounds.contains(pos) -> holder.slotsPaneBounds to slotsScroll
                holder.poolPaneBounds.contains(pos) -> holder.poolPaneBounds to poolScroll
                else -> null
            } ?: continue
            val (rect, state) = pane
            val topDepth = (rect.top + edgeZonePx - pos.y).coerceAtLeast(0f)
            val bottomDepth = (pos.y - (rect.bottom - edgeZonePx)).coerceAtLeast(0f)
            val delta = when {
                topDepth > 0f && state.canScrollBackward ->
                    -(topDepth / edgeZonePx).coerceAtMost(1f) * pxPerSecond * seconds
                bottomDepth > 0f && state.canScrollForward ->
                    (bottomDepth / edgeZonePx).coerceAtMost(1f) * pxPerSecond * seconds
                else -> 0f
            }
            if (delta != 0f) {
                state.scrollBy(delta)
                hoverStale = true
            }
        }
    }
}

// endregion

// region Slots column

@Composable
private fun SlotsColumn(
    holder: RankingDragStateHolder,
    showFlag: Boolean,
    topN: Int,
    drag: DragState,
    compactLabels: Boolean,
    scrollState: ScrollState,
    onDragStart: (BetOption, Rect, Offset) -> Int,
    onDrag: (Int, Offset) -> Unit,
    onDragEnd: (Int) -> Unit,
    onDragCancel: (Int) -> Unit,
    onTapSlot: (Int) -> Unit,
    minHeight: Dp,
    onHeightMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val returnToPoolLabel = stringResource(Res.string.ranking_a11y_return_to_pool)

    Column(
        // Modifier order is load-bearing:
        // 1. onSizeChanged sits BELOW heightIn so it reports the height the pane actually
        //    settled on (SideBySideLayout feeds it back as the other pane's minimum).
        // 2. onGloballyPositioned MUST come BEFORE verticalScroll — below it, it would
        //    report the scrolling content rect instead of the pane viewport rect.
        // 3. verticalScroll is legal here only because RankingDragList caps the height
        //    with heightIn(max = paneMaxHeight) above this subtree. No explicit clip is
        //    needed either: verticalScroll applies clipScrollableContainer itself.
        modifier = modifier
            .heightIn(min = minHeight)
            .onSizeChanged { size -> onHeightMeasured(size.height) }
            .onGloballyPositioned { coords -> holder.slotsPaneBounds = coords.boundsInRoot() }
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
            .verticalScroll(scrollState)
            .padding(standardPaddingSmall),
    ) {
        // Gap indicators are ALWAYS laid out (only painted while hovered) so starting a
        // drag cannot change content height and shift every row mid-gesture.
        GapIndicator(active = drag is DragState.Hovering && drag.target == DropTarget.Gap(0))
        for (i in 0 until topN) {
            val occupant = holder.slots.getOrNull(i)
            val dragItemId = when (drag) {
                is DragState.Picking -> drag.itemId
                is DragState.Hovering -> drag.itemId
                DragState.Idle -> null
            }
            val isBeingDragged = occupant != null && occupant.id == dragItemId
            // Slot lights up when its center is the hover target (place/swap).
            val isHoverTarget = drag is DragState.Hovering && drag.target is DropTarget.Slot && drag.target.index == i

            SlotRow(
                index = i,
                occupant = occupant,
                showFlag = showFlag,
                compactLabels = compactLabels,
                isBeingDragged = isBeingDragged,
                isHoverTarget = isHoverTarget,
                returnToPoolLabel = returnToPoolLabel,
                isDragActive = { holder.drag !is DragState.Idle },
                onBoundsChanged = { bounds -> holder.slotBounds[i] = bounds },
                onDragStart = { bounds, fingerInRoot ->
                    if (occupant != null) onDragStart(occupant, bounds, fingerInRoot) else 0
                },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onTap = { if (occupant != null) onTapSlot(i) },
            )
            GapIndicator(active = drag is DragState.Hovering && drag.target == DropTarget.Gap(i + 1))
        }
    }
}

@Composable
private fun GapIndicator(active: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RankingDragDefaults.GapIndicatorHeight)
            .then(
                if (active) {
                    Modifier.background(color = color, shape = androidx.compose.foundation.shape.CircleShape)
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun SlotRow(
    index: Int,
    occupant: BetOption?,
    showFlag: Boolean,
    compactLabels: Boolean,
    isBeingDragged: Boolean,
    isHoverTarget: Boolean,
    returnToPoolLabel: String,
    isDragActive: () -> Boolean,
    onBoundsChanged: (RowBounds) -> Unit,
    onDragStart: (sourceBounds: Rect, fingerInRoot: Offset) -> Int,
    onDrag: (Int, Offset) -> Unit,
    onDragEnd: (Int) -> Unit,
    onDragCancel: (Int) -> Unit,
    onTap: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "slot_hover")
    val pulseBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(animation = tween(600)),
        label = "border_alpha",
    )
    val borderColor = when {
        isHoverTarget -> MaterialTheme.colorScheme.primary.copy(alpha = pulseBorderAlpha)
        occupant != null -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    val haptics = LocalHapticFeedback.current

    // UNCLIPPED row rect: used as the ghost's source geometry and as the origin for
    // converting a detector-local start offset into root coords. boundsInRoot() is
    // clipped by the pane's scroller, so it must not be used for either.
    var rowFullInRoot by remember { mutableStateOf(Rect.Zero) }

    // Drag entry point 1: long-press anywhere on a filled row.
    // - Plain clickable, NOT combinedClickable with a no-op onLongClick: a non-null onLongClick
    //   publishes SemanticsActions.OnLongClick (Clickable.kt:1563), so TalkBack would offer
    //   "double-tap and hold" for an action that does nothing.
    // - Long-press-then-release still does NOT remove the item: on a graceful end the drag
    //   detector consumes the up (DragGestureDetector.kt:395-396) and clickable only fires
    //   onClick for an unconsumed up (Clickable.kt:895, changedToUp() = !isConsumed && …).
    // - The drag pointerInput MUST stay AFTER clickable in the chain: the Main pointer pass
    //   reaches the inner (later) node first, so the drag detector consumes the change before
    //   clickable sees it.
    val dragModifier = if (occupant != null) {
        Modifier
            .clickable(onClick = onTap)
            .pointerInput(occupant.id) {
                var token = 0
                detectDragGesturesAfterLongPress(
                    onDragStart = { startPos ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        token = onDragStart(rowFullInRoot, rowFullInRoot.topLeft + startPos)
                    },
                    onDrag = { _, delta -> onDrag(token, delta) },
                    onDragEnd = { onDragEnd(token) },
                    onDragCancel = { onDragCancel(token) },
                )
            }
    } else Modifier

    val semanticsModifier = if (occupant != null) {
        Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(label = returnToPoolLabel, action = { onTap(); true }),
            )
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RankingDragDefaults.RowMinHeight)
            .testTag("ranking_slot_$index")
            .onGloballyPositioned { coords ->
                val full = Rect(coords.positionInRoot(), coords.size.toSize())
                rowFullInRoot = full
                onBoundsChanged(RowBounds(visible = coords.boundsInRoot(), full = full))
            }
            .then(
                if (occupant == null) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = borderColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                                cap = StrokeCap.Round,
                            ),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                    }
                } else {
                    Modifier.border(
                        width = if (isHoverTarget) 2.dp else 1.dp,
                        color = borderColor,
                        shape = MaterialTheme.shapes.small,
                    )
                },
            )
            .background(
                color = if (occupant != null) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .alpha(if (isBeingDragged) 0.3f else 1f)
            .then(dragModifier)
            .then(semanticsModifier)
            .padding(horizontal = standardPaddingSmall, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (occupant != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SlotNumber(number = index + 1, compact = compactLabels)
                    if (showFlag && occupant.countryCode != null) {
                        FlagImage(code = occupant.countryCode, size = 20.dp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // Drag entry point 2: immediate drag from the handle, no long press.
                    DragHandle(
                        gestureKey = occupant.id,
                        isDragActive = isDragActive,
                        onDragStart = { fingerInRoot -> onDragStart(rowFullInRoot, fingerInRoot) },
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    )
                }
                Text(
                    text = occupant.label,
                    style = if (compactLabels) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SlotNumber(number = index + 1, dim = true, compact = compactLabels)
                Text(
                    text = stringResource(Res.string.ranking_drop_here_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Plain numbered slot indicator. A circle with the slot number — matches plan §4
 * "1 / 2 / 3" placement labels (the leaderboard-style [RankChip] is intentionally
 * not used here because it reads as "current rank out of N competitors").
 */
@Composable
private fun SlotNumber(number: Int, dim: Boolean = false, compact: Boolean = false) {
    val bg = if (dim) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    val fg = if (dim) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .background(color = bg, shape = androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$number",
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

// endregion

// region Pool list

@Composable
private fun PoolList(
    holder: RankingDragStateHolder,
    showFlag: Boolean,
    topN: Int,
    drag: DragState,
    compactLabels: Boolean,
    scrollState: ScrollState,
    onDragStart: (BetOption, Rect, Offset) -> Int,
    onDrag: (Int, Offset) -> Unit,
    onDragEnd: (Int) -> Unit,
    onDragCancel: (Int) -> Unit,
    onDropToSlot: (itemId: String, slotIndex: Int) -> Unit,
    minHeight: Dp,
    onHeightMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDragActive = drag !is DragState.Idle

    // stringResource is @Composable and cannot be called inside semantics {}, so the
    // per-slot a11y labels are hoisted here (List's initializer is inline, so calling a
    // composable in it is legal).
    val moveToSlotLabels = List(topN) { index ->
        stringResource(Res.string.ranking_a11y_move_to_slot, index + 1)
    }
    val emptyPoolHint = stringResource(Res.string.ranking_drop_here_hint)

    // Stays a plain Column rather than a LazyColumn: the row that owns the active
    // pointerInput must stay composed for the whole gesture, and lazy recycling would
    // dispose it the moment auto-scroll pushed it out of the pane — which cancels the
    // pointer coroutine and kills the drag. Option counts are small (a few dozen).
    Column(
        // Modifier order is load-bearing — see SlotsColumn.
        modifier = modifier
            .heightIn(min = minHeight)
            .onSizeChanged { size -> onHeightMeasured(size.height) }
            .onGloballyPositioned { coords -> holder.poolPaneBounds = coords.boundsInRoot() }
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
            .verticalScroll(scrollState)
            .padding(standardPaddingSmall),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val dragItemId = when (drag) {
            is DragState.Picking -> drag.itemId
            is DragState.Hovering -> drag.itemId
            DragState.Idle -> null
        }
        if (holder.pool.isEmpty()) {
            // Keeps the empty pool readable as a drop target instead of a blank box.
            Text(
                text = emptyPoolHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(vertical = standardPaddingSmall),
            )
        }
        holder.pool.forEach { item ->
            androidx.compose.runtime.key(item.id) {
                val isBeingDragged = item.id == dragItemId
                PoolRow(
                    item = item,
                    showFlag = showFlag,
                    compactLabels = compactLabels,
                    moveToSlotLabels = moveToSlotLabels,
                    isBeingDragged = isBeingDragged,
                    dimForDrag = isDragActive && !isBeingDragged,
                    isDragActiveNow = { holder.drag !is DragState.Idle },
                    onDragStart = { bounds, fingerInRoot -> onDragStart(item, bounds, fingerInRoot) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    onDropToSlot = { slotIndex -> onDropToSlot(item.id, slotIndex) },
                )
            }
        }
    }
}

@Composable
private fun PoolRow(
    item: BetOption,
    showFlag: Boolean,
    compactLabels: Boolean,
    moveToSlotLabels: List<String>,
    isBeingDragged: Boolean,
    dimForDrag: Boolean,
    isDragActiveNow: () -> Boolean,
    onDragStart: (sourceBounds: Rect, fingerInRoot: Offset) -> Int,
    onDrag: (Int, Offset) -> Unit,
    onDragEnd: (Int) -> Unit,
    onDragCancel: (Int) -> Unit,
    onDropToSlot: (slotIndex: Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    // UNCLIPPED row rect — see SlotRow.
    var rowFullInRoot by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RankingDragDefaults.RowMinHeight)
            .testTag("ranking_pool_${item.id}")
            .onGloballyPositioned { coords ->
                rowFullInRoot = Rect(coords.positionInRoot(), coords.size.toSize())
            }
            .alpha(
                when {
                    isBeingDragged -> 0.3f
                    dimForDrag -> 0.6f
                    else -> 1f
                },
            )
            .background(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small)
            .pointerInput(item.id) {
                var token = 0
                detectDragGesturesAfterLongPress(
                    onDragStart = { startPos ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        token = onDragStart(rowFullInRoot, rowFullInRoot.topLeft + startPos)
                    },
                    onDrag = { _, delta -> onDrag(token, delta) },
                    onDragEnd = { onDragEnd(token) },
                    onDragCancel = { onDragCancel(token) },
                )
            }
            .semantics {
                customActions = moveToSlotLabels.mapIndexed { slotIndex, label ->
                    CustomAccessibilityAction(label = label, action = { onDropToSlot(slotIndex); true })
                }
            }
            .padding(horizontal = standardPaddingSmall, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Two-row content mirroring SlotRow: equal 50/50 panes are too narrow to fit
        // flag + label + handle on one line, so the label gets the full pane width.
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showFlag && item.countryCode != null) {
                    FlagImage(code = item.countryCode, size = 20.dp)
                }
                Spacer(modifier = Modifier.weight(1f))
                DragHandle(
                    gestureKey = item.id,
                    isDragActive = isDragActiveNow,
                    onDragStart = { fingerInRoot -> onDragStart(rowFullInRoot, fingerInRoot) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            }
            Text(
                text = item.label,
                style = if (compactLabels) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

/**
 * The "≡" grip. Starts a drag on touch-slop with no long press, wired to the SAME handlers
 * as the row's long-press detector.
 *
 * Hand-rolled instead of `detectDragGestures` because two things need explicit control over
 * consumption, both verified against foundation 1.11.0 sources:
 *
 * 1. The down is consumed. `clickable` only arms on an UNCONSUMED down
 *    (`Clickable.kt:891 isChangedToDown(requireUnconsumed = true)`), so a tap on the ≡ of a
 *    filled slot can no longer fall through to the row's tap-to-remove.
 * 2. Consuming the down does NOT stop the row's long-press detector — that awaits its down
 *    with `requireUnconsumed = false` (`DragGestureDetector.kt:384`) and its long-press timer
 *    keeps running while the finger rests on the handle. So when slop is finally crossed and
 *    a drag is ALREADY live, this detector steps aside without consuming: consuming would make
 *    the row's `drag()` see a consumed change and cancel the live drag mid-gesture.
 *
 * [onDragStart] receives the finger position already converted to ROOT coords, because the
 * change position is local to this handle, not to the row.
 *
 * Width/height are fixed instead of fillMaxHeight: fillMaxHeight is a no-op under the
 * unbounded height constraints a verticalScroll subtree hands down.
 */
@Composable
private fun DragHandle(
    gestureKey: String,
    isDragActive: () -> Boolean,
    onDragStart: (fingerInRoot: Offset) -> Int,
    onDrag: (Int, Offset) -> Unit,
    onDragEnd: (Int) -> Unit,
    onDragCancel: (Int) -> Unit,
) {
    var handleOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .width(RankingDragDefaults.HandleTouchSize)
            .heightIn(min = RankingDragDefaults.HandleTouchSize)
            .onGloballyPositioned { coords -> handleOriginInRoot = coords.positionInRoot() }
            .pointerInput(gestureKey) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var token = 0
                    var owns = false
                    var travelled = Offset.Zero
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                if (owns) onDragCancel(token)
                                break
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                if (owns) {
                                    change.consume()
                                    onDragEnd(token)
                                }
                                break
                            }
                            if (owns) {
                                onDrag(token, change.positionChange())
                                change.consume()
                                continue
                            }
                            travelled += change.positionChange()
                            if (travelled.getDistance() < slop) continue
                            if (isDragActive()) break   // row's long press already owns this pointer
                            token = onDragStart(handleOriginInRoot + change.position)
                            owns = true
                            onDrag(token, travelled)
                            change.consume()
                        }
                    } catch (cancellation: CancellationException) {
                        if (owns) onDragCancel(token)
                        throw cancellation
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "≡",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

// region Shared row content

/** Ghost-overlay row content. Both panes render their own two-row layout inline. */
@Composable
private fun OptionRowContent(
    option: BetOption,
    showFlag: Boolean,
    compactLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showFlag && option.countryCode != null) {
            FlagImage(code = option.countryCode, size = 20.dp)
        }
        Text(
            text = option.label,
            style = if (compactLabels) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// endregion

// region Previews

private val previewOptions = listOf(
    BetOption(id = "ar", label = "Argentina", countryCode = "AR"),
    BetOption(id = "be", label = "Belgium", countryCode = "BE"),
    BetOption(id = "hr", label = "Croatia", countryCode = "HR"),
    BetOption(id = "dk", label = "Denmark", countryCode = "DK"),
    BetOption(id = "fi", label = "Finland", countryCode = "FI"),
    BetOption(id = "fr", label = "France", countryCode = "FR"),
    BetOption(id = "de", label = "Germany", countryCode = "DE"),
    BetOption(id = "it", label = "Italy", countryCode = "IT"),
)

@Composable
private fun PreviewFrame(width: Dp? = null, content: @Composable () -> Unit) {
    se.atte.bragwise.theme.ThemePreview {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalSnackbarHost provides androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() },
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(720.dp).padding(standardPadding)) {
                // width simulates the narrow card the widget actually gets inside
                // PredictScreen (screen width minus LazyColumn + SectionCard padding).
                Box(modifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Side-by-side — empty", showBackground = true)
@Composable
private fun RankingDragList_Empty_Preview() {
    PreviewFrame {
        RankingDragList(
            options = previewOptions,
            topN = 3,
            orderedOptionIds = emptyList(),
            showFlag = true,
            onReorder = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Side-by-side — partial", showBackground = true)
@Composable
private fun RankingDragList_Partial_Preview() {
    PreviewFrame {
        RankingDragList(
            options = previewOptions,
            topN = 3,
            orderedOptionIds = listOf("fr"),
            showFlag = true,
            onReorder = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Side-by-side — full", showBackground = true)
@Composable
private fun RankingDragList_Full_Preview() {
    PreviewFrame {
        RankingDragList(
            options = previewOptions,
            topN = 3,
            orderedOptionIds = listOf("fi", "fr", "de"),
            showFlag = true,
            onReorder = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Side-by-side — no flags", showBackground = true)
@Composable
private fun RankingDragList_NoFlags_Preview() {
    PreviewFrame {
        RankingDragList(
            options = listOf(
                BetOption(id = "a", label = "Player A"),
                BetOption(id = "b", label = "Player B"),
                BetOption(id = "c", label = "Player C"),
                BetOption(id = "d", label = "Player D"),
            ),
            topN = 3,
            orderedOptionIds = listOf("b"),
            showFlag = false,
            onReorder = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "topN 8 — side-by-side", showBackground = true)
@Composable
private fun RankingDragList_TopN8_Preview() {
    PreviewFrame {
        RankingDragList(
            options = previewOptions,
            topN = 8,
            orderedOptionIds = listOf("ar", "be"),
            showFlag = true,
            onReorder = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "topN 8 — narrow card", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun RankingDragList_Narrow_Preview() {
    PreviewFrame(width = 296.dp) {
        RankingDragList(
            options = previewOptions,
            topN = 8,
            orderedOptionIds = listOf("ar", "be"),
            showFlag = true,
            onReorder = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "topN 4 — long labels", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun RankingDragList_LongLabels_Preview() {
    PreviewFrame(width = 296.dp) {
        RankingDragList(
            options = listOf(
                BetOption(id = "a", label = "Bosnia and Herzegovina", countryCode = "BA"),
                BetOption(id = "b", label = "United Kingdom of Great Britain", countryCode = "GB"),
                BetOption(id = "c", label = "Netherlands", countryCode = "NL"),
                BetOption(id = "d", label = "Czechia", countryCode = "CZ"),
            ),
            topN = 4,
            orderedOptionIds = listOf("a"),
            showFlag = true,
            onReorder = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "topN 8 — pool empty", showBackground = true)
@Composable
private fun RankingDragList_PoolEmpty_Preview() {
    PreviewFrame {
        RankingDragList(
            options = previewOptions,
            topN = 8,
            orderedOptionIds = previewOptions.map { it.id },
            showFlag = true,
            onReorder = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "topN 8 — RTL", showBackground = true)
@Composable
private fun RankingDragList_Rtl_Preview() {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
    ) {
        PreviewFrame(width = 296.dp) {
            RankingDragList(
                options = previewOptions,
                topN = 8,
                orderedOptionIds = listOf("ar", "be"),
                showFlag = true,
                onReorder = {},
            )
        }
    }
}

// endregion
