package se.atte.bragwise.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.app_name
import bragwise.shared.generated.resources.auth_continue_guest
import bragwise.shared.generated.resources.welcome_sign_in
import bragwise.shared.generated.resources.welcome_tagline
import se.atte.bragwise.theme.BragwiseTheme
import se.atte.bragwise.ui.components.AppButton
import se.atte.bragwise.ui.components.AppTextButton

/**
 * OB-01 Welcome — first-launch hero. Routes onto SignIn (magic-link)
 * or guest tabs. No ViewModel: pure presentational, decisions are pushed
 * up to AppNav.
 */
@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onContinueAsGuest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.welcome_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSignIn,
        ) {
            Text(stringResource(Res.string.welcome_sign_in))
        }
        Spacer(Modifier.height(12.dp))
        AppTextButton(onClick = onContinueAsGuest) {
            Text(stringResource(Res.string.auth_continue_guest))
        }
    }
}

@Preview
@Composable
private fun Welcome_Preview() {
    BragwiseTheme {
        WelcomeScreen(onSignIn = {}, onContinueAsGuest = {})
    }
}
