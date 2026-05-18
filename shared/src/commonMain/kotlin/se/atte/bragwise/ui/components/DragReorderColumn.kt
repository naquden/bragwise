package se.atte.bragwise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A [Column] whose items can be long-pressed and dragged to reorder.
 * KMP-safe — uses only compose-foundation gestures available in commonMain.
 *
 * [items] is the ordered list to render. [key] provides a stable identity
 * per item. [onReorder] fires once when the user releases with the new order.
 * [itemContent] receives the item and its current visual index (0-based).
 *
 * Animation: each row animates its Y offset with a spring on drag; on release
 * it snaps to its new slot and [onReorder] is called with the rearranged list.
 */
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, index: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Mutable ordered list tracked inside composition
    var orderedItems by remember(items) { mutableStateOf(items) }

    // Per-item heights and Y positions (measured on layout)
    val itemHeights = remember(orderedItems.size) { mutableListOf<Float>().apply { repeat(orderedItems.size) { add(0f) } } }
    val itemTops = remember(orderedItems.size) { mutableListOf<Float>().apply { repeat(orderedItems.size) { add(0f) } } }

    // Drag state
    var draggingIndex by remember { mutableStateOf(-1) }
    val dragOffset = remember { Animatable(0f) }
    var cumulativeDrag by remember { mutableStateOf(0f) }

    Column(modifier = modifier) {
        orderedItems.forEachIndexed { index, item ->
            key(key(item)) {
                val isDragging = draggingIndex == index

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (index < itemHeights.size) {
                                itemHeights[index] = coords.size.height.toFloat()
                                itemTops[index] = coords.positionInParent().y
                            }
                        }
                        .then(
                            if (isDragging) Modifier.offset {
                                IntOffset(0, dragOffset.value.roundToInt())
                            } else Modifier,
                        )
                        .pointerInput(orderedItems.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    cumulativeDrag = 0f
                                    scope.launch { dragOffset.snapTo(0f) }
                                },
                                onDrag = { _, delta ->
                                    cumulativeDrag += delta.y
                                    scope.launch { dragOffset.snapTo(cumulativeDrag) }

                                    // Compute where the centre of the dragged item currently sits
                                    val draggingTop = itemTops.getOrElse(draggingIndex) { 0f } + cumulativeDrag
                                    val draggingCentre = draggingTop + itemHeights.getOrElse(draggingIndex) { 0f } / 2f

                                    // Find the target slot
                                    val target = itemTops.indexOfFirst { top ->
                                        val bottom = top + itemHeights.getOrElse(itemTops.indexOf(top)) { 0f }
                                        draggingCentre in top..bottom
                                    }.takeIf { it >= 0 && it != draggingIndex } ?: draggingIndex

                                    if (target != draggingIndex) {
                                        val newList = orderedItems.toMutableList()
                                        val moved = newList.removeAt(draggingIndex)
                                        newList.add(target, moved)
                                        orderedItems = newList
                                        // Adjust cumulative drag to follow the row after reorder
                                        val heightMoved = itemHeights.getOrElse(draggingIndex) { 0f }
                                        cumulativeDrag += if (target < draggingIndex) heightMoved else -heightMoved
                                        scope.launch { dragOffset.snapTo(cumulativeDrag) }
                                        draggingIndex = target
                                    }
                                },
                                onDragEnd = {
                                    scope.launch {
                                        dragOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                        )
                                        draggingIndex = -1
                                        cumulativeDrag = 0f
                                        onReorder(orderedItems)
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        dragOffset.animateTo(0f)
                                        draggingIndex = -1
                                        cumulativeDrag = 0f
                                    }
                                },
                            )
                        },
                ) {
                    itemContent(item, index)
                }
            }
        }
    }
}
