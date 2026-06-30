package se.atte.bragwise.ui.components

import kotlin.time.Instant

@JsFun("(ms) => new Date(ms).toLocaleString()")
private external fun jsFormatDate(ms: Double): String

@JsFun("(ms) => -(new Date(ms).getTimezoneOffset()) * 60000")
private external fun jsTzOffsetMs(ms: Double): Double

@JsFun("(ms) => new Date(ms).toLocaleDateString()")
private external fun jsFormatDateOnly(ms: Double): String

actual fun formatDeadline(instant: Instant): String =
    jsFormatDate(instant.toEpochMilliseconds().toDouble())

actual fun timezoneOffsetMs(epochMs: Long): Long =
    jsTzOffsetMs(epochMs.toDouble()).toLong()

actual fun formatDay(instant: Instant): String =
    jsFormatDateOnly(instant.toEpochMilliseconds().toDouble())
