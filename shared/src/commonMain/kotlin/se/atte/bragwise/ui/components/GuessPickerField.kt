package se.atte.bragwise.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import bragwise.shared.generated.resources.Res
import bragwise.shared.generated.resources.cc_guess_pick_day_title
import bragwise.shared.generated.resources.cc_guess_pick_number_title
import bragwise.shared.generated.resources.cc_guess_pick_time_title
import bragwise.shared.generated.resources.deadline_cancel
import bragwise.shared.generated.resources.deadline_done
import se.atte.bragwise.domain.GuessGranularity
import se.atte.bragwise.ui.formatGuessValue

/**
 * Tappable field that opens a TimePicker (TIME), DatePicker (DAY), or a numeric
 * input dialog (NUMBER) and emits a Long value.
 * TIME:   minutes since local midnight (0..1439).
 * DAY:    UTC epoch-day (days since 1970-01-01).
 * NUMBER: any integer.
 * Null [value] means no guess picked yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuessPickerField(
    granularity: GuessGranularity,
    value: Long?,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .clickable { showPicker = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (value != null) formatGuessValue(value, granularity) else when (granularity) {
                GuessGranularity.TIME -> stringResource(Res.string.cc_guess_pick_time_title)
                GuessGranularity.DAY -> stringResource(Res.string.cc_guess_pick_day_title)
                GuessGranularity.NUMBER -> stringResource(Res.string.cc_guess_pick_number_title)
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showPicker) {
        when (granularity) {
            GuessGranularity.TIME -> TimeGuessPicker(
                initialValue = value,
                onConfirm = { minutes ->
                    onValueChange(minutes)
                    showPicker = false
                },
                onDismiss = { showPicker = false },
            )
            GuessGranularity.DAY -> DayGuessPicker(
                initialValue = value,
                onConfirm = { epochDay ->
                    onValueChange(epochDay)
                    showPicker = false
                },
                onDismiss = { showPicker = false },
            )
            GuessGranularity.NUMBER -> NumberGuessPicker(
                initialValue = value,
                onConfirm = { number ->
                    onValueChange(number)
                    showPicker = false
                },
                onDismiss = { showPicker = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeGuessPicker(
    initialValue: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHour = initialValue?.div(60)?.toInt() ?: 12
    val initialMinute = initialValue?.rem(60)?.toInt() ?: 0
    val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.cc_guess_pick_time_title)) },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            AppTextButton(onClick = {
                onConfirm(timeState.hour * 60L + timeState.minute)
            }) { Text(stringResource(Res.string.deadline_done)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.deadline_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayGuessPicker(
    initialValue: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker state uses UTC-midnight millis; convert epoch-day to millis for initial value.
    val initialMillis = initialValue?.let { it * 86_400_000L }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppTextButton(onClick = {
                val selected = dateState.selectedDateMillis ?: return@AppTextButton
                onConfirm(selected / 86_400_000L)
            }) { Text(stringResource(Res.string.deadline_done)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.deadline_cancel)) }
        },
    ) {
        DatePicker(state = dateState)
    }
}

@Composable
private fun NumberGuessPicker(
    initialValue: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue?.toString() ?: "") }
    val parsed = text.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.cc_guess_pick_number_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            AppTextButton(
                onClick = { parsed?.let { onConfirm(it) } },
                enabled = parsed != null,
            ) { Text(stringResource(Res.string.deadline_done)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text(stringResource(Res.string.deadline_cancel)) }
        },
    )
}
