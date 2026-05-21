package se.atte.bragwise.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.SectionCard

private val avatarSeeds = listOf("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10", "a11", "a12")

@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel,
    snackbarHostState: SnackbarHostState,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { e ->
        when (e) {
            EditProfileViewModel.Effect.Saved -> onSaved()
            is EditProfileViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(e.text)
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Identity") {
                    OutlinedTextField(
                        value = state.handle,
                        onValueChange = { viewModel.onIntent(EditProfileViewModel.Intent.SetHandle(it)) },
                        label = { Text("Handle") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = { viewModel.onIntent(EditProfileViewModel.Intent.SetDisplayName(it)) },
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
            item {
                SectionCard(title = "Avatar") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        avatarSeeds.take(6).forEach { seed ->
                            AvatarTile(
                                seed = seed,
                                selected = seed == state.avatarSeed,
                                onClick = { viewModel.onIntent(EditProfileViewModel.Intent.SetAvatarSeed(seed)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        avatarSeeds.drop(6).forEach { seed ->
                            AvatarTile(
                                seed = seed,
                                selected = seed == state.avatarSeed,
                                onClick = { viewModel.onIntent(EditProfileViewModel.Intent.SetAvatarSeed(seed)) },
                            )
                        }
                    }
                }
            }
        }
        BottomActionBar {
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.onIntent(EditProfileViewModel.Intent.Save) },
                enabled = !state.saving && state.initialised,
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        }
    }
}

@Composable
private fun AvatarTile(seed: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(seed, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
