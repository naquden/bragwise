package se.atte.bragwise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Lucide
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.deadline_cancel
import bragwise.shared.generated.resources.deadline_done
import bragwise.shared.generated.resources.deadline_hint
import bragwise.shared.generated.resources.deadline_locks_prefix
import bragwise.shared.generated.resources.deadline_next
import bragwise.shared.generated.resources.deadline_pick_time_title
import se.atte.bragwise.theme.ThemePreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlinePickerField(
    locksAt: Instant,
    onLocksAtChange: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf(0L) }

    val todayUtcMidnightMs = run {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val msPerDay = 86_400_000L
        (nowMs / msPerDay) * msPerDay
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = locksAt.toEpochMilliseconds(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= todayUtcMidnightMs
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                AppTextButton(onClick = {
                    val selected = dateState.selectedDateMillis ?: return@AppTextButton
                    pendingDateMillis = selected
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(stringResource(Res.string.deadline_next)) }
            },
            dismissButton = {
                AppTextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.deadline_cancel)) }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val currentHour = ((locksAt.toEpochMilliseconds() % 86_400_000L) / 3_600_000L).toInt()
        val currentMinute = ((locksAt.toEpochMilliseconds() % 3_600_000L) / 60_000L).toInt()
        val timeState = rememberTimePickerState(
            initialHour = currentHour,
            initialMinute = currentMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(Res.string.deadline_pick_time_title)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                AppTextButton(onClick = {
                    val offsetMs = (timeState.hour * 3600L + timeState.minute * 60L) * 1000L
                    onLocksAtChange(Instant.fromEpochMilliseconds(pendingDateMillis + offsetMs))
                    showTimePicker = false
                }) { Text(stringResource(Res.string.deadline_done)) }
            },
            dismissButton = {
                AppTextButton(onClick = { showTimePicker = false }) { Text(stringResource(Res.string.deadline_cancel)) }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true }
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.deadline_locks_prefix, formatDeadline(locksAt)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector = Lucide.CalendarClock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(Res.string.deadline_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// region Previews

@Preview
@Composable
private fun DeadlinePickerField_Preview() {
    ThemePreview {
        SectionCard(title = "Deadline") {
            DeadlinePickerField(
                locksAt = Instant.fromEpochMilliseconds(1_750_096_200_000L),
                onLocksAtChange = {},
            )
        }
    }
}

// endregion
