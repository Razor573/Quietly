package dev.quietly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quietly.domain.intelligence.HabitIntelligenceEngine
import dev.quietly.domain.intelligence.HabitIntelligenceEngine.IntelligenceReport
import dev.quietly.domain.intelligence.HabitIntelligenceEngine.PacingStatus
import dev.quietly.util.toHoursMinutes
import kotlin.math.roundToInt

@Composable
fun SmartForecastCard(
    report: IntelligenceReport,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forecast = report.forecast
    val persona = report.persona
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Top Header Pill & Persona Tag ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = primaryColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "🧠 ON-DEVICE FORECAST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${persona.type.icon} ${persona.type.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // ── Projected Total & Velocity Comparison ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "Projected Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "~${forecast.projectedEndDayMs.toHoursMinutes()}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "±${(forecast.confidenceDeltaMs / 60_000).coerceAtLeast(5)}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Pacing Badge
                val paceColor = Color(forecast.pacingStatus.colorHex)
                Surface(
                    color = paceColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val paceText = when (forecast.pacingStatus) {
                        PacingStatus.CALM -> "🟢 Calm Pace"
                        PacingStatus.STEADY -> "🔵 Steady"
                        PacingStatus.ELEVATED -> "🟠 +${((forecast.pacingFactor - 1f) * 100).roundToInt()}% Pace"
                        PacingStatus.SURGE -> "🔴 Surge Velocity"
                    }
                    Text(
                        paceText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = paceColor
                    )
                }
            }

            // ── Dual Track Pacing Progress Bar ────────────────────────────
            val maxScaleMs = (forecast.projectedEndDayMs * 1.25f).toLong().coerceAtLeast(3_600_000L)
            val currentFraction = (forecast.currentMs.toFloat() / maxScaleMs.toFloat()).coerceIn(0.01f, 1f)
            val projectedFraction = (forecast.projectedEndDayMs.toFloat() / maxScaleMs.toFloat()).coerceIn(currentFraction, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(surfaceColor)
                ) {
                    // Projected track (shaded band)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(projectedFraction)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        primaryColor.copy(alpha = 0.4f),
                                        tertiaryColor.copy(alpha = 0.5f)
                                    )
                                )
                            )
                    )
                    // Current actual elapsed (solid)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(currentFraction)
                            .fillMaxHeight()
                            .background(primaryColor)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Elapsed: ${forecast.currentMs.toHoursMinutes()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Baseline: ${forecast.baselineAverageMs.toHoursMinutes()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Imminent Goal Breach Alert (if any) ────────────────────────
            val urgentBreach = report.goalPredictions.firstOrNull { it.willBreach }
            if (urgentBreach != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⚠️", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${urgentBreach.appLabel}: breach predicted at ${urgentBreach.predictedBreachTime}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── Footer link to Insights ───────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "View Behavioral Intelligence",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = primaryColor
                )
            }
        }
    }
}
