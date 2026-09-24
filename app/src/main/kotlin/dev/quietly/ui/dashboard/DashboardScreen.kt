package dev.quietly.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.data.db.dao.DayTotal
import dev.quietly.ui.components.AppUsageRow
import dev.quietly.ui.components.BottomNavBar
import dev.quietly.ui.components.SmartForecastCard
import dev.quietly.ui.navigation.Screen
import dev.quietly.util.toHoursMinutes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    vm: DashboardViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsState()
    val today = remember { LocalDate.now().toEpochDay().toInt() }
    val calibratedDays = remember(uiState.calibrationSamples) {
        uiState.calibrationSamples.filter { it.packageName == null }.map { it.epochDay }.toSet()
    }
    val calibratedPkgs = remember(uiState.calibrationSamples, uiState.selectedEpochDay) {
        uiState.calibrationSamples
            .filter { it.epochDay == uiState.selectedEpochDay && !it.packageName.isNullOrBlank() }
            .mapNotNull { it.packageName }
            .toSet()
    }

    var showCalibrationDialog by remember { mutableStateOf(false) }
    var calibrationTargetPkg by remember { mutableStateOf<String?>(null) }
    var calibrationTargetTitle by remember { mutableStateOf("Total Screen Time") }
    var calibrationCurrentMs by remember { mutableStateOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isViewingPastDay) "Daily History" else "Today")
                },
                actions = {
                    IconButton(
                        onClick = { vm.refresh() },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(
                        onClick = { navController.navigate(Screen.Settings.route) },
                        modifier = Modifier.testTag("settings_button")
                    ) {
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
                // ── Past Day Active Banner (if user selected a past day) ──
                if (uiState.isViewingPastDay) {
                    val selDate = LocalDate.ofEpochDay(uiState.selectedEpochDay.toLong())
                    val dayTitle = selDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                    item {
                        Surface(
                            onClick = { vm.selectDay(today) },
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("past_day_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            "Viewing $dayTitle",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            "Tap here to return to today",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Return to today",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ── Total time hero card ──────────────────────────────────
                item {
                    val isPast = uiState.isViewingPastDay
                    val selDate = LocalDate.ofEpochDay(uiState.selectedEpochDay.toLong())
                    val heroLabel = if (isPast) {
                        "Screen time on " + selDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                    } else {
                        "Screen time today"
                    }
                    val heroTotal = if (isPast) uiState.selectedDayTotalMs else uiState.totalTodayMs

                    TotalTimeHeroCard(
                        totalMs = heroTotal,
                        label = heroLabel,
                        onCalibrateClick = {
                            calibrationTargetPkg = null
                            calibrationTargetTitle = if (isPast) "Screen Time ($heroLabel)" else "Screen Time Today"
                            calibrationCurrentMs = heroTotal
                            showCalibrationDialog = true
                        }
                    )
                }

                // ── AI/ML Adaptive Calibration Status Card ────────────────
                item {
                    MlCalibrationStatusCard(
                        weights = uiState.calibrationWeights,
                        onAdjustClick = {
                            val isPast = uiState.isViewingPastDay
                            val heroTotal = if (isPast) uiState.selectedDayTotalMs else uiState.totalTodayMs
                            calibrationTargetPkg = null
                            calibrationTargetTitle = if (isPast) "Screen Time (Selected Day)" else "Screen Time Today"
                            calibrationCurrentMs = heroTotal
                            showCalibrationDialog = true
                        },
                        onResetClick = { vm.resetCalibration() }
                    )
                }

                // ── Phone Telemetry vs Smart ML Model Card ────────────────
                item {
                    ContextTelemetryCard(
                        telemetry = uiState.selectedDayTelemetry,
                        weights = uiState.calibrationWeights,
                        dayTotalMs = if (uiState.isViewingPastDay) uiState.selectedDayTotalMs else uiState.totalTodayMs
                    )
                }

                // ── 24-Hour Activity Timeline & Heatmap ───────────────────
                item {
                    HourlyTimelineCard(
                        telemetry = uiState.selectedDayTelemetry,
                        dayTotalMs = if (uiState.isViewingPastDay) uiState.selectedDayTotalMs else uiState.totalTodayMs
                    )
                }

                // ── AI/ML Smart Forecast & Trajectory Card (Today only) ───
                if (!uiState.isViewingPastDay) {
                    uiState.intelligenceReport?.let { report ->
                        item {
                            SmartForecastCard(
                                report = report,
                                onClick = { navController.navigate(Screen.Insights.route) }
                            )
                        }
                    }
                }

                // ── 7-day interactive bar chart ───────────────────────────
                if (uiState.weeklyTotals.isNotEmpty()) {
                    item {
                        WeeklyBarChart(
                            totals = uiState.weeklyTotals,
                            selectedEpochDay = uiState.selectedEpochDay,
                            calibratedDays = calibratedDays,
                            onSelectDay = { day -> vm.selectDay(day) }
                        )
                    }

                    // ── Interactive history & day breakdown detail card ───
                    item {
                        InteractiveDayDetailCard(
                            uiState = uiState,
                            onPrevDay = { vm.selectPreviousDay() },
                            onNextDay = { vm.selectNextDay() },
                            onReturnToToday = { vm.selectDay(today) }
                        )
                    }
                }

                val currentAppList = if (uiState.isViewingPastDay) uiState.selectedDayUsages else uiState.appUsages
                val activeList = currentAppList.filter { it.totalTimeMs > 0L }

                if (activeList.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (uiState.isViewingPastDay) "No app records on this day" else "No usage recorded today",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (uiState.isViewingPastDay)
                                        "Select another day on the bar chart above to explore history."
                                    else
                                        "Use your apps normally and their screen time will automatically appear here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    val currentDayTotal = if (uiState.isViewingPastDay) uiState.selectedDayTotalMs else uiState.totalTodayMs

                    // ── Dashboard Charts: Top Apps Distribution ───────────────
                    item {
                        TopAppsDistributionCard(
                            apps = activeList,
                            dayTotalMs = currentDayTotal,
                            calibratedPkgs = calibratedPkgs,
                            onAppClick = { pkg ->
                                navController.navigate(Screen.AppDetail.withArg(pkg))
                            },
                            onCalibrateApp = { app ->
                                calibrationTargetPkg = app.packageName
                                calibrationTargetTitle = app.appLabel
                                calibrationCurrentMs = app.totalTimeMs
                                showCalibrationDialog = true
                            }
                        )
                    }

                    // ── Dashboard Charts: Category Breakdown ─────────────────
                    item {
                        CategoryDistributionCard(
                            apps = activeList,
                            dayTotalMs = currentDayTotal
                        )
                    }

                    item {
                        val selDate = LocalDate.ofEpochDay(uiState.selectedEpochDay.toLong())
                        val appListTitle = if (uiState.isViewingPastDay) {
                            "Apps Used on " + selDate.format(DateTimeFormatter.ofPattern("EEE, MMM d")) + " (${activeList.size})"
                        } else {
                            "App Screen Time Today (${activeList.size})"
                        }
                        Text(
                            appListTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    // ── App rows (tappable → detail) ──────────────────────────
                    items(activeList, key = { it.packageName }) { usage ->
                        AppUsageRow(
                            usage            = usage,
                            goal             = uiState.goals[usage.packageName],
                            dayTotalMs       = currentDayTotal,
                            isAnchored       = usage.packageName in calibratedPkgs,
                            onCalibrateClick = {
                                calibrationTargetPkg = usage.packageName
                                calibrationTargetTitle = usage.appLabel
                                calibrationCurrentMs = usage.totalTimeMs
                                showCalibrationDialog = true
                            },
                            onClick          = {
                                navController.navigate(Screen.AppDetail.withArg(usage.packageName))
                            }
                        )
                    }
                }
            }
        }

        if (showCalibrationDialog) {
            MlCalibrationDialog(
                title = calibrationTargetTitle,
                currentDurationMs = calibrationCurrentMs,
                onDismiss = { showCalibrationDialog = false },
                onConfirmActualMinutes = { actualMins ->
                    showCalibrationDialog = false
                    vm.submitCalibrationFeedback(
                        epochDay = uiState.selectedEpochDay,
                        packageName = calibrationTargetPkg,
                        rawDurationMs = calibrationCurrentMs,
                        actualMinutes = actualMins
                    )
                }
            )
        }
    }
}

@Composable
private fun TotalTimeHeroCard(
    totalMs: Long,
    label: String = "Screen time today",
    onCalibrateClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                totalMs.toHoursMinutes(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onCalibrateClick,
                modifier = Modifier.testTag("hero_calibrate_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Is this time correct?",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun MlCalibrationStatusCard(
    weights: dev.quietly.domain.ml.CalibrationModelWeights,
    onAdjustClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🧠", style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text(
                            "Adaptive Accuracy Model",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (weights.sampleCount > 0)
                                "Trained on ${weights.sampleCount} manual correction${if (weights.sampleCount > 1) "s" else ""}"
                            else
                                "Ready to learn from your inputs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (weights.sampleCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${(weights.confidence * 100).toInt()}% confidence",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (weights.sampleCount > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Active Fidelity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${(weights.activeSessionRetentionRate * 100).toInt()}% preserved", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Ghost Idle Filter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val prunedPct = (100 - (weights.passiveGhostRetentionRate * 100).toInt()).coerceAtLeast(0)
                            Text("$prunedPct% pruned", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = weights.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAdjustClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Calibrate Time", style = MaterialTheme.typography.labelMedium)
                }

                if (weights.sampleCount > 0) {
                    TextButton(
                        onClick = onResetClick,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Reset ML", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun MlCalibrationDialog(
    title: String,
    currentDurationMs: Long,
    onDismiss: () -> Unit,
    onConfirmActualMinutes: (Int) -> Unit
) {
    val currentTotalMins = (currentDurationMs / 60_000L).toInt()
    val initialHours = currentTotalMins / 60
    val initialMins = currentTotalMins % 60

    var hoursText by remember { mutableStateOf(initialHours.toString()) }
    var minutesText by remember { mutableStateOf(initialMins.toString()) }
    var isCorrectOptionSelected by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🧠", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Calibrate $title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Currently measured: ${currentDurationMs.toHoursMinutes()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Did this feel accurate to how much time you actually spent?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = (isCorrectOptionSelected == true),
                        onClick = { isCorrectOptionSelected = true },
                        label = { Text("Yes, it was correct") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = (isCorrectOptionSelected == false),
                        onClick = { isCorrectOptionSelected = false },
                        label = { Text("No, adjust it") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isCorrectOptionSelected == true) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Awesome! The ML model uses this day's session length and app telemetry as a confirmed ground-truth anchor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isCorrectOptionSelected == false) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Enter what your actual time was. The model will analyze the phone's telemetry (session continuity, launch density, and app behavior) to adjust this day accurately without flattening or distorting other days:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { hoursText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Hours") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Mins") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCorrectOptionSelected == true) {
                        onConfirmActualMinutes(currentTotalMins)
                    } else if (isCorrectOptionSelected == false) {
                        val h = hoursText.toIntOrNull() ?: 0
                        val m = minutesText.toIntOrNull() ?: 0
                        val totalM = (h * 60 + m).coerceAtLeast(0)
                        onConfirmActualMinutes(totalM)
                    }
                },
                enabled = (isCorrectOptionSelected != null)
            ) {
                Text("Train Model")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ContextTelemetryCard(
    telemetry: dev.quietly.domain.ml.DayTelemetry?,
    weights: dev.quietly.domain.ml.CalibrationModelWeights,
    dayTotalMs: Long,
    modifier: Modifier = Modifier
) {
    if (telemetry == null || telemetry.rawTotalMs <= 0L) return

    val rawMs = telemetry.rawTotalMs
    val diffMs = dayTotalMs - rawMs
    val isPruned = diffMs < -60_000L
    val isBoosted = diffMs > 60_000L

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "📱",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            "Phone Telemetry & Pattern Analysis",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Sensor telemetry combined with adaptive learning",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Comparison row: Raw Phone Sensor vs Smart Calibrated
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "RAW PHONE SENSOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        rawMs.toHoursMinutes(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    if (isPruned) "➔  pruned" else if (isBoosted) "➔  restored" else "➔  synced",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPruned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "ADAPTIVE SCREEN TIME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        dayTotalMs.toHoursMinutes(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isPruned) {
                Spacer(Modifier.height(10.dp))
                val pruneMs = Math.abs(diffMs)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Filtered ${pruneMs.toHoursMinutes()} of passive idle screen-left-on time based on session interaction telemetry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 4 Telemetry Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "App Pickups",
                    value = "${telemetry.totalLaunches}",
                    sub = "Distinct launches",
                    modifier = Modifier.weight(1f)
                )
                val avgMins = if (telemetry.sessionCount > 0)
                    (telemetry.rawTotalMs / telemetry.sessionCount / 60_000L).coerceAtLeast(1L)
                else 0L
                TelemetryStatTile(
                    label = "Avg Session",
                    value = "${avgMins}m",
                    sub = "${telemetry.sessionCount} sessions",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatTile(
                    label = "Longest Continuous",
                    value = telemetry.longestSessionMs.toHoursMinutes(),
                    sub = "Peak unbroken block",
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    label = "Media Playback",
                    value = telemetry.mediaTimeMs.toHoursMinutes(),
                    sub = "Video & background audio",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun HourlyTimelineCard(
    telemetry: dev.quietly.domain.ml.DayTelemetry?,
    dayTotalMs: Long,
    modifier: Modifier = Modifier
) {
    var selectedHour by remember { mutableStateOf<Int?>(null) }
    val hourlyList = telemetry?.hourlyDistributionMs ?: emptyList()
    val hasData = hourlyList.isNotEmpty() && hourlyList.any { it > 0L }

    val maxHourlyMs = if (hasData) hourlyList.maxOrNull()?.coerceAtLeast(1L) ?: 3600_000L else 3600_000L
    val peakHour = if (hasData) hourlyList.indices.maxByOrNull { hourlyList[it] } ?: 0 else 0
    val peakHourMs = if (hasData) hourlyList[peakHour] else 0L

    // Daypart totals (Night 0-5, Morning 6-11, Afternoon 12-17, Evening 18-23)
    val nightMs = if (hasData) (0..5).sumOf { hourlyList.getOrElse(it) { 0L } } else 0L
    val morningMs = if (hasData) (6..11).sumOf { hourlyList.getOrElse(it) { 0L } } else 0L
    val afternoonMs = if (hasData) (12..17).sumOf { hourlyList.getOrElse(it) { 0L } } else 0L
    val eveningMs = if (hasData) (18..23).sumOf { hourlyList.getOrElse(it) { 0L } } else 0L

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "24-Hour Activity Timeline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (peakHourMs > 0L) {
                    val amPm = if (peakHour < 12) "AM" else "PM"
                    val hour12 = if (peakHour == 0) 12 else if (peakHour > 12) peakHour - 12 else peakHour
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Peak: $hour12 $amPm (${peakHourMs.toHoursMinutes()})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 24 Hourly vertical bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                (0..23).forEach { hour ->
                    val ms = if (hasData) hourlyList.getOrElse(hour) { 0L } else 0L
                    val fraction = if (ms > 0L) (ms.toFloat() / maxHourlyMs.toFloat()).coerceIn(0.08f, 1f) else 0f
                    val isSelected = (selectedHour == hour)
                    val isPeak = (hour == peakHour && ms > 0L)

                    val barColor = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isPeak -> MaterialTheme.colorScheme.tertiary
                        ms > 0L -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                selectedHour = if (selectedHour == hour) null else hour
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isSelected) 0.95f else 0.75f)
                                .fillMaxHeight(if (fraction > 0f) fraction else 0.05f)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barColor)
                        )
                    }
                }
            }

            // Timeline time markers below bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("12A", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("6A", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("12P", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("6P", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("11P", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Detailed hour inspection banner if tapped
            if (selectedHour != null) {
                val h = selectedHour!!
                val amPm = if (h < 12) "AM" else "PM"
                val h12 = if (h == 0) 12 else if (h > 12) h - 12 else h
                val nextH = (h + 1) % 24
                val nextAmPm = if (nextH < 12) "AM" else "PM"
                val nextH12 = if (nextH == 0) 12 else if (nextH > 12) nextH - 12 else nextH
                val ms = if (hasData) hourlyList.getOrElse(h) { 0L } else 0L

                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$h12 $amPm – $nextH12 $nextAmPm",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            if (ms > 0L) ms.toHoursMinutes() else "No screen time",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 4 Dayparts chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DaypartChip("🌙 Night", nightMs, Modifier.weight(1f))
                DaypartChip("🌅 Mmg", morningMs, Modifier.weight(1f))
                DaypartChip("☀️ Aftn", afternoonMs, Modifier.weight(1f))
                DaypartChip("🌆 Eve", eveningMs, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DaypartChip(label: String, timeMs: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(
                if (timeMs > 0L) timeMs.toHoursMinutes() else "0m",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TelemetryStatTile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun WeeklyBarChart(
    totals: List<DayTotal>,
    selectedEpochDay: Int = LocalDate.now().toEpochDay().toInt(),
    calibratedDays: Set<Int> = emptySet(),
    onSelectDay: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val maxMs = (totals.maxOfOrNull { it.totalTimeMs } ?: 0L).coerceAtLeast(1L)
    val today = remember { LocalDate.now().toEpochDay().toInt() }

    val nonZeroTotals = totals.filter { it.totalTimeMs > 0L }
    val avgMs = if (nonZeroTotals.isNotEmpty()) nonZeroTotals.sumOf { it.totalTimeMs } / nonZeroTotals.size else 0L

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Last 7 Days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (avgMs > 0L) {
                        Text(
                            "Avg: ${avgMs.toHoursMinutes()} / day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "Tap bar to view day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val dayMap = totals.associateBy { it.dateEpochDay }
                (0..6).forEach { offset ->
                    val day = today - 6 + offset
                    val ms = dayMap[day]?.totalTimeMs ?: 0L
                    val frac = if (ms > 0L) (ms.toFloat() / maxMs.toFloat()).coerceIn(0.06f, 1f) else 0f
                    val date = LocalDate.ofEpochDay(day.toLong())
                    val isSelected = (day == selectedEpochDay)
                    val isAnchored = day in calibratedDays
                    val lbl = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectDay(day) }
                            .padding(vertical = 2.dp)
                            .testTag("bar_day_$offset"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Duration label or anchored badge if selected
                        if (isSelected) {
                            Text(
                                if (ms > 0L) ms.toHoursMinutes() else "0m",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                        } else if (isAnchored) {
                            Text(
                                "🎯",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.height(2.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (frac > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (isSelected) 0.85f else 0.70f)
                                        .fillMaxHeight(frac)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            if (isSelected) barColor
                                            else if (isAnchored) barColor.copy(alpha = 0.55f)
                                            else barColor.copy(alpha = 0.35f)
                                        )
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            lbl,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveDayDetailCard(
    uiState: DashboardUiState,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onReturnToToday: () -> Unit
) {
    val today = remember { LocalDate.now().toEpochDay().toInt() }
    val isPast = uiState.isViewingPastDay
    val selectedDate = LocalDate.ofEpochDay(uiState.selectedEpochDay.toLong())
    val dateLabel = if (uiState.selectedEpochDay == today) "Today"
                    else selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    val dayTotalMs = if (isPast) uiState.selectedDayTotalMs else uiState.totalTodayMs
    val apps = if (isPast) uiState.selectedDayUsages else uiState.appUsages

    // Compute weekly average
    val nonZeroDays = uiState.weeklyTotals.filter { it.totalTimeMs > 0L }
    val weeklyAvgMs = if (nonZeroDays.isNotEmpty()) nonZeroDays.map { it.totalTimeMs }.average().toLong() else 0L
    val diffPercent = if (weeklyAvgMs > 0L && dayTotalMs > 0L) {
        ((dayTotalMs - weeklyAvgMs).toFloat() / weeklyAvgMs.toFloat() * 100).toInt()
    } else null

    // Compute category breakdown for this day
    val categoryTotals = apps.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.totalTimeMs } }
        .filterValues { it > 0L }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Day stepper navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevDay,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.ChevronLeft, "Previous day")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isPast) "Historical Breakdown" else "Daily Breakdown",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onNextDay,
                    enabled = isPast,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        "Next day",
                        tint = if (isPast) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Total Screen Time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        dayTotalMs.toHoursMinutes(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (diffPercent != null) {
                    val isHigher = diffPercent > 0
                    val badgeColor = if (isHigher) MaterialTheme.colorScheme.errorContainer
                                     else MaterialTheme.colorScheme.primaryContainer
                    val textColor = if (isHigher) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onPrimaryContainer
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isHigher) "+$diffPercent% vs avg" else "$diffPercent% vs avg",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                }
            }

            // Category spectrum breakdown bar
            if (categoryTotals.isNotEmpty() && dayTotalMs > 0L) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Activity Categories",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    categoryTotals.entries.sortedByDescending { it.value }.forEach { (cat, catMs) ->
                        val weight = (catMs.toFloat() / dayTotalMs.toFloat()).coerceAtLeast(0.01f)
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(getCategoryColor(cat))
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    categoryTotals.entries.sortedByDescending { it.value }.take(3).forEach { (cat, catMs) ->
                        val percent = (catMs * 100 / dayTotalMs).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(getCategoryColor(cat))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$cat $percent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Return to Live Today button if inspecting past day
            if (isPast) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onReturnToToday,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Return to Live Today")
                }
            }
        }
    }
}

private fun getCategoryColor(category: String): Color = when (category) {
    "Social" -> Color(0xFFE91E63)
    "Video", "Entertainment" -> Color(0xFF9C27B0)
    "Productivity" -> Color(0xFF2196F3)
    "Games" -> Color(0xFFFF9800)
    "News" -> Color(0xFF009688)
    "Maps" -> Color(0xFF4CAF50)
    "Photos" -> Color(0xFF3F51B5)
    "Music" -> Color(0xFFE040FB)
    else -> Color(0xFF78909C)
}

