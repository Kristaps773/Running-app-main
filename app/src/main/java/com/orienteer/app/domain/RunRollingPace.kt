package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils

object RunRollingPace {
    const val WINDOW_MS = 5L * 60_000L
    const val REFRESH_MS = 10_000L

    /**
     * Pace over GPS crumbs in the last [WINDOW_MS].
     * Returns null if lists mismatch, <2 points in window, or gates fail.
     */
    fun paceSecPerKm(
        points: List<GeoPoint>,
        timestampsMs: List<Long>,
        nowMs: Long,
        windowMs: Long = WINDOW_MS
    ): Double? {
        if (points.size != timestampsMs.size || points.size < 2) return null
        val cutoff = nowMs - windowMs
        var startIdx = 0
        while (startIdx < timestampsMs.size && timestampsMs[startIdx] < cutoff) {
            startIdx++
        }
        // Need the point just before the window so the first segment into the window counts.
        if (startIdx > 0) startIdx--
        if (timestampsMs.lastIndex - startIdx < 1) return null

        var distanceM = 0.0
        for (i in startIdx until points.lastIndex) {
            distanceM += GeoUtils.distanceMeters(points[i], points[i + 1])
        }
        val elapsedMs = (timestampsMs.last() - timestampsMs[startIdx]).coerceAtLeast(0L)
        return GeoUtils.paceSecPerKm(distanceM, elapsedMs)
    }

    /**
     * Prefer rolling window; else whole-run average. Formatted "mm:ss /km" or "--:-- /km".
     */
    fun formatDisplayPace(
        points: List<GeoPoint>,
        timestampsMs: List<Long>,
        nowMs: Long,
        totalDistanceM: Double,
        totalElapsedMs: Long
    ): String {
        val rolling = paceSecPerKm(points, timestampsMs, nowMs)
        if (rolling != null) return GeoUtils.formatPace(rolling)
        return GeoUtils.formatRunPace(totalDistanceM, totalElapsedMs)
    }
}
