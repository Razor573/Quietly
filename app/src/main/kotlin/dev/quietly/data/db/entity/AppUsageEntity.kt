package dev.quietly.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row = one app’s usage for one calendar day.
 * [dateEpochDay] = LocalDate.toEpochDay() — compact, timezone-safe.
 */
@Entity(tableName = "app_usage", primaryKeys = ["packageName", "dateEpochDay"])
data class AppUsageEntity(
    val packageName   : String,
    val appLabel      : String,
    val dateEpochDay  : Long,
    val totalTimeMs   : Long,   // foreground ms that day
    val launchCount   : Int,
    val lastUsedMs    : Long    // epoch ms of last event
)
