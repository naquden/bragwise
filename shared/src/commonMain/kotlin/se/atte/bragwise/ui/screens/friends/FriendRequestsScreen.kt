package se.atte.bragwise.ui.screens.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.freq_cancel
import bragwise.shared.generated.resources.freq_incoming
import bragwise.shared.generated.resources.freq_no_pending
import bragwise.shared.generated.resources.freq_sent
import bragwise.shared.generated.resources.friends_accept
import bragwise.shared.generated.resources.friends_decline
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
                snackbarHostState.showSnackbar(getString(e.message.res, *e.message.args.toTypedArray()))
        }
    }

    FriendRequestsContent(
        incoming = state.incoming,
        outgoing = state.outgoing,
        state = state,
        onAccept = { viewModel.onIntent(FriendRequestsViewModel.Intent.Accept(it)) },
        onDecline = { viewModel.onIntent(FriendRequestsViewModel.Intent.Decline(it)) },
        onWithdraw = { viewModel.onIntent(FriendRequestsViewModel.Intent.Withdraw(it)) },
    )
}

@Composable
private fun FriendRequestsContent(
    incoming: List<FriendRequestsViewModel.RequestRow>,
    outgoing: List<FriendRequestsViewModel.RequestRow>,
    state: FriendRequestsViewModel.State,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onWithdraw: (String) -> Unit,
) {
    if (incoming.isEmpty() && outgoing.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.freq_no_pending))
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(standardPadding),
        verticalArrangement = Arrangement.spacedBy(standardPadding),
    ) {
        if (incoming.isNotEmpty()) {
            item {
                SectionCard(title = stringResource(Res.string.freq_incoming, incoming.size)) {
                    incoming.forEachIndexed { index, row ->
                        RequestRow(
                            row = row,
                            isAccepting = state.isActing(row.uid, FriendRequestsViewModel.FriendAction.Accept),
                            isDeclining = state.isActing(row.uid, FriendRequestsViewModel.FriendAction.Decline),
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
                SectionCard(title = stringResource(Res.string.freq_sent, outgoing.size)) {
                    outgoing.forEachIndexed { index, row ->
                        SentRow(
                            row = row,
                            isWithdrawing = state.isActing(row.uid, FriendRequestsViewModel.FriendAction.Withdraw),
                            onWithdraw = onWithdraw,
                        )
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
    isAccepting: Boolean,
    isDeclining: Boolean,
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
                enabled = !isDeclining && !isAccepting,
            ) { Text(stringResource(Res.string.friends_decline)) }
            AppButton(
                onClick = { onAccept(row.uid) },
                enabled = !isAccepting && !isDeclining,
            ) {
                if (isAccepting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Text(stringResource(Res.string.friends_accept))
                }
            }
        }
    }
}

@Composable
private fun SentRow(
    row: FriendRequestsViewModel.RequestRow,
    isWithdrawing: Boolean,
    onWithdraw: (String) -> Unit,
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
        AppOutlinedButton(
            onClick = { onWithdraw(row.uid) },
            enabled = !isWithdrawing,
        ) {
            if (isWithdrawing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Text(stringResource(Res.string.freq_cancel))
            }
        }
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
            state = FriendRequestsViewModel.State(),
            onAccept = {},
            onDecline = {},
            onWithdraw = {},
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
            state = FriendRequestsViewModel.State(),
            onAccept = {},
            onDecline = {},
            onWithdraw = {},
        )
    }
}

// endregion
