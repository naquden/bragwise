package se.atte.bragwise.ui.screens.challenges

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import androidx.lifecycle.viewModelScope
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.Invitation
import se.atte.bragwise.mvi.Cause
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

/**
 * CL-01 Challenges screen. Single-scroll, four optional sections:
 * My / Promoted / FromFriends / Invites — each hidden when empty.
 */
class ChallengesViewModel(
    private val challenges: ChallengeRepository,
    private val social: SocialRepository,
) : ScreenViewModel<ChallengesViewModel.State, ChallengesViewModel.Intent, ChallengesViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    data class Sections(
        val mine: List<Challenge>,
        val promoted: List<Challenge>,
        val fromFriends: List<Challenge>,
        val invites: List<Invitation>,
    )

    data class State(val ui: UiState<Sections>)

    sealed interface Intent {
        data object Refresh : Intent
        data class OpenChallenge(val id: String) : Intent
        data object CreateChallenge : Intent
    }

    sealed interface Effect {
        data class GoToChallenge(val id: String) : Effect
        data object GoToCreate : Effect
    }

    init {
        combine(
            challenges.observeMine(),
            challenges.observePromoted(),
            challenges.observeFromFriends(),
            challenges.observePendingInvites(),
        ) { mine, promoted, fromFriends, invites ->
            Sections(mine, promoted, fromFriends, invites)
        }
            .onEach { sections ->
                val isEmpty = sections.mine.isEmpty() &&
                    sections.promoted.isEmpty() &&
                    sections.fromFriends.isEmpty() &&
                    sections.invites.isEmpty()
                update {
                    it.copy(ui = if (isEmpty) UiState.Empty() else UiState.Ready(sections))
                }
            }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.Refresh -> { /* observe-driven; refresh is a hint only */ }
            is Intent.OpenChallenge -> emitEffect(Effect.GoToChallenge(intent.id))
            Intent.CreateChallenge -> emitEffect(Effect.GoToCreate)
        }
    }
}
