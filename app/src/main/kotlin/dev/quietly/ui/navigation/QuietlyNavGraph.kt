package dev.quietly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.quietly.ui.apps.AppsScreen
import dev.quietly.ui.appdetail.AppDetailScreen
import dev.quietly.ui.dashboard.DashboardScreen
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
    object AppDetail  : Screen("app_detail/{packageName}") {
        fun withArg(pkg: String) = "app_detail/$pkg"
    }
}

@Composable
fun QuietlyNavGraph(startDestination: String) {
    val nav: NavHostController = rememberNavController()
    NavHost(navController = nav, startDestination = startDestination) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onPermissionGranted = {
                    nav.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route)  { DashboardScreen(nav) }
        composable(Screen.Apps.route)       { AppsScreen(nav) }
        composable(Screen.Goals.route)      { GoalsScreen(nav) }
        composable(Screen.Insights.route)   { InsightsScreen(nav) }
        composable(Screen.Settings.route)   { SettingsScreen(nav) }

        composable(Screen.AppDetail.route)  { back ->
            val pkg = back.arguments?.getString("packageName") ?: return@composable
            AppDetailScreen(pkg = pkg, nav = nav)
        }
    }
}
