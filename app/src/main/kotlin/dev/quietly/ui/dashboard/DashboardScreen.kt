package dev.quietly.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.data.db.dao.DayTotal
import dev.quietly.ui.components.AppUsageRow
import dev.quietly.ui.components.BottomNavBar
import dev.quietly.ui.navigation.Screen
import dev.quietly.util.toHoursMinutes
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    vm: DashboardViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = { BottomNavBar(navController) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top    = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start  = 16.dp, end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Total time hero card ──────────────────────────────────
                item {
                    TotalTimeHeroCard(totalMs = uiState.totalTodayMs)
                }

                // ── 7-day bar chart ───────────────────────────────────────
                if (uiState.weeklyTotals.isNotEmpty()) {
                    item {
                        WeeklyBarChart(totals = uiState.weeklyTotals)
                    }
                }

                // ── App rows (tappable → detail) ──────────────────────────
                items(uiState.appUsages, key = { it.packageName }) { usage ->
                    AppUsageRow(
                        usage    = usage,
                        goal     = uiState.goals[usage.packageName],
                        onClick  = {
                            navController.navigate(Screen.AppDetail.withArg(usage.packageName))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalTimeHeroCard(totalMs: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Screen time today", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(totalMs.toHoursMinutes(), style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun WeeklyBarChart(totals: List<DayTotal>, modifier: Modifier = Modifier) {
    val barColor   = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val maxMs      = totals.maxOfOrNull { it.totalTimeMs } ?: 1L
    val today      = LocalDate.now().toEpochDay().toInt()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Fill missing days with 0
                val dayMap = totals.associateBy { it.dateEpochDay }
                (0..6).forEach { offset ->
                    val day  = today - 6 + offset
                    val ms   = dayMap[day]?.totalTimeMs ?: 0L
                    val frac = (ms.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                    val date = LocalDate.ofEpochDay(day.toLong())
                    val lbl  = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .take(2)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement  = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(frac.coerceAtLeast(0.02f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        if (day == today) barColor
                                        else barColor.copy(alpha = 0.45f)
                                    )
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(lbl, style = MaterialTheme.typography.labelSmall,
                            color = labelColor)
                    }
                }
            }
        }
    }
}
