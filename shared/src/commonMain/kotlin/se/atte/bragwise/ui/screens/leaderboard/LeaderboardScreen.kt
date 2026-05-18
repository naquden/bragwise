package se.atte.bragwise.ui.screens.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.PointsPill
import se.atte.bragwise.ui.components.RankChip

@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LeaderboardBody(
        state = state,
        onSetFriendsOnly = { viewModel.onIntent(LeaderboardViewModel.Intent.SetFriendsOnly(it)) },
    )
}

@Composable
private fun LeaderboardBody(
    state: LeaderboardViewModel.State,
    onSetFriendsOnly: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(standardPadding)) {
        if (state.showTabs) {
            PrimaryTabRow(selectedTabIndex = if (state.friendsOnly) 1 else 0) {
                Tab(
                    selected = !state.friendsOnly,
                    onClick = { onSetFriendsOnly(false) },
                    text = { Text("All") },
                )
                Tab(
                    selected = state.friendsOnly,
                    onClick = { onSetFriendsOnly(true) },
                    text = { Text("Friends") },
                )
            }
        }
        when (val ui = state.ui) {
            UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No leaderboard yet")
            }
            is UiState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = ui.cause.toUserMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is UiState.Ready -> LazyColumn(Modifier.fillMaxSize()) {
                items(items = ui.data, key = { it.uid }) { entry -> EntryRow(entry) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LeaderboardEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = standardPaddingSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankChip(rank = entry.rank, total = 0)
        Text(text = entry.displayName, style = MaterialTheme.typography.titleLarge)
        PointsPill(points = entry.points)
    }
}

// region Previews

private fun previewEntry(rank: Int, name: String, points: Int) =
    LeaderboardEntry(uid = "u$rank", displayName = name, points = points, rank = rank)

@Preview
@Composable
private fun Leaderboard_Empty_Preview() {
    ThemePreview {
        LeaderboardBody(
            state = LeaderboardViewModel.State(ui = UiState.Empty(), friendsOnly = false, showTabs = false),
            onSetFriendsOnly = {},
        )
    }
}

@Preview
@Composable
private fun Leaderboard_Ready_Preview() {
    val entries = listOf(
        previewEntry(1, "Atte", 150),
        previewEntry(2, "Alice", 120),
        previewEntry(3, "Bob", 95),
    )
    ThemePreview {
        LeaderboardBody(
            state = LeaderboardViewModel.State(
                ui = UiState.Ready(entries),
                friendsOnly = false,
                showTabs = true,
            ),
            onSetFriendsOnly = {},
        )
    }
}

// endregion
