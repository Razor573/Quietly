package dev.quietly.ui.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.ui.components.BottomNavBar
import dev.quietly.ui.components.GoalCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    navController: NavController,
    vm: GoalsViewModel = hiltViewModel()
) {
    val goals by vm.goals.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar    = { TopAppBar(title = { Text("Goals") }) },
        bottomBar = { BottomNavBar(navController) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Outlined.Add, "Add goal")
            }
        }
    ) { pad ->
        if (goals.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No goals yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to set a daily limit for any app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = pad.calculateTopPadding() + 8.dp,
                    bottom = pad.calculateBottomPadding() + 72.dp,
                    start = 16.dp, end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(goals, key = { it.packageName }) { goal ->
                    GoalCard(
                        goal           = goal,
                        onDelete       = { vm.delete(goal) },
                        onToggleRemind = { vm.toggleReminder(goal) }
                    )
                }
            }
        }
        if (showDialog) {
            AddGoalDialog(
                onDismiss = { showDialog = false },
                onConfirm = { pkg, label, ms, remind ->
                    vm.addGoal(pkg, label, ms, remind)
                    showDialog = false
                }
            )
        }
    }
}
