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
        is Intent.SetDisplayName -> update { it.copy(displayName = intent.v, userEdited = true) }
        is Intent.SetAvatarSeed -> update { it.copy(avatarSeed = intent.v, userEdited = true) }
        Intent.Save -> {
            val formatError = usernameFormatError(state.value.username)
            if (formatError != null) {
                update { it.copy(usernameError = formatError) }
            } else {
                viewModelScope.launch {
                    update { it.copy(saving = true) }
                    val s = state.value
                    val usernameChanged = s.username != s.originalUsername && s.username.isNotBlank()
                    val claimResult = if (usernameChanged) profiles.claimUsername(s.username) else Result.success(Unit)
                    claimResult
                        .onSuccess {
                            profiles.updateProfile(
                                displayName = s.displayName.takeIf { it.isNotBlank() },
                                username = if (usernameChanged) s.username else null,
                                avatarSeed = s.avatarSeed.takeIf { it.isNotBlank() },
                            ).onSuccess { emitEffect(Effect.Saved) }
                                .onFailure { errorReporter.report(it) }
                        }
                        .onFailure { error ->
                            val code = (error as? FirebaseFunctionsException)?.code
                            val isTaken = code == FunctionsExceptionCode.ALREADY_EXISTS
                                || error.message?.contains("handle-taken") == true
                            val isInvalid = code == FunctionsExceptionCode.INVALID_ARGUMENT
                            when {
                                isTaken -> update { it.copy(usernameError = "That username is already taken") }
                                isInvalid -> update { it.copy(usernameError = USERNAME_FORMAT_MESSAGE) }
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

    companion object {
        private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")
        private const val USERNAME_FORMAT_MESSAGE =
            "3–20 characters: lowercase letters, numbers and _ only"
    }
}
