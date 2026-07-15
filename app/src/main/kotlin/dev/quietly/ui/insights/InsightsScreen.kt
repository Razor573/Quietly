package dev.quietly.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.quietly.domain.ImportanceEngine.ImportanceLabel
import dev.quietly.domain.ImportanceEngine.RecommendationType
import dev.quietly.domain.ImportanceEngine.ScoredApp
import dev.quietly.ui.components.BottomNavBar
import dev.quietly.util.toHoursMinutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    navController: NavController,
    vm: InsightsViewModel = hiltViewModel()
) {
    val s by vm.uiState.collectAsState()
    var overrideTarget by remember { mutableStateOf<ScoredApp?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                actions = {
                    Text(
                        "${s.analysisWindowDays}-day window",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = { BottomNavBar(navController) }
    ) { pad ->

        if (s.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (s.rankedApps.isEmpty()) {
            EmptyInsightsState(pad)
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                top    = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 16.dp,
                start  = 16.dp,
                end    = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Category breakdown ───────────────────────────────────────────────
            if (s.categoryBreakdown.isNotEmpty()) {
                item { CategoryBreakdownCard(breakdown = s.categoryBreakdown) }
            }

            // ── Remove candidates ────────────────────────────────────────────────
            if (s.removeList.isNotEmpty()) {
                item {
                    SectionHeader(
                        title    = "🗑️ Remove candidates",
                        subtitle = "${s.removeList.size} app(s) with low importance and low recency.",
                        tint     = MaterialTheme.colorScheme.error
                    )
                }
                items(s.removeList) { app ->
                    ScoredAppCard(
                        app       = app,
                        onOverride = { overrideTarget = app }
                    )
                }
            }

            // ── Limit candidates ─────────────────────────────────────────────────
            if (s.limitList.isNotEmpty()) {
                item {
                    SectionHeader(
                        title    = "⏱ Limit candidates",
                        subtitle = "${s.limitList.size} app(s) are high-usage or distraction-prone.",
                        tint     = MaterialTheme.colorScheme.tertiary
                    )
                }
                items(s.limitList) { app ->
                    ScoredAppCard(
                        app       = app,
                        onOverride = { overrideTarget = app }
                    )
                }
            }

            // ── Protected apps ──────────────────────────────────────────────────
            if (s.protectedList.isNotEmpty()) {
                item {
                    SectionHeader(
                        title    = "🛡️ Protected apps",
                        subtitle = "${s.protectedList.size} app(s) are essential or manually protected.",
                        tint     = MaterialTheme.colorScheme.primary
                    )
                }
                items(s.protectedList) { app ->
                    ScoredAppCard(
                        app        = app,
                        onOverride = { overrideTarget = app }
                    )
                }
            }

            // ── Full ranked list ───────────────────────────────────────────────────
            item {
                SectionHeader(
                    title    = "📊 All apps — ranked by importance",
                    subtitle = "90-day analysis window. Tap any app to set an override."
                )
            }
            items(s.rankedApps) { app ->
                ScoredAppCard(
                    app        = app,
                    onOverride = { overrideTarget = app }
                )
            }
        }
    }

    // ── Per-app override bottom sheet ──────────────────────────────────────────
    overrideTarget?.let { target ->
        AppOverrideSheet(
            app        = target,
            onDismiss  = { overrideTarget = null },
            onSetOverride = { type ->
                vm.setOverride(target.packageName, type)
                overrideTarget = null
            },
            onClearOverride = {
                vm.clearOverride(target.packageName)
                overrideTarget = null
            }
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
//  Sub-composables
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title:    String,
    subtitle: String,
    tint:     Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = tint, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScoredAppCard(
    app:        ScoredApp,
    onOverride: () -> Unit
) {
    val labelColor = when (app.label) {
        ImportanceLabel.ESSENTIAL        -> MaterialTheme.colorScheme.primary
        ImportanceLabel.USEFUL           -> MaterialTheme.colorScheme.secondary
        ImportanceLabel.OPTIONAL         -> MaterialTheme.colorScheme.tertiary
        ImportanceLabel.REMOVE_CANDIDATE -> MaterialTheme.colorScheme.error
    }
    val labelText = when (app.label) {
        ImportanceLabel.ESSENTIAL        -> "Essential"
        ImportanceLabel.USEFUL           -> "Useful"
        ImportanceLabel.OPTIONAL         -> "Optional"
        ImportanceLabel.REMOVE_CANDIDATE -> "Low value"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOverride),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                    Text(app.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Score badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${app.score}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )
                    Text(
                        labelText,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor
                    )
                }
            }

            // Score bar
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(app.score / 100f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(labelColor)
                )
            }

            // Reason string
            if (app.reason.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape  = RoundedCornerShape(6.dp),
                    color  = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        app.reason,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Stats row
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${app.activeDays} active days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (app.lastSeenDaysAgo == 0) "Seen today"
                    else "Last seen ${app.lastSeenDaysAgo}d ago",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    app.totalTimeMs.toHoursMinutes() + " total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Protection badge
            if (app.isProtected) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = "Protected",
                        tint   = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Protected — excluded from removal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppOverrideSheet(
    app:            ScoredApp,
    onDismiss:      () -> Unit,
    onSetOverride:  (String) -> Unit,
    onClearOverride: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(
                "Override: ${app.appLabel}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Your override changes how Quietly scores and recommends this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            OverrideOption("🛡️ Mark as Essential",
                "Protected — never suggested for removal.") {
                onSetOverride("ESSENTIAL")
            }
            OverrideOption("💧 Mark as Focus Drain",
                "Treated as a distraction — usage limit suggested.") {
                onSetOverride("FOCUS_DRAIN")
            }
            OverrideOption("👀 Ignore",
                "Kept in score list but excluded from suggestions.") {
                onSetOverride("IGNORE")
            }
            OverrideOption("❌ Exclude from suggestions",
                "Never shown in Remove or Limit lists.") {
                onSetOverride("EXCLUDE_SUGGESTIONS")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick  = onClearOverride,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear override (use automatic scoring)")
            }
        }
    }
}

@Composable
private fun OverrideOption(
    title:    String,
    subtitle: String,
    onClick:  () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(8.dp),
        color  = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyInsightsState(pad: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(pad)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.BarChart,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Not enough data yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Quietly needs a few days of usage to generate insights. Check back soon.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryBreakdownCard(breakdown: Map<String, Long>) {
    val total  = breakdown.values.sum().coerceAtLeast(1L)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.outline
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("By category — 90-day window",
                style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
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
                    Box(
                        Modifier.size(8.dp).clip(RoundedCornerShape(2.dp))
                            .background(colors.getOrElse(i) { colors.last() })
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$cat ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
