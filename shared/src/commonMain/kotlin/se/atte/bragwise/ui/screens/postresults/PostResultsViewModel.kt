package se.atte.bragwise.ui.screens.postresults

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

class PostResultsViewModel(
    private val challengeId: String,
    private val challenges: ChallengeRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<PostResultsViewModel.State, PostResultsViewModel.Intent, PostResultsViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {
    data class State(
        val ui: UiState<ChallengeDetail>,
        val results: Map<String, PredictionPayload> = emptyMap(),
        val submitting: Boolean = false,
        val confirming: Boolean = false,
    )

    sealed interface Intent {
        data class SetSinglePick(val betId: String, val optionId: String) : Intent
        data class SetBoolean(val betId: String, val value: Boolean) : Intent
        data class SetRanking(val betId: String, val orderedIds: List<String>) : Intent
        data object RequestConfirm : Intent
        data object Cancel : Intent
        data object Submit : Intent
    }

    sealed interface Effect {
        data object Posted : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        challenges.observeChallengeDetail(challengeId)
            .distinctUntilChanged()
            .onEach { detail -> update { it.copy(ui = UiState.Ready(detail)) } }
            .catch { e ->
                update { it.copy(ui = UiState.Failed(e.toCause())) }
                errorReporter.report(e)
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) = when (intent) {
        is Intent.SetSinglePick -> update {
            it.copy(results = it.results + (intent.betId to PredictionPayload.SinglePick(intent.optionId)))
        }
        is Intent.SetBoolean -> update {
            it.copy(results = it.results + (intent.betId to PredictionPayload.BooleanProp(intent.value)))
        }
        is Intent.SetRanking -> update {
            it.copy(results = it.results + (intent.betId to PredictionPayload.Ranking(intent.orderedIds)))
        }
        Intent.RequestConfirm -> update { it.copy(confirming = true) }
        Intent.Cancel -> update { it.copy(confirming = false) }
        Intent.Submit -> {
            viewModelScope.launch {
                update { it.copy(submitting = true, confirming = false) }
                challenges.postResults(challengeId, state.value.results)
                    .onSuccess { emitEffect(Effect.Posted) }
                    .onFailure { errorReporter.report(it) }
                update { it.copy(submitting = false) }
            }
            Unit
        }
    }
}

internal fun PredictionPayload?.isCompleteFor(bet: Bet): Boolean = when (bet) {
    is Bet.Ranking -> {
        // A ranking is complete only when every slot is filled — no gaps.
        // Slot order may carry "" sentinels for empty slots while editing.
        val ids = (this as? PredictionPayload.Ranking)?.orderedOptionIds.orEmpty()
        ids.count { it.isNotEmpty() } == bet.topN
    }
    is Bet.SinglePick, is Bet.BooleanProp -> this != null
}
