package dev.quietly.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One row = one app's usage for one calendar day.
 * Composite PK: (packageName, dateEpochDay)
 */
@Entity(
    tableName = "app_usage",
    primaryKeys = ["packageName", "dateEpochDay"],
    indices = [Index(value = ["dateEpochDay"])]
)
data class AppUsageEntity(
    val packageName:     String,
    val dateEpochDay:    Int,          // LocalDate.toEpochDay()
    val appLabel:        String,
    val totalTimeMs:     Long,
    val launchCount:     Int    = 0,
    val category:        String = "Other",
    /** Epoch day of the most recent session seen for this package (updated on each sync). */
    val lastSeenEpochDay: Int   = dateEpochDay
)
