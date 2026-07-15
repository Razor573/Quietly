package dev.quietly.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.quietly.data.prefs.SecurePreferences
import dev.quietly.ui.lock.LockScreen
import dev.quietly.ui.navigation.QuietlyNavGraph
import dev.quietly.ui.navigation.Screen
import dev.quietly.ui.theme.QuietlyTheme
import dev.quietly.util.hasUsageStatsPermission
import javax.inject.Inject
import androidx.compose.runtime.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuietlyTheme(darkTheme = prefs.darkTheme) {
                var unlocked by remember {
                    mutableStateOf(!prefs.pinEnabled)
                }

                if (!unlocked && prefs.pinHash != null) {
                    LockScreen(
                        storedHash = prefs.pinHash!!,
                        onUnlocked = { unlocked = true }
                    )
                } else {
                    val start = when {
                        !prefs.onboardingComplete        -> Screen.Onboarding.route
                        !hasUsageStatsPermission()       -> Screen.Onboarding.route
                        else                             -> Screen.Dashboard.route
                    }
                    QuietlyNavGraph(startDestination = start)
                }
            }
        }
    }
}
