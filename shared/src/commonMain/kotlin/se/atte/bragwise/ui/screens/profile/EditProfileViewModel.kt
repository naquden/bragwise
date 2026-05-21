package se.atte.bragwise.ui.screens.profile

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.mvi.ScreenViewModel

class EditProfileViewModel(
    private val profiles: ProfileRepository,
) : ScreenViewModel<EditProfileViewModel.State, EditProfileViewModel.Intent, EditProfileViewModel.Effect>(
    initialState = State(),
) {
    data class State(
        val initialised: Boolean = false,
        val handle: String = "",
        val displayName: String = "",
        val avatarSeed: String = "",
        val originalHandle: String = "",
        val saving: Boolean = false,
    )

    sealed interface Intent {
        data class SetHandle(val v: String) : Intent
        data class SetDisplayName(val v: String) : Intent
        data class SetAvatarSeed(val v: String) : Intent
        data object Save : Intent
    }

    sealed interface Effect {
        data object Saved : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        profiles.observeMe()
            .onEach { player ->
                if (player == null) return@onEach
                update {
                    if (it.initialised) it
                    else it.copy(
                        initialised = true,
                        handle = player.handle,
                        originalHandle = player.handle,
                        displayName = player.displayName,
                        avatarSeed = player.avatarSeed,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) = when (intent) {
        is Intent.SetHandle -> update { it.copy(handle = intent.v) }
        is Intent.SetDisplayName -> update { it.copy(displayName = intent.v) }
        is Intent.SetAvatarSeed -> update { it.copy(avatarSeed = intent.v) }
        Intent.Save -> {
            viewModelScope.launch {
                update { it.copy(saving = true) }
                val s = state.value
                val handleChanged = s.handle != s.originalHandle && s.handle.isNotBlank()
                val handleResult = if (handleChanged) profiles.claimHandle(s.handle) else Result.success(Unit)
                handleResult
                    .onSuccess {
                        profiles.updateProfile(
                            displayName = s.displayName.takeIf { it.isNotBlank() },
                            handle = if (handleChanged) s.handle else null,
                            avatarSeed = s.avatarSeed.takeIf { it.isNotBlank() },
                        ).onSuccess { emitEffect(Effect.Saved) }
                            .onFailure { emitEffect(Effect.Snackbar("Save failed: ${it.message ?: "unknown"}")) }
                    }
                    .onFailure { emitEffect(Effect.Snackbar("Handle unavailable: ${it.message ?: "unknown"}")) }
                update { it.copy(saving = false) }
            }
            Unit
        }
    }
}
