package se.atte.bragwise.ui.screens.create

import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.cc_snackbar_deadline_future
import bragwise.shared.generated.resources.cc_snackbar_invite_needs_friends
import bragwise.shared.generated.resources.cc_snackbar_no_reachable_invitees
import bragwise.shared.generated.resources.cc_snackbar_nothing_to_save
import bragwise.shared.generated.resources.cc_snackbar_title_and_bet_required
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.EnsureNamedAccount
import se.atte.bragwise.data.NameState
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.UiText
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.GuessGranularity
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.mvi.ScreenViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Single-screen challenge creator/editor — title, visibility and bets all on one form. */
class CreateChallengeViewModel(
    private val challenges: ChallengeRepository,
    private val social: SocialRepository,
    private val ensureNamedAccount: EnsureNamedAccount,
    private val errorReporter: ErrorReporter,
    draftId: String? = null,
) : ScreenViewModel<CreateChallengeViewModel.State, CreateChallengeViewModel.Intent, CreateChallengeViewModel.Effect>(
    initialState = State(),
) {

    val friends: StateFlow<List<CloudFriend>> = social.observeFriends()
        .map { list -> list.filterIsInstance<CloudFriend>() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    data class State(
        val draftId: String? = null,
        val title: String = "",
        val category: String = "Other",
        val visibility: Visibility = Visibility.FRIENDS,
        val locksAt: Instant = Clock.System.now() + 1.hours,
        val bets: List<Bet> = emptyList(),
        val betSeq: Int = 0,
        val invitedUids: Set<String> = emptySet(),
        val betsVisible: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null,
        val needsName: Boolean = false,
        val pendingPublish: Boolean = false,
    )

    sealed interface Intent {
        data class SetTitle(val title: String) : Intent
        data class SetCategory(val category: String) : Intent
        data class SetVisibility(val visibility: Visibility) : Intent
        data class SetLocksAt(val locksAt: Instant) : Intent
        data class AddSinglePick(
            val title: String,
            val options: List<BetOption>,
            val optionType: OptionType = OptionType.NONE,
        ) : Intent
        data class AddRanking(
            val title: String,
            val options: List<BetOption>,
            val optionType: OptionType = OptionType.NONE,
            val topN: Int = 3,
        ) : Intent
        data class AddBoolean(val title: String) : Intent
        data class AddGuess(val title: String, val granularity: GuessGranularity, val closest: Boolean = true) : Intent
        data class RemoveBet(val betId: String) : Intent
        data class UpdateBet(val bet: Bet) : Intent
        data class SetInvitedUids(val uids: Set<String>) : Intent
        data class SetBetsVisible(val visible: Boolean) : Intent
        data object Publish : Intent
        data object SaveDraft : Intent
        data class ConfirmName(val name: String) : Intent
        data object DismissName : Intent
    }

    sealed interface Effect {
        data class Published(val challengeId: String) : Effect
        data class DraftSaved(val challengeId: String) : Effect
        data class Snackbar(val message: UiText) : Effect
    }

    init {
        if (draftId != null) {
            val draft = challenges.getDraft(draftId)
            if (draft != null) {
                update {
                    val maxSeq = draft.bets.maxOfOrNull { b -> b.id.removePrefix("b").toIntOrNull() ?: 0 } ?: 0
                    it.copy(
                        draftId = draftId,
                        title = draft.title,
                        category = draft.category,
                        visibility = draft.visibility,
                        locksAt = draft.locksAt ?: it.locksAt,
                        bets = draft.bets,
                        betSeq = maxSeq,
                        betsVisible = draft.betsVisible,
                        invitedUids = draft.invitedUids,
                    )
                }
            }
        }
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.SetTitle -> update { it.copy(title = intent.title) }
            is Intent.SetCategory -> update { it.copy(category = intent.category) }
            is Intent.SetVisibility -> update { it.copy(visibility = intent.visibility) }
            is Intent.SetLocksAt -> update { it.copy(locksAt = intent.locksAt) }
            is Intent.AddSinglePick -> update {
                val seq = it.betSeq + 1
                val opts = intent.options.mapIndexed { i, opt ->
                    opt.copy(id = "o$i")
                }
                val bet = Bet.SinglePick(
                    id = "b$seq",
                    title = intent.title,
                    optionType = intent.optionType,
                    options = opts,
                )
                it.copy(bets = it.bets + bet, betSeq = seq)
            }
            is Intent.AddRanking -> update {
                val seq = it.betSeq + 1
                val opts = intent.options.mapIndexed { i, opt ->
                    opt.copy(id = "o$i")
                }
                val bet = Bet.Ranking(
                    id = "b$seq",
                    title = intent.title,
                    optionType = intent.optionType,
                    topN = intent.topN,
                    options = opts,
                )
                it.copy(bets = it.bets + bet, betSeq = seq)
            }
            is Intent.AddBoolean -> update {
                val seq = it.betSeq + 1
                val bet = Bet.BooleanProp(id = "b$seq", title = intent.title)
                it.copy(bets = it.bets + bet, betSeq = seq)
            }
            is Intent.AddGuess -> update {
                val seq = it.betSeq + 1
                val bet = Bet.Guess(id = "b$seq", title = intent.title, granularity = intent.granularity, closest = intent.closest)
                it.copy(bets = it.bets + bet, betSeq = seq)
            }
            is Intent.RemoveBet -> update { it.copy(bets = it.bets.filterNot { b -> b.id == intent.betId }) }
            is Intent.UpdateBet -> update {
                it.copy(bets = it.bets.map { b -> if (b.id == intent.bet.id) intent.bet else b })
            }
            is Intent.SetInvitedUids -> update { it.copy(invitedUids = intent.uids) }
            is Intent.SetBetsVisible -> update { it.copy(betsVisible = intent.visible) }
            Intent.SaveDraft -> persistDraft()
            Intent.Publish -> publish()
            is Intent.ConfirmName -> viewModelScope.launch {
                update { it.copy(submitting = true, needsName = false) }
                ensureNamedAccount.ensure(intent.name).fold(
                    onSuccess = { publish() },
                    onFailure = { e ->
                        update { it.copy(submitting = false) }
                        errorReporter.report(e)
                    },
                )
            }
            Intent.DismissName -> update { it.copy(needsName = false, pendingPublish = false) }
        }
    }

    private fun persistDraft() {
        if (state.value.submitting) return
        val s = state.value
        if (s.title.isBlank() && s.bets.isEmpty()) {
            emitEffect(Effect.Snackbar(UiText(Res.string.cc_snackbar_nothing_to_save)))
            return
        }
        update { it.copy(submitting = true) }
        viewModelScope.launch {
            val draft = buildChallenge(s)
            challenges.saveDraft(draft).fold(
                onSuccess = { saved ->
                    update { it.copy(submitting = false, draftId = saved.id) }
                    emitEffect(Effect.DraftSaved(saved.id))
                },
                onFailure = { e ->
                    update { it.copy(submitting = false) }
                    errorReporter.report(e)
                },
            )
        }
    }

    private fun publish() {
        if (state.value.submitting) return
        val s = state.value
        if (s.title.isBlank() || s.bets.isEmpty()) {
            emitEffect(Effect.Snackbar(UiText(Res.string.cc_snackbar_title_and_bet_required)))
            return
        }
        if (s.locksAt <= Clock.System.now()) {
            emitEffect(Effect.Snackbar(UiText(Res.string.cc_snackbar_deadline_future)))
            return
        }
        if (s.visibility == Visibility.INVITE_ONLY && s.invitedUids.isEmpty()) {
            emitEffect(Effect.Snackbar(UiText(Res.string.cc_snackbar_invite_needs_friends)))
            return
        }
        when (ensureNamedAccount.nameState.value) {
            is NameState.Loading -> return  // cloud not resolved yet; user can retry
            is NameState.Absent -> {
                update { it.copy(needsName = true, pendingPublish = true) }
                return
            }
            is NameState.Present -> Unit
        }
        update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val draft = buildChallenge(s)
            challenges.publish(draft).fold(
                onSuccess = { saved ->
                    update { it.copy(submitting = false) }
                    emitEffect(Effect.Published(saved.id))
                },
                onFailure = { e ->
                    update { it.copy(submitting = false) }
                    if (e.message?.contains("invite-only-no-reachable-invitees") == true) {
                        emitEffect(Effect.Snackbar(UiText(Res.string.cc_snackbar_no_reachable_invitees)))
                    } else {
                        errorReporter.report(e)
                    }
                },
            )
        }
    }

    private fun buildChallenge(s: State): Challenge = Challenge(
        id = s.draftId ?: "",
        title = s.title,
        description = "",
        category = s.category,
        visibility = s.visibility,
        createdBy = "",
        createdAt = Clock.System.now(),
        locksAt = s.locksAt,
        resultsPostedAt = null,
        status = ChallengeStatus.DRAFT,
        joinedCount = 0,
        promoted = false,
        bets = s.bets,
        results = null,
        leaderboard = null,
        betsVisible = s.betsVisible,
        invitedUids = s.invitedUids,
    )
}
