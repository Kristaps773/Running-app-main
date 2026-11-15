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
import com.orienteer.app.debug.ScreenshotDemoMode
import com.orienteer.app.debug.ScreenshotDemoSeeder
import com.orienteer.app.presentation.navigation.AppNavigation
import com.orienteer.app.ui.theme.OrienteerThemeRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var screenshotDemoSeeder: ScreenshotDemoSeeder

    private var hasLocationPermission by mutableStateOf(true)
    private var openActiveRunRouteId by mutableStateOf<String?>(null)
    private var screenshotScene by mutableStateOf<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            hasLocationPermission =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                        hasAnyLocationPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyScreenshotExtras(intent)

        val startAtHistory = BuildConfig.DEBUG && (
            intent?.getBooleanExtra(EXTRA_OPEN_HISTORY, false) == true ||
                screenshotScene.equals("history", ignoreCase = true)
        )
        openActiveRunRouteId = intent.routeIdForActiveRun()
            ?: screenshotScene.takeIf { it.equals("run", ignoreCase = true) }
                ?.let { ScreenshotDemoMode.ROUTE_ID }

        // Skip splash when jumping to a capture scene — faster first frame on emulator.
        if (!startAtHistory && screenshotScene.isNullOrBlank()) {
            installSplashScreen()
        }
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG && ScreenshotDemoMode.seedRequested) {
            runBlocking(Dispatchers.IO) {
                screenshotDemoSeeder.seed()
            }
        }

        hasLocationPermission = hasAnyLocationPermission()

        if (!startAtHistory && screenshotScene.isNullOrBlank()) {
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
        } else if (BuildConfig.DEBUG && screenshotScene != null) {
            // Capture scenes: do not block on permission dialogs.
            hasLocationPermission = true
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
                        screenshotScene = screenshotScene,
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
        applyScreenshotExtras(intent)
        openActiveRunRouteId = intent.routeIdForActiveRun()
            ?: screenshotScene.takeIf { it.equals("run", ignoreCase = true) }
                ?.let { ScreenshotDemoMode.ROUTE_ID }
    }

    private fun applyScreenshotExtras(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) {
            ScreenshotDemoMode.seedRequested = false
            ScreenshotDemoMode.scene = null
            screenshotScene = null
            return
        }
        ScreenshotDemoMode.seedRequested =
            intent.getBooleanExtra(EXTRA_SEED_SCREENSHOT_DEMO, false)
        val scene = intent.getStringExtra(EXTRA_SCREENSHOT_SCENE)?.trim()?.lowercase()
            ?.takeIf { it in ScreenshotDemoMode.knownScenes }
        ScreenshotDemoMode.scene = scene
        screenshotScene = scene
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
        /** Debug: seed Berlin demo route + fake history before UI. */
        const val EXTRA_SEED_SCREENSHOT_DEMO = "com.orienteer.app.EXTRA_SEED_SCREENSHOT_DEMO"
        /** Debug: setup | map | run | history | settings | favorites */
        const val EXTRA_SCREENSHOT_SCENE = "com.orienteer.app.EXTRA_SCREENSHOT_SCENE"
    }
}
