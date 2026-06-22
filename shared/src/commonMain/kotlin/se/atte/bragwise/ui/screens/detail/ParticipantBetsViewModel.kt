package se.atte.bragwise.ui.screens.detail

import androidx.lifecycle.viewModelScope
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.pb_snackbar_request_failed
import bragwise.shared.generated.resources.pb_snackbar_request_sent
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.ChallengeDetail
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.GENERIC_DISPLAY_NAME
import se.atte.bragwise.domain.ParticipantInfo
import se.atte.bragwise.domain.PredictionPayload
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.UiText
import se.atte.bragwise.mvi.toCause

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ParticipantBetsViewModel(
    private val challengeId: String,
    private val uid: String,
    private val challenges: ChallengeRepository,
    private val profiles: ProfileRepository,
    private val social: SocialRepository,
    private val auth: AuthRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<ParticipantBetsViewModel.State, ParticipantBetsViewModel.Intent, ParticipantBetsViewModel.Effect>(
    initialState = State(),
) {
    enum class FriendState { SELF, FRIENDS, REQUESTED, CAN_ADD, NOT_FRIENDABLE }

    data class Data(
        val participant: ParticipantInfo,
        val bets: List<Bet>,
        val predictions: Map<String, PredictionPayload>,
        val results: Map<String, PredictionPayload>?,
        val username: String?,
        val friendState: FriendState,
    )

    data class State(val ui: UiState<Data> = UiState.Loading)

    sealed interface Intent {
        data object SendFriendRequest : Intent
    }

    sealed interface Effect {
        data class Snackbar(val message: UiText) : Effect
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.SendFriendRequest -> {
                val username = (state.value.ui as? UiState.Ready)?.data?.username ?: return
                viewModelScope.launch {
                    social.sendFriendRequest(username).fold(
                        onSuccess = {
                            emitEffect(Effect.Snackbar(UiText(Res.string.pb_snackbar_request_sent, listOf(username))))
                        },
                        onFailure = {
                            emitEffect(Effect.Snackbar(UiText(Res.string.pb_snackbar_request_failed)))
                        },
                    )
                }
            }
        }
    }

    init {
        auth.authState
            .flatMapLatest { authState ->
                val myUid = (authState as? AuthState.SignedIn)?.uid ?: ""
                combine(
                    challenges.observeChallengeDetail(id = challengeId),
                    challenges.observeParticipantPredictions(challengeId = challengeId, uid = uid),
                    profiles.observePublicProfile(uid),
                    social.observeFriends().map { friends ->
                        friends.filterIsInstance<CloudFriend>().map { it.id }.toSet()
                    },
                    social.observeFriendRequests(),
                ) { detail: ChallengeDetail, predictions: Map<String, PredictionPayload>, profile, friendUids, requests ->
                    val participant = detail.challenge.participants.firstOrNull { it.uid == uid }
                        ?: ParticipantInfo(uid = uid, displayName = GENERIC_DISPLAY_NAME, avatarSeed = "")
                    val username = profile?.username?.takeIf { it.isNotBlank() }
                    val friendState = when {
                        uid == myUid -> FriendState.SELF
                        uid in friendUids -> FriendState.FRIENDS
                        uid in requests.outgoing -> FriendState.REQUESTED
                        username == null -> FriendState.NOT_FRIENDABLE // anonymous guest proxy
                        else -> FriendState.CAN_ADD
                    }
                    Data(
                        participant = participant,
                        bets = detail.challenge.bets,
                        predictions = predictions,
                        results = detail.challenge.results,
                        username = username,
                        friendState = friendState,
                    )
                }
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
