package se.atte.bragwise.ui.screens.settings

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.mvi.ScreenViewModel

class SettingsViewModel(
    private val auth: AuthRepository,
) : ScreenViewModel<SettingsViewModel.State, SettingsViewModel.Intent, SettingsViewModel.Effect>(
    initialState = State(),
) {
    data class State(val signedIn: Boolean = false, val confirmingDelete: Boolean = false)

    sealed interface Intent {
        data object SignOut : Intent
        data object RequestDelete : Intent
        data object CancelDelete : Intent
        data object ConfirmDelete : Intent
    }

    sealed interface Effect {
        data object SignedOut : Effect
        data object Deleted : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        auth.authState
            .onEach { s -> update { it.copy(signedIn = s is AuthState.SignedIn) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) = when (intent) {
        Intent.SignOut -> {
            viewModelScope.launch {
                auth.signOut()
                emitEffect(Effect.SignedOut)
            }
            Unit
        }
        Intent.RequestDelete -> update { it.copy(confirmingDelete = true) }
        Intent.CancelDelete -> update { it.copy(confirmingDelete = false) }
        Intent.ConfirmDelete -> {
            viewModelScope.launch {
                update { it.copy(confirmingDelete = false) }
                auth.deleteAccount()
                    .onSuccess { emitEffect(Effect.Deleted) }
                    .onFailure { emitEffect(Effect.Snackbar("Delete failed: ${it.message ?: "unknown"}")) }
            }
            Unit
        }
    }
}
