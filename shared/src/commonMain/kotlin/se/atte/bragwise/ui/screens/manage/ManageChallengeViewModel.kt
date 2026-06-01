package se.atte.bragwise.ui.screens.manage

import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

class ManageChallengeViewModel(
    private val challengeId: String,
    private val challenges: ChallengeRepository,
    private val auth: AuthRepository,
) : ScreenViewModel<ManageChallengeViewModel.State, ManageChallengeViewModel.Intent, ManageChallengeViewModel.Effect>(
    initialState = State(ui = UiState.Loading, isOwner = false),
) {
    data class State(
        val ui: UiState<ChallengeDetail>,
        val isOwner: Boolean,
        val confirmingDelete: Boolean = false,
    )

    sealed interface Intent {
        data object RequestDelete : Intent
        data object CancelDelete : Intent
        data object ConfirmDelete : Intent
    }

    sealed interface Effect {
        data object Deleted : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        challenges.observeChallengeDetail(challengeId)
            .distinctUntilChanged()
            .onEach { detail ->
                val myUid = (auth.authState.value as? AuthState.SignedIn)?.uid
                update {
                    it.copy(
                        ui = UiState.Ready(detail),
                        isOwner = myUid != null && detail.challenge.createdBy == myUid,
                    )
                }
            }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.RequestDelete -> update { it.copy(confirmingDelete = true) }
            Intent.CancelDelete -> update { it.copy(confirmingDelete = false) }
            Intent.ConfirmDelete -> viewModelScope.launch {
                update { it.copy(confirmingDelete = false) }
                challenges.deleteChallenge(challengeId)
                    .onSuccess { emitEffect(Effect.Deleted) }
                    .onFailure { e ->
                        if (e is FirebaseFunctionsException && e.code == FunctionsExceptionCode.NOT_FOUND) {
                            emitEffect(Effect.Deleted)
                        } else {
                            emitEffect(Effect.Snackbar("Delete failed: ${e.message ?: "unknown"}"))
                        }
                    }
            }
        }
    }
}
