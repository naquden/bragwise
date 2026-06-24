package se.atte.bragwise.ui.screens.me

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AppLanguage
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.isFullyAuthed
import se.atte.bragwise.data.LanguagePrefs
import se.atte.bragwise.data.NotificationPrefs
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.domain.Player
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.theme.ThemeMode

class MeViewModel(
    private val profile: ProfileRepository,
    private val auth: AuthRepository,
    private val themePrefs: ThemePrefs,
    private val languagePrefs: LanguagePrefs,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<MeViewModel.State, MeViewModel.Intent, MeViewModel.Effect>(
    initialState = State(themeMode = themePrefs.mode.value, language = languagePrefs.language.value),
) {

    /**
     * Auth-aware Me state.
     *
     * Rendering rules:
     *   - `isLoading`    -> spinner
     *   - `hasAccount`   -> show notifications / sign-out / delete-account rows
     *   - `isFullyAuthed`-> hide the sign-up upgrade CTA
     */
    data class State(
        val hasAccount: Boolean = false,
        val isFullyAuthed: Boolean = false,
        val isLoading: Boolean = true,
        val player: Player? = null,
        val email: String? = null,
        val themeMode: ThemeMode = ThemeMode.System,
        val language: AppLanguage = AppLanguage.System,
        val notificationPrefs: NotificationPrefs = NotificationPrefs.DEFAULT,
        val notificationCategoriesExpanded: Boolean = false,
        val confirmingDelete: Boolean = false,
    )

    sealed interface Intent {
        data object OpenFriends : Intent
        data object OpenEditProfile : Intent
        data object OpenAbout : Intent
        data object SignOut : Intent
        data class SetTheme(val mode: ThemeMode) : Intent
        data class SetLanguage(val language: AppLanguage) : Intent
        data class SetNotifications(val enabled: Boolean) : Intent
        data class SetNotificationCategory(val key: String, val enabled: Boolean) : Intent
        data object ToggleNotificationCategories : Intent
        data object RequestDelete : Intent
        data object CancelDelete : Intent
        data object ConfirmDelete : Intent
    }

    sealed interface Effect {
        data object GoToFriends : Effect
        data object GoToEditProfile : Effect
        data object GoToAbout : Effect
        data object SignedOut : Effect
        data object Deleted : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        combine(auth.authState, profile.observeMe()) { authState, player ->
            when (authState) {
                AuthState.Loading -> StateAuth(
                    hasAccount = false,
                    isFullyAuthed = false,
                    isLoading = true,
                    player = null,
                    email = null,
                )
                AuthState.SignedOut -> StateAuth(
                    hasAccount = false,
                    isFullyAuthed = false,
                    isLoading = false,
                    player = null,
                    email = null,
                )
                is AuthState.SignedIn -> StateAuth(
                    hasAccount = true,
                    isFullyAuthed = authState.isFullyAuthed,
                    isLoading = false,
                    player = player,
                    email = authState.email,
                )
            }
        }
            .onEach { s ->
                update {
                    it.copy(
                        hasAccount = s.hasAccount,
                        isFullyAuthed = s.isFullyAuthed,
                        isLoading = s.isLoading,
                        player = s.player,
                        email = s.email,
                    )
                }
            }
            .catch { _ -> update { it.copy(isLoading = false) } }
            .launchIn(viewModelScope)

        themePrefs.mode
            .onEach { m -> update { it.copy(themeMode = m) } }
            .launchIn(viewModelScope)

        languagePrefs.language
            .onEach { l -> update { it.copy(language = l) } }
            .launchIn(viewModelScope)

        profile.observeNotificationPrefs()
            .onEach { prefs -> update { it.copy(notificationPrefs = prefs) } }
            .catch { /* keep last known */ }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.OpenFriends -> emitEffect(Effect.GoToFriends)
            Intent.OpenEditProfile -> emitEffect(Effect.GoToEditProfile)
            Intent.OpenAbout -> emitEffect(Effect.GoToAbout)
            Intent.SignOut -> viewModelScope.launch {
                auth.signOut()
                emitEffect(Effect.SignedOut)
            }
            is Intent.SetTheme -> themePrefs.set(intent.mode)
            is Intent.SetLanguage -> languagePrefs.set(intent.language)
            is Intent.SetNotifications -> viewModelScope.launch {
                val prev = state.value.notificationPrefs
                update { it.copy(notificationPrefs = it.notificationPrefs.copy(master = intent.enabled)) }
                profile.setMasterNotification(intent.enabled)
                    .onFailure {
                        update { s -> s.copy(notificationPrefs = prev) }
                        errorReporter.report(it)
                    }
            }
            is Intent.SetNotificationCategory -> viewModelScope.launch {
                val prev = state.value.notificationPrefs
                val updated = when (intent.key) {
                    "social" -> prev.copy(social = intent.enabled)
                    "results" -> prev.copy(results = intent.enabled)
                    "participations" -> prev.copy(participations = intent.enabled)
                    "invites" -> prev.copy(invites = intent.enabled)
                    else -> prev
                }
                update { it.copy(notificationPrefs = updated) }
                profile.setCategoryNotification(intent.key, intent.enabled)
                    .onFailure {
                        update { s -> s.copy(notificationPrefs = prev) }
                        errorReporter.report(it)
                    }
            }
            Intent.ToggleNotificationCategories -> update {
                it.copy(notificationCategoriesExpanded = !it.notificationCategoriesExpanded)
            }
            Intent.RequestDelete -> update { it.copy(confirmingDelete = true) }
            Intent.CancelDelete -> update { it.copy(confirmingDelete = false) }
            Intent.ConfirmDelete -> viewModelScope.launch {
                update { it.copy(confirmingDelete = false) }
                auth.deleteAccount()
                    .onSuccess { emitEffect(Effect.Deleted) }
                    .onFailure { errorReporter.report(it) }
            }
        }
    }

    private data class StateAuth(
        val hasAccount: Boolean,
        val isFullyAuthed: Boolean,
        val isLoading: Boolean,
        val player: Player?,
        val email: String?,
    )
}
