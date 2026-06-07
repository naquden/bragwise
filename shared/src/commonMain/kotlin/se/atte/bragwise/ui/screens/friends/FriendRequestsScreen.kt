package se.atte.bragwise.ui.screens.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import se.atte.bragwise.ui.components.ListGroupDivider
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall

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
        incoming = state.incoming,
        outgoing = state.outgoing,
        acting = state.acting,
        onAccept = { viewModel.onIntent(FriendRequestsViewModel.Intent.Accept(it)) },
        onDecline = { viewModel.onIntent(FriendRequestsViewModel.Intent.Decline(it)) },
    )
}

@Composable
private fun FriendRequestsContent(
    incoming: List<FriendRequestsViewModel.RequestRow>,
    outgoing: List<FriendRequestsViewModel.RequestRow>,
    acting: Set<String>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    if (incoming.isEmpty() && outgoing.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending requests")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(standardPadding),
        verticalArrangement = Arrangement.spacedBy(standardPadding),
    ) {
        if (incoming.isNotEmpty()) {
            item {
                SectionCard(title = "Incoming (${incoming.size})") {
                    incoming.forEachIndexed { index, row ->
                        RequestRow(
                            row = row,
                            isActing = row.uid in acting,
                            onAccept = onAccept,
                            onDecline = onDecline,
                        )
                        if (index < incoming.size - 1) ListGroupDivider()
                    }
                }
            }
        }
        if (outgoing.isNotEmpty()) {
            item {
                SectionCard(title = "Sent (${outgoing.size})") {
                    outgoing.forEachIndexed { index, row ->
                        SentRow(row = row)
                        if (index < outgoing.size - 1) ListGroupDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(
    row: FriendRequestsViewModel.RequestRow,
    isActing: Boolean,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(standardPaddingSmall)) {
            AppOutlinedButton(
                onClick = { onDecline(row.uid) },
                enabled = !isActing,
            ) { Text("Decline") }
            AppButton(
                onClick = { onAccept(row.uid) },
                enabled = !isActing,
            ) {
                if (isActing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Text("Accept")
                }
            }
        }
    }
}

@Composable
private fun SentRow(row: FriendRequestsViewModel.RequestRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = standardPadding, vertical = standardPaddingSmall),
        verticalAlignment = Alignment.CenterVertically,
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
        Text(
            text = "Pending",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// region Previews

private fun previewRow(n: Int) = FriendRequestsViewModel.RequestRow(
    uid = "u$n",
    displayName = "User $n",
    username = "user$n",
)

@Preview
@Composable
private fun FriendRequests_Preview() {
    ThemePreview {
        FriendRequestsContent(
            incoming = listOf(previewRow(1), previewRow(2)),
            outgoing = listOf(previewRow(3)),
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
            outgoing = emptyList(),
            acting = emptySet(),
            onAccept = {},
            onDecline = {},
        )
    }
}

// endregion
