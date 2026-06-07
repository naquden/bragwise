package se.atte.bragwise.ui.screens.friends

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.data.observeProfiles
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModel(
    private val social: SocialRepository,
    private val profiles: ProfileRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<FriendsViewModel.State, FriendsViewModel.Intent, FriendsViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    data class RequestRow(
        val uid: String,
        val displayName: String,
        val username: String?,
    )

    data class State(
        val ui: UiState<List<Friend>>,
        val incoming: List<RequestRow> = emptyList(),
        val outgoingCount: Int = 0,
        val acting: Set<String> = emptySet(),
        val addingFriend: Boolean = false,
        val sendingRequest: Boolean = false,
    )

    sealed interface Intent {
        data object OpenAddFriend : Intent
        data object DismissAddFriend : Intent
        data class SendFriendRequest(val username: String) : Intent
        data class OpenCloud(val uid: String) : Intent
        data class Accept(val uid: String) : Intent
        data class Decline(val uid: String) : Intent
    }

    sealed interface Effect {
        data class OpenCloudProfile(val uid: String) : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        social.observeFriends()
            .flatMapLatest { friends ->
                val cloud = friends.filterIsInstance<CloudFriend>()
                val uids = cloud.map { it.player.uid }
                profiles.observeProfiles(uids).map { byUid ->
                    cloud.map { friend ->
                        val profile = byUid[friend.player.uid]
                        friend.copy(
                            player = friend.player.copy(
                                displayName = profile?.displayName?.ifBlank { null } ?: friend.player.uid,
                                username = profile?.username ?: "",
                            ),
                        )
                    }
                }
            }
            .onEach { list ->
                update { it.copy(ui = if (list.isEmpty()) UiState.Empty() else UiState.Ready(list)) }
            }
            .catch { e ->
                update { it.copy(ui = UiState.Failed(e.toCause())) }
                errorReporter.report(e)
            }
            .launchIn(viewModelScope)

        social.observeFriendRequests()
            .flatMapLatest { friendRequests ->
                val incomingUids = friendRequests.incoming.keys.toList()
                val outgoingCount = friendRequests.outgoing.size
                profiles.observeProfiles(incomingUids).map { byUid ->
                    val rows = incomingUids.map { uid ->
                        val profile = byUid[uid]
                        RequestRow(
                            uid = uid,
                            displayName = profile?.displayName ?: uid,
                            username = profile?.username?.ifBlank { null },
                        )
                    }
                    Pair(rows, outgoingCount)
                }
            }
            .onEach { (rows, outgoingCount) -> update { it.copy(incoming = rows, outgoingCount = outgoingCount) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.OpenAddFriend -> update { it.copy(addingFriend = true) }
            Intent.DismissAddFriend -> update { it.copy(addingFriend = false) }
            is Intent.SendFriendRequest -> sendFriendRequest(intent.username)
            is Intent.OpenCloud -> emitEffect(Effect.OpenCloudProfile(intent.uid))
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

    private fun sendFriendRequest(username: String) {
        if (state.value.sendingRequest) return
        val trimmed = username.trim().removePrefix("@")
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            update { it.copy(sendingRequest = true) }
            social.sendFriendRequest(trimmed)
                .onSuccess {
                    update { it.copy(addingFriend = false, sendingRequest = false) }
                    emitEffect(Effect.Snackbar("Friend request sent to @$trimmed"))
                }
                .onFailure { error ->
                    update { it.copy(sendingRequest = false) }
                    errorReporter.report(error)
                }
        }
    }
}
