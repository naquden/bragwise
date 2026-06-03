package se.atte.bragwise.ui.screens.invite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.preview.sampleCloudFriends

/**
 * CR-05 Invite friends — multi-select picker on top of cloud friends.
 * Local friends are explicitly excluded — invitations target uids.
 */
@Composable
fun InviteFriendsScreen(
    viewModel: InviteFriendsViewModel,
    snackbarHostState: SnackbarHostState,
    onSent: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { e ->
        when (e) {
            InviteFriendsViewModel.Effect.Sent -> onSent()
            is InviteFriendsViewModel.Effect.Snackbar ->
                snackbarHostState.showSnackbar(e.text)
        }
    }

    InviteFriendsContent(
        friends = state.friends,
        selected = state.selected,
        sending = state.sending,
        onToggle = { viewModel.onIntent(InviteFriendsViewModel.Intent.Toggle(it)) },
        onSend = { viewModel.onIntent(InviteFriendsViewModel.Intent.Send) },
    )
}

@Composable
private fun InviteFriendsContent(
    friends: List<CloudFriend>,
    selected: Set<String>,
    sending: Boolean,
    onToggle: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (friends.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No friends yet — add some first.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionCard(title = "Pick friends to invite") {
                        Text(
                            "${selected.size} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(items = friends, key = { it.id }) { friend ->
                    FriendRow(
                        friend = friend,
                        selected = friend.id in selected,
                        onToggle = { onToggle(friend.id) },
                    )
                }
            }
        }
        BottomActionBar {
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSend,
                enabled = !sending && selected.isNotEmpty(),
            ) {
                Text(if (sending) "Sending…" else "Invite ${selected.size}")
            }
        }
    }
}

@Composable
private fun FriendRow(friend: CloudFriend, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(friend.displayName, style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
    }
}

// region Previews

@Preview
@Composable
private fun InviteFriends_Preview() {
    ThemePreview {
        InviteFriendsContent(
            friends = sampleCloudFriends,
            selected = setOf("u2"),
            sending = false,
            onToggle = {},
            onSend = {},
        )
    }
}

@Preview
@Composable
private fun InviteFriends_Empty_Preview() {
    ThemePreview {
        InviteFriendsContent(
            friends = emptyList(),
            selected = emptySet(),
            sending = false,
            onToggle = {},
            onSend = {},
        )
    }
}

// endregion
