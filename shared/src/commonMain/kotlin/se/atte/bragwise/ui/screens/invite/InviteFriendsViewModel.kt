package se.atte.bragwise.ui.screens.invite

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel

class InviteFriendsViewModel(
    private val challengeId: String,
    private val social: SocialRepository,
    private val challenges: ChallengeRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<InviteFriendsViewModel.State, InviteFriendsViewModel.Intent, InviteFriendsViewModel.Effect>(
    initialState = State(),
) {
    data class State(
        val friends: List<CloudFriend> = emptyList(),
        val selected: Set<String> = emptySet(),
        val sending: Boolean = false,
    )

    sealed interface Intent {
        data class Toggle(val uid: String) : Intent
        data object Send : Intent
    }

    sealed interface Effect {
        data object Sent : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        social.observeFriends()
            .onEach { friends ->
                update { it.copy(friends = friends.filterIsInstance<CloudFriend>()) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) = when (intent) {
        is Intent.Toggle -> update {
            it.copy(
                selected = if (intent.uid in it.selected) it.selected - intent.uid
                else it.selected + intent.uid,
            )
        }
        Intent.Send -> {
            viewModelScope.launch {
                update { it.copy(sending = true) }
                challenges.inviteFriends(challengeId, state.value.selected.toList())
                    .onSuccess { emitEffect(Effect.Sent) }
                    .onFailure { errorReporter.report(it) }
                update { it.copy(sending = false) }
            }
            Unit
        }
    }
}
