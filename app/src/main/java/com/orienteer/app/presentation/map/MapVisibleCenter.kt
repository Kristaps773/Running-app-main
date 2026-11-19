package com.orienteer.app.presentation.map

import android.graphics.Point
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView

/**
 * Centers [geo] in the **visible** map (above a bottom sheet / dashboard), not the
 * full MapView including the obstructed region.
 */
fun MapView.centerOnVisibleArea(geo: OsmGeoPoint, bottomObstructionPx: Float) {
    controller.setCenter(visibleAreaCenter(geo, bottomObstructionPx))
}

/**
 * Same offset as [centerOnVisibleArea], animated over [durationMs] (OSMDroid map controller).
 */
fun MapView.animateCenterOnVisibleArea(
    geo: OsmGeoPoint,
    bottomObstructionPx: Float,
    durationMs: Long
) {
    val target = visibleAreaCenter(geo, bottomObstructionPx)
    if (durationMs <= 0L) {
        controller.setCenter(target)
        return
    }
    controller.animateTo(target, null, durationMs)
}

private fun MapView.visibleAreaCenter(
    geo: OsmGeoPoint,
    bottomObstructionPx: Float
): OsmGeoPoint {
    if (width <= 0 || height <= 0 || bottomObstructionPx <= 1f) {
        return geo
    }
    val offsetPx = bottomObstructionPx / 2f
    val proj = projection
    val p = Point()
    proj.toPixels(geo, p)
    // Shift the map center downward so [geo] sits in the mid of the uncovered band.
    val shifted = proj.fromPixels(p.x, (p.y + offsetPx).toInt())
    return OsmGeoPoint(shifted.latitude, shifted.longitude)
}
