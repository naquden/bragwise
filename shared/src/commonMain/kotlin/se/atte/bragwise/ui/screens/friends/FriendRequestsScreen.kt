package se.atte.bragwise.ui.screens.friends

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
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.SectionCard

/**
 * LB-03 Friend requests inbox. Accept/decline incoming; outgoing
 * shown as a read-only count for now.
 */
@Composable
fun FriendRequestsScreen(
    viewModel: FriendRequestsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { e ->
        when (e) {
            is FriendRequestsViewModel.Effect.Snackbar ->
                snackbarHostState.showSnackbar(e.text)
        }
    }

    FriendRequestsContent(
        incoming = state.requests.incoming.keys.toList(),
        outgoing = state.requests.outgoing.size,
        acting = state.acting,
        onAccept = { viewModel.onIntent(FriendRequestsViewModel.Intent.Accept(it)) },
        onDecline = { viewModel.onIntent(FriendRequestsViewModel.Intent.Decline(it)) },
    )
}

@Composable
private fun FriendRequestsContent(
    incoming: List<String>,
    outgoing: Int,
    acting: Set<String>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    if (incoming.isEmpty() && outgoing == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending requests")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (incoming.isNotEmpty()) {
            item {
                SectionCard(title = "Incoming (${incoming.size})") {
                    Text(
                        "Tap accept to add as a friend.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = incoming, key = { it }) { uid ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(uid, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isActing = uid in acting
                        AppOutlinedButton(
                            onClick = { onDecline(uid) },
                            enabled = !isActing,
                        ) { Text("Decline") }
                        AppButton(
                            onClick = { onAccept(uid) },
                            enabled = !isActing,
                        ) { Text("Accept") }
                    }
                }
            }
        }
        if (outgoing > 0) {
            item {
                SectionCard(title = "Outgoing ($outgoing)") {
                    Text(
                        "Waiting for the other person to accept.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// region Previews

@Preview
@Composable
private fun FriendRequests_Preview() {
    ThemePreview {
        FriendRequestsContent(
            incoming = listOf("alice", "bob"),
            outgoing = 1,
            acting = emptySet(),
            onAccept = {},
            onDecline = {},
        )
    }
}

@Preview
@Composable
private fun FriendRequests_Empty_Preview() {
    ThemePreview {
        FriendRequestsContent(
            incoming = emptyList(),
            outgoing = 0,
            acting = emptySet(),
            onAccept = {},
            onDecline = {},
        )
    }
}

// endregion
