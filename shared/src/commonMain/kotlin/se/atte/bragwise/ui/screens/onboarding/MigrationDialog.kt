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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppTextButton

/**
 * OB-05 Migration dialog. Triggered after sign-in completes when local
 * data exists (predictions, local friends). Calls migrateGuestData
 * callable; success closes; failure offers retry.
 *
 * Phase state lives in MigrationViewModel (survives rotation) so the
 * migration is never accidentally re-triggered by a config change.
 */
@Composable
fun MigrationDialog(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: MigrationViewModel = koinViewModel(),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()

    // React to terminal phase rather than a one-shot effect: the VM is
    // Activity-scoped (hand-rolled nav has no per-route ViewModelStoreOwner),
    // so on a 2nd sign-in the same instance is reused with phase already
    // Done. A consumed one-shot effect would never re-fire — leaving the
    // dialog stuck on "All done." Keying on `phase` re-navigates on reuse.
    val currentOnComplete by rememberUpdatedState(onComplete)
    LaunchedEffect(phase) {
        if (phase is MigrationViewModel.Phase.Done) currentOnComplete()
    }

    AlertDialog(
        onDismissRequest = { /* gated; user must resolve */ },
        title = { Text("Saving your data…") },
        text = {
            when (val p = phase) {
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
                MigrationViewModel.Phase.Loading -> Unit
                // Manual escape so the user is never trapped, even if the
                // auto-navigation LaunchedEffect above somehow doesn't fire.
                MigrationViewModel.Phase.Done -> AppButton(onClick = onComplete) { Text("OK") }
                is MigrationViewModel.Phase.Failed -> AppButton(onClick = viewModel::retry) { Text("Retry") }
            }
        },
        dismissButton = {
            when (phase) {
                MigrationViewModel.Phase.Loading,
                MigrationViewModel.Phase.Done -> Unit
                is MigrationViewModel.Phase.Failed -> AppTextButton(onClick = onSkip) { Text("Skip") }
            }
        },
    )
}
