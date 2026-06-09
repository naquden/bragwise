package se.atte.bragwise.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.me_cancel
import bragwise.shared.generated.resources.me_delete
import bragwise.shared.generated.resources.me_delete_account
import bragwise.shared.generated.resources.me_delete_account_body
import bragwise.shared.generated.resources.me_delete_account_title
import bragwise.shared.generated.resources.me_edit_profile
import bragwise.shared.generated.resources.me_friends
import bragwise.shared.generated.resources.me_guest_body
import bragwise.shared.generated.resources.me_guest_label
import bragwise.shared.generated.resources.me_logged_in
import bragwise.shared.generated.resources.me_section_account
import bragwise.shared.generated.resources.me_sign_in_or_sign_up
import bragwise.shared.generated.resources.me_sign_out
import bragwise.shared.generated.resources.settings_about_title
import bragwise.shared.generated.resources.settings_notifications_title
import bragwise.shared.generated.resources.settings_section_title
import bragwise.shared.generated.resources.settings_theme_dark
import bragwise.shared.generated.resources.settings_theme_light
import bragwise.shared.generated.resources.settings_theme_system
import bragwise.shared.generated.resources.settings_theme_title
import androidx.compose.material3.Icon
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UsersRound
import org.jetbrains.compose.resources.stringResource
import se.atte.bragwise.domain.Player
import se.atte.bragwise.theme.ThemeMode
import se.atte.bragwise.ui.standardPadding
import se.atte.bragwise.ui.standardPaddingSmall
import se.atte.bragwise.theme.ThemePreview
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.ListGroup
import se.atte.bragwise.ui.components.ListGroupDivider
import se.atte.bragwise.ui.components.ListRow
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.components.SectionGap
import kotlin.time.Instant

@Composable
fun MeScreen(
    viewModel: MeViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToFriends: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onSignedOut: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            MeViewModel.Effect.GoToFriends -> onNavigateToFriends()
            MeViewModel.Effect.GoToEditProfile -> onNavigateToEditProfile()
            MeViewModel.Effect.GoToAbout -> onNavigateToAbout()
            MeViewModel.Effect.SignedOut -> onSignedOut()
            MeViewModel.Effect.Deleted -> onDeleted()
            is MeViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(effect.text)
        }
    }

    when {
        // Initial auth resolution or signed-in but profile still loading
        state.isLoading -> Box(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        else -> MeContent(
            player = state.player,
            email = state.email,
            isSignedIn = state.isSignedIn,
            themeMode = state.themeMode,
            notificationsEnabled = state.notificationsEnabled,
            onFriends = { viewModel.onIntent(MeViewModel.Intent.OpenFriends) },
            onEditProfile = { viewModel.onIntent(MeViewModel.Intent.OpenEditProfile) },
            onAbout = { viewModel.onIntent(MeViewModel.Intent.OpenAbout) },
            onSignOut = { viewModel.onIntent(MeViewModel.Intent.SignOut) },
            onSignIn = onNavigateToSignIn,
            onSetTheme = { viewModel.onIntent(MeViewModel.Intent.SetTheme(it)) },
            onSetNotifications = { viewModel.onIntent(MeViewModel.Intent.SetNotifications(it)) },
            onRequestDelete = { viewModel.onIntent(MeViewModel.Intent.RequestDelete) },
        )
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(MeViewModel.Intent.CancelDelete) },
            title = { Text(stringResource(Res.string.me_delete_account_title)) },
            text = {
                Text(
                    stringResource(Res.string.me_delete_account_body),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            confirmButton = {
                AppButton(onClick = { viewModel.onIntent(MeViewModel.Intent.ConfirmDelete) }) {
                    Text(stringResource(Res.string.me_delete))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.onIntent(MeViewModel.Intent.CancelDelete) },
                ) { Text(stringResource(Res.string.me_cancel)) }
            },
        )
    }
}

@Composable
private fun MeContent(
    player: Player?,
    email: String?,
    isSignedIn: Boolean,
    themeMode: ThemeMode,
    notificationsEnabled: Boolean,
    onFriends: () -> Unit,
    onEditProfile: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(standardPadding),
    ) {
        SectionCard {
            if (!isSignedIn) {
                Text(
                    text = player?.displayName?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.me_guest_label),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = stringResource(Res.string.me_guest_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                AppButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = standardPadding),
                    onClick = onSignIn,
                ) { Text(stringResource(Res.string.me_sign_in_or_sign_up)) }
            } else if (player != null) {
                Text(text = player.displayName, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "@${player.username}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = stringResource(Res.string.me_logged_in),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = email ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SectionGap()

        SectionHeader(stringResource(Res.string.me_section_account))
        ListGroup {
            ListRow(
                title = stringResource(Res.string.me_friends),
                leadingIcon = { Icon(imageVector = Lucide.UsersRound, contentDescription = null) },
                onClick = onFriends,
            )
            if (isSignedIn) {
                ListGroupDivider()
                ListRow(title = stringResource(Res.string.me_edit_profile), onClick = onEditProfile)
            }
        }

        SectionGap()

        SectionHeader(stringResource(Res.string.settings_section_title))
        ListGroup {
            ListRow(title = stringResource(Res.string.settings_about_title), onClick = onAbout)
            ListGroupDivider()
            ThemePickerRow(current = themeMode, onSelect = onSetTheme)
            if (isSignedIn) {
                ListGroupDivider()
                NotificationToggleRow(enabled = notificationsEnabled, onToggle = onSetNotifications)
            }
        }

        if (isSignedIn) {
            SectionGap()
            ListGroup {
                ListRow(
                    title = stringResource(Res.string.me_sign_out),
                    titleColor = MaterialTheme.colorScheme.error,
                    trailing = null,
                    onClick = onSignOut,
                )
                ListGroupDivider()
                ListRow(
                    title = stringResource(Res.string.me_delete_account),
                    titleColor = MaterialTheme.colorScheme.error,
                    trailing = null,
                    onClick = onRequestDelete,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = standardPadding, bottom = standardPaddingSmall),
    )
}

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.System -> Res.string.settings_theme_system
        ThemeMode.Light -> Res.string.settings_theme_light
        ThemeMode.Dark -> Res.string.settings_theme_dark
    },
)

@Composable
private fun NotificationToggleRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = standardPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.settings_notifications_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun ThemePickerRow(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListRow(
            title = stringResource(Res.string.settings_theme_title),
            trailing = current.label(),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ThemeMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label()) },
                    onClick = {
                        onSelect(m)
                        open = false
                    },
                )
            }
        }
    }
}

// region Previews

@Preview
@Composable
private fun MeContent_Guest_Preview() {
    ThemePreview {
        MeContent(
            player = null,
            email = null,
            isSignedIn = false,
            themeMode = ThemeMode.System,
            notificationsEnabled = true,
            onFriends = {},
            onEditProfile = {},
            onAbout = {},
            onSignOut = {},
            onSignIn = {},
            onSetTheme = {},
            onSetNotifications = {},
            onRequestDelete = {},
        )
    }
}

@Preview
@Composable
private fun MeContent_SignedIn_Preview() {
    ThemePreview {
        MeContent(
            player = Player(
                uid = "u1",
                username = "atte",
                displayName = "Atte Lindqvist",
                avatarSeed = "atte",
                createdAt = Instant.fromEpochSeconds(0),
            ),
            email = "atte@example.com",
            isSignedIn = true,
            themeMode = ThemeMode.System,
            notificationsEnabled = true,
            onFriends = {},
            onEditProfile = {},
            onAbout = {},
            onSignOut = {},
            onSignIn = {},
            onSetTheme = {},
            onSetNotifications = {},
            onRequestDelete = {},
        )
    }
}

// endregion
