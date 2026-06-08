package se.atte.bragwise.ui.screens.results

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.domain.LeaderboardEntry
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ResultsRevealViewModel(
    private val challengeId: String,
    private val challenges: ChallengeRepository,
    private val auth: AuthRepository,
    private val seenStore: ResultsSeenStore,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<ResultsRevealViewModel.State, Nothing, ResultsRevealViewModel.Effect>(
    initialState = State(),
) {
    data class State(
        val ui: UiState<RevealData> = UiState.Loading,
    )

    sealed interface Effect {
        data object PlayConfetti : Effect
    }

    data class RevealData(
        val challengeTitle: String,
        val leaderboard: List<LeaderboardEntry>,
        val myUid: String,
        val myRank: Int?,
        val myPoints: Int?,
        val participantCount: Int,
        val alreadySeen: Boolean,
        val iAmCreator: Boolean = false,
    ) {
        val winner: LeaderboardEntry? get() = leaderboard.getOrNull(0)
        val iAmWinner: Boolean get() = leaderboard.filter { it.rank == 1 }.any { it.uid == myUid }
        val fieldEntries: List<LeaderboardEntry>
            get() = if (leaderboard.size > FIELD_LIMIT) leaderboard.take(FIELD_LIMIT) else leaderboard
        val myEntryOutsideField: LeaderboardEntry?
            get() {
                if (leaderboard.size <= FIELD_LIMIT) return null
                val fieldUids = fieldEntries.mapTo(HashSet()) { it.uid }
                return leaderboard.firstOrNull { it.uid == myUid && it.uid !in fieldUids }
            }
    }

    private companion object {
        const val FIELD_LIMIT = 10
    }

    private var confettiEmitted = false

    override fun onIntent(intent: Nothing) = Unit

    init {
        auth.authState
            .flatMapLatest { authState ->
                val myUid = (authState as? AuthState.SignedIn)?.uid ?: ""
                combine(
                    challenges.observeChallengeDetail(id = challengeId),
                    challenges.observeLeaderboard(challengeId = challengeId),
                    seenStore.seenIds.take(1),
                ) { detail, leaderboard, seenAtEntry ->
                    val myEntry = leaderboard.firstOrNull { it.uid == myUid }
                    RevealData(
                        challengeTitle = detail.challenge.title,
                        leaderboard = leaderboard,
                        myUid = myUid,
                        myRank = myEntry?.rank ?: detail.myRank,
                        myPoints = myEntry?.points ?: detail.challenge.leaderboard?.get(myUid),
                        participantCount = maxOf(detail.challenge.joinedCount, leaderboard.size),
                        alreadySeen = challengeId in seenAtEntry,
                        iAmCreator = detail.challenge.createdBy == myUid,
                    )
                }
            }
            .onEach { data ->
                update { it.copy(ui = if (data.leaderboard.isEmpty()) UiState.Empty() else UiState.Ready(data)) }
                seenStore.markSeen(challengeId = challengeId)
                if (!confettiEmitted && data.iAmWinner && !data.alreadySeen) {
                    confettiEmitted = true
                    emitEffect(Effect.PlayConfetti)
                }
            }
            .catch { error ->
                update { it.copy(ui = UiState.Failed(error.toCause())) }
                errorReporter.report(error)
            }
            .launchIn(viewModelScope)
    }
}
