package dev.quietly.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity

@Composable
fun AppUsageRow(
    usage: AppUsageEntity,
    goal : GoalEntity? = null
) {
    val overLimit = goal != null && usage.totalTimeMs > goal.dailyLimitMs

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (overLimit)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment   = Alignment.CenterVertically
            ) {
                Text(
                    text  = usage.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text  = usage.totalTimeMs.toHoursMinutesDisplay(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (goal != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (usage.totalTimeMs.toFloat() / goal.dailyLimitMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color    = if (overLimit)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Goal: ${goal.dailyLimitMs.toHoursMinutesDisplay()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
