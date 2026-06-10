package se.atte.bragwise.ui.components

import kotlin.time.Instant

fun formatDeadline(instant: Instant): String {
    val ms = instant.toEpochMilliseconds()
    val totalSec = ms / 1000L
    val days = totalSec / 86_400L
    val year: Int
    val month: Int
    val day: Int
    run {
        var n = days.toInt()
        var y = 1970
        while (true) {
            val diy = if (isLeapYear(y)) 366 else 365
            if (n < diy) break
            n -= diy
            y++
        }
        year = y
        val leapYear = isLeapYear(year)
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

private fun isLeapYear(y: Int) = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)
