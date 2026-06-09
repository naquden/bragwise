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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import org.jetbrains.compose.resources.getString
import se.atte.bragwise.mvi.ObserveEffects
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.app_name
import bragwise.shared.generated.resources.auth_check_inbox
import bragwise.shared.generated.resources.auth_continue_guest
import bragwise.shared.generated.resources.auth_email_hint
import bragwise.shared.generated.resources.auth_email_label
import bragwise.shared.generated.resources.auth_resend
import bragwise.shared.generated.resources.auth_send_link
import bragwise.shared.generated.resources.auth_sending
import bragwise.shared.generated.resources.auth_sent_link_to
import bragwise.shared.generated.resources.auth_sign_in_with_email
import bragwise.shared.generated.resources.auth_tap_link_hint
import bragwise.shared.generated.resources.auth_use_different_email
import bragwise.shared.generated.resources.welcome_tagline
import se.atte.bragwise.ui.components.SectionGap

@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    snackbarHostState: SnackbarHostState,
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
            is SignInViewModel.Effect.Snackbar -> snackbarHostState.showSnackbar(
                getString(effect.message.res, *effect.message.args.toTypedArray())
            )
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
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    stringResource(Res.string.welcome_tagline),
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
            AppTextButton(
                onClick = onGuest,
                modifier = Modifier.testTag("sign_in_guest"),
            ) {
                Text(stringResource(Res.string.auth_continue_guest))
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
    SectionCard {
        Text(
            stringResource(Res.string.auth_sign_in_with_email),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text(stringResource(Res.string.auth_email_label)) },
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
            Text(if (state.submitting) stringResource(Res.string.auth_sending) else stringResource(Res.string.auth_send_link))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.auth_email_hint),
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
    SectionCard(title = stringResource(Res.string.auth_check_inbox)) {
        Text(
            stringResource(Res.string.auth_sent_link_to),
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
            stringResource(Res.string.auth_tap_link_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppTextButton(onClick = onEditEmail) { Text(stringResource(Res.string.auth_use_different_email)) }
            AppTextButton(onClick = onResend) { Text(stringResource(Res.string.auth_resend)) }
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
