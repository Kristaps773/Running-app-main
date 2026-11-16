package com.orienteer.app.debug

/**
 * Debug-only flags for Play Store screenshot capture.
 * Set from [com.orienteer.app.MainActivity] before Compose starts.
 */
object ScreenshotDemoMode {
    const val ROUTE_ID = "demo-store-route"

    @Volatile
    var seedRequested: Boolean = false

    /** One of: setup, map, map_pdf, run, history, settings, favorites — or null for normal launch. */
    @Volatile
    var scene: String? = null

    val useFakeActiveRun: Boolean
        get() = scene.equals("run", ignoreCase = true)

    /** Collapse route/run bottom sheets so the map fills the store screenshot. */
    val collapseSheetsForCapture: Boolean
        get() = scene.equals("map", ignoreCase = true) ||
            scene.equals("run", ignoreCase = true)

    val knownScenes: Set<String> =
        setOf("setup", "map", "map_pdf", "run", "history", "settings", "favorites")
}
