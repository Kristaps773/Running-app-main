package com.orienteer.app.presentation.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.orienteer.app.presentation.history.HistoryReplayMapScreen
import com.orienteer.app.presentation.history.HistoryScreen
import com.orienteer.app.presentation.map.MapScreen
import com.orienteer.app.presentation.run.RunScreen
import com.orienteer.app.presentation.settings.SettingsScreen
import com.orienteer.app.presentation.setup.SetupScreen
import com.orienteer.app.presentation.setup.SetupViewModel

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Map : Screen("map/{routeId}") {
        fun createRoute(routeId: String) = "map/$routeId"
    }
    data object Run : Screen("run/{routeId}") {
        fun createRoute(routeId: String) = "run/$routeId"
    }
    data object RunMap : Screen("run/{routeId}/map") {
        fun createRoute(routeId: String) = "run/$routeId/map"
    }
    data object History : Screen("history")
    data object HistoryMap : Screen("history/{sessionId}/map") {
        fun createRoute(sessionId: Long) = "history/$sessionId/map"
    }
    data object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit
) {
    val navController = rememberNavController()
    val activeRunVm: ActiveRunBarViewModel = hiltViewModel()
    val activeRouteId by activeRunVm.activeRouteId.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val hideActiveBanner = currentRoute.startsWith("run/") &&
        navBackStackEntry?.arguments?.getString("routeId") == activeRouteId
    val showActiveBanner = activeRouteId != null && !hideActiveBanner

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Setup.route
        ) {
            composable(Screen.Setup.route) {
                SetupScreen(
                    onRouteReady = { route ->
                        navController.navigate(Screen.Map.createRoute(route.id))
                    },
                    onViewPreviousRoute = { routeId ->
                        navController.navigate(Screen.Map.createRoute(routeId))
                    },
                    onOpenHistory = { navController.navigate(Screen.History.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = onRequestLocationPermission
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = Screen.Map.route,
                arguments = listOf(navArgument("routeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
                val setupEntry = remember(navController) {
                    runCatching { navController.getBackStackEntry(Screen.Setup.route) }.getOrNull()
                }
                val setupViewModel: SetupViewModel? = setupEntry?.let { hiltViewModel(it) }
                MapScreen(
                    routeId = routeId,
                    setupViewModel = setupViewModel,
                    onStartRun = { id ->
                        navController.navigate(Screen.Run.createRoute(id))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Run.route,
                arguments = listOf(navArgument("routeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
                RunScreen(
                    routeId = routeId,
                    onRunComplete = {
                        navController.navigate(Screen.Setup.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = {
                        navController.navigate(Screen.Setup.route) {
                            popUpTo(Screen.Setup.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onRunStopped = {
                        navController.navigate(Screen.History.route) {
                            popUpTo(Screen.Setup.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.RunMap.route,
                arguments = listOf(navArgument("routeId") { type = NavType.StringType })
            ) { backStackEntry ->
                // Map is primary on RunScreen; deep link pops back to the run.
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenFullMap = { sessionId ->
                        navController.navigate(Screen.HistoryMap.createRoute(sessionId))
                    }
                )
            }

            composable(
                route = Screen.HistoryMap.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                HistoryReplayMapScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        if (showActiveBanner && activeRouteId != null) {
            val routeIdForActiveRun = activeRouteId!!
            ActiveRunBanner(
                onOpen = {
                    navController.navigate(Screen.Run.createRoute(routeIdForActiveRun)) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ActiveRunBanner(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Run in progress — tap to return",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            TextButton(onClick = onOpen) {
                Text("Open run")
            }
        }
    }
}
