package se.atte.bragwise.ui.screens.results

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

class ResultsViewModel(
    private val challenges: ChallengeRepository,
    private val auth: AuthRepository,
    private val seenStore: ResultsSeenStore,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<ResultsViewModel.State, ResultsViewModel.Intent, ResultsViewModel.Effect>(
    initialState = State(),
) {
    data class Sections(
        val unseen: List<Challenge>,
        val history: List<Challenge>,
        val myUid: String,
    )

    data class State(val ui: UiState<Sections> = UiState.Loading)

    sealed interface Intent {
        data class OpenReveal(val challengeId: String) : Intent
    }

    sealed interface Effect {
        data class GoToReveal(val challengeId: String) : Effect
    }

    init {
        combine(
            challenges.observeFinished(),
            auth.authState.map { state -> (state as? AuthState.SignedIn)?.uid ?: "" },
        ) { finished, myUid ->
            val seenIds = seenStore.seenIds()
            val unseen = finished.filter { it.id !in seenIds }
            val history = finished.filter { it.id in seenIds }
            Sections(unseen = unseen, history = history, myUid = myUid)
        }
            .onEach { sections ->
                val isEmpty = sections.unseen.isEmpty() && sections.history.isEmpty()
                update { it.copy(ui = if (isEmpty) UiState.Empty() else UiState.Ready(sections)) }
            }
            .catch { error ->
                update { it.copy(ui = UiState.Failed(error.toCause())) }
                errorReporter.report(error)
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.OpenReveal -> emitEffect(Effect.GoToReveal(challengeId = intent.challengeId))
        }
    }
}
