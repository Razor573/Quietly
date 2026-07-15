package dev.quietly.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quietly.util.hasUsagePermission

@Composable
fun OnboardingScreen(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current

    // If already granted, skip straight through
    if (context.hasUsagePermission()) {
        onPermissionGranted()
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                text  = "🔇",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text      = "Welcome to Quietly",
                style     = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text      = "Quietly reads your screen-time data entirely on-device.\nNothing ever leaves your phone.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Usage Access")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { if (context.hasUsagePermission()) onPermissionGranted() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I’ve granted it — continue")
            }
        }
    }
}
