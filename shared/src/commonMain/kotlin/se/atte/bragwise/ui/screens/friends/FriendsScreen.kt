package se.atte.bragwise.ui.screens.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.domain.Player
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.LoadingDialog
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListGroupDivider
import se.atte.bragwise.ui.components.ListRow
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingLarge
import se.atte.bragwise.ui.standardPaddingSmall
import kotlin.time.Instant

@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenCloudProfile: (uid: String) -> Unit,
    onOpenFriendRequests: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is FriendsViewModel.Effect.OpenCloudProfile -> onOpenCloudProfile(effect.uid)
            is FriendsViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(effect.text)
        }
    }

    FriendsBody(
        state = state,
        onIntent = { viewModel.onIntent(it) },
        onOpenFriendRequests = onOpenFriendRequests,
    )
}

@Composable
private fun FriendsBody(
    state: FriendsViewModel.State,
    onIntent: (FriendsViewModel.Intent) -> Unit,
    onOpenFriendRequests: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(standardPadding),
            verticalArrangement = Arrangement.spacedBy(standardPadding),
        ) {
            if (state.incoming.isNotEmpty()) {
                item {
                    PendingRequestsSection(
                        incoming = state.incoming,
                        acting = state.acting,
                        onAccept = { onIntent(FriendsViewModel.Intent.Accept(it)) },
                        onDecline = { onIntent(FriendsViewModel.Intent.Decline(it)) },
                    )
                }
            }

            val requestSubtitle = listOfNotNull(
                state.incoming.size.takeIf { it > 0 }?.let { "$it incoming" },
                state.outgoingCount.takeIf { it > 0 }?.let { "$it sent" },
            ).joinToString(" · ").ifBlank { null }
            item {
                ListGroup {
                    ListRow(
                        title = "Friend requests",
                        subtitle = requestSubtitle,
                        onClick = onOpenFriendRequests,
                    )
                }
            }

            when (val ui = state.ui) {
                UiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(standardPaddingLarge), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Empty -> item {
                    EmptyState(hasPending = state.incoming.isNotEmpty())
                }
                is UiState.Failed -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(standardPaddingLarge), contentAlignment = Alignment.Center) {
                        Text(
                            text = ui.cause.toUserMessage(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is UiState.Ready -> {
                    val cloud = ui.data.filterIsInstance<CloudFriend>()
                    if (cloud.isNotEmpty()) {
                        item {
                            SectionCard(title = "Friends (${cloud.size})") {
                                ListGroup {
                                    cloud.forEachIndexed { index, friend ->
                                        ListRow(
                                            title = friend.displayName,
                                            subtitle = "@${friend.player.username}",
                                            leading = "👤",
                                            onClick = { onIntent(FriendsViewModel.Intent.OpenCloud(friend.player.uid)) },
                                        )
                                        if (index < cloud.size - 1) ListGroupDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BottomActionBar {
            AppButton(
                modifier = Modifier.weight(1f),
                onClick = { onIntent(FriendsViewModel.Intent.OpenAddFriend) },
            ) {
                Text(text = "Add by username")
            }
        }
    }

    if (state.sendingRequest) {
        LoadingDialog(message = "Sending request…")
    }

    if (state.addingFriend) {
        AddFriendDialog(
            onDismiss = { onIntent(FriendsViewModel.Intent.DismissAddFriend) },
            onSend = { username -> onIntent(FriendsViewModel.Intent.SendFriendRequest(username)) },
        )
    }
}

@Composable
private fun PendingRequestsSection(
    incoming: List<FriendsViewModel.RequestRow>,
    acting: Set<String>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    SectionCard(title = "Pending requests (${incoming.size})") {
        incoming.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = standardPadding, vertical = standardPaddingSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = row.displayName, style = MaterialTheme.typography.bodyLarge)
                    if (row.username != null) {
                        Text(
                            text = "@${row.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val isActing = row.uid in acting
                Row(horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
                    AppOutlinedButton(
                        onClick = { onDecline(row.uid) },
                        enabled = !isActing,
                    ) { Text("Decline") }
                    AppButton(
                        onClick = { onAccept(row.uid) },
                        enabled = !isActing,
                    ) { Text("Accept") }
                }
            }
            if (index < incoming.size - 1) ListGroupDivider()
        }
    }
}

@Composable
private fun EmptyState(hasPending: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(standardPaddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "👥", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(standardPadding))
        Text(text = "No friends yet", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(standardPaddingSmall))
        Text(
            text = if (hasPending) "Accept a pending request above, or add friends by their @username."
            else "Add friends by their @username to compete on shared challenges.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddFriendDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var username by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add friend") },
        text = {
            Column {
                Text(
                    text = "Enter the @username of the person you want to add.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(standardPadding))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    placeholder = { Text("@username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            AppButton(
                onClick = { onSend(username) },
                enabled = username.trim().isNotBlank(),
            ) { Text("Send request") }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// region Previews

private fun previewPlayer(n: Int) = Player(
    uid = "u$n",
    username = "user$n",
    displayName = "User $n",
    avatarSeed = "seed$n",
    createdAt = Instant.fromEpochSeconds(0),
)

private fun previewCloudFriend(n: Int) = CloudFriend(
    player = previewPlayer(n),
    since = Instant.fromEpochSeconds(0),
)

private fun previewRequest(n: Int) = FriendsViewModel.RequestRow(
    uid = "u$n",
    displayName = "User $n",
    username = "user$n",
)

@Preview
@Composable
private fun Friends_Empty_Preview() {
    ThemePreview {
        FriendsBody(
            state = FriendsViewModel.State(ui = UiState.Empty()),
            onIntent = {},
            onOpenFriendRequests = {},
        )
    }
}

@Preview
@Composable
private fun Friends_EmptyWithPending_Preview() {
    ThemePreview {
        FriendsBody(
            state = FriendsViewModel.State(
                ui = UiState.Empty(),
                incoming = listOf(previewRequest(1), previewRequest(2)),
                outgoingCount = 1,
            ),
            onIntent = {},
            onOpenFriendRequests = {},
        )
    }
}

@Preview
@Composable
private fun Friends_Ready_Preview() {
    val friends: List<Friend> = listOf(
        previewCloudFriend(1),
        previewCloudFriend(2),
    )
    ThemePreview {
        FriendsBody(
            state = FriendsViewModel.State(ui = UiState.Ready(friends)),
            onIntent = {},
            onOpenFriendRequests = {},
        )
    }
}

@Preview
@Composable
private fun Friends_ReadyWithPending_Preview() {
    val friends: List<Friend> = listOf(previewCloudFriend(1))
    ThemePreview {
        FriendsBody(
            state = FriendsViewModel.State(
                ui = UiState.Ready(friends),
                incoming = listOf(previewRequest(2)),
            ),
            onIntent = {},
            onOpenFriendRequests = {},
        )
    }
}

@Preview
@Composable
private fun Friends_AddDialog_Preview() {
    ThemePreview {
        FriendsBody(
            state = FriendsViewModel.State(ui = UiState.Empty(), addingFriend = true),
            onIntent = {},
            onOpenFriendRequests = {},
        )
    }
}

// endregion
