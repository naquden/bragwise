package se.atte.bragwise.ui.screens.create

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ChallengeRepository
import se.atte.bragwise.domain.Bet
import se.atte.bragwise.domain.BetOption
import se.atte.bragwise.domain.Challenge
import se.atte.bragwise.domain.ChallengeStatus
import se.atte.bragwise.domain.OptionType
import se.atte.bragwise.domain.Visibility
import se.atte.bragwise.mvi.ScreenViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * CR-01..03 Create wizard. Plan §5: a single VM with a `step` field rather
 * than three VMs sharing data. Bet composition is rudimentary in this
 * scaffold — the real composer is a Phase-1 deliverable that pairs with the
 * `CR-03 Bet Type Picker` bottom sheet.
 */
class CreateChallengeViewModel(
    private val challenges: ChallengeRepository,
) : ScreenViewModel<CreateChallengeViewModel.State, CreateChallengeViewModel.Intent, CreateChallengeViewModel.Effect>(
    initialState = State(),
) {

    enum class Step { Metadata, Bets }

    data class State(
        val step: Step = Step.Metadata,
        val title: String = "",
        val category: String = "Other",
        val visibility: Visibility = Visibility.FRIENDS,
        val locksAt: Instant = Clock.System.now() + 7.days,
        val bets: List<Bet> = emptyList(),
        val submitting: Boolean = false,
        val error: String? = null,
    )

    sealed interface Intent {
        data class SetTitle(val title: String) : Intent
        data class SetCategory(val category: String) : Intent
        data class SetVisibility(val visibility: Visibility) : Intent
        data class SetLocksAt(val locksAt: Instant) : Intent
        data object NextStep : Intent
        data object PrevStep : Intent
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
        data object Publish : Intent
        data object SaveDraft : Intent
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
            Intent.NextStep -> update { it.copy(step = Step.Bets) }
            Intent.PrevStep -> update { it.copy(step = Step.Metadata) }
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
            Intent.Publish -> persist(publish = true)
            Intent.SaveDraft -> persist(publish = false)
        }
    }

    private fun persist(publish: Boolean) {
        if (state.value.submitting) return
        val s = state.value
        if (s.title.isBlank() || s.bets.isEmpty()) {
            emitEffect(Effect.Snackbar("Title and at least one bet required"))
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
            )
            val created = challenges.createDraft(draft)
            update { it.copy(submitting = false) }
            created.fold(
                onSuccess = { saved ->
                    if (publish) {
                        challenges.publish(saved.id).fold(
                            onSuccess = { emitEffect(Effect.Published(saved.id)) },
                            onFailure = { e -> emitEffect(Effect.Snackbar(e.message ?: "Publish failed")) },
                        )
                    } else {
                        emitEffect(Effect.DraftSaved(saved.id))
                    }
                },
                onFailure = { e -> emitEffect(Effect.Snackbar(e.message ?: "Create failed")) },
            )
        }
    }
}
