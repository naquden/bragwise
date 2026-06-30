package se.atte.bragwise.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Unspecified
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.theme.Elevation
import se.atte.bragwise.theme.LocalIsDark
import se.atte.bragwise.theme.appShadow

/**
 * Plan §4 list-row card. Shows a rank chip when the viewer has joined; shows
 * a `🔒 LOCKED` marker when (now > locksAt && results not posted); shows a ✓
 * when `predicted = true` (current user has submitted predictions). Real shadow
 * per plan §4 elevation rules.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChallengeCard(
    challenge: Challenge,
    rank: Int? = null,
    predicted: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    accent: Boolean = false,
    surfaceColor: Color = Unspecified,
    caption: String? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
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
            .testTag("challenge_card_${challenge.id}")
            .then(
                if (onLongClick != null)
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                else
                    Modifier.clickable(onClick = onClick)
            ),
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
                        Icon(
                            imageVector = Lucide.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.width(standardPaddingSmall))
                    }
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (predicted) {
                    Icon(
                        imageVector = Lucide.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
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
                    when (challenge.status) {
                        ChallengeStatus.DRAFT -> Text(
                            text = "Draft",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ChallengeStatus.LOCKED,
                        ChallengeStatus.RESULTS_POSTED -> {
                            if (challenge.status == ChallengeStatus.RESULTS_POSTED) {
                                Text(
                                    text = "Finished",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Lucide.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "LOCKED",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(standardPaddingSmall))
                        }
                        else -> Unit
                    }
                    if (rank != null) {
                        RankChip(rank = rank)
                    }
                }
            }
            if (caption != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Challenge.visibilityLabel(): String = when (visibility) {
    Visibility.FRIENDS -> "Friends"
    Visibility.INVITE_ONLY -> "Invite only"
    Visibility.PROMOTED -> "Promoted"
}
