package se.atte.bragwise.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.error_dialog_dismiss
import org.jetbrains.compose.resources.stringResource
import se.atte.bragwise.mvi.AppError

/**
 * App-wide error dialog. The headline is the localized [AppError.cause]
 * message; the raw [AppError.detail] (when present) is shown smaller
 * underneath for context.
 */
@Composable
fun ErrorDialog(error: AppError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = error.cause.toUserMessage()) },
        text = {
            val detail = error.detail
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            AppButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.error_dialog_dismiss))
            }
        },
    )
}
