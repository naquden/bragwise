package se.atte.bragwise.ui.screens.detail

import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.shareUrlForChallenge
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.mvi.ErrorReporter
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
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<ChallengeDetailViewModel.State, ChallengeDetailViewModel.Intent, ChallengeDetailViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    data class State(
        val ui: UiState<ChallengeDetail>,
        val isOwner: Boolean = false,
        val myUid: String = "",
        val confirmingDelete: Boolean = false,
    )

    sealed interface Intent {
        data object Refresh : Intent
        data object OpenPredict : Intent
        data class OpenBet(val betId: String) : Intent
        data object OpenSummary : Intent
        data object OpenPostResults : Intent
        data class OpenParticipant(val uid: String) : Intent
        data object RequestDelete : Intent
        data object CancelDelete : Intent
        data object ConfirmDelete : Intent
        data object Share : Intent
    }

    sealed interface Effect {
        data class GoToBet(val betId: String) : Effect
        data class GoToSummary(val challengeId: String) : Effect
        data class GoToPostResults(val challengeId: String) : Effect
        data class GoToParticipant(val challengeId: String, val uid: String) : Effect
        data object Deleted : Effect
        data class ShareLink(val url: String, val message: ShareMessage) : Effect
        data class Snackbar(val message: SnackbarMessage) : Effect
    }

    sealed interface ShareMessage {
        data class ChallengeShare(val challengeTitle: String) : ShareMessage
    }

    sealed interface SnackbarMessage {
        data object ShareFailed : SnackbarMessage
        data class DeleteFailed(val message: String) : SnackbarMessage
    }

    init {
        combine(
            challenges.observeChallengeDetail(challengeId),
            auth.authState,
        ) { detail, authState ->
            val uid = (authState as? AuthState.SignedIn)?.uid
            update {
                it.copy(
                    ui = UiState.Ready(detail),
                    isOwner = uid != null && uid == detail.challenge.createdBy,
                    myUid = uid ?: "",
                )
            }
        }
            .distinctUntilChanged()
            .catch { e ->
                update { it.copy(ui = UiState.Failed(e.toCause())) }
                errorReporter.report(e)
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.Refresh -> { /* re-subscribe or force-reload */ }
            Intent.OpenPredict -> emitEffect(Effect.GoToBet(challengeId))
            is Intent.OpenBet -> emitEffect(Effect.GoToBet(intent.betId))
            Intent.OpenSummary -> emitEffect(Effect.GoToSummary(challengeId))
            Intent.OpenPostResults -> emitEffect(Effect.GoToPostResults(challengeId))
            is Intent.OpenParticipant -> emitEffect(Effect.GoToParticipant(challengeId = challengeId, uid = intent.uid))
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
                            errorReporter.report(e)
                        }
                    }
            }
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
