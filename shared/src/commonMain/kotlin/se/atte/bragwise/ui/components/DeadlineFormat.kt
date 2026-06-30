package se.atte.bragwise.ui.components

import kotlin.time.Instant

expect fun formatDeadline(instant: Instant): String

expect fun timezoneOffsetMs(epochMs: Long): Long

expect fun formatDay(instant: Instant): String

fun localEpochDay(instant: Instant): Long {
    val epochMs = instant.toEpochMilliseconds()
    val adjustedMs = epochMs + timezoneOffsetMs(epochMs)
    return if (adjustedMs >= 0) adjustedMs / 86_400_000L
    else (adjustedMs - 86_399_999L) / 86_400_000L
}
