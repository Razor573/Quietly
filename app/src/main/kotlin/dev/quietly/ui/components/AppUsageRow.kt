package dev.quietly.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.util.toHoursMinutes

@Composable
fun AppUsageRow(
    usage:   AppUsageEntity,
    goal:    GoalEntity? = null,
    onClick: (() -> Unit)? = null
) {
    val progress = goal?.let {
        (usage.totalTimeMs.toFloat() / it.dailyLimitMs.toFloat()).coerceIn(0f, 1f)
    }
    val progressColor = when {
        progress == null -> MaterialTheme.colorScheme.primary
        progress >= 1f   -> MaterialTheme.colorScheme.error
        progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else             -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(usage.appLabel, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${usage.launchCount} opens · ${usage.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Text(usage.totalTimeMs.toHoursMinutes(),
                    style = MaterialTheme.typography.labelLarge)
            }
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress        = { progress },
                    modifier        = Modifier.fillMaxWidth().height(4.dp),
                    color           = progressColor,
                    trackColor      = MaterialTheme.colorScheme.surfaceVariant
                )
                if (progress >= 1f) {
                    Text(
                        "Goal exceeded!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
