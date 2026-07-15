package dev.quietly.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quietly.util.hasUsageStatsPermission

@Composable
fun OnboardingScreen(
    onPermissionGranted: () -> Unit,
    wasRevoked: Boolean = false
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (context.hasUsageStatsPermission()) onPermissionGranted()
    }

    var showDeniedWarning by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("\uD83D\uDD07", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(20.dp))

            Text(
                text       = if (wasRevoked) "Permission removed" else "Meet Quietly",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = if (wasRevoked)
                    "Usage Access was turned off in your device settings. " +
                    "Quietly can\u2019t analyse your apps without it. Please re-enable it below."
                else
                    "Quietly analyses which apps are truly essential to you \u2014 " +
                    "and which are wasting your time. All analysis stays on your device. " +
                    "Nothing is ever sent anywhere.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(32.dp))

            if (!wasRevoked) {
                ValueBulletCard()
                Spacer(Modifier.height(32.dp))
            }

            PermissionRationaleCard()
            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(visible = showDeniedWarning, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        "\u26A0\uFE0F Usage Access is still not granted. " +
                        "Please tap \"Grant Usage Access\", find Quietly in the list, and enable the toggle.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick  = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp)
            ) { Text("Grant Usage Access") }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    if (context.hasUsageStatsPermission()) onPermissionGranted()
                    else showDeniedWarning = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp)
            ) { Text("I\u2019ve granted it \u2014 continue") }
        }
    }
}

@Composable
private fun ValueBulletCard() {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ValueBullet("\uD83D\uDCCA", "90-day importance scoring",
                "Find out which apps actually matter \u2014 and which ones just waste your time.")
            ValueBullet("\uD83D\uDEE1\uFE0F", "Protected essential apps",
                "Banking, authenticators, and security apps are always kept safe from removal suggestions.")
            ValueBullet("\uD83D\uDD12", "100% private, always offline",
                "No accounts, no cloud, no ads. Your data never leaves this device.")
        }
    }
}

@Composable
private fun ValueBullet(emoji: String, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(emoji, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 2.dp, end = 10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(body,  style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionRationaleCard() {
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83D\uDCF1", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 12.dp))
            Column {
                Text("Why Usage Access?",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Quietly reads which apps you open and for how long. " +
                    "This data never leaves your device \u2014 it is only used to score app importance locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}
