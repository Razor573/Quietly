package dev.quietly.data.db

data class DailyUsageRecord(
    val epochDay: Long,
    val packageName: String,
    val usageMinutes: Int
)
