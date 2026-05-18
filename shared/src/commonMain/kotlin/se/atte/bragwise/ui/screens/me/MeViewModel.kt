package se.atte.bragwise.ui.screens.me

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.domain.Player
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

class MeViewModel(
    private val profile: ProfileRepository,
    private val auth: AuthRepository,
) : ScreenViewModel<MeViewModel.State, MeViewModel.Intent, MeViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    data class State(val ui: UiState<Player?>)

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
        profile.observeMe()
            .onEach { player -> update { it.copy(ui = UiState.Ready(player)) } }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
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
