package se.atte.bragwise.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListRow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onEditProfile: () -> Unit,
    onAbout: () -> Unit,
    onSignedOut: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { e ->
        when (e) {
            SettingsViewModel.Effect.SignedOut -> onSignedOut()
            SettingsViewModel.Effect.Deleted -> onDeleted()
            is SettingsViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(e.text)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ListGroup {
                if (state.signedIn) {
                    ListRow(title = "Edit profile", onClick = onEditProfile)
                }
                ListRow(title = "About", onClick = onAbout)
            }
        }
        if (state.signedIn) {
            item {
                ListGroup {
                    ListRow(
                        title = "Sign out",
                        titleColor = MaterialTheme.colorScheme.error,
                        trailing = null,
                        onClick = { viewModel.onIntent(SettingsViewModel.Intent.SignOut) },
                    )
                    ListRow(
                        title = "Delete account",
                        titleColor = MaterialTheme.colorScheme.error,
                        trailing = null,
                        onClick = { viewModel.onIntent(SettingsViewModel.Intent.RequestDelete) },
                    )
                }
            }
        }
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(SettingsViewModel.Intent.CancelDelete) },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "This permanently removes your account, predictions, and friends. This cannot be undone.",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            confirmButton = {
                AppButton(onClick = { viewModel.onIntent(SettingsViewModel.Intent.ConfirmDelete) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                AppTextButton(onClick = { viewModel.onIntent(SettingsViewModel.Intent.CancelDelete) }) {
                    Text("Cancel")
                }
            },
        )
    }
}
