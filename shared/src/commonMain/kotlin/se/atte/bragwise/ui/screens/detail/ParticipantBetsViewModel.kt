package se.atte.bragwise.ui.screens.detail

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.ParticipantInfo
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

class ParticipantBetsViewModel(
    private val challengeId: String,
    private val uid: String,
    private val challenges: ChallengeRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<ParticipantBetsViewModel.State, Nothing, Nothing>(
    initialState = State(),
) {
    data class Data(
        val participant: ParticipantInfo,
        val bets: List<Bet>,
        val predictions: Map<String, PredictionPayload>,
        val results: Map<String, PredictionPayload>?,
    )

    data class State(val ui: UiState<Data> = UiState.Loading)

    override fun onIntent(intent: Nothing) = Unit

    init {
        combine(
            challenges.observeChallengeDetail(id = challengeId),
            challenges.observeParticipantPredictions(challengeId = challengeId, uid = uid),
        ) { detail: ChallengeDetail, predictions: Map<String, PredictionPayload> ->
            val participant = detail.challenge.participants.firstOrNull { it.uid == uid }
                ?: ParticipantInfo(uid = uid, displayName = uid, avatarSeed = uid)
            Data(
                participant = participant,
                bets = detail.challenge.bets,
                predictions = predictions,
                results = detail.challenge.results,
            )
        }
            .distinctUntilChanged()
            .onEach { data -> update { it.copy(ui = UiState.Ready(data)) } }
            .catch { e ->
                update { it.copy(ui = UiState.Failed(e.toCause())) }
                errorReporter.report(e)
            }
            .launchIn(viewModelScope)
    }
}
