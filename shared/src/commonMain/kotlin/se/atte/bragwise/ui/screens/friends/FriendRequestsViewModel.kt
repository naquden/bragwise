package se.atte.bragwise.ui.screens.friends

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.data.observeProfiles
import se.atte.bragwise.domain.GENERIC_DISPLAY_NAME
import se.atte.bragwise.mvi.Cause
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.toCause

@OptIn(ExperimentalCoroutinesApi::class)
class FriendRequestsViewModel(
    private val social: SocialRepository,
    private val profiles: ProfileRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<FriendRequestsViewModel.State, FriendRequestsViewModel.Intent, FriendRequestsViewModel.Effect>(
    initialState = State(),
) {
    data class RequestRow(
        val uid: String,
        val displayName: String,
        val username: String?,
    )

    enum class FriendAction { Accept, Decline, Withdraw }

    data class State(
        val incoming: List<RequestRow> = emptyList(),
        val outgoing: List<RequestRow> = emptyList(),
        val acting: Set<Pair<String, FriendAction>> = emptySet(),
    ) {
        fun isActing(uid: String, action: FriendAction) = (uid to action) in acting
    }

    sealed interface Intent {
        data class Accept(val uid: String) : Intent
        data class Decline(val uid: String) : Intent
        data class Withdraw(val uid: String) : Intent
    }

    sealed interface Effect {
        data class Snackbar(val text: String) : Effect
    }

    init {
        social.observeFriendRequests()
            .flatMapLatest { friendRequests ->
                val incomingUids = friendRequests.incoming.keys.toList()
                val outgoingUids = friendRequests.outgoing.keys.toList()
                combine(
                    profiles.observeProfiles(incomingUids),
                    profiles.observeProfiles(outgoingUids),
                ) { incomingByUid, outgoingByUid ->
                    Pair(
                        incomingUids.map { uid -> toRow(uid = uid, profile = incomingByUid[uid]) },
                        outgoingUids.map { uid -> toRow(uid = uid, profile = outgoingByUid[uid]) },
                    )
                }
            }
            .onEach { (incomingRows, outgoingRows) ->
                update { it.copy(incoming = incomingRows, outgoing = outgoingRows) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.Accept -> act(intent.uid, FriendAction.Accept) { social.acceptFriendRequest(intent.uid) }
            is Intent.Decline -> act(intent.uid, FriendAction.Decline) { social.declineFriendRequest(intent.uid) }
            is Intent.Withdraw -> act(intent.uid, FriendAction.Withdraw) { social.withdrawFriendRequest(intent.uid) }
        }
    }

    private fun act(uid: String, action: FriendAction, op: suspend () -> Result<Unit>) {
        val key = uid to action
        viewModelScope.launch {
            update { it.copy(acting = it.acting + key) }
            op().onFailure { e ->
                val cause = e.toCause()
                val msg = when (cause) {
                    Cause.NotFound -> "This request is no longer available"
                    else -> null
                }
                if (msg != null) emitEffect(Effect.Snackbar(msg))
                else errorReporter.report(e)
            }
            update { it.copy(acting = it.acting - key) }
        }
    }

    private fun toRow(uid: String, profile: se.atte.bragwise.domain.PublicProfile?) = RequestRow(
        uid = uid,
        displayName = profile?.displayName ?: GENERIC_DISPLAY_NAME,
        username = profile?.username?.ifBlank { null },
    )
}
