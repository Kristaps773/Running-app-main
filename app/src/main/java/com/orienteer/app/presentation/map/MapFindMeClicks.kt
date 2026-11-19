package com.orienteer.app.presentation.map

import android.os.SystemClock

/**
 * Find Me: first tap recenters immediately (rotation unchanged).
 * A second tap within [windowMs] also resets the map to north-up.
 */
class MapFindMeClicks(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val onFindMe: () -> Unit,
    private val onResetNorth: () -> Unit
) {
    private var lastPressElapsedMs: Long = 0L

    fun onClick() {
        val now = SystemClock.elapsedRealtime()
        val isDouble = lastPressElapsedMs > 0L && (now - lastPressElapsedMs) <= windowMs
        lastPressElapsedMs = now
        onFindMe()
        if (isDouble) onResetNorth()
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 700L
    }
}
