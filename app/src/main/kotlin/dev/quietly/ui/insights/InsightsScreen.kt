package dev.quietly.ui.insights

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.ui.components.BottomNavBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    navController: NavController,
    vm: InsightsViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsState()

    Scaffold(
        topBar    = { TopAppBar(title = { Text("Insights") }) },
        bottomBar = { BottomNavBar(navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Suggested to remove card
            if (uiState.suggestedToRemove.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Consider removing", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        uiState.suggestedToRemove.forEach { app ->
                            Text(
                                text  = "• ${app.appLabel} — ${app.totalTimeMs.toHoursMinutes()} avg/day",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Top 5 — last 7 days", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    uiState.topApps.forEachIndexed { i, app ->
                        Text(
                            text  = "${i + 1}. ${app.appLabel} — ${app.totalTimeMs.toHoursMinutes()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

fun Long.toHoursMinutes(): String {
    val h = this / 3_600_000
    val m = (this % 3_600_000) / 60_000
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
