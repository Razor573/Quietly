package dev.quietly.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.ui.components.BottomNavBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar    = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = { BottomNavBar(navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Data retention", style = MaterialTheme.typography.titleMedium)
            Text(
                text  = "Keep ${state.retentionDays} days of history",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Slider(
                value         = state.retentionDays.toFloat(),
                onValueChange = { vm.setRetentionDays(it.toInt()) },
                valueRange    = 7f..365f,
                steps         = 0,
                modifier      = Modifier.fillMaxWidth()
            )
            HorizontalDivider()
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                text  = "Quietly v1.0 — all data is stored locally and never transmitted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
