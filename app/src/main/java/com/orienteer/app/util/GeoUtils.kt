package com.orienteer.app.util

import com.orienteer.app.data.model.GeoPoint
import kotlin.math.*

object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Haversine distance between two coordinates, in metres.
     */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)

        val sinDLat = sin(dLat / 2)
        val sinDLng = sin(dLng / 2)
        val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLng * sinDLng
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /**
     * Offset [origin] by [distanceM] metres in the given [bearingDeg] (0 = North, clockwise).
     */
    fun offsetPoint(origin: GeoPoint, distanceM: Double, bearingDeg: Double): GeoPoint {
        val d = distanceM / EARTH_RADIUS_M
        val bearingRad = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(origin.latitude)
        val lng1 = Math.toRadians(origin.longitude)

        val lat2 = asin(
            sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(bearingRad)
        )
        val lng2 = lng1 + atan2(
            sin(bearingRad) * sin(d) * cos(lat1),
            cos(d) - sin(lat1) * sin(lat2)
        )

        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lng2))
    }

    /** Bearing from [from] to [to] in degrees (0 = north, clockwise). */
    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Format metres as a readable distance string (e.g. "4.8 km" or "850 m").
     */
    fun formatDistance(meters: Double): String = when {
        meters >= 1000 -> "%.1f km".format(meters / 1000.0)
        else -> "%.0f m".format(meters)
    }

    /** Two decimal km for route preview where air vs path can differ by &lt;100 m. */
    fun formatDistanceDetailed(meters: Double): String = when {
        meters >= 1000 -> "%.2f km".format(meters / 1000.0)
        else -> "%.0f m".format(meters)
    }

    /**
     * Format seconds-per-km pace as "mm:ss /km" (minutes capped at 59 for display).
     */
    fun formatPace(secPerKm: Double): String {
        val total = secPerKm.coerceIn(0.0, 3599.0).toInt()
        return "%d:%02d /km".format(total / 60, total % 60)
    }

    /** Minimum metres travelled before showing a meaningful pace. */
    const val MIN_PACE_DISTANCE_M = 50.0

    /** Above this pace (sec/km) the runner is effectively stationary — hide pace. */
    const val MAX_REASONABLE_PACE_SEC_PER_KM = 1_200.0

    fun paceSecPerKm(distanceM: Double, elapsedMs: Long): Double? {
        if (distanceM < MIN_PACE_DISTANCE_M || elapsedMs <= 0L) return null
        val secPerKm = (elapsedMs / 1000.0) / (distanceM / 1000.0)
        if (!secPerKm.isFinite() || secPerKm > MAX_REASONABLE_PACE_SEC_PER_KM) return null
        return secPerKm
    }

    fun formatRunPace(distanceM: Double, elapsedMs: Long): String =
        paceSecPerKm(distanceM, elapsedMs)?.let { formatPace(it) } ?: "--:-- /km"

    /**
     * Format elapsed milliseconds as "HH:MM:SS".
     */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000L
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    /**
     * Build an OSRM coordinates path string from a list of points.
     * Format: "lng1,lat1;lng2,lat2;..."
     */
    fun toOsrmCoordinates(points: List<GeoPoint>): String =
        points.joinToString(";") { "%.6f,%.6f".format(it.longitude, it.latitude) }

    /**
     * Build an OSRM single coordinate string.
     */
    fun toOsrmCoordinate(point: GeoPoint): String =
        "%.6f,%.6f".format(point.longitude, point.latitude)
}
