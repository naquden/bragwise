package se.atte.bragwise.ui.components

import java.util.Date
import java.text.DateFormat
import kotlin.time.Instant

actual fun formatDeadline(instant: Instant): String {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return fmt.format(Date(instant.toEpochMilliseconds()))
}

actual fun timezoneOffsetMs(epochMs: Long): Long =
    java.util.TimeZone.getDefault().getOffset(epochMs).toLong()

actual fun formatDay(instant: Instant): String {
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
    return fmt.format(Date(instant.toEpochMilliseconds()))
}
