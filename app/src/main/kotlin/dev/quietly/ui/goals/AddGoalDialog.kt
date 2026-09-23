package dev.quietly.ui.goals

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalDialog(
    initialPkg: String = "",
    initialLabel: String = "",
    initialLimitMs: Long = 3_600_000L,
    initialReminder: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (pkg: String, label: String, limitMs: Long, reminder: Boolean) -> Unit
) {
    var pkg by remember { mutableStateOf(initialPkg) }
    var label by remember { mutableStateOf(initialLabel) }

    val initialHours = (initialLimitMs / 3_600_000L).toString()
    val initialMinutes = ((initialLimitMs % 3_600_000L) / 60_000L).toString()

    var hours by remember { mutableStateOf(initialHours) }
    var minutes by remember { mutableStateOf(initialMinutes) }
    var reminder by remember { mutableStateOf(initialReminder) }

    val context = LocalContext.current
    val pm = context.packageManager

    var installedApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolves: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        val myPkg = context.packageName
        val apps = resolves
            .asSequence()
            .map { it.activityInfo.packageName }
            .filter { it != myPkg }
            .distinct()
            .map { p ->
                val l = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()
                } catch (_: Exception) { p }
                p to l
            }
            .sortedBy { it.second.lowercase() }
            .toList()
        installedApps = apps
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPkg.isNotBlank()) "Edit Goal" else "New Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showAppPicker) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        placeholder = { Text("Search installed apps…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    val filtered = installedApps.filter {
                        it.second.contains(appSearchQuery, ignoreCase = true) ||
                        it.first.contains(appSearchQuery, ignoreCase = true)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        LazyColumn {
                            items(filtered) { (appPkg, appName) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pkg = appPkg
                                            label = appName
                                            showAppPicker = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(appName, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            appPkg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    if (pkg == appPkg) {
                                        Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { showAppPicker = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Done")
                    }
                } else {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("App name") },
                        trailingIcon = {
                            if (installedApps.isNotEmpty()) {
                                IconButton(onClick = { showAppPicker = true }) {
                                    Icon(Icons.Outlined.ArrowDropDown, "Pick installed app")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pkg,
                        onValueChange = { pkg = it },
                        label = { Text("Package name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Daily time limit", style = MaterialTheme.typography.labelMedium)

                    // Presets
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "30m" to Pair("0", "30"),
                            "1h" to Pair("1", "0"),
                            "1.5h" to Pair("1", "30"),
                            "2h" to Pair("2", "0")
                        ).forEach { (name, pair) ->
                            SuggestionChip(
                                onClick = {
                                    hours = pair.first
                                    minutes = pair.second
                                },
                                label = { Text(name) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { hours = it.filter { c -> c.isDigit() } },
                            label = { Text("Hours") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                            label = { Text("Minutes") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = reminder, onCheckedChange = { reminder = it })
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Alert me at 90% of limit",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = hours.toLongOrNull() ?: 0L
                    val m = minutes.toLongOrNull() ?: 0L
                    val ms = h * 3_600_000L + m * 60_000L
                    if (pkg.isNotBlank() && ms > 0L) {
                        onConfirm(pkg.trim(), label.trim().ifBlank { pkg.trim() }, ms, reminder)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
