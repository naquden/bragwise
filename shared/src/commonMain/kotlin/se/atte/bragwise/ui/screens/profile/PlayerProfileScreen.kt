package se.atte.bragwise.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.player_head_to_head
import bragwise.shared.generated.resources.player_no_shared_challenges
import bragwise.shared.generated.resources.player_not_found
import org.jetbrains.compose.resources.stringResource
import se.atte.bragwise.domain.HeadToHead
import se.atte.bragwise.domain.Player
import se.atte.bragwise.domain.PublicProfile
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.HeadToHeadPodium
import se.atte.bragwise.ui.components.SectionCard

/**
 * LB-04 Player profile — public view of another user. Identity card +
 * head-to-head record (when available).
 */
@Composable
fun PlayerProfileScreen(viewModel: PlayerProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val ui = state.ui) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.player_not_found))
        }
        is UiState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(ui.cause.toUserMessage())
        }
        is UiState.Ready -> PlayerProfileContent(data = ui.data)
    }
}

@Composable
private fun PlayerProfileContent(data: PlayerProfileViewModel.Data) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = data.profile.displayName) {
                Text("@${data.profile.username}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            SectionCard(title = stringResource(Res.string.player_head_to_head)) {
                val rec = data.head
                if (rec == null) {
                    Text(
                        stringResource(Res.string.player_no_shared_challenges),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    HeadToHeadPodium(
                        myDisplayName = data.me?.displayName ?: "You",
                        myAvatarSeed = data.me?.avatarSeed ?: "",
                        theirDisplayName = data.profile.displayName,
                        theirAvatarSeed = data.profile.avatarSeed,
                        record = rec,
                    )
                }
            }
        }
    }
}

// region Previews

private val previewMe = Player(
    uid = "u1",
    username = "me",
    displayName = "You",
    avatarSeed = "me",
    createdAt = kotlin.time.Instant.DISTANT_PAST,
)

@Preview
@Composable
private fun PlayerProfile_Preview() {
    ThemePreview {
        PlayerProfileContent(
            data = PlayerProfileViewModel.Data(
                profile = PublicProfile(uid = "u2", username = "alice", displayName = "Alice", avatarSeed = "alice"),
                me = previewMe,
                head = HeadToHead.Record(wins = 3, losses = 1, ties = 2),
            ),
        )
    }
}

@Preview
@Composable
private fun PlayerProfile_NoHistory_Preview() {
    ThemePreview {
        PlayerProfileContent(
            data = PlayerProfileViewModel.Data(
                profile = PublicProfile(uid = "u3", username = "bob", displayName = "Bob", avatarSeed = "bob"),
                me = previewMe,
                head = null,
            ),
        )
    }
}

// endregion
