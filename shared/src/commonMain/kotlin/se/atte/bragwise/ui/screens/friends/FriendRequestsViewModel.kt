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
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel

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

    data class State(
        val incoming: List<RequestRow> = emptyList(),
        val outgoing: List<RequestRow> = emptyList(),
        val acting: Set<String> = emptySet(),
    )

    sealed interface Intent {
        data class Accept(val uid: String) : Intent
        data class Decline(val uid: String) : Intent
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
            is Intent.Accept -> act(intent.uid) { social.acceptFriendRequest(intent.uid) }
            is Intent.Decline -> act(intent.uid) { social.declineFriendRequest(intent.uid) }
        }
    }

    private fun act(uid: String, op: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            update { it.copy(acting = it.acting + uid) }
            op().onFailure { errorReporter.report(it) }
            update { it.copy(acting = it.acting - uid) }
        }
    }

    private fun toRow(uid: String, profile: se.atte.bragwise.domain.PublicProfile?) = RequestRow(
        uid = uid,
        displayName = profile?.displayName ?: uid,
        username = profile?.username?.ifBlank { null },
    )
}
