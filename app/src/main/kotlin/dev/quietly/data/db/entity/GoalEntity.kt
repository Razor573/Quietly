package dev.quietly.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id            : Long   = 0,
    val packageName   : String,
    val dailyLimitMs  : Long,          // user’s target in ms
    val reminderEnabled: Boolean = true,
    val createdAt     : Long = System.currentTimeMillis()
)
