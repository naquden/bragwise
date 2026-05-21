package se.atte.bragwise.ui.screens.manage

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
) : ScreenViewModel<ManageChallengeViewModel.State, ManageChallengeViewModel.Intent, Nothing>(
    initialState = State(ui = UiState.Loading, isOwner = false),
) {
    data class State(
        val ui: UiState<ChallengeDetail>,
        val isOwner: Boolean,
    )

    sealed interface Intent

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

    override fun onIntent(intent: Intent) = Unit
}
