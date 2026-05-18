package se.atte.bragwise.ui.screens.onboarding

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ReconciliationSummary
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.LocalFriend
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

/**
 * OB-06 Reconcile friends. Surfaces any leftover [LocalFriend] rows
 * after a guest signs up; user types a cloud handle next to each row
 * (or leaves it blank to skip). Send fires real `sendFriendRequest`
 * calls; mapped rows are removed from the local store on success.
 */
class ReconcileFriendsViewModel(
    private val social: SocialRepository,
) : ScreenViewModel<ReconcileFriendsViewModel.State, ReconcileFriendsViewModel.Intent, ReconcileFriendsViewModel.Effect>(
    initialState = State(ui = UiState.Loading),
) {

    data class State(
        val ui: UiState<List<LocalFriend>>,
        val handles: Map<String, String> = emptyMap(),
        val submitting: Boolean = false,
    )

    sealed interface Intent {
        data class SetHandle(val localId: String, val handle: String) : Intent
        data object Send : Intent
        data object Skip : Intent
    }

    sealed interface Effect {
        data class Done(val summary: ReconciliationSummary) : Effect
        data object Skipped : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        social.observeLocalFriends()
            .onEach { friends ->
                val wasLoading = state.value.ui == UiState.Loading
                if (friends.isEmpty()) {
                    update { it.copy(ui = UiState.Empty()) }
                    // Only auto-skip on the initial load; after submission Done is emitted instead.
                    if (wasLoading) emitEffect(Effect.Skipped)
                } else {
                    update { it.copy(ui = UiState.Ready(friends)) }
                }
            }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.SetHandle -> update {
                it.copy(handles = it.handles + (intent.localId to intent.handle))
            }
            Intent.Skip -> emitEffect(Effect.Skipped)
            Intent.Send -> submit()
        }
    }

    private fun submit() {
        if (state.value.submitting) return
        val list = (state.value.ui as? UiState.Ready)?.data ?: return
        update { it.copy(submitting = true) }
        viewModelScope.launch {
            val mappings = list.map { row ->
                row.localId to state.value.handles[row.localId]?.takeIf { it.isNotBlank() }
            }
            val summary = social.reconcileLocalFriends(mappings)
            update { it.copy(submitting = false) }
            emitEffect(Effect.Done(summary))
        }
    }
}
