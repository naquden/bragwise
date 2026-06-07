package se.atte.bragwise.ui.screens.create

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.data.EnsureNamedAccount
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.CloudFriend
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.mvi.ScreenViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Single-screen challenge creator — title, visibility and bets all on one form. */
class CreateChallengeViewModel(
    private val challenges: ChallengeRepository,
    private val social: SocialRepository,
    private val ensureNamedAccount: EnsureNamedAccount,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<CreateChallengeViewModel.State, CreateChallengeViewModel.Intent, CreateChallengeViewModel.Effect>(
    initialState = State(),
) {

    val friends: StateFlow<List<CloudFriend>> = social.observeFriends()
        .map { list -> list.filterIsInstance<CloudFriend>() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    data class State(
        val title: String = "",
        val category: String = "Other",
        val visibility: Visibility = Visibility.FRIENDS,
        val locksAt: Instant = Clock.System.now() + 7.days,
        val bets: List<Bet> = emptyList(),
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
        data class Snackbar(val text: String) : Effect
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.SetTitle -> update { it.copy(title = intent.title) }
            is Intent.SetCategory -> update { it.copy(category = intent.category) }
            is Intent.SetVisibility -> update { it.copy(visibility = intent.visibility) }
            is Intent.SetLocksAt -> update { it.copy(locksAt = intent.locksAt) }
            is Intent.AddSinglePick -> update {
                val opts = intent.options.mapIndexed { i, opt ->
                    opt.copy(id = "o$i")
                }
                val bet = Bet.SinglePick(
                    id = "b${it.bets.size + 1}",
                    title = intent.title,
                    optionType = intent.optionType,
                    options = opts,
                )
                it.copy(bets = it.bets + bet)
            }
            is Intent.AddRanking -> update {
                val opts = intent.options.mapIndexed { i, opt ->
                    opt.copy(id = "o$i")
                }
                val bet = Bet.Ranking(
                    id = "b${it.bets.size + 1}",
                    title = intent.title,
                    optionType = intent.optionType,
                    topN = intent.topN,
                    options = opts,
                )
                it.copy(bets = it.bets + bet)
            }
            is Intent.AddBoolean -> update {
                val bet = Bet.BooleanProp(id = "b${it.bets.size + 1}", title = intent.title)
                it.copy(bets = it.bets + bet)
            }
            is Intent.RemoveBet -> update { it.copy(bets = it.bets.filterNot { b -> b.id == intent.betId }) }
            is Intent.UpdateBet -> update {
                it.copy(bets = it.bets.map { b -> if (b.id == intent.bet.id) intent.bet else b })
            }
            is Intent.SetInvitedUids -> update { it.copy(invitedUids = intent.uids) }
            is Intent.SetBetsVisible -> update { it.copy(betsVisible = intent.visible) }
            Intent.Publish -> persist(publish = true)
            Intent.SaveDraft -> persist(publish = false)
            is Intent.ConfirmName -> viewModelScope.launch {
                update { it.copy(submitting = true, needsName = false) }
                ensureNamedAccount.ensure(intent.name).fold(
                    onSuccess = { persist(publish = state.value.pendingPublish) },
                    onFailure = { e ->
                        update { it.copy(submitting = false) }
                        errorReporter.report(e)
                    },
                )
            }
            Intent.DismissName -> update { it.copy(needsName = false, pendingPublish = false) }
        }
    }

    private fun persist(publish: Boolean) {
        if (state.value.submitting) return
        val s = state.value
        if (s.title.isBlank() || s.bets.isEmpty()) {
            emitEffect(Effect.Snackbar("Title and at least one bet required"))
            return
        }
        if (s.locksAt <= Clock.System.now()) {
            emitEffect(Effect.Snackbar("Deadline must be in the future"))
            return
        }
        if (ensureNamedAccount.name.value.isNullOrBlank()) {
            update { it.copy(needsName = true, pendingPublish = publish) }
            return
        }
        update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            // Server stamps id/createdBy/createdAt — placeholders here are stripped
            // by the callable. See plan §5 "Server-derived fields".
            val draft = Challenge(
                id = "",
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
                trusted = false,
                bets = s.bets,
                results = null,
                leaderboard = null,
                betsVisible = s.betsVisible,
            )
            val created = challenges.createDraft(draft)
            update { it.copy(submitting = false) }
            created.fold(
                onSuccess = { saved ->
                    if (s.visibility == Visibility.INVITE_ONLY && s.invitedUids.isNotEmpty()) {
                        challenges.inviteFriends(saved.id, s.invitedUids.toList())
                            .onFailure { e -> errorReporter.report(e) }
                    }
                    if (publish) {
                        challenges.publish(saved.id).fold(
                            onSuccess = { emitEffect(Effect.Published(saved.id)) },
                            onFailure = { e -> errorReporter.report(e) },
                        )
                    } else {
                        emitEffect(Effect.DraftSaved(saved.id))
                    }
                },
                onFailure = { e -> errorReporter.report(e) },
            )
        }
    }
}
