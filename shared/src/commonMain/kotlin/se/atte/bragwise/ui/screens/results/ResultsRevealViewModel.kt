package se.atte.bragwise.ui.screens.results

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.data.signedInUid
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ResultsRevealViewModel(
    private val challengeId: String,
    private val challenges: ChallengeRepository,
    private val auth: AuthRepository,
    private val seenStore: ResultsSeenStore,
) : ScreenViewModel<ResultsRevealViewModel.State, Nothing, Nothing>(
    initialState = State(),
) {
    data class State(
        val ui: UiState<RevealData> = UiState.Loading,
    )

    data class RevealData(
        val challengeTitle: String,
        val leaderboard: List<LeaderboardEntry>,
        val myUid: String,
        val myRank: Int?,
        val myPoints: Int?,
        val participantCount: Int,
        val alreadySeen: Boolean,
    ) {
        val winner: LeaderboardEntry? get() = leaderboard.getOrNull(0)
        val iAmWinner: Boolean get() = leaderboard.filter { it.rank == 1 }.any { it.uid == myUid }
        val fieldEntries: List<LeaderboardEntry>
            get() = if (participantCount > 20) leaderboard.take(10) else leaderboard
        val myEntryOutsideField: LeaderboardEntry?
            get() {
                if (participantCount <= 20) return null
                val fieldUids = fieldEntries.map { it.uid }.toSet()
                return leaderboard.firstOrNull { it.uid == myUid && it.uid !in fieldUids }
            }
    }

    override fun onIntent(intent: Nothing) = Unit

    init {
        auth.authState
            .flatMapLatest { authState ->
                val myUid = (authState as? AuthState.SignedIn)?.uid ?: ""
                combine(
                    challenges.observeChallengeDetail(id = challengeId),
                    challenges.observeLeaderboard(challengeId = challengeId),
                ) { detail, leaderboard ->
                    val alreadySeen = seenStore.isSeen(challengeId = challengeId)
                    val myEntry = leaderboard.firstOrNull { it.uid == myUid }
                    RevealData(
                        challengeTitle = detail.challenge.title,
                        leaderboard = leaderboard,
                        myUid = myUid,
                        myRank = myEntry?.rank ?: detail.myRank,
                        myPoints = myEntry?.points ?: detail.challenge.leaderboard?.get(myUid),
                        participantCount = detail.challenge.joinedCount,
                        alreadySeen = alreadySeen,
                    )
                }
            }
            .onEach { data ->
                update { it.copy(ui = UiState.Ready(data)) }
                // Mark as seen after first successful load
                seenStore.markSeen(challengeId = challengeId)
            }
            .catch { error -> update { it.copy(ui = UiState.Failed(error.toCause())) } }
            .launchIn(viewModelScope)
    }
}
