package dev.quietly.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.quietly.ui.navigation.Screen

@Composable
fun BottomNavBar(navController: NavController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current   = backStack?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = current == Screen.Dashboard.route,
            onClick  = { navController.navigate(Screen.Dashboard.route) },
            icon     = { Icon(Icons.Outlined.Home, null) },
            label    = { Text("Today") }
        )
        NavigationBarItem(
            selected = current == Screen.Apps.route,
            onClick  = { navController.navigate(Screen.Apps.route) },
            icon     = { Icon(Icons.Outlined.Apps, null) },
            label    = { Text("Apps") }
        )
        NavigationBarItem(
            selected = current == Screen.Goals.route,
            onClick  = { navController.navigate(Screen.Goals.route) },
            icon     = { Icon(Icons.Outlined.Flag, null) },
            label    = { Text("Goals") }
        )
        NavigationBarItem(
            selected = current == Screen.Insights.route,
            onClick  = { navController.navigate(Screen.Insights.route) },
            icon     = { Icon(Icons.Outlined.Insights, null) },
            label    = { Text("Insights") }
        )
    }
}
