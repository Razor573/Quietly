package dev.quietly.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val s by vm.uiState.collectAsState()
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar    = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = { BottomNavBar(navController) }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsRow("Dark theme", "Always use dark mode") {
                Switch(checked = s.darkTheme, onCheckedChange = vm::setDarkTheme)
            }
            HorizontalDivider()

            Column {
                Text("Importance analysis window: ${s.analysisWindowDays} days",
                    style = MaterialTheme.typography.bodyLarge)
                Text("The primary window used by the importance engine. 90 days gives the most accurate signal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { days ->
                        FilterChip(
                            selected = s.analysisWindowDays == days,
                            onClick  = { vm.setAnalysisWindow(days) },
                            label    = { Text("${days}d") }
                        )
                    }
                }
            }
            HorizontalDivider()

            Column {
                Text("Data retention: ${s.retentionDays} days",
                    style = MaterialTheme.typography.bodyLarge)
                Text("Usage history older than this is deleted automatically. Keep at least 90 days for best insights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Slider(
                    value         = s.retentionDays.toFloat(),
                    onValueChange = { vm.setRetention(it.toInt()) },
                    valueRange    = 30f..365f,
                    steps         = 33
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("30 days", style = MaterialTheme.typography.labelSmall)
                    Text("1 year",  style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRow(
                    title    = "Online app metadata (opt-in)",
                    subtitle = "Fetch minimal category info for unknown apps to improve scoring. Defaults OFF."
                ) {
                    Switch(checked = s.onlineMetadataEnabled, onCheckedChange = vm::setOnlineMetadata)
                }
                if (s.onlineMetadataEnabled) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            "\uD83D\uDCE1 When enabled, Quietly sends the app package name to a lookup service " +
                            "to retrieve its category. No personal data is included. Results are cached locally.",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
            HorizontalDivider()

            SettingsRow(
                title    = "PIN lock",
                subtitle = if (s.pinEnabled) "App is PIN-protected" else "Protect Quietly with a 4-digit PIN"
            ) {
                Button(onClick = { showPinDialog = true }) {
                    Text(if (s.pinEnabled) "Change / Remove" else "Set PIN")
                }
            }
            HorizontalDivider()

            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )) {
                Column(Modifier.padding(14.dp)) {
                    Text("\uD83D\uDD12 Privacy & Security",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        BULLET + " All usage data stays on your device. AES-256-GCM encrypted.\n" +
                        BULLET + " No internet permission is required for core features.\n" +
                        BULLET + " Cloud and device backups are disabled.\n" +
                        BULLET + " Online metadata is strictly opt-in and clearly disclosed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            hasPinAlready = s.pinEnabled,
            onDismiss     = { showPinDialog = false },
            onConfirm     = { pin -> vm.setPin(pin); showPinDialog = false }
        )
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title,    style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        trailing()
    }
}

@Composable
private fun PinDialog(
    hasPinAlready: Boolean,
    onDismiss:     () -> Unit,
    onConfirm:     (String?) -> Unit
) {
    var pin  by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val mismatch = pin.length == 4 && pin2.length == 4 && pin != pin2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPinAlready) "Change PIN" else "Set PIN") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4-digit PIN") },
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = pin2, onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin2 = it },
                    label = { Text("Confirm PIN") }, isError = mismatch,
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (mismatch) Text("PINs don\u2019t match",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
                if (hasPinAlready) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text("Remove PIN", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (pin.length == 4 && pin == pin2) onConfirm(pin) },
                enabled = pin.length == 4 && !mismatch) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private const val BULLET = "\u2022"
