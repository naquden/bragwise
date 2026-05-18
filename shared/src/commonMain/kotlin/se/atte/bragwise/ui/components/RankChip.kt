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
import androidx.compose.ui.unit.dp
import se.atte.bragwise.theme.AppType

/**
 * `#34 / 4 213` chip with overshoot spring on rank changes. The spring lives
 * on the displayed rank value (via `animateIntAsState`) so mid-animation
 * state updates do not snap to the new value. Callers pass raw rank ints.
 *
 * Plan §4 calls for a delta arrow (↑/↓); deferred until paired with a
 * historical-rank source.
 */
@Composable
fun RankChip(rank: Int, total: Int, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(
        targetValue = rank,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "rank",
    )
    Text(
        text = "#$animated / $total",
        style = AppType.rankBadge,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.onSurface,
    )
}
