package se.atte.bragwise.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Unspecified
import androidx.compose.ui.unit.dp
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.theme.Elevation
import se.atte.bragwise.theme.appShadow

/**
 * Plan §4 list-row card. Shows a rank chip when the viewer has joined; shows
 * a `🔒 LOCKED` marker when (now > locksAt && results not posted); shows the
 * verified ✓ when `trusted = true`. Real shadow per plan §4 elevation rules.
 */
@Composable
fun ChallengeCard(
    challenge: Challenge,
    rank: Int? = null,
    onClick: () -> Unit = {},
    accent: Boolean = false,
    surfaceColor: Color = Unspecified,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val shape = MaterialTheme.shapes.medium
    val accentBorder = if (accent) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, shape)
    } else Modifier
    val resolvedColor = if (surfaceColor == Unspecified) MaterialTheme.colorScheme.surface else surfaceColor
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .appShadow(Elevation.Card, isDark = isDark, shape = shape)
            .then(accentBorder)
            .clickable(onClick = onClick),
        color = resolvedColor,
        shape = shape,
    ) {
        Column(Modifier.padding(standardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (accent) {
                        Text(
                            text = "⭐",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(standardPaddingSmall))
                    }
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (challenge.trusted) {
                    Text(text = "✓", color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = challenge.visibilityLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (challenge.status == ChallengeStatus.LOCKED ||
                        challenge.status == ChallengeStatus.RESULTS_POSTED
                    ) {
                        Text(
                            text = if (challenge.status == ChallengeStatus.RESULTS_POSTED) "Finished"
                                   else "🔒 LOCKED",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(standardPaddingSmall))
                    }
                    if (rank != null) {
                        RankChip(rank = rank)
                    }
                }
            }
        }
    }
}

private fun Challenge.visibilityLabel(): String = when (visibility) {
    Visibility.FRIENDS -> "Friends"
    Visibility.INVITE_ONLY -> "Invite only"
    Visibility.PROMOTED -> if (trusted) "Promoted · Verified" else "Promoted"
}
