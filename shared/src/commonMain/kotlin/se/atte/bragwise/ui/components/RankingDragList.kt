package se.atte.bragwise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.ranking_a11y_move_to_slot
import bragwise.shared.generated.resources.ranking_a11y_return_to_pool
import bragwise.shared.generated.resources.ranking_drop_here_hint
import bragwise.shared.generated.resources.ranking_slot_removed
import bragwise.shared.generated.resources.ranking_undo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.ui.LocalSnackbarHost
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import kotlin.math.roundToInt

// region Defaults

private object RankingDragDefaults {
    /** Plan §4: stack into a pager only when topN exceeds this many slots on compact widths. */
    const val PagerStackTopN = 6
    val PagerEdgeZone = 24.dp
    val PagerAutoFlipDelayMs = 400L
    val SlotHeight = 72.dp
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

    val slotBounds = mutableMapOf<Int, Rect>()
    val poolItemBounds = mutableMapOf<String, Rect>()
    var poolColumnBounds: Rect = Rect.Zero

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

    fun findTargetAt(pos: Offset): DropTarget? {
        for ((index, bounds) in slotBounds) {
            if (!bounds.contains(pos)) continue
            // Empty slot: whole area places directly into that slot.
            if (_slots.getOrNull(index) == null) return DropTarget.Slot(index)
            // Filled slot: top third = insert above, bottom third = insert below
            // (ripples items down), middle third = swap into this slot.
            val relY = pos.y - bounds.top
            val third = bounds.height / 3f
            return when {
                relY < third -> DropTarget.Gap(index)
                relY > third * 2f -> DropTarget.Gap(index + 1)
                else -> DropTarget.Slot(index)
            }
        }
        if (poolColumnBounds != Rect.Zero && poolColumnBounds.contains(pos)) return DropTarget.Pool
        return null
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

    val density = LocalDensity.current

    // Absolute finger position in root coords, kept in sync with the ongoing drag
    val fingerPos = remember { mutableStateOf(Offset.Zero) }

    fun handleDragStart(item: BetOption, sourceBounds: Rect, startPosInRow: Offset) {
        ghostItem = item
        ghostSize = sourceBounds.size
        ghostSourceLeft = sourceBounds.left
        ghostSourceTop = sourceBounds.top
        val fingerAbs = sourceBounds.topLeft + startPosInRow
        fingerPos.value = fingerAbs
        val centeredTopLeft = Offset(
            x = fingerAbs.x - sourceBounds.width / 2f,
            y = fingerAbs.y - sourceBounds.height / 2f,
        )
        scope.launch {
            ghostX.snapTo(centeredTopLeft.x)
            ghostY.snapTo(centeredTopLeft.y)
        }
        holder.startDrag(item.id)
    }

    fun handleDrag(delta: Offset) {
        val newFinger = fingerPos.value + delta
        fingerPos.value = newFinger
        scope.launch {
            ghostX.snapTo(newFinger.x - ghostSize.width / 2f)
            ghostY.snapTo(newFinger.y - ghostSize.height / 2f)
        }
        holder.hover(holder.findTargetAt(newFinger))
    }

    fun handleDragEnd() {
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

    fun handleDragCancel() {
        holder.cancelDrag()
        ghostItem = null
    }

    // Root-space origin of this composable, used to convert ghost root coords → local offset.
    // ghostX/ghostY are stored in root coordinates (boundsInRoot of the dragged row).
    // Modifier.offset is relative to the local container, so we subtract the container origin.
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { coords ->
            containerOrigin = coords.boundsInRoot().topLeft
        },
    ) {
        // Plan §4: side-by-side by default; stack into a swipe-pager only when topN > 6.
        val isCompact = topN > RankingDragDefaults.PagerStackTopN

        Box(modifier = Modifier.fillMaxSize()) {
            if (isCompact) {
                PagerLayout(
                    holder = holder,
                    showFlag = showFlag,
                    topN = topN,
                    drag = holder.drag,
                    ghostItemId = ghostItem?.id,
                    fingerPos = fingerPos,
                    edgeZonePx = with(density) { RankingDragDefaults.PagerEdgeZone.toPx() },
                    onDragStart = ::handleDragStart,
                    onDrag = ::handleDrag,
                    onDragEnd = ::handleDragEnd,
                    onDragCancel = ::handleDragCancel,
                    onTapSlot = ::onTapSlot,
                    onDropFromPool = { itemId, slotIndex ->
                        val newOrder = holder.applyDrop(sourceItemId = itemId, target = DropTarget.Slot(slotIndex))
                        onReorder(newOrder)
                    },
                )
            } else {
                SideBySideLayout(
                    holder = holder,
                    showFlag = showFlag,
                    topN = topN,
                    drag = holder.drag,
                    ghostItemId = ghostItem?.id,
                    onDragStart = ::handleDragStart,
                    onDrag = ::handleDrag,
                    onDragEnd = ::handleDragEnd,
                    onDragCancel = ::handleDragCancel,
                    onTapSlot = ::onTapSlot,
                    onDropFromPool = { itemId, slotIndex ->
                        val newOrder = holder.applyDrop(sourceItemId = itemId, target = DropTarget.Slot(slotIndex))
                        onReorder(newOrder)
                    },
                )
            }

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
                    OptionRowContent(option = safeGhostItem, showFlag = showFlag)
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
    ghostItemId: String?,
    onDragStart: (BetOption, Rect, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTapSlot: (Int) -> Unit,
    onDropFromPool: (itemId: String, slotIndex: Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        SlotsColumn(
            holder = holder,
            showFlag = showFlag,
            topN = topN,
            drag = drag,
            ghostItemId = ghostItemId,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            onTapSlot = onTapSlot,
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        PoolList(
            holder = holder,
            showFlag = showFlag,
            topN = topN,
            drag = drag,
            ghostItemId = ghostItemId,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            onDropToSlot = onDropFromPool,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PagerLayout(
    holder: RankingDragStateHolder,
    showFlag: Boolean,
    topN: Int,
    drag: DragState,
    ghostItemId: String?,
    fingerPos: androidx.compose.runtime.MutableState<Offset>,
    edgeZonePx: Float,
    onDragStart: (BetOption, Rect, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTapSlot: (Int) -> Unit,
    onDropFromPool: (itemId: String, slotIndex: Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // Plan §4: auto-flip the pager when a drag is held near the horizontal edge for >400 ms.
    var pagerBounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(drag) {
        if (drag is DragState.Idle) return@LaunchedEffect
        while (isActive) {
            if (pagerBounds != Rect.Zero) {
                val px = fingerPos.value.x
                if (px > pagerBounds.right - edgeZonePx && pagerState.currentPage == 0) {
                    delay(RankingDragDefaults.PagerAutoFlipDelayMs)
                    if (drag !is DragState.Idle) scope.launch { pagerState.animateScrollToPage(1) }
                } else if (px < pagerBounds.left + edgeZonePx && pagerState.currentPage == 1) {
                    delay(RankingDragDefaults.PagerAutoFlipDelayMs)
                    if (drag !is DragState.Idle) scope.launch { pagerState.animateScrollToPage(0) }
                }
            }
            delay(100)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().onGloballyPositioned { coords -> pagerBounds = coords.boundsInRoot() },
    ) { page ->
        when (page) {
            0 -> SlotsColumn(
                holder = holder,
                showFlag = showFlag,
                topN = topN,
                drag = drag,
                ghostItemId = ghostItemId,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onTapSlot = onTapSlot,
                modifier = Modifier.fillMaxSize(),
            )
            else -> PoolList(
                holder = holder,
                showFlag = showFlag,
                topN = topN,
                drag = drag,
                ghostItemId = ghostItemId,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDropToSlot = onDropFromPool,
                modifier = Modifier.fillMaxSize(),
            )
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
    ghostItemId: String?,
    onDragStart: (BetOption, Rect, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onTapSlot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val returnToPoolLabel = stringResource(Res.string.ranking_a11y_return_to_pool)

    val isDragActive = drag !is DragState.Idle

    Column(
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
            .padding(standardPaddingSmall),
    ) {
        // Insert-above-first indicator.
        if (isDragActive) {
            GapIndicator(active = drag is DragState.Hovering && drag.target == DropTarget.Gap(0))
        }
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
                topN = topN,
                occupant = occupant,
                showFlag = showFlag,
                isBeingDragged = isBeingDragged,
                isHoverTarget = isHoverTarget,
                returnToPoolLabel = returnToPoolLabel,
                onBoundsChanged = { bounds -> holder.slotBounds[i] = bounds },
                onDragStart = { bounds, startPos -> if (occupant != null) onDragStart(occupant, bounds, startPos) },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onTap = { if (occupant != null) onTapSlot(i) },
            )
            // Insert-between indicator (or plain spacer when not dragging).
            if (isDragActive) {
                GapIndicator(active = drag is DragState.Hovering && drag.target == DropTarget.Gap(i + 1))
            } else if (i < topN - 1) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun GapIndicator(active: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (active) 6.dp else 4.dp)
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
    topN: Int,
    occupant: BetOption?,
    showFlag: Boolean,
    isBeingDragged: Boolean,
    isHoverTarget: Boolean,
    returnToPoolLabel: String,
    onBoundsChanged: (Rect) -> Unit,
    onDragStart: (sourceBounds: Rect, startPosInRow: Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
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

    var rowBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    val dragModifier = if (occupant != null) {
        Modifier
            .clickable { onTap() }
            .pointerInput(occupant.id) {
                detectDragGestures(
                    onDragStart = { startPos -> onDragStart(rowBoundsInRoot, startPos) },
                    onDrag = { _, delta -> onDrag(delta) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
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
            .height(RankingDragDefaults.SlotHeight)
            .testTag("ranking_slot_$index")
            .onGloballyPositioned { coords ->
                rowBoundsInRoot = coords.boundsInRoot()
                onBoundsChanged(rowBoundsInRoot)
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
                    SlotNumber(number = index + 1)
                    if (showFlag && occupant.countryCode != null) {
                        FlagImage(code = occupant.countryCode, size = 20.dp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "≡", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = occupant.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SlotNumber(number = index + 1, dim = true)
                Text(
                    text = stringResource(Res.string.ranking_drop_here_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
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
private fun SlotNumber(number: Int, dim: Boolean = false) {
    val bg = if (dim) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    val fg = if (dim) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .background(color = bg, shape = androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "$number", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = fg)
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
    ghostItemId: String?,
    onDragStart: (BetOption, Rect, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDropToSlot: (itemId: String, slotIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDragActive = drag !is DragState.Idle

    // Pool is a regular Column rather than LazyColumn so RankingDragList can be hosted
    // inside another scroller (PredictScreen's LazyColumn) without nested-scroll errors.
    // Option counts are small (a few dozen at most), so non-lazy rendering is cheap.
    Column(
        modifier = modifier
            .onGloballyPositioned { coords -> holder.poolColumnBounds = coords.boundsInRoot() }
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
            .padding(horizontal = standardPaddingSmall, vertical = standardPaddingSmall),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val dragItemId = when (drag) {
            is DragState.Picking -> drag.itemId
            is DragState.Hovering -> drag.itemId
            DragState.Idle -> null
        }
        holder.pool.forEach { item ->
            androidx.compose.runtime.key(item.id) {
                val isBeingDragged = item.id == dragItemId
                PoolRow(
                    item = item,
                    showFlag = showFlag,
                    topN = topN,
                    isBeingDragged = isBeingDragged,
                    dimForDrag = isDragActive && !isBeingDragged,
                    onBoundsChanged = { bounds -> holder.poolItemBounds[item.id] = bounds },
                    onDragStart = { bounds, startPos -> onDragStart(item, bounds, startPos) },
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
    topN: Int,
    isBeingDragged: Boolean,
    dimForDrag: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onDragStart: (sourceBounds: Rect, startPosInRow: Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDropToSlot: (slotIndex: Int) -> Unit,
) {
    var rowBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RankingDragDefaults.SlotHeight)
            .testTag("ranking_pool_${item.id}")
            .onGloballyPositioned { coords ->
                rowBoundsInRoot = coords.boundsInRoot()
                onBoundsChanged(rowBoundsInRoot)
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
                detectDragGestures(
                    onDragStart = { startPos -> onDragStart(rowBoundsInRoot, startPos) },
                    onDrag = { _, delta -> onDrag(delta) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            }
            .semantics {
                customActions = buildList {
                    repeat(topN) { j ->
                        add(CustomAccessibilityAction(label = "Move to slot ${j + 1}", action = { onDropToSlot(j); true }))
                    }
                }
            }
            .padding(horizontal = standardPaddingSmall, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OptionRowContent(option = item, showFlag = showFlag, modifier = Modifier.weight(1f))
        Text(text = "≡", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// endregion

// region Shared row content

@Composable
private fun OptionRowContent(
    option: BetOption,
    showFlag: Boolean,
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
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
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
private fun PreviewFrame(content: @Composable () -> Unit) {
    se.atte.bragwise.theme.ThemePreview {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalSnackbarHost provides androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() },
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(420.dp).padding(standardPadding)) {
                content()
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

@androidx.compose.ui.tooling.preview.Preview(name = "Pager fallback — topN > 6", showBackground = true)
@Composable
private fun RankingDragList_Pager_Preview() {
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

// endregion
