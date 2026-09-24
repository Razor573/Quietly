package dev.quietly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.util.toHoursMinutes

@Composable
fun AppUsageRow(
    usage:            AppUsageEntity,
    goal:             GoalEntity? = null,
    dayTotalMs:       Long = 0L,
    isAnchored:       Boolean = false,
    onCalibrateClick: (() -> Unit)? = null,
    onClick:          (() -> Unit)? = null
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

    val dayPercent = if (dayTotalMs > 0L) {
        ((usage.totalTimeMs.toFloat() / dayTotalMs.toFloat()) * 100).toInt()
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag("app_row_${usage.packageName}"),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            usage.appLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (isAnchored) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    "🎯 Anchored",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (dayPercent > 0) "${usage.launchCount} opens · ${usage.category} · $dayPercent% of day"
                        else "${usage.launchCount} opens · ${usage.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        usage.totalTimeMs.toHoursMinutes(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (onCalibrateClick != null) {
                        IconButton(
                            onClick = onCalibrateClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("calibrate_${usage.packageName}")
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Calibrate ${usage.appLabel}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (progress != null) {
                Spacer(Modifier.height(8.dp))
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

