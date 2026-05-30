package se.atte.bragwise.ui.screens.predict

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.LocalPredictionStore
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
    private val auth: AuthRepository,
    private val localPredictions: LocalPredictionStore,
) : ScreenViewModel<PredictViewModel.State, PredictViewModel.Intent, PredictViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    private val isGuest: Boolean
        get() = auth.authState.value !is AuthState.SignedIn

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
                // Guests have no cloud player doc, so myPredictions is empty —
                // seed from the on-device store instead so re-opening the
                // screen keeps their picks.
                val existing = if (isGuest) {
                    localPredictions.forChallenge(challengeId)
                } else {
                    detail.myPredictions
                }
                update {
                    it.copy(
                        ui = UiState.Ready(Bets(detail.challenge.bets, existing)),
                        drafts = existing,
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
            // Guests can't reach the cloud callable — persist locally and let
            // OB-05 migration replay these on sign-in.
            if (isGuest) {
                localPredictions.put(challengeId, drafts)
                update { it.copy(submitting = false) }
                println("$PRED_DBG submit.local challengeId=$challengeId drafts=${drafts.size}")
                emitEffect(Effect.Submitted)
                return@launch
            }
            val predictions = drafts.map { (betId, payload) -> Prediction(betId, payload) }
            println("$PRED_DBG submit.start challengeId=$challengeId drafts=${predictions.size} payloads=$drafts")
            val result = challenges.submitPredictions(challengeId, predictions)
            update { it.copy(submitting = false) }
            result.fold(
                onSuccess = {
                    println("$PRED_DBG submit.success challengeId=$challengeId")
                    emitEffect(Effect.Submitted)
                },
                onFailure = { e ->
                    println("$PRED_DBG submit.failure class=${e::class.simpleName} message=${e.message}")
                    println("$PRED_DBG submit.failure.stack ${e.stackTraceToString()}")
                    emitEffect(Effect.Snackbar("Submit failed: ${e::class.simpleName}: ${e.message}"))
                },
            )
        }
    }
}

private const val PRED_DBG = "BRAGWISE_PRED_9c95cf"
