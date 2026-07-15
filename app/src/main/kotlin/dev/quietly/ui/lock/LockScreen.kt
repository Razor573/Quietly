package dev.quietly.ui.lock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Full-screen PIN entry shown by MainActivity when a PIN is set.
 * Calls onUnlocked() if the entered PIN matches storedHash.
 */
@Composable
fun LockScreen(
    storedHash:  String,
    onUnlocked:  () -> Unit
) {
    var pin     by remember { mutableStateOf("") }
    var error   by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Quietly", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Enter your PIN to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value         = pin,
            onValueChange = {
                if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                    pin   = it
                    error = false
                    if (it.length == 4) {
                        if (it == storedHash) onUnlocked()
                        else { error = true; pin = "" }
                    }
                }
            },
            label         = { Text("PIN") },
            isError       = error,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine    = true
        )
        if (error) {
            Spacer(Modifier.height(8.dp))
            Text("Incorrect PIN", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
