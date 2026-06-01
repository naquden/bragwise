package se.atte.bragwise.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Lucide
import org.koin.compose.viewmodel.koinViewModel
import se.atte.bragwise.data.MigrationMode
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppOutlinedButton
import se.atte.bragwise.ui.components.AppTextButton

@Composable
private fun RecommendedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = LocalContentColor.current.copy(alpha = 0.18f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = "Recommended",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

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
            when (phase) {
                MigrationViewModel.Phase.Choosing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(imageVector = Lucide.Database, contentDescription = null)
                    Text("You have local data")
                }
                else -> Text("Saving your data…")
            }
        },
        text = {
            when (val p = phase) {
                MigrationViewModel.Phase.Choosing -> MigrationChoosingContent(
                    isNewAccount = viewModel.isNewAccount,
                    onSync = {
                        lastMode = MigrationMode.SYNC
                        viewModel.onChoose(MigrationMode.SYNC)
                    },
                    onRestore = {
                        lastMode = MigrationMode.RESTORE
                        viewModel.onChoose(MigrationMode.RESTORE)
                    },
                    onSkip = currentOnSkip,
                )
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

@Composable
private fun MigrationChoosingContent(
    isNewAccount: Boolean,
    onSync: () -> Unit,
    onRestore: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "You made predictions as a guest. What would you like to do?",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        AppButton(modifier = Modifier.fillMaxWidth(), onClick = onSync) {
            Text("Sync local to cloud")
            if (isNewAccount) RecommendedBadge(modifier = Modifier.padding(start = 8.dp))
        }
        AppOutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRestore) {
            Text("Restore from cloud")
            if (!isNewAccount) RecommendedBadge(modifier = Modifier.padding(start = 8.dp))
        }
        AppTextButton(modifier = Modifier.fillMaxWidth(), onClick = onSkip) {
            Text("Skip — stay guest")
        }
    }
}

// region Previews

@Preview
@Composable
private fun MigrationDialog_NewAccount_Preview() {
    ThemePreview {
        MigrationChoosingContent(
            isNewAccount = true,
            onSync = {},
            onRestore = {},
            onSkip = {},
        )
    }
}

@Preview
@Composable
private fun MigrationDialog_ExistingAccount_Preview() {
    ThemePreview {
        MigrationChoosingContent(
            isNewAccount = false,
            onSync = {},
            onRestore = {},
            onSkip = {},
        )
    }
}

// endregion
