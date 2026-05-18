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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingLarge
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.domain.LocalFriend
import se.atte.bragwise.domain.Player
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListGroupDivider
import se.atte.bragwise.ui.components.ListRow
import se.atte.bragwise.ui.components.SectionCard
import kotlin.time.Instant

@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onLocalAddOrEdit: (localId: String?) -> Unit,
    onOpenCloudProfile: (handle: String) -> Unit,
    onOpenReconcile: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var rowMenuFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                FriendsViewModel.Effect.OpenLocalAdd -> onLocalAddOrEdit(null)
                is FriendsViewModel.Effect.OpenLocalEdit -> onLocalAddOrEdit(effect.localId)
                is FriendsViewModel.Effect.OpenCloudProfile -> onOpenCloudProfile(effect.uid)
                FriendsViewModel.Effect.OpenReconcile -> onOpenReconcile()
                is FriendsViewModel.Effect.Snackbar -> { /* TODO */ }
            }
        }
    }

    FriendsBody(
        state = state,
        rowMenuFor = rowMenuFor,
        onRowMenu = { rowMenuFor = it },
        onIntent = { viewModel.onIntent(it) },
    )
}

@Composable
private fun FriendsBody(
    state: FriendsViewModel.State,
    rowMenuFor: String?,
    onRowMenu: (String?) -> Unit,
    onIntent: (FriendsViewModel.Intent) -> Unit,
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
                is UiState.Empty -> EmptyState(state.mode)
                is UiState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Failed: ${ui.cause}")
                }
                is UiState.Ready -> FriendsList(
                    friends = ui.data,
                    rowMenuFor = rowMenuFor,
                    onRowMenu = onRowMenu,
                    onIntent = onIntent,
                )
            }
        }

        BottomActionBar {
            AppOutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { onIntent(FriendsViewModel.Intent.Reconcile) },
            ) { Text("Reconcile") }
            AppButton(
                modifier = Modifier.weight(1f),
                onClick = { onIntent(FriendsViewModel.Intent.AddFriend) },
            ) {
                Text(
                    text = when (state.mode) {
                        FriendsViewModel.Mode.Guest -> "Add friend"
                        FriendsViewModel.Mode.SignedIn -> "Add by handle"
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(mode: FriendsViewModel.Mode) {
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
            text = when (mode) {
                FriendsViewModel.Mode.Guest ->
                    "Add a friend to keep track of who you predict against."
                FriendsViewModel.Mode.SignedIn ->
                    "Add friends by their @handle to compete on shared challenges."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FriendsList(
    friends: List<Friend>,
    rowMenuFor: String?,
    onRowMenu: (String?) -> Unit,
    onIntent: (FriendsViewModel.Intent) -> Unit,
) {
    val locals = friends.filterIsInstance<LocalFriend>()
    val cloud = friends.filterIsInstance<CloudFriend>()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(standardPadding),
        verticalArrangement = Arrangement.spacedBy(standardPadding),
    ) {
        if (cloud.isNotEmpty()) {
            item {
                SectionCard(title = "Friends (${cloud.size})") {
                    ListGroup {
                        cloud.forEachIndexed { index, friend ->
                            ListRow(
                                title = friend.displayName,
                                subtitle = "@${friend.player.handle}",
                                leading = "👤",
                                onClick = { onIntent(FriendsViewModel.Intent.OpenCloud(friend.player.uid)) },
                            )
                            if (index < cloud.size - 1) ListGroupDivider()
                        }
                    }
                }
            }
        }
        if (locals.isNotEmpty()) {
            item {
                SectionCard(title = "Local (${locals.size})") {
                    Text(
                        text = "Stored on this device. Sign up to play with them on cloud challenges.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = standardPaddingSmall),
                    )
                    ListGroup {
                        locals.forEachIndexed { index, friend ->
                            ListRow(
                                title = friend.displayName,
                                subtitle = "Local",
                                leading = friend.avatarSeed.firstOrNull()?.toString() ?: "·",
                                onClick = { onRowMenu(friend.localId) },
                            )
                            if (index < locals.size - 1) ListGroupDivider()
                        }
                    }
                }
            }
        }
    }

    val target = rowMenuFor
    if (target != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onRowMenu(null) },
            confirmButton = {
                AppTextButton(onClick = {
                    onIntent(FriendsViewModel.Intent.EditLocal(target))
                    onRowMenu(null)
                }) { Text("Edit") }
            },
            dismissButton = {
                AppTextButton(
                    onClick = {
                        onIntent(FriendsViewModel.Intent.RemoveLocal(target))
                        onRowMenu(null)
                    },
                    color = MaterialTheme.colorScheme.error,
                ) { Text("Remove") }
            },
            title = { Text("Local friend") },
            text = { Text("Edit or remove this local friend.") },
        )
    }
}

// region Previews

private fun previewPlayer(n: Int) = Player(
    uid = "u$n",
    handle = "user$n",
    displayName = "User $n",
    avatarSeed = "seed$n",
    createdAt = Instant.fromEpochSeconds(0),
)

private fun previewCloudFriend(n: Int) = CloudFriend(
    player = previewPlayer(n),
    since = Instant.fromEpochSeconds(0),
)

private fun previewLocalFriend(n: Int) = LocalFriend(
    localId = "l$n",
    displayName = "Local Friend $n",
    avatarSeed = "loc$n",
    addedAt = Instant.fromEpochSeconds(0),
)

@Preview
@Composable
private fun Friends_Empty_Guest_Preview() {
    ThemePreview {
        FriendsBody(
            state = FriendsViewModel.State(ui = UiState.Empty(), mode = FriendsViewModel.Mode.Guest),
            rowMenuFor = null,
            onRowMenu = {},
            onIntent = {},
        )
    }
}

@Preview
@Composable
private fun Friends_Ready_Preview() {
    val friends: List<Friend> = listOf(
        previewCloudFriend(1),
        previewLocalFriend(2),
        previewLocalFriend(3),
    )
    ThemePreview {
        FriendsBody(
            state = FriendsViewModel.State(ui = UiState.Ready(friends), mode = FriendsViewModel.Mode.SignedIn),
            rowMenuFor = null,
            onRowMenu = {},
            onIntent = {},
        )
    }
}

// endregion
