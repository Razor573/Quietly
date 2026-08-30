package dev.quietly.data.db

data class AppEntity(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long
)
