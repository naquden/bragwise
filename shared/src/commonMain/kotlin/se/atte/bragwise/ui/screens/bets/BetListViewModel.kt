package se.atte.bragwise.ui.screens.bets

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

class BetListViewModel(
    private val challengeId: String,
    private val challenges: ChallengeRepository,
) : ScreenViewModel<BetListViewModel.State, BetListViewModel.Intent, BetListViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {
    data class State(val ui: UiState<ChallengeDetail>)
    sealed interface Intent
    sealed interface Effect

    init {
        challenges.observeChallengeDetail(challengeId)
            .distinctUntilChanged()
            .onEach { detail ->
                update {
                    val ui: UiState<ChallengeDetail> = if (detail.challenge.bets.isEmpty()) {
                        UiState.Empty()
                    } else {
                        UiState.Ready(detail)
                    }
                    it.copy(ui = ui)
                }
            }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) = Unit
}
