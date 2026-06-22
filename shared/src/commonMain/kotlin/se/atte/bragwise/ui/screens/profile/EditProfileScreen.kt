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
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.edit_error_username_taken
import bragwise.shared.generated.resources.edit_guest_note
import bragwise.shared.generated.resources.edit_avatar_emoji
import bragwise.shared.generated.resources.edit_avatar_flags
import bragwise.shared.generated.resources.edit_display_name_hint
import bragwise.shared.generated.resources.edit_display_name_label
import bragwise.shared.generated.resources.edit_email_hint
import bragwise.shared.generated.resources.edit_email_label
import bragwise.shared.generated.resources.edit_save
import bragwise.shared.generated.resources.edit_saving
import bragwise.shared.generated.resources.edit_saving_dialog
import bragwise.shared.generated.resources.edit_section_avatar
import bragwise.shared.generated.resources.edit_section_identity
import bragwise.shared.generated.resources.edit_show_all_flags
import bragwise.shared.generated.resources.edit_show_less
import bragwise.shared.generated.resources.edit_username_hint
import bragwise.shared.generated.resources.edit_username_label
import se.atte.bragwise.mvi.ObserveEffects
import se.atte.bragwise.mvi.UiText
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
import se.atte.bragwise.ui.components.LoadingDialog
import se.atte.bragwise.ui.components.FlagImage
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.components.allFlagCodes
import se.atte.bragwise.ui.components.emojiAvatars
import se.atte.bragwise.ui.InputLimits
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
            is EditProfileViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(
                getString(e.message.res, *e.message.args.toTypedArray())
            )
        }
    }

    if (state.saving) {
        LoadingDialog(message = stringResource(Res.string.edit_saving_dialog))
    }

    EditProfileContent(
        state = state,
        onSetUsername = { viewModel.onIntent(EditProfileViewModel.Intent.SetUsername(it)) },
        onSetDisplayName = { viewModel.onIntent(EditProfileViewModel.Intent.SetDisplayName(it)) },
        onSetAvatarSeed = { viewModel.onIntent(EditProfileViewModel.Intent.SetAvatarSeed(it)) },
        onSave = { viewModel.onIntent(EditProfileViewModel.Intent.Save) },
    )
}

private const val FLAGS_COLLAPSED_COUNT = 40

@Composable
private fun EditProfileContent(
    state: EditProfileViewModel.State,
    onSetUsername: (String) -> Unit,
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
            if (!state.isFullyAuthed) {
                item {
                    Text(
                        text = stringResource(Res.string.edit_guest_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionCard(title = stringResource(Res.string.edit_section_identity)) {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { if (it.length <= InputLimits.HANDLE) onSetUsername(it) },
                        label = { Text(stringResource(Res.string.edit_username_label)) },
                        prefix = { Text("@") },
                        enabled = !state.saving,
                        isError = state.usernameError != null,
                        supportingText = {
                            Text(state.usernameError?.let { stringResource(it.res, *it.args.toTypedArray()) }
                                ?: stringResource(Res.string.edit_username_hint))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = { if (it.length <= InputLimits.DISPLAY_NAME) onSetDisplayName(it) },
                        label = { Text(stringResource(Res.string.edit_display_name_label)) },
                        enabled = !state.saving,
                        isError = state.displayNameError != null,
                        supportingText = {
                            Text(state.displayNameError?.let { stringResource(it.res, *it.args.toTypedArray()) }
                                ?: stringResource(Res.string.edit_display_name_hint))
                        },
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
                            label = { Text(stringResource(Res.string.edit_email_label)) },
                            supportingText = { Text(stringResource(Res.string.edit_email_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }
            item {
                SectionCard(title = stringResource(Res.string.edit_section_avatar)) {
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
                        text = stringResource(Res.string.edit_avatar_emoji),
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
                        text = stringResource(Res.string.edit_avatar_flags),
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
                            if (showAllFlags) stringResource(Res.string.edit_show_less)
                            else stringResource(Res.string.edit_show_all_flags, allFlagCodes.size),
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
                Text(if (state.saving) stringResource(Res.string.edit_saving) else stringResource(Res.string.edit_save))
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
                username = "bravefox4821",
                displayName = "Atte Lindqvist",
                avatarSeed = "😎",
                email = "atte@gmail.com",
            ),
            onSetUsername = {},
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
                username = "taken",
                displayName = "Atte Lindqvist",
                avatarSeed = "flag:SE",
                usernameError = UiText(Res.string.edit_error_username_taken),
                email = "atte@gmail.com",
            ),
            onSetUsername = {},
            onSetDisplayName = {},
            onSetAvatarSeed = {},
            onSave = {},
        )
    }
}

// endregion
