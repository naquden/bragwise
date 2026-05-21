package se.atte.bragwise.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import se.atte.bragwise.theme.BragwiseTheme
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppTextButton
import se.atte.bragwise.ui.components.SectionCard
import se.atte.bragwise.ui.components.SectionGap

@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    onSignedIn: () -> Unit,
    onGuest: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // State-driven sign-in navigation: works even when the user lands on this
    // screen while already authed (state replays).
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            SignInViewModel.Effect.ContinuedAsGuest -> onGuest()
            is SignInViewModel.Effect.Snackbar -> { /* TODO host snackbar */ }
        }
    }

    SignInContent(
        state = state,
        onEmail = { viewModel.onIntent(SignInViewModel.Intent.SetEmail(it)) },
        onSendLink = { viewModel.onIntent(SignInViewModel.Intent.SendLink) },
        onResend = { viewModel.onIntent(SignInViewModel.Intent.Resend) },
        onEditEmail = { viewModel.onIntent(SignInViewModel.Intent.EditEmail) },
        onGuest = { viewModel.onIntent(SignInViewModel.Intent.ContinueAsGuest) },
    )
}

@Composable
private fun SignInContent(
    state: SignInViewModel.State,
    onEmail: (String) -> Unit,
    onSendLink: () -> Unit,
    onResend: () -> Unit,
    onEditEmail: () -> Unit,
    onGuest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Bragwise",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "Predict. Compete. Brag.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.sentTo == null) {
            EnterEmail(
                state = state,
                onEmail = onEmail,
                onSendLink = onSendLink,
            )
        } else {
            CheckYourInbox(
                sentTo = state.sentTo,
                onResend = onResend,
                onEditEmail = onEditEmail,
            )
        }

        SectionGap(24.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            AppTextButton(onClick = onGuest) {
                Text("Continue as guest")
            }
        }
    }
}

@Composable
private fun EnterEmail(
    state: SignInViewModel.State,
    onEmail: (String) -> Unit,
    onSendLink: () -> Unit,
) {
    SectionCard(title = "Sign in with email") {
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        Spacer(Modifier.height(12.dp))
        AppButton(
            onClick = onSendLink,
            enabled = !state.submitting && state.email.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.submitting) "Sending…" else "Send sign-in link")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "We'll email you a link. Tap it on this device to finish signing in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CheckYourInbox(
    sentTo: String,
    onResend: () -> Unit,
    onEditEmail: () -> Unit,
) {
    SectionCard(title = "Check your inbox") {
        Text(
            "We sent a sign-in link to:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            sentTo,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Tap the link on this device to finish signing in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppTextButton(onClick = onEditEmail) { Text("Use a different email") }
            AppTextButton(onClick = onResend) { Text("Resend") }
        }
    }
}

// region Previews

@Preview
@Composable
private fun SignIn_EnterEmail_Preview() {
    BragwiseTheme {
        SignInContent(
            state = SignInViewModel.State(email = "atte@example.com"),
            onEmail = {}, onSendLink = {}, onResend = {}, onEditEmail = {}, onGuest = {},
        )
    }
}

@Preview
@Composable
private fun SignIn_Sending_Preview() {
    BragwiseTheme {
        SignInContent(
            state = SignInViewModel.State(email = "atte@example.com", submitting = true),
            onEmail = {}, onSendLink = {}, onResend = {}, onEditEmail = {}, onGuest = {},
        )
    }
}

@Preview
@Composable
private fun SignIn_Sent_Preview() {
    BragwiseTheme {
        SignInContent(
            state = SignInViewModel.State(
                email = "atte@example.com",
                sentTo = "atte@example.com",
            ),
            onEmail = {}, onSendLink = {}, onResend = {}, onEditEmail = {}, onGuest = {},
        )
    }
}

// endregion
