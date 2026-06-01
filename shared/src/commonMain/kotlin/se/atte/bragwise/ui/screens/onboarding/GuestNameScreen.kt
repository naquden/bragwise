package se.atte.bragwise.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import se.atte.bragwise.theme.BragwiseTheme
import se.atte.bragwise.ui.components.AppButton

private const val MAX_GUEST_NAME = 40

/**
 * Guest onboarding name step. Sits between Welcome → guest and the
 * Challenges tab: a guest must pick an on-device display name before
 * playing their first challenge. Pure presentational — the chosen name
 * is persisted by [se.atte.bragwise.ui.nav.AppNav] into onboarding prefs.
 */
@Composable
fun GuestNameScreen(
    initialName: String,
    onContinue: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Choose a name",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This is how friends see you in challenges. You can sign up later to play on shared challenges.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= MAX_GUEST_NAME) name = it },
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(24.dp))
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onContinue(trimmed) },
            enabled = trimmed.isNotBlank(),
        ) {
            Text("Continue")
        }
    }
}

@Preview
@Composable
private fun GuestName_Preview() {
    BragwiseTheme {
        GuestNameScreen(initialName = "", onContinue = {})
    }
}
