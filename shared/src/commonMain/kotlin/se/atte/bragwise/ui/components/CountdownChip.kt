package se.atte.bragwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.composables.icons.lucide.Lucide
import se.atte.bragwise.ui.icons.LucideSparkles
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Live ticking countdown. Re-evaluates each second. ARIA-polite update
 * semantics belong on the parent screen — this component just renders.
 *
 * `Clock.System` from `kotlinx.datetime` is the plan's preference but isn't
 * wired as a dep yet; using the experimental `kotlin.time.Clock` is enough
 * here since we only need wall-clock seconds remaining.
 */
@Composable
fun CountdownChip(locksAt: Instant?, modifier: Modifier = Modifier) {
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }

    LaunchedEffect(locksAt) {
        if (locksAt != null) {
            while (true) {
                delay(1000)
                nowMs = Clock.System.now().toEpochMilliseconds()
            }
        }
    }

    val remainingSec = locksAt?.let {
        ((it.toEpochMilliseconds() - nowMs) / 1000L).coerceAtLeast(0L)
    }
    val isUrgent = remainingSec != null && remainingSec in 1..3600

    val contentColor = if (isUrgent) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(imageVector = LucideSparkles, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (remainingSec != null) formatRemaining(remainingSec) else "—",
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        )
    }
}

private fun formatRemaining(totalSec: Long): String {
    if (totalSec <= 0) return "Locked"
    val days = totalSec / 86_400
    val hours = (totalSec % 86_400) / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}
