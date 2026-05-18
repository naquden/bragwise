package se.atte.bragwise.ui.screens.leaderboard

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LeaderboardViewModel(
    private val challengeId: String,
    isPromoted: Boolean,
    private val challenges: ChallengeRepository,
) : ScreenViewModel<LeaderboardViewModel.State, LeaderboardViewModel.Intent, LeaderboardViewModel.Effect>(
    initialState = State(ui = UiState.Loading, friendsOnly = false, showTabs = isPromoted),
) {

    data class State(
        val ui: UiState<List<LeaderboardEntry>>,
        val friendsOnly: Boolean,
        val showTabs: Boolean,
    )

    sealed interface Intent {
        data class SetFriendsOnly(val friendsOnly: Boolean) : Intent
    }

    sealed interface Effect

    private val friendsOnly = MutableStateFlow(false)

    init {
        friendsOnly
            .flatMapLatest { fo -> challenges.observeLeaderboard(challengeId, fo) }
            .onEach { entries ->
                update {
                    it.copy(ui = if (entries.isEmpty()) UiState.Empty() else UiState.Ready(entries))
                }
            }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.SetFriendsOnly -> {
                friendsOnly.value = intent.friendsOnly
                update { it.copy(friendsOnly = intent.friendsOnly) }
            }
        }
    }
}
