package se.atte.bragwise.ui.screens.friends

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

class FriendsViewModel(
    private val social: SocialRepository,
) : ScreenViewModel<FriendsViewModel.State, FriendsViewModel.Intent, FriendsViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    data class State(
        val ui: UiState<List<Friend>>,
        val addingFriend: Boolean = false,
        val sendingRequest: Boolean = false,
    )

    sealed interface Intent {
        data object OpenAddFriend : Intent
        data object DismissAddFriend : Intent
        data class SendFriendRequest(val username: String) : Intent
        data class OpenCloud(val uid: String) : Intent
    }

    sealed interface Effect {
        data class OpenCloudProfile(val uid: String) : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        social.observeFriends()
            .onEach { friends ->
                update {
                    it.copy(ui = if (friends.isEmpty()) UiState.Empty() else UiState.Ready(friends))
                }
            }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.OpenAddFriend -> update { it.copy(addingFriend = true) }
            Intent.DismissAddFriend -> update { it.copy(addingFriend = false) }
            is Intent.SendFriendRequest -> sendFriendRequest(intent.username)
            is Intent.OpenCloud -> emitEffect(Effect.OpenCloudProfile(intent.uid))
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
                    emitEffect(Effect.Snackbar(error.message ?: "Failed to send friend request"))
                }
        }
    }
}
