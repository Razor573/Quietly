package dev.quietly.util

fun Long.toHoursMinutes(): String {
    val h = this / 3_600_000
    val m = (this % 3_600_000) / 60_000
    return when {
        h > 0  -> "${h}h ${m}m"
        m > 0  -> "${m}m"
        else   -> "<1m"
    }
}

fun Long.toHoursMinutesLong(): String {
    val h = this / 3_600_000
    val m = (this % 3_600_000) / 60_000
    val s = (this % 60_000) / 1_000
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
}
