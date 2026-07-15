package dev.quietly.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.ui.components.BottomNavBar
import dev.quietly.util.toHoursMinutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    navController: NavController,
    vm: InsightsViewModel = hiltViewModel()
) {
    val s by vm.uiState.collectAsState()

    Scaffold(
        topBar    = { TopAppBar(title = { Text("Insights") }) },
        bottomBar = { BottomNavBar(navController) }
    ) { pad ->
        if (s.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 16.dp,
                start = 16.dp, end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Category donut-style breakdown ────────────────────────────
            if (s.categoryBreakdown.isNotEmpty()) {
                item {
                    CategoryBreakdownCard(breakdown = s.categoryBreakdown)
                }
            }

            // ── Suggest remove ────────────────────────────────────────────
            if (s.suggestedRemove.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Consider removing",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "These apps average more than 2 h/day over the last 30 days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(8.dp))
                            s.suggestedRemove.forEach { app ->
                                Text(
                                    "• ${app.appLabel} — ${app.avgDailyMs.toHoursMinutes()} avg/day",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // ── Top 10 apps (30-day avg) ──────────────────────────────────
            item {
                Text("Top apps — 30-day average",
                    style = MaterialTheme.typography.titleSmall)
            }
            val maxMs = s.topApps.maxOfOrNull { it.avgDailyMs }?.coerceAtLeast(1L) ?: 1L
            items(s.topApps) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(app.category, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(app.avgDailyMs.toHoursMinutes(),
                            style = MaterialTheme.typography.labelLarge)
                        val frac = (app.avgDailyMs.toFloat() / maxMs).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .width(80.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(frac)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
                Divider(modifier = Modifier.padding(top = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(breakdown: Map<String, Long>) {
    val total = breakdown.values.sum().coerceAtLeast(1L)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.outline
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("By category", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                breakdown.entries.sortedByDescending { it.value }
                    .forEachIndexed { i, (_, ms) ->
                        val frac = ms.toFloat() / total
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(frac.coerceAtLeast(0.01f))
                                .background(colors.getOrElse(i) { colors.last() })
                        )
                    }
            }
            Spacer(Modifier.height(8.dp))
            breakdown.entries.sortedByDescending { it.value }.forEachIndexed { i, (cat, ms) ->
                val pct = (ms * 100f / total).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp))
                        .background(colors.getOrElse(i) { colors.last() }))
                    Spacer(Modifier.width(6.dp))
                    Text("$cat ($pct%)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}
