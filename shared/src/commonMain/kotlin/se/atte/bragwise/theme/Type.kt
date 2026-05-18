package se.atte.bragwise.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Custom display tokens — M3 Typography has a fixed slot set, so the two
 * display tokens live as standalone TextStyles. `tnum` keeps width stable
 * during live updates of scores and ranks.
 */
object AppType {
    val scoreDisplay = TextStyle(
        fontSize = 72.sp,
        fontWeight = FontWeight.Black,
        fontFeatureSettings = "tnum",
    )
    val rankBadge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )
}

val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight(600)),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight(600),
        letterSpacing = 0.5.sp,
    ),
)
