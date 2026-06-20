package se.atte.bragwise.ui.components

import platform.Foundation.*
import kotlin.time.Instant

actual fun formatDeadline(instant: Instant): String {
    val date = NSDate.dateWithTimeIntervalSince1970(instant.toEpochMilliseconds() / 1000.0)
    val fmt = NSDateFormatter()
    fmt.dateStyle = NSDateFormatterMediumStyle
    fmt.timeStyle = NSDateFormatterShortStyle
    fmt.locale = NSLocale.currentLocale
    return fmt.stringFromDate(date)
}
