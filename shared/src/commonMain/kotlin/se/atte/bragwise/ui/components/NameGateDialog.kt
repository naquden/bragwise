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
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.namegate_cancel
import bragwise.shared.generated.resources.namegate_confirm
import bragwise.shared.generated.resources.namegate_label
import bragwise.shared.generated.resources.namegate_placeholder
import bragwise.shared.generated.resources.namegate_title
import se.atte.bragwise.ui.InputLimits

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
        title = { Text(stringResource(Res.string.namegate_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= InputLimits.DISPLAY_NAME) name = it },
                label = { Text(stringResource(Res.string.namegate_label)) },
                placeholder = { Text(stringResource(Res.string.namegate_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AppButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotBlank(),
            ) {
                Text(stringResource(Res.string.namegate_confirm))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.namegate_cancel))
            }
        },
    )
}
