package se.atte.bragwise.ui.screens.me

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.ThemePrefs
import se.atte.bragwise.domain.Player
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.theme.ThemeMode

class MeViewModel(
    private val profile: ProfileRepository,
    private val auth: AuthRepository,
    private val themePrefs: ThemePrefs,
) : ScreenViewModel<MeViewModel.State, MeViewModel.Intent, MeViewModel.Effect>(
    initialState = State(themeMode = themePrefs.mode.value),
) {

    /**
     * Auth-aware Me state. `isSignedIn` is the canonical session signal
     * (sourced from `AuthRepository.authState`); `player` is the optional
     * Firestore profile that may lag behind auth (or never arrive if the
     * `updateProfile` callable hasn't been called yet).
     *
     * Rendering rules:
     *   - `isLoading`                 -> spinner
     *   - `!isSignedIn`               -> Guest UI
     *   - `isSignedIn && player==null`-> spinner (profile loading)
     *   - `isSignedIn && player!=null`-> full signed-in UI
     */
    data class State(
        val isSignedIn: Boolean = false,
        val isLoading: Boolean = true,
        val player: Player? = null,
        val email: String? = null,
        val themeMode: ThemeMode = ThemeMode.System,
        val notificationsEnabled: Boolean = true,
        val confirmingDelete: Boolean = false,
    )

    sealed interface Intent {
        data object OpenFriends : Intent
        data object OpenEditProfile : Intent
        data object OpenAbout : Intent
        data object SignOut : Intent
        data class SetTheme(val mode: ThemeMode) : Intent
        data class SetNotifications(val enabled: Boolean) : Intent
        data object RequestDelete : Intent
        data object CancelDelete : Intent
        data object ConfirmDelete : Intent
    }

    sealed interface Effect {
        data object GoToFriends : Effect
        data object GoToEditProfile : Effect
        data object GoToAbout : Effect
        data object Deleted : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        combine(auth.authState, profile.observeMe()) { authState, player ->
            when (authState) {
                AuthState.Loading -> StateAuth(
                    isSignedIn = false,
                    isLoading = true,
                    player = null,
                    email = null,
                )
                AuthState.SignedOut -> StateAuth(
                    isSignedIn = false,
                    isLoading = false,
                    player = null,
                    email = null,
                )
                is AuthState.SignedIn -> if (authState.isAnonymous) StateAuth(
                    // Anonymous guest: render the Guest UI (sign-up CTA), not the
                    // full account UI with edit-profile / delete-account.
                    // Pass the player through so the name is shown when available.
                    isSignedIn = false,
                    isLoading = false,
                    player = player,
                    email = null,
                ) else StateAuth(
                    isSignedIn = true,
                    isLoading = false,
                    player = player,
                    email = authState.email,
                )
            }
        }
            .onEach { s ->
                update {
                    it.copy(
                        isSignedIn = s.isSignedIn,
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

        profile.observeNotificationsEnabled()
            .onEach { enabled -> update { it.copy(notificationsEnabled = enabled) } }
            .catch { /* keep last known */ }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.OpenFriends -> emitEffect(Effect.GoToFriends)
            Intent.OpenEditProfile -> emitEffect(Effect.GoToEditProfile)
            Intent.OpenAbout -> emitEffect(Effect.GoToAbout)
            Intent.SignOut -> viewModelScope.launch { auth.signOut() }
            is Intent.SetTheme -> themePrefs.set(intent.mode)
            is Intent.SetNotifications -> viewModelScope.launch {
                // Optimistic: reflect immediately; the observe flow corrects on
                // server confirmation, and we revert + snackbar on failure.
                update { it.copy(notificationsEnabled = intent.enabled) }
                profile.setNotificationsEnabled(intent.enabled)
                    .onFailure {
                        update { s -> s.copy(notificationsEnabled = !intent.enabled) }
                        emitEffect(Effect.Snackbar("Couldn't update notifications"))
                    }
            }
            Intent.RequestDelete -> update { it.copy(confirmingDelete = true) }
            Intent.CancelDelete -> update { it.copy(confirmingDelete = false) }
            Intent.ConfirmDelete -> viewModelScope.launch {
                update { it.copy(confirmingDelete = false) }
                auth.deleteAccount()
                    .onSuccess { emitEffect(Effect.Deleted) }
                    .onFailure { emitEffect(Effect.Snackbar("Delete failed: ${it.message ?: "unknown"}")) }
            }
        }
    }

    private data class StateAuth(
        val isSignedIn: Boolean,
        val isLoading: Boolean,
        val player: Player?,
        val email: String?,
    )
}
