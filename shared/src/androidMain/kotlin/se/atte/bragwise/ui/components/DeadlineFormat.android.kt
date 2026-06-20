package se.atte.bragwise.ui.components

import java.util.Date
import java.text.DateFormat
import kotlin.time.Instant

actual fun formatDeadline(instant: Instant): String {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return fmt.format(Date(instant.toEpochMilliseconds()))
}
