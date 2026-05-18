package se.atte.bragwise.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Real drop-shadows on cards and primary buttons — deliberately inverting
 * Material 3's tonal-elevation default. Load-bearing for the
 * "tactile competition" pillar (plan §4).
 */
object Elevation {
    val None: Dp = 0.dp
    val Card: Dp = 2.dp
    val CardPressed: Dp = 4.dp
    val Hero: Dp = 4.dp
    val Sheet: Dp = 8.dp
    val Fab: Dp = 6.dp
    val FabPressed: Dp = 8.dp
    val AppBar: Dp = 2.dp
    val ButtonPrimary: Dp = 2.dp
    val ButtonPrimaryPressed: Dp = 4.dp
}

/**
 * Single call site for shadows so dark-mode alpha and shape stay consistent.
 * Default ambient/spot black at alpha 0 disappears against dark surfaces;
 * dark mode bumps alpha so the shadow remains visible.
 */
/**
 * Soft theme-aware shadow, clipped to the supplied shape so the shadow follows
 * rounded corners (passing `RectangleShape` here erased the bottom corner glow
 * on rounded cards). Default shape mirrors `AppShapes.medium` (12.dp).
 */
fun Modifier.appShadow(
    elevation: Dp,
    isDark: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.08f),
    spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.16f),
)
