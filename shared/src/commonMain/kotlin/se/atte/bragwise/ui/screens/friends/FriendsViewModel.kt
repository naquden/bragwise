package se.atte.bragwise.ui.screens.friends

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.SocialRepository
import se.atte.bragwise.domain.Friend
import se.atte.bragwise.mvi.ScreenViewModel
import se.atte.bragwise.mvi.UiState
import se.atte.bragwise.mvi.toCause

/**
 * LB-02 Friends. Mode-aware:
 *  - Guest: only local friends rendered; CTA "Add friend" opens local editor.
 *  - SignedIn: cloud friends rendered; CTA "Add by handle" calls
 *    `sendFriendRequest`. Local friends still observable so a user who
 *    skipped OB-06 can resume reconciliation later.
 */
class FriendsViewModel(
    private val social: SocialRepository,
    private val auth: AuthRepository,
) : ScreenViewModel<FriendsViewModel.State, FriendsViewModel.Intent, FriendsViewModel.Effect>(
    initialState = State(ui = UiState.Loading, mode = Mode.Guest),
) {

    enum class Mode { Guest, SignedIn }

    data class State(
        val ui: UiState<List<Friend>>,
        val mode: Mode,
    )

    sealed interface Intent {
        data object AddFriend : Intent
        data class EditLocal(val localId: String) : Intent
        data class RemoveLocal(val localId: String) : Intent
        data class OpenCloud(val uid: String) : Intent
        data object Reconcile : Intent
    }

    sealed interface Effect {
        data object OpenLocalAdd : Effect
        data class OpenLocalEdit(val localId: String) : Effect
        data class OpenCloudProfile(val uid: String) : Effect
        data object OpenReconcile : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        auth.authState
            .onEach { authState ->
                update {
                    it.copy(
                        mode = if (authState is AuthState.SignedIn) Mode.SignedIn else Mode.Guest,
                    )
                }
            }
            .launchIn(viewModelScope)

        social.observeFriends()
            .onEach { friends ->
                update {
                    it.copy(ui = if (friends.isEmpty()) UiState.Empty() else UiState.Ready(friends))
                }
            }
            .catch { e -> update { it.copy(ui = UiState.Failed(e.toCause())) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            Intent.AddFriend -> emitEffect(Effect.OpenLocalAdd)
            is Intent.EditLocal -> emitEffect(Effect.OpenLocalEdit(intent.localId))
            is Intent.RemoveLocal -> viewModelScope.launch {
                if (!social.removeLocalFriend(intent.localId)) {
                    emitEffect(Effect.Snackbar("Friend not found"))
                }
            }
            is Intent.OpenCloud -> emitEffect(Effect.OpenCloudProfile(intent.uid))
            Intent.Reconcile -> emitEffect(Effect.OpenReconcile)
        }
    }

    fun saveLocalFriend(localId: String?, displayName: String, avatarSeed: String) {
        if (displayName.isBlank()) return
        if (localId == null) {
            social.addLocalFriend(displayName = displayName, avatarSeed = avatarSeed)
        } else {
            social.editLocalFriend(localId = localId, displayName = displayName, avatarSeed = avatarSeed)
        }
    }
}
