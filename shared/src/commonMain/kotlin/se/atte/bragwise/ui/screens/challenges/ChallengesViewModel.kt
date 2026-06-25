package se.atte.bragwise.ui.screens.challenges

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.launchIn
import androidx.lifecycle.viewModelScope
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.data.observeProfiles
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

// #region agent log
private const val DBG = "BRAGWISE_DBG_9c95cf"
private fun dbg(msg: String) { println("$DBG $msg") }
private fun <T> Flow<T>.tag(name: String): Flow<T> = this
    .onStart { dbg("$name.start") }
    .onEach { v ->
        val size = (v as? Collection<*>)?.size
        dbg("$name.value size=$size")
    }
    .catch { e ->
        dbg("$name.error type=${e::class.simpleName} msg=${e.message}")
        throw e
    }
// #endregion

/**
 * CL-01 Challenges screen. Single-scroll, four optional sections:
 * My / Promoted / FromFriends / Invites — each hidden when empty.
 */
class ChallengesViewModel(
    private val challenges: ChallengeRepository,
    private val social: SocialRepository,
    private val profiles: ProfileRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<ChallengesViewModel.State, ChallengesViewModel.Intent, ChallengesViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    data class InviteEntry(
        val challenge: Challenge,
        val invitedByName: String,
    )

    data class Sections(
        val mine: List<Challenge>,
        val promoted: List<Challenge>,
        val fromFriends: List<Challenge>,
        val invites: List<InviteEntry>,
        val joinedIds: Set<String> = emptySet(),
    )

    data class State(val ui: UiState<Sections>)

    sealed interface Intent {
        data object Refresh : Intent
        data class OpenChallenge(val id: String) : Intent
        data object CreateChallenge : Intent
    }

    sealed interface Effect {
        data class GoToChallenge(val id: String) : Effect
        data object GoToCreate : Effect
    }

    init {
        // #region agent log
        dbg("init.start")
        // #endregion

        // Resolve invite entries: observe InviteCard list then flatMapLatest to fetch
        // the inviter display names, producing a List<InviteEntry>.
        val inviteEntriesFlow = challenges.observePendingInvites()
            .tag("invites")
            .flatMapLatest { cards ->
                val inviterUids = cards.map { it.invitedByUid }.distinct()
                profiles.observeProfiles(inviterUids)
                    .onStart { emit(emptyMap()) }
                    .catch { emit(emptyMap()) }
                    .onEach { profileMap ->
                        dbg("inviteProfiles.value size=${profileMap.size}")
                    }
                    .flatMapLatest { profileMap ->
                        kotlinx.coroutines.flow.flowOf(
                            cards.map { card ->
                                val name = profileMap[card.invitedByUid]
                                    ?.displayName
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Someone"
                                InviteEntry(challenge = card.challenge, invitedByName = name)
                            }
                        )
                    }
            }

        combine(
            challenges.observeMine().tag("mine"),
            challenges.observePromoted().tag("promoted"),
            challenges.observeFromFriends().tag("fromFriends"),
            inviteEntriesFlow,
            challenges.observeJoinedIds().tag("joinedIds"),
        ) { allMine, promoted, fromFriends, inviteEntries, joinedIds ->
            fun List<Challenge>.byLockAsc() = sortedWith(compareBy(nullsLast()) { it.locksAt })
            val mine = allMine.filter { it.status != ChallengeStatus.RESULTS_POSTED }
            val mineIds = mine.map { it.id }.toSet()
            val promotedIds = promoted.map { it.id }.toSet()
            val fromFriendsIds = fromFriends.map { it.id }.toSet()
            val occupiedIds = mineIds + promotedIds + fromFriendsIds
            Sections(
                mine = mine.byLockAsc(),
                promoted = promoted
                    .filter { it.status != ChallengeStatus.LOCKED || it.id in joinedIds }
                    .byLockAsc(),
                fromFriends = fromFriends.byLockAsc(),
                invites = inviteEntries.filter { it.challenge.id !in occupiedIds },
                joinedIds = joinedIds,
            )
        }
            .onEach { sections ->
                val isEmpty = sections.mine.isEmpty() &&
                    sections.promoted.isEmpty() &&
                    sections.fromFriends.isEmpty() &&
                    sections.invites.isEmpty()
                update {
                    it.copy(ui = if (isEmpty) UiState.Empty() else UiState.Ready(sections))
                }
            }
            .catch { e ->
                // #region agent log
                dbg("combine.error type=${e::class.simpleName} msg=${e.message}")
                // #endregion
                update { it.copy(ui = UiState.Failed(e.toCause())) }
                errorReporter.report(e)
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.Refresh -> { /* observe-driven; refresh is a hint only */ }
            is Intent.OpenChallenge -> emitEffect(Effect.GoToChallenge(intent.id))
            Intent.CreateChallenge -> emitEffect(Effect.GoToCreate)
        }
    }
}
