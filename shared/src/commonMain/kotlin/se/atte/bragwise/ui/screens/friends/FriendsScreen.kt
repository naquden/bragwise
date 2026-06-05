package se.atte.bragwise.ui.screens.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    onOpenCloudProfile: (handle: String) -> Unit,
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
        androidx.compose.animation.Crossfade(
            targetState = state.ui,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
            label = "friends-ui",
            modifier = Modifier.weight(1f),
        ) { ui ->
            when (ui) {
                UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Empty -> EmptyState()
                is UiState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = ui.cause.toUserMessage(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is UiState.Ready -> FriendsList(
                    friends = ui.data,
                    onIntent = onIntent,
                    onOpenFriendRequests = onOpenFriendRequests,
                )
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
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(standardPaddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "👥", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(standardPadding))
        Text(text = "No friends yet", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(standardPaddingSmall))
        Text(
            text = "Add friends by their @username to compete on shared challenges.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FriendsList(
    friends: List<Friend>,
    onIntent: (FriendsViewModel.Intent) -> Unit,
    onOpenFriendRequests: () -> Unit,
) {
    val cloud = friends.filterIsInstance<CloudFriend>()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(standardPadding),
        verticalArrangement = Arrangement.spacedBy(standardPadding),
    ) {
        item {
            ListGroup {
                ListRow(
                    title = "Friend requests",
                    onClick = onOpenFriendRequests,
                )
            }
        }
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
