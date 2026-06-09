package se.atte.bragwise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        Row {
            Text(
                text = stringResource(Res.string.deadline_locks_prefix, formatDeadline(locksAt)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
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

private fun formatDeadline(instant: Instant): String {
    val ms = instant.toEpochMilliseconds()
    val totalSec = ms / 1000L
    val days = totalSec / 86_400L
    // Days since 1970-01-01 (UTC). Use a minimal algorithm to avoid kotlinx-datetime.
    val year: Int
    val month: Int
    val day: Int
    run {
        var n = days.toInt()
        var y = 1970
        while (true) {
            val diy = if (isLeap(y)) 366 else 365
            if (n < diy) break
            n -= diy
            y++
        }
        year = y
        val leapYear = isLeap(year)
        val monthDays = intArrayOf(31, if (leapYear) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var m = 0
        while (m < 12 && n >= monthDays[m]) { n -= monthDays[m]; m++ }
        month = m + 1
        day = n + 1
    }
    val remSec = totalSec % 86_400L
    val hour = (remSec / 3600L).toInt()
    val minute = ((remSec % 3600L) / 60L).toInt()
    val monthName = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")[month - 1]
    return "$day $monthName $year, ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} UTC"
}

private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)
