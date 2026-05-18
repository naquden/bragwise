package se.atte.bragwise.ui.screens.auth

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import se.atte.bragwise.data.AuthRepository
import se.atte.bragwise.data.AuthState
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
) : ScreenViewModel<SignInViewModel.State, SignInViewModel.Intent, SignInViewModel.Effect>(
    initialState = State(),
) {

    data class State(
        val email: String = "",
        /** Non-null after a successful `sendSignInLink` — UI flips to "Check your inbox". */
        val sentTo: String? = null,
        val submitting: Boolean = false,
    )

    sealed interface Intent {
        data class SetEmail(val email: String) : Intent
        data object SendLink : Intent
        data object Resend : Intent
        data object EditEmail : Intent
        data object ContinueAsGuest : Intent
    }

    sealed interface Effect {
        data object SignedIn : Effect
        data object ContinuedAsGuest : Effect
        data class Snackbar(val text: String) : Effect
    }

    init {
        // MainActivity routes the inbound App Link directly into AuthRepository,
        // which flips authState to SignedIn. This collector is the single point
        // of "we're signed in, leave OB-02" — works whether sign-in completed
        // from a fresh launch, a same-session deep-link return, or because the
        // user was already signed in when we opened the screen.
        auth.authState
            .filterIsInstance<AuthState.SignedIn>()
            .onEach { emitEffect(Effect.SignedIn) }
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
                onFailure = { e -> emitEffect(Effect.Snackbar(e.message ?: "Couldn't send link")) },
            )
        }
    }
}
