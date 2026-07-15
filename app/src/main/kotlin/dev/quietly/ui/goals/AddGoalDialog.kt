package dev.quietly.ui.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (pkg: String, label: String, limitMs: Long, reminder: Boolean) -> Unit
) {
    var pkg       by remember { mutableStateOf("") }
    var label     by remember { mutableStateOf("") }
    var hours     by remember { mutableStateOf("1") }
    var minutes   by remember { mutableStateOf("0") }
    var reminder  by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("New Goal") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = label,
                    onValueChange = { label = it },
                    label         = { Text("App name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = pkg,
                    onValueChange = { pkg = it },
                    label         = { Text("Package name (e.g. com.instagram.android)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() } },
                        label         = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = minutes,
                        onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                        label         = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reminder, onCheckedChange = { reminder = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Send reminder at 90% of limit",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h  = hours.toLongOrNull() ?: 1L
                    val m  = minutes.toLongOrNull() ?: 0L
                    val ms = h * 3_600_000L + m * 60_000L
                    if (pkg.isNotBlank() && ms > 0)
                        onConfirm(pkg.trim(), label.trim().ifBlank { pkg.trim() }, ms, reminder)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
