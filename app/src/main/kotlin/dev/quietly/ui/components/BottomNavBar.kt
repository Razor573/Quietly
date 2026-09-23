package dev.quietly.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.quietly.ui.navigation.Screen

@Composable
fun BottomNavBar(navController: NavController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current   = backStack?.destination?.route

    fun navigateTo(route: String) {
        if (current == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavigationBar {
        NavigationBarItem(
            selected = current == Screen.Dashboard.route,
            onClick  = { navigateTo(Screen.Dashboard.route) },
            icon     = { Icon(Icons.Outlined.Home, "Today") },
            label    = { Text("Today") }
        )
        NavigationBarItem(
            selected = current == Screen.Apps.route,
            onClick  = { navigateTo(Screen.Apps.route) },
            icon     = { Icon(Icons.Outlined.Apps, "Apps") },
            label    = { Text("Apps") }
        )
        NavigationBarItem(
            selected = current == Screen.Goals.route,
            onClick  = { navigateTo(Screen.Goals.route) },
            icon     = { Icon(Icons.Outlined.Flag, "Goals") },
            label    = { Text("Goals") }
        )
        NavigationBarItem(
            selected = current == Screen.Insights.route,
            onClick  = { navigateTo(Screen.Insights.route) },
            icon     = { Icon(Icons.Outlined.Insights, "Insights") },
            label    = { Text("Insights") }
        )
    }
}
