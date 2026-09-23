package dev.quietly.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.util.toHoursMinutes

@Composable
fun GoalCard(
    goal:           GoalEntity,
    todayUsedMs:    Long = 0L,
    onDelete:       () -> Unit,
    onToggleRemind: () -> Unit = {},
    onClick:        (() -> Unit)? = null
) {
    val progress = (todayUsedMs.toFloat() / goal.dailyLimitMs.toFloat().coerceAtLeast(1f))
        .coerceIn(0f, 1f)
    val isExceeded = todayUsedMs >= goal.dailyLimitMs

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        goal.appLabel.ifBlank { goal.packageName },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Used: ${todayUsedMs.toHoursMinutes()} / ${goal.dailyLimitMs.toHoursMinutes()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete goal",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.small),
                color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = if (isExceeded) {
                    "Limit reached! Exceeded by ${(todayUsedMs - goal.dailyLimitMs).toHoursMinutes()}"
                } else {
                    "${(goal.dailyLimitMs - todayUsedMs).toHoursMinutes()} remaining today"
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
