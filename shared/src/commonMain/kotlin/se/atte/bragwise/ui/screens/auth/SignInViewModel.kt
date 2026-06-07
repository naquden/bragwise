package se.atte.bragwise.ui.screens.auth

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.isFullyAuthed
import se.atte.bragwise.mvi.ErrorReporter
import se.atte.bragwise.mvi.ScreenViewModel

/**
 * OB-02 — unified Sign In / Sign Up via email-link passwordless. There is
 * no separate sign-up step: `signInWithEmailLink` on the Firebase side
 * auto-creates the account if one doesn't exist for the typed email.
 *
 * Phase 1 ships email-link only. Google + Apple OAuth buttons land in
 * Phase 2 (must ship together on iOS — App Store guideline 4.8).
 */
class SignInViewModel(
    private val auth: AuthRepository,
    private val errorReporter: ErrorReporter,
) : ScreenViewModel<SignInViewModel.State, SignInViewModel.Intent, SignInViewModel.Effect>(
    initialState = State(),
) {

    data class State(
        val email: String = "",
        /** Non-null after a successful `sendSignInLink` — UI flips to "Check your inbox". */
        val sentTo: String? = null,
        val submitting: Boolean = false,
        /**
         * Mirrors `AuthRepository.authState` becoming `SignedIn`. State-driven
         * (not effect-driven) so the navigation signal survives the race where
         * the user lands on this screen while already authed — the StateFlow
         * replays the current value to the screen's late-attaching collector,
         * whereas `Effect.SignedIn` on a replay-0 SharedFlow would be lost.
         */
        val signedIn: Boolean = false,
    )

    sealed interface Intent {
        data class SetEmail(val email: String) : Intent
        data object SendLink : Intent
        data object Resend : Intent
        data object EditEmail : Intent
        data object ContinueAsGuest : Intent
    }

    sealed interface Effect {
        data object ContinuedAsGuest : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        // MainActivity routes the inbound App Link directly into AuthRepository,
        // which flips authState to SignedIn. We mirror that into our State so
        // SignInScreen reacts via LaunchedEffect on state.signedIn — works
        // whether sign-in completed from a fresh launch, a same-session
        // deep-link return, or because the user was already signed in when we
        // opened the screen (StateFlow replays the current value).
        auth.authState
            // Anonymous guests are "signed in" too; only a real (email) account
            // should advance past the sign-in screen — otherwise a guest opening
            // this screen to upgrade would be bounced straight back out.
            .filter { it.isFullyAuthed }
            .onEach { update { it.copy(signedIn = true) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.SetEmail -> update { it.copy(email = intent.email) }
            Intent.SendLink -> sendLink()
            Intent.Resend -> sendLink()
            Intent.EditEmail -> update { it.copy(sentTo = null) }
            Intent.ContinueAsGuest -> emitEffect(Effect.ContinuedAsGuest)
        }
    }

    private fun sendLink() {
        val email = state.value.email.trim()
        if (state.value.submitting || email.isBlank()) return
        update { it.copy(submitting = true) }
        viewModelScope.launch {
            val r = auth.sendSignInLink(email)
            update { it.copy(submitting = false) }
            r.fold(
                onSuccess = { update { s -> s.copy(sentTo = email) } },
                onFailure = { e -> errorReporter.report(e) },
            )
        }
    }
}
