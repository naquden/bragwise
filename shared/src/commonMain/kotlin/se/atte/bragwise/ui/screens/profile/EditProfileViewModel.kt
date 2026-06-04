package se.atte.bragwise.ui.screens.profile

import androidx.lifecycle.viewModelScope
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
import se.atte.bragwise.data.ProfileRepository
import se.atte.bragwise.mvi.ScreenViewModel

class EditProfileViewModel(
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
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
        val handleError: String? = null,
        val email: String? = null,
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

        auth.authState
            .onEach { authState ->
                val email = (authState as? AuthState.SignedIn)?.email
                update { it.copy(email = email) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) = when (intent) {
        is Intent.SetHandle -> update { it.copy(handle = intent.v, handleError = null) }
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
                    .onFailure { error ->
                        val isTaken = (error is FirebaseFunctionsException && error.code == FunctionsExceptionCode.ALREADY_EXISTS)
                            || error.message?.contains("handle-taken") == true
                        if (isTaken) {
                            update { it.copy(handleError = "That username is already taken") }
                        } else {
                            emitEffect(Effect.Snackbar("Failed to save username: ${error.message ?: "unknown"}"))
                        }
                    }
                update { it.copy(saving = false) }
            }
            Unit
        }
    }
}
