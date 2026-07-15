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
import dev.quietly.util.toHoursMinutesDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    navController: NavController,
    vm: AppsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("All Apps") },
                    actions = {
                        // Sort menu
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Outlined.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Time ↓") },
                                    onClick = { vm.setSortOrder(AppSortOrder.TIME_DESC); showSortMenu = false },
                                    leadingIcon = {
                                        if (state.sortOrder == AppSortOrder.TIME_DESC)
                                            Icon(Icons.Outlined.Check, null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Time ↑") },
                                    onClick = { vm.setSortOrder(AppSortOrder.TIME_ASC); showSortMenu = false },
                                    leadingIcon = {
                                        if (state.sortOrder == AppSortOrder.TIME_ASC)
                                            Icon(Icons.Outlined.Check, null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Most Opens") },
                                    onClick = { vm.setSortOrder(AppSortOrder.LAUNCHES_DESC); showSortMenu = false },
                                    leadingIcon = {
                                        if (state.sortOrder == AppSortOrder.LAUNCHES_DESC)
                                            Icon(Icons.Outlined.Check, null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name A–Z") },
                                    onClick = { vm.setSortOrder(AppSortOrder.NAME_ASC); showSortMenu = false },
                                    leadingIcon = {
                                        if (state.sortOrder == AppSortOrder.NAME_ASC)
                                            Icon(Icons.Outlined.Check, null)
                                    }
                                )
                            }
                        }
                    }
                )
                // Search bar
                OutlinedTextField(
                    value         = state.searchQuery,
                    onValueChange = { vm.setSearchQuery(it) },
                    placeholder   = { Text("Search apps…") },
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon  = {
                        if (state.searchQuery.isNotBlank())
                            IconButton(onClick = { vm.setSearchQuery("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                            }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        },
        bottomBar = { BottomNavBar(navController) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text  = if (state.searchQuery.isBlank()) "No usage data yet\nOpen some apps and come back"
                            else "No apps match \"${state.searchQuery}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top    = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                    start  = 16.dp,
                    end    = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Summary header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier            = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text  = "${state.apps.size}",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text  = "Apps used",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text  = state.apps.sumOf { it.launchCount }.toString(),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text  = "Total opens",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text  = state.apps.sumOf { it.totalTimeMs }.toHoursMinutesDisplay(),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text  = "Total time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // App rows with launch count shown
                items(state.apps, key = { it.packageName }) { app ->
                    AppUsageRow(
                        usage         = app,
                        goal          = null,
                        showLaunches  = true
                    )
                }
            }
        }
    }
}
