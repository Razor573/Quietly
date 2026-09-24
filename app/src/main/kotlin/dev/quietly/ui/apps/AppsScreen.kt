package dev.quietly.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.data.db.entity.GoalEntity
import dev.quietly.ui.components.BottomNavBar
import dev.quietly.ui.goals.AddGoalDialog
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
    var goalTargetApp by remember { mutableStateOf<AppUsageEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Explorer & Audit") },
                actions = {
                    Box {
                        IconButton(onClick = { sortExpanded = true }) {
                            Icon(Icons.Outlined.FilterList, "Sort")
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Most time spent") },
                                onClick = { vm.setSort(AppSort.TIME_DESC); sortExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Least time spent") },
                                onClick = { vm.setSort(AppSort.TIME_ASC); sortExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Most app opens") },
                                onClick = { vm.setSort(AppSort.LAUNCHES); sortExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Alphabetical (A–Z)") },
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
                    bottom = padding.calculateBottomPadding() + 16.dp,
                    start  = 16.dp, end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Hero Audit Metrics Card ───────────────────────────────
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Daily App Ecosystem",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        s.totalTimeMs.toHoursMinutes(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "${s.totalApps} active apps today",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${s.totalOpens} total opens",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "~${String.format("%.1f", s.avgSessionMinutes)}m per open",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Audit Focus Filter Chips ──────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = s.auditFilter == AppAuditFilter.ALL,
                            onClick = { vm.setAuditFilter(AppAuditFilter.ALL) },
                            label = { Text("All Apps (${s.totalApps})") }
                        )
                        FilterChip(
                            selected = s.auditFilter == AppAuditFilter.HEAVY_SINKS,
                            onClick = { vm.setAuditFilter(AppAuditFilter.HEAVY_SINKS) },
                            label = { Text("🔥 Heavy Sinks (${s.heavySinksCount})") }
                        )
                        FilterChip(
                            selected = s.auditFilter == AppAuditFilter.MICRO_CHECKERS,
                            onClick = { vm.setAuditFilter(AppAuditFilter.MICRO_CHECKERS) },
                            label = { Text("⚡ Dopamine Loops (${s.microCheckersCount})") }
                        )
                        FilterChip(
                            selected = s.auditFilter == AppAuditFilter.UNUSED,
                            onClick = { vm.setAuditFilter(AppAuditFilter.UNUSED) },
                            label = { Text("📦 Declutter / Unused") }
                        )
                    }
                }

                // ── Category Selector Chips ───────────────────────────────
                if (s.availableCategories.size > 2) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            s.availableCategories.forEach { cat ->
                                val isSelected = s.selectedCategory == cat
                                InputChip(
                                    selected = isSelected,
                                    onClick = { vm.setCategory(cat) },
                                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

                // ── Search bar ────────────────────────────────────────────
                item {
                    OutlinedTextField(
                        value         = s.query,
                        onValueChange = vm::setQuery,
                        placeholder   = { Text("Search by name or package…") },
                        leadingIcon   = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon  = {
                            if (s.query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) {
                                    Icon(Icons.Outlined.Close, "Clear search")
                                }
                            }
                        },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = RoundedCornerShape(12.dp)
                    )
                }

                // ── App items ─────────────────────────────────────────────
                if (s.displayApps.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (s.auditFilter == AppAuditFilter.HEAVY_SINKS) "No heavy time sinks found! Great digital discipline."
                                    else if (s.auditFilter == AppAuditFilter.MICRO_CHECKERS) "No dopamine checking loops detected."
                                    else "No apps match your search or filter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            "Showing ${s.displayApps.size} apps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(s.displayApps, key = { it.packageName }) { app ->
                        EnhancedAppCard(
                            app = app,
                            totalDayTimeMs = s.totalTimeMs,
                            goal = s.goals[app.packageName],
                            onClick = {
                                navController.navigate(Screen.AppDetail.withArg(app.packageName))
                            },
                            onSetLimit = {
                                goalTargetApp = app
                            }
                        )
                    }
                }
            }
        }

        // Add / Edit Goal Dialog
        goalTargetApp?.let { app ->
            val existingGoal = s.goals[app.packageName]
            AddGoalDialog(
                initialPkg = app.packageName,
                initialLabel = app.appLabel,
                initialLimitMs = existingGoal?.dailyLimitMs ?: (if (app.totalTimeMs > 0) app.totalTimeMs * 8 / 10 else 3_600_000L),
                initialReminder = existingGoal?.reminderEnabled ?: true,
                onDismiss = { goalTargetApp = null },
                onConfirm = { pkg, label, ms, remind ->
                    vm.saveGoal(pkg, label, ms, remind)
                    goalTargetApp = null
                }
            )
        }
    }
}

@Composable
private fun EnhancedAppCard(
    app: AppUsageEntity,
    totalDayTimeMs: Long,
    goal: GoalEntity?,
    onClick: () -> Unit,
    onSetLimit: () -> Unit
) {
    val percentOfTotal = if (totalDayTimeMs > 0L) (app.totalTimeMs.toFloat() / totalDayTimeMs.toFloat()) else 0f
    val avgPerOpen = if (app.launchCount > 0) (app.totalTimeMs / app.launchCount) else 0L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("app_card_${app.packageName}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon / initial
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(getAppCategoryColor(app.category).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        app.appLabel.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = getAppCategoryColor(app.category)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.appLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = getAppCategoryColor(app.category).copy(alpha = 0.12f)
                        ) {
                            Text(
                                app.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = getAppCategoryColor(app.category),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    Text(
                        "${app.launchCount} opens • ~${avgPerOpen.toHoursMinutes()}/open",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Time spent & quick limit button
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        app.totalTimeMs.toHoursMinutes(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    IconButton(
                        onClick = onSetLimit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (goal != null) Icons.Outlined.Edit else Icons.Outlined.HourglassTop,
                            contentDescription = "Set limit",
                            tint = if (goal != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Share of day progress bar
            if (percentOfTotal > 0f) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LinearProgressIndicator(
                        progress = { percentOfTotal.coerceIn(0.01f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape),
                        color = getAppCategoryColor(app.category),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${(percentOfTotal * 100).toInt()}% of day",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun getAppCategoryColor(category: String): Color = when (category) {
    "Social" -> Color(0xFFE91E63)
    "Video", "Entertainment" -> Color(0xFF9C27B0)
    "Productivity" -> Color(0xFF2196F3)
    "Games" -> Color(0xFFFF9800)
    "News" -> Color(0xFF009688)
    "Maps" -> Color(0xFF4CAF50)
    "Photos" -> Color(0xFF3F51B5)
    "Music" -> Color(0xFFE040FB)
    else -> Color(0xFF607D8B)
}
