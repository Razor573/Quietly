package dev.quietly.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores per-app user overrides (Essential, Focus Drain, Ignore, Exclude).
 * One row per package — upserted on every user change.
 */
@Entity(tableName = "app_overrides")
data class AppOverrideEntity(
    @PrimaryKey val packageName: String,
    /** One of: ESSENTIAL, FOCUS_DRAIN, IGNORE, EXCLUDE_SUGGESTIONS */
    val overrideType: String
)
