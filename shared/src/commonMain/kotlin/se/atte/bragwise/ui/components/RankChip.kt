package se.atte.bragwise.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import se.atte.bragwise.theme.AppType

/**
 * `#34` placement chip with overshoot spring on rank changes. The spring lives
 * on the displayed rank value (via `animateIntAsState`) so mid-animation
 * state updates do not snap to the new value. Callers pass raw rank ints.
 *
 * Plan §4 calls for a delta arrow (↑/↓); deferred until paired with a
 * historical-rank source.
 */
private val Gold = Color(0xFFFFD700).copy(alpha = 0.55f)
private val Silver = Color(0xFFCFD4DA)
private val SilverText = Color(0xFF2A2E33)
private val Bronze = Color(0xFFCD7F32)
private val BronzeText = Color.White

@Composable
fun RankChip(rank: Int, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(
        targetValue = rank,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "rank",
    )
    val (chipBg, chipText) = when (rank) {
        1 -> Gold to MaterialTheme.colorScheme.onSurface
        2 -> Silver to SilverText
        3 -> Bronze to BronzeText
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = "#$animated",
        style = AppType.rankBadge,
        modifier = modifier
            .background(
                color = chipBg,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = chipText,
    )
}
