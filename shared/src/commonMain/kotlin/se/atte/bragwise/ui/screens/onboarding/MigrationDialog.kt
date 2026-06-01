package se.atte.bragwise.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import se.atte.bragwise.data.MigrationMode
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.AppTextButton

/**
 * OB-05 Migration dialog. Shows three explicit choices so the user is never
 * silently dropped into Restore or Sync. Only shown when local guest data
 * exists — see AppNav guard.
 */
@Composable
fun MigrationDialog(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: MigrationViewModel = koinViewModel(),
) {
    // Activity-scoped VM is reused across sign-ins; clear any terminal phase
    // from a previous run before the first phase read so the choice re-shows.
    remember { viewModel.resetIfTerminal() }
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    var lastMode by remember { mutableStateOf(MigrationMode.SYNC) }
    val currentOnSkip by rememberUpdatedState(onSkip)

    val currentOnComplete by rememberUpdatedState(onComplete)
    LaunchedEffect(phase) {
        if (phase is MigrationViewModel.Phase.Done) currentOnComplete()
    }

    AlertDialog(
        onDismissRequest = { /* gated; user must resolve */ },
        title = {
            Text(
                when (phase) {
                    MigrationViewModel.Phase.Choosing -> "You have local data"
                    else -> "Saving your data…"
                },
            )
        },
        text = {
            when (val p = phase) {
                MigrationViewModel.Phase.Choosing -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You made predictions as a guest. What would you like to do?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    AppButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            lastMode = MigrationMode.SYNC
                            viewModel.onChoose(MigrationMode.SYNC)
                        },
                    ) { Text(if (viewModel.isNewAccount) "Sync local to cloud (recommended)" else "Sync local to cloud") }
                    AppOutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            lastMode = MigrationMode.RESTORE
                            viewModel.onChoose(MigrationMode.RESTORE)
                        },
                    ) { Text(if (!viewModel.isNewAccount) "Restore from cloud (recommended)" else "Restore from cloud") }
                    AppTextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { currentOnSkip() },
                    ) { Text("Skip — stay guest") }
                }
                MigrationViewModel.Phase.Loading -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Text("Migrating predictions and friends…")
                }
                MigrationViewModel.Phase.Done -> Text("All done.")
                is MigrationViewModel.Phase.Failed -> Column {
                    Text(
                        text = "Migration failed: ${p.message}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "You can retry, or skip and migrate later from Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            when (phase) {
                MigrationViewModel.Phase.Choosing,
                MigrationViewModel.Phase.Loading -> Unit
                MigrationViewModel.Phase.Done -> AppButton(onClick = onComplete) { Text("OK") }
                is MigrationViewModel.Phase.Failed -> AppButton(onClick = { viewModel.retry(lastMode) }) { Text("Retry") }
            }
        },
        dismissButton = {
            when (phase) {
                MigrationViewModel.Phase.Choosing,
                MigrationViewModel.Phase.Loading,
                MigrationViewModel.Phase.Done -> Unit
                is MigrationViewModel.Phase.Failed -> AppTextButton(onClick = onSkip) { Text("Skip") }
            }
        },
    )
}
