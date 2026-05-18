package se.atte.bragwise.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rounded M3 shape scale. Slots used by app composables:
 *   - extraSmall (4dp) — chips
 *   - small      (8dp) — text fields, buttons
 *   - medium     (12dp) — cards, list groups
 *   - large      (16dp) — hero cards, dialogs, bottom sheets
 *   - extraLarge (28dp) — FAB, hero pills
 *
 * Replaces the previous all-`RectangleShape` SharpShapes — see decision.md
 * "Rounded refresh".
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Deprecated("Use AppShapes — kept for transition compatibility", ReplaceWith("AppShapes"))
val SharpShapes = AppShapes
