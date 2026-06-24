package se.atte.bragwise.ui.screens.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalUriHandler
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
import bragwise.shared.generated.resources.me_legal_section
import bragwise.shared.generated.resources.me_logged_in
import bragwise.shared.generated.resources.me_privacy_policy
import bragwise.shared.generated.resources.me_section_account
import bragwise.shared.generated.resources.me_sign_in_or_sign_up
import bragwise.shared.generated.resources.me_sign_out
import bragwise.shared.generated.resources.me_support
import bragwise.shared.generated.resources.me_terms_of_service
import bragwise.shared.generated.resources.settings_about_title
import bragwise.shared.generated.resources.settings_language_system
import bragwise.shared.generated.resources.settings_language_title
import bragwise.shared.generated.resources.settings_notifications_title
import bragwise.shared.generated.resources.settings_notifications_friend_requests
import bragwise.shared.generated.resources.settings_notifications_results
import bragwise.shared.generated.resources.settings_notifications_participations
import bragwise.shared.generated.resources.settings_notifications_invites
import bragwise.shared.generated.resources.settings_section_title
import bragwise.shared.generated.resources.settings_theme_dark
import bragwise.shared.generated.resources.settings_theme_light
import bragwise.shared.generated.resources.settings_theme_system
import bragwise.shared.generated.resources.settings_theme_title
import androidx.compose.material3.Icon
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UsersRound
import se.atte.bragwise.data.NotificationPrefs
import org.jetbrains.compose.resources.stringResource
import se.atte.bragwise.data.AppLanguage
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
            hasAccount = state.hasAccount,
            isFullyAuthed = state.isFullyAuthed,
            themeMode = state.themeMode,
            language = state.language,
            notificationPrefs = state.notificationPrefs,
            notificationCategoriesExpanded = state.notificationCategoriesExpanded,
            onFriends = { viewModel.onIntent(MeViewModel.Intent.OpenFriends) },
            onEditProfile = { viewModel.onIntent(MeViewModel.Intent.OpenEditProfile) },
            onAbout = { viewModel.onIntent(MeViewModel.Intent.OpenAbout) },
            onSignOut = { viewModel.onIntent(MeViewModel.Intent.SignOut) },
            onSignIn = onNavigateToSignIn,
            onSetTheme = { viewModel.onIntent(MeViewModel.Intent.SetTheme(it)) },
            onSetLanguage = { viewModel.onIntent(MeViewModel.Intent.SetLanguage(it)) },
            onSetNotifications = { viewModel.onIntent(MeViewModel.Intent.SetNotifications(it)) },
            onToggleNotificationCategories = { viewModel.onIntent(MeViewModel.Intent.ToggleNotificationCategories) },
            onSetNotificationCategory = { key, enabled -> viewModel.onIntent(MeViewModel.Intent.SetNotificationCategory(key, enabled)) },
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
    hasAccount: Boolean,
    isFullyAuthed: Boolean,
    themeMode: ThemeMode,
    language: AppLanguage,
    notificationPrefs: NotificationPrefs,
    notificationCategoriesExpanded: Boolean,
    onFriends: () -> Unit,
    onEditProfile: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onToggleNotificationCategories: () -> Unit,
    onSetNotificationCategory: (String, Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(standardPadding),
    ) {
        SectionCard {
            if (player != null) {
                Text(text = player.displayName, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "@${player.username}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (isFullyAuthed) {
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
            } else {
                Text(
                    text = stringResource(Res.string.me_guest_label),
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            if (!isFullyAuthed) {
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
            ListGroupDivider()
            ListRow(title = stringResource(Res.string.me_edit_profile), onClick = onEditProfile)
        }

        SectionGap()

        SectionHeader(stringResource(Res.string.settings_section_title))
        ListGroup {
            ListRow(title = stringResource(Res.string.settings_about_title), onClick = onAbout)
            ListGroupDivider()
            ThemePickerRow(current = themeMode, onSelect = onSetTheme)
            ListGroupDivider()
            LanguagePickerRow(current = language, onSelect = onSetLanguage)
            if (hasAccount) {
                ListGroupDivider()
                NotificationMasterRow(
                    enabled = notificationPrefs.master,
                    expanded = notificationCategoriesExpanded,
                    onToggle = onSetNotifications,
                    onExpandToggle = onToggleNotificationCategories,
                )
                AnimatedVisibility(visible = notificationCategoriesExpanded) {
                    Column {
                        val masterEnabled = notificationPrefs.master
                        NotificationCategoryRow(
                            label = stringResource(Res.string.settings_notifications_friend_requests),
                            enabled = masterEnabled && notificationPrefs.social,
                            checkable = masterEnabled,
                            onToggle = { onSetNotificationCategory("social", it) },
                        )
                        NotificationCategoryRow(
                            label = stringResource(Res.string.settings_notifications_results),
                            enabled = masterEnabled && notificationPrefs.results,
                            checkable = masterEnabled,
                            onToggle = { onSetNotificationCategory("results", it) },
                        )
                        NotificationCategoryRow(
                            label = stringResource(Res.string.settings_notifications_participations),
                            enabled = masterEnabled && notificationPrefs.participations,
                            checkable = masterEnabled,
                            onToggle = { onSetNotificationCategory("participations", it) },
                        )
                        NotificationCategoryRow(
                            label = stringResource(Res.string.settings_notifications_invites),
                            enabled = masterEnabled && notificationPrefs.invites,
                            checkable = masterEnabled,
                            onToggle = { onSetNotificationCategory("invites", it) },
                        )
                    }
                }
            }
        }

        SectionGap()

        SectionHeader(stringResource(Res.string.me_legal_section))
        ListGroup {
            ListRow(
                title = stringResource(Res.string.me_terms_of_service),
                onClick = { uriHandler.openUri("https://bragwise.web.app/terms.html") },
            )
            ListGroupDivider()
            ListRow(
                title = stringResource(Res.string.me_privacy_policy),
                onClick = { uriHandler.openUri("https://bragwise.web.app/privacy.html") },
            )
            ListGroupDivider()
            ListRow(
                title = stringResource(Res.string.me_support),
                onClick = { uriHandler.openUri("https://bragwise.web.app/support.html") },
            )
        }

        if (hasAccount) {
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
private fun NotificationMasterRow(
    enabled: Boolean,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onExpandToggle: () -> Unit,
) {
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
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.IconButton(onClick = onExpandToggle) {
            Icon(
                imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun NotificationCategoryRow(
    label: String,
    enabled: Boolean,
    checkable: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = standardPadding * 2, end = standardPadding, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checkable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            enabled = checkable,
        )
    }
}

@Composable
private fun AppLanguage.label(): String = nativeName ?: stringResource(Res.string.settings_language_system)

@Composable
private fun LanguagePickerRow(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListRow(
            title = stringResource(Res.string.settings_language_title),
            trailing = current.label(),
            onClick = { open = true },
        )
        val windowHeight = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp()
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            Box(Modifier.heightIn(max = windowHeight)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.label()) },
                            onClick = {
                                onSelect(lang)
                                open = false
                            },
                        )
                    }
                }
            }
        }
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
private fun MeContent_BrowsingGuest_Preview() {
    ThemePreview {
        MeContent(
            player = null,
            email = null,
            hasAccount = false,
            isFullyAuthed = false,
            themeMode = ThemeMode.System,
            language = AppLanguage.System,
            notificationPrefs = NotificationPrefs.DEFAULT,
            notificationCategoriesExpanded = false,
            onFriends = {},
            onEditProfile = {},
            onAbout = {},
            onSignOut = {},
            onSignIn = {},
            onSetTheme = {},
            onSetLanguage = {},
            onSetNotifications = {},
            onToggleNotificationCategories = {},
            onSetNotificationCategory = { _, _ -> },
            onRequestDelete = {},
        )
    }
}

@Preview
@Composable
private fun MeContent_NamedGuest_Preview() {
    ThemePreview {
        MeContent(
            player = Player(
                uid = "u1",
                username = "bravefox821",
                displayName = "Alex",
                avatarSeed = "😎",
                createdAt = Instant.fromEpochSeconds(0),
            ),
            email = null,
            hasAccount = true,
            isFullyAuthed = false,
            themeMode = ThemeMode.System,
            language = AppLanguage.System,
            notificationPrefs = NotificationPrefs.DEFAULT,
            notificationCategoriesExpanded = false,
            onFriends = {},
            onEditProfile = {},
            onAbout = {},
            onSignOut = {},
            onSignIn = {},
            onSetTheme = {},
            onSetLanguage = {},
            onSetNotifications = {},
            onToggleNotificationCategories = {},
            onSetNotificationCategory = { _, _ -> },
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
            hasAccount = true,
            isFullyAuthed = true,
            themeMode = ThemeMode.System,
            language = AppLanguage.System,
            notificationPrefs = NotificationPrefs(master = true, social = true, results = false, participations = true, invites = true),
            notificationCategoriesExpanded = true,
            onFriends = {},
            onEditProfile = {},
            onAbout = {},
            onSignOut = {},
            onSignIn = {},
            onSetTheme = {},
            onSetLanguage = {},
            onSetNotifications = {},
            onToggleNotificationCategories = {},
            onSetNotificationCategory = { _, _ -> },
            onRequestDelete = {},
        )
    }
}

// endregion
