package se.atte.bragwise.ui.screens.detail

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.shareUrlForChallenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

/**
 * CH-01 Challenge Detail. Worked example from plan §5. ViewModel never
 * touches platform types — it emits a typed `ShareLink(url, message)`
 * effect, the screen resolves the message into title/subject via Compose
 * Resources and calls `PlatformShare.send(...)`.
 */
class ChallengeDetailViewModel(
    private val challengeId: String,
    private val challenges: ChallengeRepository,
    private val auth: AuthRepository,
) : ScreenViewModel<ChallengeDetailViewModel.State, ChallengeDetailViewModel.Intent, ChallengeDetailViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    data class State(val ui: UiState<ChallengeDetail>)

    sealed interface Intent {
        data object Refresh : Intent
        data object OpenPredict : Intent
        data class OpenBet(val betId: String) : Intent
        data object OpenLeaderboard : Intent
        data object Share : Intent
    }

    sealed interface Effect {
        data class GoToBet(val betId: String) : Effect
        data class GoToLeaderboard(val challengeId: String) : Effect
        data class ShareLink(val url: String, val message: ShareMessage) : Effect
        data class Snackbar(val message: SnackbarMessage) : Effect
    }

    sealed interface ShareMessage {
        data class ChallengeShare(val challengeTitle: String) : ShareMessage
    }

    sealed interface SnackbarMessage {
        data object ShareFailed : SnackbarMessage
    }

    init {
        challenges.observeChallengeDetail(challengeId)
            .distinctUntilChanged()
            .onEach { detail -> update { it.copy(ui = UiState.Ready(detail)) } }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.Refresh -> { /* re-subscribe or force-reload */ }
            Intent.OpenPredict -> emitEffect(Effect.GoToBet(challengeId))
            is Intent.OpenBet -> emitEffect(Effect.GoToBet(intent.betId))
            Intent.OpenLeaderboard -> emitEffect(Effect.GoToLeaderboard(challengeId))
            Intent.Share -> {
                val title = (state.value.ui as? UiState.Ready)?.data?.title
                if (title != null) {
                    emitEffect(
                        Effect.ShareLink(
                            url = shareUrlForChallenge(challengeId),
                            message = ShareMessage.ChallengeShare(challengeTitle = title),
                        ),
                    )
                }
            }
        }
    }
}
