package se.atte.bragwise.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AvatarBubble
import se.atte.bragwise.ui.components.BottomActionBar
import se.atte.bragwise.ui.components.FlagImage
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.components.allFlagCodes
import se.atte.bragwise.ui.components.emojiAvatars
import se.atte.bragwise.ui.components.flagSeed

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

    EditProfileContent(
        state = state,
        onSetHandle = { viewModel.onIntent(EditProfileViewModel.Intent.SetHandle(it)) },
        onSetDisplayName = { viewModel.onIntent(EditProfileViewModel.Intent.SetDisplayName(it)) },
        onSetAvatarSeed = { viewModel.onIntent(EditProfileViewModel.Intent.SetAvatarSeed(it)) },
        onSave = { viewModel.onIntent(EditProfileViewModel.Intent.Save) },
    )
}

private const val FLAGS_COLLAPSED_COUNT = 40

@Composable
private fun EditProfileContent(
    state: EditProfileViewModel.State,
    onSetHandle: (String) -> Unit,
    onSetDisplayName: (String) -> Unit,
    onSetAvatarSeed: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showAllFlags by remember { mutableStateOf(false) }
    val visibleFlags = if (showAllFlags) allFlagCodes else allFlagCodes.take(FLAGS_COLLAPSED_COUNT)

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
                        onValueChange = onSetHandle,
                        label = { Text("Username (@handle)") },
                        prefix = { Text("@") },
                        isError = state.handleError != null,
                        supportingText = {
                            Text(state.handleError ?: "Your unique @name. Lowercase letters, numbers and _, 3-20 characters.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = onSetDisplayName,
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (state.email != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.email,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Email") },
                            supportingText = { Text("Used to sign in. Not shown to others.") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }
            item {
                SectionCard(title = "Avatar") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AvatarBubble(
                            displayName = state.displayName,
                            avatarSeed = state.avatarSeed,
                            size = 72.dp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Emoji",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        emojiAvatars.forEach { emoji ->
                            AvatarPickerTile(
                                selected = emoji == state.avatarSeed,
                                onClick = { onSetAvatarSeed(emoji) },
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Flags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        visibleFlags.forEach { code ->
                            val seed = flagSeed(code)
                            AvatarPickerTile(
                                selected = seed == state.avatarSeed,
                                onClick = { onSetAvatarSeed(seed) },
                            ) {
                                FlagImage(code = code, size = 32.dp)
                            }
                        }
                    }
                    TextButton(
                        onClick = { showAllFlags = !showAllFlags },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (showAllFlags) "Show less"
                            else "Show all ${allFlagCodes.size} flags",
                        )
                    }
                }
            }
        }
        BottomActionBar {
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSave,
                enabled = !state.saving && state.initialised,
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        }
    }
}

@Composable
private fun AvatarPickerTile(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = if (selected) BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(
            modifier = Modifier.padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

// region Previews

@Preview
@Composable
private fun EditProfile_Preview() {
    ThemePreview {
        EditProfileContent(
            state = EditProfileViewModel.State(
                initialised = true,
                handle = "bravefox4821",
                displayName = "Atte Lindqvist",
                avatarSeed = "😎",
                email = "atte@gmail.com",
            ),
            onSetHandle = {},
            onSetDisplayName = {},
            onSetAvatarSeed = {},
            onSave = {},
        )
    }
}

@Preview
@Composable
private fun EditProfile_HandleError_Preview() {
    ThemePreview {
        EditProfileContent(
            state = EditProfileViewModel.State(
                initialised = true,
                handle = "taken",
                displayName = "Atte Lindqvist",
                avatarSeed = "flag:SE",
                handleError = "That username is already taken",
                email = "atte@gmail.com",
            ),
            onSetHandle = {},
            onSetDisplayName = {},
            onSetAvatarSeed = {},
            onSave = {},
        )
    }
}

// endregion
