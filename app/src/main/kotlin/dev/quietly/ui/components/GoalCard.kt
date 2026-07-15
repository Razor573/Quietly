package dev.quietly.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.util.toHoursMinutes

@Composable
fun GoalCard(
    goal:           GoalEntity,
    onDelete:       () -> Unit,
    onToggleRemind: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(goal.appLabel.ifBlank { goal.packageName },
                    style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Limit: ${goal.dailyLimitMs.toHoursMinutes()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onToggleRemind) {
                Icon(
                    imageVector = if (goal.reminderEnabled)
                        Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                    contentDescription = "Toggle reminder",
                    tint = if (goal.reminderEnabled)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, "Delete",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
