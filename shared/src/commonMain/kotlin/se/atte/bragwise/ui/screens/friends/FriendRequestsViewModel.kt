package se.atte.bragwise.ui.screens.friends

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.FriendRequests
import se.atte.bragwise.mvi.ScreenViewModel

class FriendRequestsViewModel(
    private val social: SocialRepository,
) : ScreenViewModel<FriendRequestsViewModel.State, FriendRequestsViewModel.Intent, FriendRequestsViewModel.Effect>(
    initialState = State(),
) {
    data class State(
        val requests: FriendRequests = FriendRequests(emptyMap(), emptyMap()),
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
            .onEach { reqs -> update { it.copy(requests = reqs) } }
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
            op().onFailure { emitEffect(Effect.Snackbar("Failed: ${it.message ?: "unknown"}")) }
            update { it.copy(acting = it.acting - uid) }
        }
    }
}
