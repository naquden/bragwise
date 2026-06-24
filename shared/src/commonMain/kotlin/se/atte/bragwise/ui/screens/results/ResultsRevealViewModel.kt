package se.atte.bragwise.ui.screens.results

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.ResultsSeenStore
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.CloudFriend
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
    private val social: SocialRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<ResultsRevealViewModel.State, ResultsRevealViewModel.Intent, ResultsRevealViewModel.Effect>(
    initialState = State(),
) {
    data class State(
        val ui: UiState<RevealData> = UiState.Loading,
        val friendsOnly: Boolean = false,
    ) {
        val displayedData: RevealData?
            get() = (ui as? UiState.Ready)?.data
    }

    sealed interface Intent {
        data object ToggleFriendsFilter : Intent
    }

    sealed interface Effect {
        data object PlayConfetti : Effect
    }

    data class RevealData(
        val challengeTitle: String,
        val leaderboard: List<LeaderboardEntry>,
        val allLeaderboard: List<LeaderboardEntry>,
        val myUid: String,
        val myRank: Int?,
        val myPoints: Int?,
        val participantCount: Int,
        val friendUids: Set<String>,
        val alreadySeen: Boolean,
        val iAmCreator: Boolean = false,
    ) {
        val displayedLeaderboard: List<LeaderboardEntry>
            get() = leaderboard

        val winner: LeaderboardEntry? get() = displayedLeaderboard.getOrNull(0)
        val iAmWinner: Boolean get() = displayedLeaderboard.filter { it.rank == 1 }.any { it.uid == myUid }
        val fieldEntries: List<LeaderboardEntry>
            get() = if (displayedLeaderboard.size > FIELD_LIMIT) displayedLeaderboard.take(FIELD_LIMIT) else displayedLeaderboard
        val myEntryOutsideField: LeaderboardEntry?
            get() {
                if (displayedLeaderboard.size <= FIELD_LIMIT) return null
                val fieldUids = fieldEntries.mapTo(HashSet()) { it.uid }
                return displayedLeaderboard.firstOrNull { it.uid == myUid && it.uid !in fieldUids }
            }
        val hasFriendsToFilter: Boolean
            get() = friendUids.isNotEmpty() && allLeaderboard.any { it.uid in friendUids }
        val displayedParticipantCount: Int
            get() = displayedLeaderboard.size
    }

    private companion object {
        const val FIELD_LIMIT = 10
    }

    private var confettiEmitted = false
    private var alreadySeenAtEntry: Boolean? = null
    private val friendsOnlyState = MutableStateFlow(false)

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.ToggleFriendsFilter -> {
                friendsOnlyState.value = !friendsOnlyState.value
                confettiEmitted = false
            }
        }
    }

    init {
        auth.authState
            .flatMapLatest { authState ->
                val myUid = (authState as? AuthState.SignedIn)?.uid ?: ""
                combine(
                    challenges.observeChallengeDetail(id = challengeId),
                    challenges.observeLeaderboard(challengeId = challengeId),
                    seenStore.seenIds,
                    social.observeFriends().map { friends ->
                        friends.filterIsInstance<CloudFriend>().map { it.id }.toSet()
                    },
                    friendsOnlyState,
                ) { detail, leaderboard, seenIds, friendUids, friendsOnly ->
                    val myEntry = leaderboard.firstOrNull { it.uid == myUid }
                    val displayLeaderboard = if (friendsOnly) {
                        leaderboard.filter { it.uid in friendUids || it.uid == myUid }
                    } else {
                        leaderboard
                    }
                    val seenAtEntry = alreadySeenAtEntry ?: (challengeId in seenIds).also {
                        alreadySeenAtEntry = it
                    }
                    RevealData(
                        challengeTitle = detail.challenge.title,
                        leaderboard = displayLeaderboard,
                        allLeaderboard = leaderboard,
                        myUid = myUid,
                        myRank = myEntry?.rank ?: detail.myRank,
                        myPoints = myEntry?.points ?: detail.challenge.leaderboard?.get(myUid),
                        participantCount = maxOf(detail.challenge.joinedCount, leaderboard.size),
                        friendUids = friendUids,
                        alreadySeen = seenAtEntry,
                        iAmCreator = detail.challenge.createdBy == myUid,
                    )
                }
            }
            .onEach { data ->
                update { it.copy(ui = if (data.allLeaderboard.isEmpty()) UiState.Empty() else UiState.Ready(data), friendsOnly = friendsOnlyState.value) }
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
