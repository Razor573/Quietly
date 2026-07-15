package dev.quietly.ui.appdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.ui.dashboard.WeeklyBarChart
import dev.quietly.util.toHoursMinutes
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    pkg: String,
    nav: NavController,
    vm:  AppDetailViewModel = hiltViewModel()
) {
    val s by vm.uiState.collectAsState()
    LaunchedEffect(pkg) { vm.load(pkg) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.appLabel.ifBlank { pkg }) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (s.playStoreUrl.isNotBlank()) {
                        val ctx = LocalContext.current
                        IconButton(onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(s.playStoreUrl)
                            )
                            ctx.startActivity(intent)
                        }) {
                            Icon(Icons.Outlined.OpenInNew, "Play Store")
                        }
                    }
                }
            )
        }
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
            // ── Stats summary ─────────────────────────────────────────────
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Today",    s.todayMs.toHoursMinutes())
                        StatItem("7-day avg", s.avgDailyMs.toHoursMinutes())
                        StatItem("Category", s.category)
                    }
                }
            }

            // ── 7-day bar chart ───────────────────────────────────────────
            if (s.weeklyTotals.isNotEmpty()) {
                item { WeeklyBarChart(totals = s.weeklyTotals) }
            }

            // ── 30-day history list ───────────────────────────────────────
            item {
                Text("Usage history", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp))
            }
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            itemsIndexed(s.history) { _, row ->
                val date = LocalDate.ofEpochDay(row.dateEpochDay.toLong()).format(fmt)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(date, modifier = Modifier.width(64.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    val maxMs = s.history.maxOfOrNull { it.totalTimeMs }?.coerceAtLeast(1L) ?: 1L
                    val frac  = (row.totalTimeMs.toFloat() / maxMs).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(frac)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(row.totalTimeMs.toHoursMinutes(),
                        modifier = Modifier.width(52.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}
