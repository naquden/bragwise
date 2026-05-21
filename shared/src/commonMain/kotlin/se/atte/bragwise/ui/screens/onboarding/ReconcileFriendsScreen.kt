package se.atte.bragwise.ui.screens.onboarding

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.LocalFriend
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.SectionCard
import kotlin.time.Instant

@Composable
fun ReconcileFriendsScreen(
    viewModel: ReconcileFriendsViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is ReconcileFriendsViewModel.Effect.Done,
            ReconcileFriendsViewModel.Effect.Skipped -> onDone()
            is ReconcileFriendsViewModel.Effect.Snackbar -> { /* TODO */ }
        }
    }

    ReconcileBody(
        state = state,
        onIntent = { viewModel.onIntent(it) },
    )
}

@Composable
private fun ReconcileBody(
    state: ReconcileFriendsViewModel.State,
    onIntent: (ReconcileFriendsViewModel.Intent) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        when (val ui = state.ui) {
            UiState.Loading -> Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Empty -> Box(modifier = Modifier.weight(1f).fillMaxSize())
            is UiState.Failed -> Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = ui.cause.toUserMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is UiState.Ready -> ReconcileContent(
                rows = ui.data,
                handles = state.handles,
                submitting = state.submitting,
                onHandle = { id, h -> onIntent(ReconcileFriendsViewModel.Intent.SetHandle(id, h)) },
                modifier = Modifier.weight(1f),
            )
        }

        BottomActionBar {
            AppOutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { onIntent(ReconcileFriendsViewModel.Intent.Skip) },
                enabled = !state.submitting,
            ) { Text("Skip") }
            AppButton(
                modifier = Modifier.weight(1f),
                onClick = { onIntent(ReconcileFriendsViewModel.Intent.Send) },
                enabled = !state.submitting,
            ) {
                Text(if (state.submitting) "Sending…" else "Send requests")
            }
        }
    }
}

@Composable
private fun ReconcileContent(
    rows: List<LocalFriend>,
    handles: Map<String, String>,
    submitting: Boolean,
    onHandle: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(standardPadding),
        verticalArrangement = Arrangement.spacedBy(standardPadding),
    ) {
        item {
            SectionCard(title = "Match your local friends to real accounts") {
                Text(
                    text = "We'll send a friend request to each handle you fill in. Leave blank to keep that friend local.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items = rows, key = { it.localId }) { row ->
            SectionCard {
                Text(text = row.displayName, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(standardPaddingSmall))
                OutlinedTextField(
                    value = handles[row.localId].orEmpty(),
                    onValueChange = { onHandle(row.localId, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("@handle (optional)") },
                    singleLine = true,
                    enabled = !submitting,
                )
            }
        }
    }
}

// region Previews

private fun previewLocal(n: Int) = LocalFriend(
    localId = "l$n",
    displayName = "Local Friend $n",
    avatarSeed = "loc$n",
    addedAt = Instant.fromEpochSeconds(0),
)

@Preview
@Composable
private fun Reconcile_Loading_Preview() {
    ThemePreview {
        ReconcileBody(
            state = ReconcileFriendsViewModel.State(ui = UiState.Loading),
            onIntent = {},
        )
    }
}

@Preview
@Composable
private fun Reconcile_Ready_Preview() {
    val rows = listOf(previewLocal(1), previewLocal(2), previewLocal(3))
    ThemePreview {
        ReconcileBody(
            state = ReconcileFriendsViewModel.State(
                ui = UiState.Ready(rows),
                handles = mapOf("l1" to "alice"),
            ),
            onIntent = {},
        )
    }
}

// endregion
