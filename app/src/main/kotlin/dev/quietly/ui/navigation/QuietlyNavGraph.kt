package dev.quietly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.quietly.ui.dashboard.DashboardScreen
import dev.quietly.ui.apps.AppsScreen
import dev.quietly.ui.goals.GoalsScreen
import dev.quietly.ui.insights.InsightsScreen
import dev.quietly.ui.onboarding.OnboardingScreen
import dev.quietly.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Dashboard  : Screen("dashboard")
    object Apps       : Screen("apps")
    object Goals      : Screen("goals")
    object Insights   : Screen("insights")
    object Settings   : Screen("settings")
}

@Composable
fun QuietlyNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Onboarding.route
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onPermissionGranted = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.Apps.route) {
            AppsScreen(navController = navController)
        }
        composable(Screen.Goals.route) {
            GoalsScreen(navController = navController)
        }
        composable(Screen.Insights.route) {
            InsightsScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}
