package se.atte.bragwise.ui.components

import kotlin.time.Instant

expect fun formatDeadline(instant: Instant): String

expect fun timezoneOffsetMs(epochMs: Long): Long
