package dev.quietly.ui.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AddGoalDialog(
    onConfirm: (packageName: String, limitMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var pkg     by remember { mutableStateOf("") }
    var hours   by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Set Daily Limit") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onValueChange = { hours = it },
                        label         = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value         = minutes,
                        onValueChange = { minutes = it },
                        label         = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hours.toLongOrNull() ?: 0L
                val m = minutes.toLongOrNull() ?: 0L
                val limitMs = (h * 60 + m) * 60_000L
                if (pkg.isNotBlank() && limitMs > 0) onConfirm(pkg.trim(), limitMs)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
