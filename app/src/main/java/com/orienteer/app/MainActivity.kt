package com.orienteer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.orienteer.app.presentation.navigation.AppNavigation
import com.orienteer.app.ui.theme.OrienteerThemeRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var hasLocationPermission by mutableStateOf(true)
    private var openActiveRunRouteId by mutableStateOf<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            hasLocationPermission =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                        hasAnyLocationPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Debug-only: open History directly for Play Store screenshot capture.
        val startAtHistory = BuildConfig.DEBUG &&
            intent?.getBooleanExtra(EXTRA_OPEN_HISTORY, false) == true
        openActiveRunRouteId = intent.routeIdForActiveRun()
        // Skip splash when jumping straight to History — keeps the frame drawable sooner
        // under low-RAM emulator conditions used for store screenshots.
        if (!startAtHistory) {
            installSplashScreen()
        }
        super.onCreate(savedInstanceState)
        hasLocationPermission = hasAnyLocationPermission()

        if (!startAtHistory) {
            val needed = buildList {
                if (!hasLocationPermission) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
            }
        }

        setContent {
            OrienteerThemeRoot {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        hasLocationPermission = hasLocationPermission,
                        onRequestLocationPermission = {
                            val perms = buildList {
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                        startAtHistory = startAtHistory,
                        openActiveRunRouteId = openActiveRunRouteId,
                        onConsumedOpenActiveRun = { openActiveRunRouteId = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openActiveRunRouteId = intent.routeIdForActiveRun()
    }

    private fun Intent?.routeIdForActiveRun(): String? =
        this?.getStringExtra(EXTRA_OPEN_ACTIVE_RUN_ROUTE_ID)?.takeIf { it.isNotBlank() }

    private fun hasAnyLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        /** Debug builds only — used to capture Run History Play Store screenshots. */
        const val EXTRA_OPEN_HISTORY = "com.orienteer.app.EXTRA_OPEN_HISTORY"
        /** Opens the Run screen for this route id (from the active-run notification). */
        const val EXTRA_OPEN_ACTIVE_RUN_ROUTE_ID = "com.orienteer.app.EXTRA_OPEN_ACTIVE_RUN_ROUTE_ID"
    }
}
