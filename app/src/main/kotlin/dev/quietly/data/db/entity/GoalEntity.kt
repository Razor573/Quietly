package dev.quietly.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val packageName: String,
    val appLabel:        String  = "",
    val dailyLimitMs:    Long,            // user-set daily limit in ms
    val reminderEnabled: Boolean = true   // NEW: per-goal reminder toggle
)
