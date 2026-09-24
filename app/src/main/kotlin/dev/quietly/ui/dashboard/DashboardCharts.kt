package dev.quietly.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.util.toHoursMinutes

/**
 * Visual ranked distribution chart of top applications for the day.
 * Displays proportional progress bars, category styling, percentage of total time,
 * and immediate calibration controls.
 */
@Composable
fun TopAppsDistributionCard(
    apps: List<AppUsageEntity>,
    dayTotalMs: Long,
    calibratedPkgs: Set<String> = emptySet(),
    onAppClick: (String) -> Unit,
    onCalibrateApp: (AppUsageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (apps.isEmpty() || dayTotalMs <= 0L) return

    val topApps = remember(apps) {
        apps.filter { it.totalTimeMs > 0L }
            .sortedByDescending { it.totalTimeMs }
            .take(5)
    }

    if (topApps.isEmpty()) return

    val maxAppMs = topApps.first().totalTimeMs.coerceAtLeast(1L)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_apps_chart_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Top Apps Distribution",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "${topApps.size} apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                topApps.forEachIndexed { index, app ->
                    val fraction = (app.totalTimeMs.toFloat() / maxAppMs.toFloat()).coerceIn(0.04f, 1f)
                    val dayPercent = ((app.totalTimeMs.toFloat() / dayTotalMs.toFloat()) * 100).toInt()
                    val categoryColor = getChartCategoryColor(app.category)
                    val isAnchored = app.packageName in calibratedPkgs

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAppClick(app.packageName) }
                            .padding(vertical = 2.dp)
                            .testTag("top_app_item_$index")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor)
                                )
                                Text(
                                    app.appLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                if (isAnchored) {
                                    Text(
                                        "🎯",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "$dayPercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    app.totalTimeMs.toHoursMinutes(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { onCalibrateApp(app) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "Calibrate ${app.appLabel}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Ranked horizontal proportion bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(categoryColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive category breakdown chart showing time proportions by activity type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDistributionCard(
    apps: List<AppUsageEntity>,
    dayTotalMs: Long,
    modifier: Modifier = Modifier
) {
    if (apps.isEmpty() || dayTotalMs <= 0L) return

    val categoryTotals = remember(apps) {
        apps.filter { it.totalTimeMs > 0L }
            .groupBy { it.category.ifBlank { "Other" } }
            .mapValues { entry -> entry.value.sumOf { it.totalTimeMs } }
            .toList()
            .sortedByDescending { it.second }
    }

    if (categoryTotals.isEmpty()) return

    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_chart_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Category Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "${categoryTotals.size} categories",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            // Multi-segment category bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                categoryTotals.forEach { (cat, catMs) ->
                    val weight = (catMs.toFloat() / dayTotalMs.toFloat()).coerceAtLeast(0.015f)
                    val isSelected = selectedCategory == null || selectedCategory == cat
                    val color = getChartCategoryColor(cat)

                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(if (isSelected) color else color.copy(alpha = 0.25f))
                            .clickable {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Category detail chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categoryTotals.forEach { (cat, catMs) ->
                    val percent = ((catMs.toFloat() / dayTotalMs.toFloat()) * 100).toInt()
                    val isSelected = selectedCategory == cat
                    val color = getChartCategoryColor(cat)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, color) else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Text(
                                cat,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                "$percent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                catMs.toHoursMinutes(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getChartCategoryColor(category: String): Color = when (category) {
    "Social" -> Color(0xFFE91E63)
    "Video", "Entertainment" -> Color(0xFF9C27B0)
    "Productivity" -> Color(0xFF2196F3)
    "Games" -> Color(0xFFFF9800)
    "News" -> Color(0xFF009688)
    "Maps" -> Color(0xFF4CAF50)
    "Photos" -> Color(0xFF3F51B5)
    "Music" -> Color(0xFFE040FB)
    "Communication" -> Color(0xFF00BCD4)
    else -> Color(0xFF78909C)
}
