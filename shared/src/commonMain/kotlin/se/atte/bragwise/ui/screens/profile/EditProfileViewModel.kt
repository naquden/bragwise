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
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel

class EditProfileViewModel(
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<EditProfileViewModel.State, EditProfileViewModel.Intent, EditProfileViewModel.Effect>(
    initialState = State(),
) {
    data class State(
        val initialised: Boolean = false,
        val profileLoaded: Boolean = false,
        val userEdited: Boolean = false,
        val username: String = "",
        val displayName: String = "",
        val avatarSeed: String = "",
        val originalUsername: String = "",
        val saving: Boolean = false,
        val usernameError: String? = null,
        val displayNameError: String? = null,
        val email: String? = null,
    )

    sealed interface Intent {
        data class SetUsername(val v: String) : Intent
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
                    if (it.profileLoaded || it.userEdited) it.copy(profileLoaded = true)
                    else it.copy(
                        profileLoaded = true,
                        username = player.username,
                        originalUsername = player.username,
                        displayName = player.displayName,
                        avatarSeed = player.avatarSeed,
                    )
                }
            }
            .launchIn(viewModelScope)

        auth.authState
            .onEach { authState ->
                val signedIn = authState is AuthState.SignedIn
                val email = (authState as? AuthState.SignedIn)?.email
                update { it.copy(email = email, initialised = it.initialised || signedIn) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) = when (intent) {
        is Intent.SetUsername -> update {
            it.copy(username = intent.v, usernameError = usernameFormatError(intent.v), userEdited = true)
        }
        is Intent.SetDisplayName -> update {
            it.copy(displayName = intent.v, displayNameError = displayNameError(intent.v), userEdited = true)
        }
        is Intent.SetAvatarSeed -> update { it.copy(avatarSeed = intent.v, userEdited = true) }
        Intent.Save -> {
            val usernameErr = usernameFormatError(state.value.username)
            val displayNameErr = displayNameError(state.value.displayName)
            if (usernameErr != null || displayNameErr != null) {
                update { it.copy(usernameError = usernameErr, displayNameError = displayNameErr) }
            } else {
                viewModelScope.launch {
                    update { it.copy(saving = true) }
                    val s = state.value
                    val usernameChanged = s.username != s.originalUsername && s.username.isNotBlank()
                    profiles.updateProfile(
                        displayName = s.displayName.takeIf { it.isNotBlank() },
                        username = if (usernameChanged) s.username else null,
                        avatarSeed = s.avatarSeed.takeIf { it.isNotBlank() },
                    ).onSuccess { emitEffect(Effect.Saved) }
                        .onFailure { error ->
                            val msg = (error as? FirebaseFunctionsException)?.message ?: error.message ?: ""
                            val code = (error as? FirebaseFunctionsException)?.code
                            when {
                                code == FunctionsExceptionCode.ALREADY_EXISTS
                                    || msg.contains("handle-taken", ignoreCase = true) ->
                                    update { it.copy(usernameError = "That username is already taken") }

                                msg.contains("invalid-displayName", ignoreCase = true) ->
                                    update { it.copy(displayNameError = DISPLAY_NAME_FORMAT_MESSAGE) }

                                msg.contains("invalid-avatarSeed", ignoreCase = true) ->
                                    emitEffect(Effect.Snackbar("That avatar can't be used. Pick another."))

                                msg.contains("invalid-handle", ignoreCase = true)
                                    || code == FunctionsExceptionCode.INVALID_ARGUMENT ->
                                    update { it.copy(usernameError = USERNAME_FORMAT_MESSAGE) }

                                else -> errorReporter.report(error)
                            }
                        }
                    update { it.copy(saving = false) }
                }
            }
            Unit
        }
    }

    private fun usernameFormatError(value: String): String? = when {
        value.isEmpty() -> null
        !USERNAME_REGEX.matches(value) -> USERNAME_FORMAT_MESSAGE
        else -> null
    }

    private fun displayNameError(value: String): String? = when {
        value.length > DISPLAY_NAME_MAX -> DISPLAY_NAME_FORMAT_MESSAGE
        else -> null
    }

    companion object {
        private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")
        private const val USERNAME_FORMAT_MESSAGE =
            "3–20 characters: lowercase letters, numbers and _ only"
        const val DISPLAY_NAME_MAX = 40
        private const val DISPLAY_NAME_FORMAT_MESSAGE = "Display name must be 40 characters or fewer"
    }
}
