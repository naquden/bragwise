package se.atte.bragwise.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private const val MAX_DISPLAY_NAME = 40

/**
 * Just-in-time name capture dialog. Shown the first time a user tries to
 * participate (place bets, create a challenge) without having a display name.
 * Calls [onConfirm] with the trimmed name when the user confirms; calls
 * [onDismiss] when the user dismisses without confirming.
 */
@Composable
fun NameGateDialog(
    initialName: String = "",
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= MAX_DISPLAY_NAME) name = it },
                label = { Text("Your name") },
                placeholder = { Text("How friends see you") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AppButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotBlank(),
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
