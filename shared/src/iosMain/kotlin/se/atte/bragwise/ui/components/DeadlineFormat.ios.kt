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

actual fun timezoneOffsetMs(epochMs: Long): Long {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)
    return NSTimeZone.localTimeZone.secondsFromGMTForDate(date) * 1000L
}

actual fun formatDay(instant: Instant): String {
    val date = NSDate.dateWithTimeIntervalSince1970(instant.toEpochMilliseconds() / 1000.0)
    val fmt = NSDateFormatter()
    fmt.dateStyle = NSDateFormatterMediumStyle
    fmt.timeStyle = NSDateFormatterNoneStyle
    fmt.locale = NSLocale.currentLocale
    return fmt.stringFromDate(date)
}
