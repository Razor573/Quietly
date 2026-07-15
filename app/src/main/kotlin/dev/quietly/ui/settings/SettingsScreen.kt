package dev.quietly.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Theme ─────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dark theme", style = MaterialTheme.typography.bodyLarge)
                    Text("Always use dark mode", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(checked = s.darkTheme, onCheckedChange = vm::setDarkTheme)
            }
            Divider()

            // ── Data retention ────────────────────────────────────────────
            Column {
                Text("Data retention: ${s.retentionDays} days",
                    style = MaterialTheme.typography.bodyLarge)
                Text("Usage history older than this is deleted automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Slider(
                    value         = s.retentionDays.toFloat(),
                    onValueChange = { vm.setRetention(it.toInt()) },
                    valueRange    = 7f..365f,
                    steps         = 35
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("7 days",  style = MaterialTheme.typography.labelSmall)
                    Text("1 year",  style = MaterialTheme.typography.labelSmall)
                }
            }
            Divider()

            // ── PIN lock ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PIN lock", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (s.pinEnabled) "App is PIN-protected"
                        else "Protect Quietly with a 4-digit PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Button(onClick = { showPinDialog = true }) {
                    Text(if (s.pinEnabled) "Change / Remove" else "Set PIN")
                }
            }

            // ── Security notice ───────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Privacy & Security", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "All data is stored locally on this device using AES-256-GCM encryption. " +
                        "No data is ever sent to any server or cloud service.",
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
        title   = { Text(if (hasPinAlready) "Change PIN" else "Set PIN") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label         = { Text("4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine    = true
                )
                OutlinedTextField(
                    value         = pin2,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin2 = it },
                    label         = { Text("Confirm PIN") },
                    isError       = mismatch,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine    = true
                )
                if (mismatch) Text("PINs don't match",
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
            TextButton(
                onClick  = { if (pin.length == 4 && pin == pin2) onConfirm(pin) },
                enabled  = pin.length == 4 && !mismatch
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
