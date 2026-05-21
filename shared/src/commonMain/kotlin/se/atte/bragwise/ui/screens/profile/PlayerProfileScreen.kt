package se.atte.bragwise.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.mvi.UiState
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
            Text("Player not found")
        }
        is UiState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(ui.cause.toUserMessage())
        }
        is UiState.Ready -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = ui.data.profile.displayName) {
                    Text("@${ui.data.profile.handle}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                SectionCard(title = "Head to head") {
                    val rec = ui.data.head
                    if (rec == null) {
                        Text(
                            "No shared challenges yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "You: ${rec.wins} · Them: ${rec.losses} · Ties: ${rec.ties}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
