package dev.quietly.util

/**
 * Converts milliseconds to a human-readable "Xh Ym" or "Ym" or "<1m" string.
 * Moved here so it can be imported from both components and screens without
 * relying on an extension defined only in AppUsageRow.
 */
fun Long.toHoursMinutesDisplay(): String {
    val totalMinutes = this / 60_000
    val hours   = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes == 0L -> "< 1m"
        hours == 0L        -> "${minutes}m"
        minutes == 0L      -> "${hours}h"
        else               -> "${hours}h ${minutes}m"
    }
}
