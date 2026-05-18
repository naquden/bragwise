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
import se.atte.bragwise.domain.Player
import se.atte.bragwise.mvi.ScreenViewModel

class MeViewModel(
    private val profile: ProfileRepository,
    private val auth: AuthRepository,
) : ScreenViewModel<MeViewModel.State, MeViewModel.Intent, MeViewModel.Effect>(
    initialState = State(),
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
    )

    sealed interface Intent {
        data object OpenSettings : Intent
        data object OpenFriends : Intent
        data object SignOut : Intent
    }

    sealed interface Effect {
        data object GoToSettings : Effect
        data object GoToFriends : Effect
    }

    init {
        combine(auth.authState, profile.observeMe()) { authState, player ->
            when (authState) {
                AuthState.Loading -> State(
                    isSignedIn = false,
                    isLoading = true,
                    player = null,
                    email = null,
                )
                AuthState.SignedOut -> State(
                    isSignedIn = false,
                    isLoading = false,
                    player = null,
                    email = null,
                )
                is AuthState.SignedIn -> State(
                    isSignedIn = true,
                    isLoading = false,
                    player = player,
                    email = authState.email,
                )
            }
        }
            .onEach { newState -> update { newState } }
            .catch { _ ->
                update {
                    it.copy(
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.OpenSettings -> emitEffect(Effect.GoToSettings)
            Intent.OpenFriends -> emitEffect(Effect.GoToFriends)
            Intent.SignOut -> viewModelScope.launch { auth.signOut() }
        }
    }
}
