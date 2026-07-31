package se.atte.bragwise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.loading_dialog_stop_waiting
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

private val STOP_WAITING_DELAY = 20.seconds

/**
 * [onStopWaiting], when supplied, reveals a "Stop waiting" button after
 * [STOP_WAITING_DELAY] — well past how long a healthy call takes — so a
 * stalled network can never trap the user behind a permanent dialog. It is
 * deliberately not "Cancel": by the time this shows, the underlying write is
 * already in flight server-side and cannot be undone; this only stops
 * *waiting* for it, it does not undo anything.
 */
@Composable
fun LoadingDialog(message: String = "Please wait…", onStopWaiting: (() -> Unit)? = null) {
    var canStopWaiting by remember { mutableStateOf(false) }
    LaunchedEffect(onStopWaiting) {
        if (onStopWaiting != null) {
            delay(STOP_WAITING_DELAY)
            canStopWaiting = true
        }
    }
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            if (canStopWaiting && onStopWaiting != null) {
                TextButton(onClick = onStopWaiting) {
                    Text(text = stringResource(Res.string.loading_dialog_stop_waiting))
                }
            }
        },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(text = message)
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )
}
