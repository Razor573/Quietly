package dev.quietly.ui.apps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.ui.components.AppUsageRow
import dev.quietly.ui.components.BottomNavBar
import dev.quietly.ui.navigation.Screen
import dev.quietly.util.toHoursMinutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    navController: NavController,
    vm: AppsViewModel = hiltViewModel()
) {
    val s by vm.uiState.collectAsState()
    var sortExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Apps") },
                actions = {
                    Box {
                        IconButton(onClick = { sortExpanded = true }) {
                            Icon(Icons.Outlined.Sort, "Sort")
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Most time") },
                                onClick = { vm.setSort(AppSort.TIME_DESC); sortExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Least time") },
                                onClick = { vm.setSort(AppSort.TIME_ASC); sortExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Most opens") },
                                onClick = { vm.setSort(AppSort.LAUNCHES); sortExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("A – Z") },
                                onClick = { vm.setSort(AppSort.NAME); sortExpanded = false }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = { BottomNavBar(navController) }
    ) { padding ->
        if (s.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top    = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start = 16.dp, end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Summary header ────────────────────────────────────────
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatChip(label = "Apps",  value = "${s.totalApps}")
                            StatChip(label = "Opens", value = "${s.totalOpens}")
                            StatChip(label = "Time",  value = s.totalTimeMs.toHoursMinutes())
                        }
                    }
                }
                // ── Search bar ────────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value         = s.query,
                        onValueChange = vm::setQuery,
                        placeholder   = { Text("Search apps…") },
                        leadingIcon   = { Icon(Icons.Outlined.Search, null) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                }
                // ── App list ──────────────────────────────────────────────
                if (s.filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val emptyMessage = if (s.apps.isEmpty()) {
                                "No applications recorded today. Ensure Usage Access permission is enabled."
                            } else {
                                "No applications match your search."
                            }
                            Text(
                                text = emptyMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(s.filtered, key = { it.packageName }) { usage ->
                        AppUsageRow(
                            usage   = usage,
                            onClick = {
                                navController.navigate(Screen.AppDetail.withArg(usage.packageName))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}
