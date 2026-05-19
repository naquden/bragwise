package se.atte.bragwise.ui.screens.predict

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.domain.Prediction
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

/** MC-03 Predict — SinglePick, BooleanProp, and Ranking (via [RankingDragList]). */
class PredictViewModel(
    private val challengeId: String,
    private val challenges: ChallengeRepository,
) : ScreenViewModel<PredictViewModel.State, PredictViewModel.Intent, PredictViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    data class State(
        val ui: UiState<Bets>,
        val drafts: Map<String, PredictionPayload> = emptyMap(),
        val submitting: Boolean = false,
    )

    data class Bets(
        val bets: List<Bet>,
        val existing: Map<String, PredictionPayload>,
    )

    sealed interface Intent {
        data class SetSinglePick(val betId: String, val optionId: String) : Intent
        data class SetBoolean(val betId: String, val value: Boolean) : Intent
        data class SetRanking(val betId: String, val orderedOptionIds: List<String>) : Intent
        data object Submit : Intent
    }

    sealed interface Effect {
        data object Submitted : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        challenges.observeChallengeDetail(challengeId)
            .distinctUntilChanged()
            .onEach { detail ->
                update {
                    it.copy(
                        ui = UiState.Ready(Bets(detail.challenge.bets, detail.myPredictions)),
                        drafts = detail.myPredictions,
                    )
                }
            }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.SetSinglePick -> update {
                it.copy(drafts = it.drafts + (intent.betId to PredictionPayload.SinglePick(intent.optionId)))
            }
            is Intent.SetBoolean -> update {
                it.copy(drafts = it.drafts + (intent.betId to PredictionPayload.BooleanProp(intent.value)))
            }
            is Intent.SetRanking -> update {
                it.copy(drafts = it.drafts + (intent.betId to PredictionPayload.Ranking(intent.orderedOptionIds)))
            }
            Intent.Submit -> submit()
        }
    }

    private fun submit() {
        if (state.value.submitting) return
        update { it.copy(submitting = true) }
        viewModelScope.launch {
            val drafts = state.value.drafts
            val predictions = drafts.map { (betId, payload) -> Prediction(betId, payload) }
            val result = challenges.submitPredictions(challengeId, predictions)
            update { it.copy(submitting = false) }
            result.fold(
                onSuccess = { emitEffect(Effect.Submitted) },
                onFailure = { e -> emitEffect(Effect.Snackbar(e.message ?: "Submit failed")) },
            )
        }
    }
}
