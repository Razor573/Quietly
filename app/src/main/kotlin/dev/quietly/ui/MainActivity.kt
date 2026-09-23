package dev.quietly.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dagger.hilt.android.AndroidEntryPoint
import dev.quietly.data.prefs.SecurePreferences
import dev.quietly.ui.lock.LockScreen
import dev.quietly.ui.navigation.QuietlyNavGraph
import dev.quietly.ui.navigation.Screen
import dev.quietly.ui.theme.QuietlyTheme
import dev.quietly.util.hasUsageStatsPermission
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme by prefs.darkThemeFlow.collectAsState()
            val pinEnabled by prefs.pinEnabledFlow.collectAsState()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {}
                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            QuietlyTheme(darkTheme = darkTheme) {
                var unlocked by remember(pinEnabled) {
                    mutableStateOf(!pinEnabled)
                }

                if (!unlocked && prefs.pinHash != null) {
                    LockScreen(
                        storedHash = prefs.pinHash!!,
                        onUnlocked = { unlocked = true }
                    )
                } else {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission()) }
                    var permissionRevoked by remember { mutableStateOf(false) }

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val current = hasUsageStatsPermission()
                                permissionRevoked = hasUsagePermission && !current
                                hasUsagePermission = current
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    val start = when {
                        !prefs.onboardingComplete        -> Screen.Onboarding.route
                        !hasUsagePermission              -> Screen.Onboarding.route
                        else                             -> Screen.Dashboard.route
                    }
                    val showRevokedOnboarding = (prefs.onboardingComplete && !hasUsagePermission) || permissionRevoked
                    key(start, showRevokedOnboarding) {
                        QuietlyNavGraph(
                            startDestination = start,
                            onboardingWasRevoked = showRevokedOnboarding,
                            onOnboardingComplete = {
                                prefs.onboardingComplete = true
                            }
                        )
                    }
                }
            }
        }
    }
}
