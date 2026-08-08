package com.orienteer.app.presentation.map

import android.util.Log
import android.view.ViewOutlineProvider
import com.orienteer.app.R
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.model.Route
import com.orienteer.app.ui.theme.MapOverlayColors
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import androidx.compose.ui.graphics.toArgb
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Planned (OSRM) path — Stitch history mockup gray dashed line. */
val ReplayPlannedLineArgb = android.graphics.Color.parseColor("#87948B")

/** Actual GPS path — Stitch primary mint track. */
val ReplayActualLineArgb = android.graphics.Color.parseColor("#68DBA9")

fun boundingBoxForPoints(points: List<GeoPoint>): BoundingBox? {
    if (points.isEmpty()) return null
    var minLat = points.first().latitude
    var maxLat = minLat
    var minLon = points.first().longitude
    var maxLon = minLon
    points.forEach { p ->
        minLat = minOf(minLat, p.latitude)
        maxLat = maxOf(maxLat, p.latitude)
        minLon = minOf(minLon, p.longitude)
        maxLon = maxOf(maxLon, p.longitude)
    }
    val padLat = (maxLat - minLat).coerceAtLeast(0.0005) * 0.12
    val padLon = (maxLon - minLon).coerceAtLeast(0.0005) * 0.12
    return BoundingBox(maxLat + padLat, maxLon + padLon, minLat - padLat, minLon - padLon)
}

fun boundingBoxForReplay(
    planned: List<GeoPoint>,
    actual: List<GeoPoint>,
    checkpoints: List<Checkpoint> = emptyList()
): BoundingBox? {
    val all = ArrayList<GeoPoint>(planned.size + actual.size + checkpoints.size)
    all.addAll(planned)
    all.addAll(actual)
    checkpoints.forEach { all.add(it.position) }
    return boundingBoxForPoints(all)
}

/** Keep tiles and panning inside the route area so zoom does not spill past the card edges. */
fun applyMapViewClipping(map: MapView) {
    map.clipToOutline = true
    map.outlineProvider = ViewOutlineProvider.BACKGROUND
    map.isHorizontalMapRepetitionEnabled = false
    map.isVerticalMapRepetitionEnabled = false
}

fun applyScrollLimits(map: MapView, box: BoundingBox?) {
    if (box == null) return
    map.setTag(R.id.map_tag_replay_bounds, box)
    map.setScrollableAreaLimitDouble(box)
    clampMapCenterToBox(map, box)
}

fun clampMapCenterToBox(map: MapView, box: BoundingBox) {
    val center = map.mapCenter ?: return
    val lat = center.latitude.coerceIn(box.latSouth, box.latNorth)
    val lon = center.longitude.coerceIn(box.lonWest, box.lonEast)
    if (lat != center.latitude || lon != center.longitude) {
        map.controller.setCenter(OsmGeoPoint(lat, lon))
    }
}

fun isCompactReplayPreview(map: MapView): Boolean =
    map.getTag(R.id.map_tag_compact_preview) == true

fun attachScrollLimitListener(map: MapView) {
    if (map.getTag(R.id.map_tag_replay_listener) != null) return
    map.addMapListener(object : org.osmdroid.events.MapListener {
        override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
            (map.getTag(R.id.map_tag_replay_bounds) as? BoundingBox)?.let {
                applyScrollLimits(map, it)
            }
            return false
        }

        override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
            (map.getTag(R.id.map_tag_replay_bounds) as? BoundingBox)?.let {
                applyScrollLimits(map, it)
            }
            return false
        }
    })
    map.setTag(R.id.map_tag_replay_listener, true)
}

fun fitMapToBoundingBox(map: MapView, box: BoundingBox?, paddingPx: Int) {
    if (box == null) return
    map.post {
        if (map.width <= 0 || map.height <= 0) {
            map.post { fitMapToBoundingBox(map, box, paddingPx) }
            return@post
        }
        map.zoomToBoundingBox(box, false, paddingPx)
        val fittedZoom = map.zoomLevelDouble
        if (isCompactReplayPreview(map)) {
            val maxTileZoom = map.tileProvider.maximumZoomLevel.toDouble()
            val minTileZoom = map.tileProvider.minimumZoomLevel.toDouble()
            map.setMaxZoomLevel((fittedZoom + 2.0).coerceAtMost(maxTileZoom))
            map.setMinZoomLevel((fittedZoom - 1.0).coerceAtLeast(minTileZoom))
        }
        applyScrollLimits(map, box)
        map.invalidate()
        Log.d(
            "OrienteerMap",
            "fit bbox zoom=${map.zoomLevelDouble} compact=${isCompactReplayPreview(map)} " +
                "size=${map.width}x${map.height}"
        )
    }
}

fun addReplayPolylines(
    map: MapView,
    planned: List<GeoPoint>,
    actual: List<GeoPoint>
) {
    if (planned.size > 1) {
        map.overlays.add(
            Polyline(map).apply {
                setPoints(planned.map { OsmGeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = ReplayPlannedLineArgb
                outlinePaint.strokeWidth = 5f
                outlinePaint.isAntiAlias = true
                setOnClickListener { _, _, _ -> false }
            }
        )
    }
    if (actual.size > 1) {
        map.overlays.add(
            Polyline(map).apply {
                setPoints(actual.map { OsmGeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = ReplayActualLineArgb
                outlinePaint.strokeWidth = 7f
                outlinePaint.isAntiAlias = true
                setOnClickListener { _, _, _ -> false }
            }
        )
    }
}

fun addCheckpointMarkers(
    map: MapView,
    checkpoints: List<Checkpoint>,
    primaryColor: Int,
    startColor: Int
) {
    val ctx = map.context
    checkpoints.forEach { checkpoint ->
        val label = if (checkpoint.isStart) "S" else "${checkpoint.id}"
        val icon = CheckpointMarkerIcon.create(
            context = ctx,
            label = label,
            isStart = checkpoint.isStart,
            primaryColor = primaryColor,
            startColor = startColor
        )
        map.overlays.add(
            Marker(map).apply {
                position = OsmGeoPoint(checkpoint.position.latitude, checkpoint.position.longitude)
                setIcon(icon)
                infoWindow = null
                setOnMarkerClickListener { _, _ -> true }
                title = if (checkpoint.isStart) "Start / Finish" else "Checkpoint ${checkpoint.id}"
                snippet = checkpoint.description
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
        )
    }
}

/** Draw route preview overlays: walking path, air legs, distance labels, checkpoint badges. */
fun applyRouteMapOverlays(
    map: MapView,
    route: Route,
    prefs: MapDisplayPreferences,
    mapColors: MapOverlayColors,
    primaryColorArgb: Int,
    startColorArgb: Int
) {
    map.overlays.clear()
    val routeColor = mapColors.walkingPath.toArgb()
    val airColor = mapColors.airLeg.toArgb()

    if (prefs.showWalkingPathPolyline && route.polylinePoints.isNotEmpty()) {
        map.overlays.add(
            Polyline(map).apply {
                setPoints(route.polylinePoints.map { OsmGeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = routeColor
                outlinePaint.strokeWidth = 8f
                outlinePaint.alpha = 220
            }
        )
    }

    if (prefs.showAirLegLines) {
        val legs = airLegSegments(route)
        legs.forEach { (from, to) ->
            map.overlays.add(
                Polyline(map).apply {
                    setPoints(
                        listOf(
                            OsmGeoPoint(from.latitude, from.longitude),
                            OsmGeoPoint(to.latitude, to.longitude)
                        )
                    )
                    outlinePaint.color = airColor
                    outlinePaint.strokeWidth = 3f
                    outlinePaint.alpha = 160
                }
            )
        }
        if (prefs.showAirLegDistanceLabels) {
            addAirLegDistanceLabels(map, route, legs)
        }
    }

    addCheckpointMarkers(map, route.checkpoints, primaryColorArgb, startColorArgb)
    map.invalidate()
}

private fun airLegSegments(route: Route): List<Pair<GeoPoint, GeoPoint>> {
    val cps = route.checkpoints
    if (cps.size < 2) return emptyList()
    val segments = cps.zipWithNext { a, b -> a.position to b.position }.toMutableList()
    segments.add(cps.last().position to cps.first().position)
    return segments
}

private fun addAirLegDistanceLabels(
    map: MapView,
    route: Route,
    legs: List<Pair<GeoPoint, GeoPoint>>
) {
    val ctx = map.context
    legs.forEachIndexed { index, (from, to) ->
        val distanceM = when {
            index < route.checkpoints.size - 1 -> {
                route.checkpoints.getOrNull(index + 1)?.distanceFromPrev
                    ?: GeoUtils.distanceMeters(from, to)
            }
            else -> GeoUtils.distanceMeters(from, to)
        }
        if (distanceM <= 0.0) return@forEachIndexed
        val midLat = (from.latitude + to.latitude) / 2.0
        val midLon = (from.longitude + to.longitude) / 2.0
        val label = GeoUtils.formatDistance(distanceM)
        val icon = AirLegLabelIcon.create(ctx, label)
        map.overlays.add(
            Marker(map).apply {
                position = OsmGeoPoint(midLat, midLon)
                setIcon(icon)
                infoWindow = null
                setOnMarkerClickListener { _, _ -> true }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
        )
    }
}
