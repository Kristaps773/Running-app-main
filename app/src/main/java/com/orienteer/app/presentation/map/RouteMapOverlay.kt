package com.orienteer.app.presentation.map

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.Log
import android.view.ViewOutlineProvider
import android.graphics.Color as AndroidColor
import com.orienteer.app.R
import com.orienteer.app.data.local.BasemapStyle
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

/**
 * High-contrast OSRM foot-path colors per basemap (stroke + halo).
 * Theme greens blend into OpenTopo/CyclOSM vegetation; magenta air legs stay distinct.
 */
data class WalkingPathPaint(val strokeArgb: Int, val haloArgb: Int)

fun walkingPathPaintForBasemap(style: BasemapStyle): WalkingPathPaint = when (style) {
    BasemapStyle.DARK -> WalkingPathPaint(
        strokeArgb = AndroidColor.parseColor("#FFD54F"),
        haloArgb = AndroidColor.parseColor("#000000")
    )
    BasemapStyle.OPEN_TOPO, BasemapStyle.CYCLOSM -> WalkingPathPaint(
        strokeArgb = AndroidColor.parseColor("#E91E8C"),
        haloArgb = AndroidColor.parseColor("#FFFFFF")
    )
    BasemapStyle.STANDARD, BasemapStyle.PSEUDO_O, BasemapStyle.STREET_O -> WalkingPathPaint(
        strokeArgb = AndroidColor.parseColor("#0D47A1"),
        haloArgb = AndroidColor.parseColor("#FFFFFF")
    )
}

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
    actual: List<GeoPoint>,
    betweenPlannedAndActual: (() -> Unit)? = null
) {
    if (planned.size > 1) {
        val pts = planned.map { OsmGeoPoint(it.latitude, it.longitude) }
        map.overlays.add(
            Polyline(map).apply {
                setPoints(pts)
                outlinePaint.color = AndroidColor.WHITE
                outlinePaint.strokeWidth = 9f
                outlinePaint.isAntiAlias = true
                outlinePaint.strokeCap = Paint.Cap.ROUND
                setOnClickListener { _, _, _ -> false }
            }
        )
        map.overlays.add(
            Polyline(map).apply {
                setPoints(pts)
                outlinePaint.color = ReplayPlannedLineArgb
                outlinePaint.strokeWidth = 5f
                outlinePaint.isAntiAlias = true
                outlinePaint.strokeCap = Paint.Cap.ROUND
                setOnClickListener { _, _, _ -> false }
            }
        )
    }
    betweenPlannedAndActual?.invoke()
    if (actual.size > 1) {
        val pts = actual.map { OsmGeoPoint(it.latitude, it.longitude) }
        map.overlays.add(
            Polyline(map).apply {
                setPoints(pts)
                outlinePaint.color = AndroidColor.BLACK
                outlinePaint.strokeWidth = 11f
                outlinePaint.isAntiAlias = true
                outlinePaint.strokeCap = Paint.Cap.ROUND
                setOnClickListener { _, _, _ -> false }
            }
        )
        map.overlays.add(
            Polyline(map).apply {
                setPoints(pts)
                outlinePaint.color = ReplayActualLineArgb
                outlinePaint.strokeWidth = 7f
                outlinePaint.isAntiAlias = true
                outlinePaint.strokeCap = Paint.Cap.ROUND
                setOnClickListener { _, _, _ -> false }
            }
        )
    }
}

fun addCheckpointMarkers(
    map: MapView,
    checkpoints: List<Checkpoint>,
    primaryColor: Int,
    startColor: Int,
    markerScale: Float = 1f
) {
    val ctx = map.context
    val firstStartIndex = checkpoints.indexOfFirst { it.isStart }
    val lastIndex = checkpoints.lastIndex
    val oneWayDistinct = checkpoints.size >= 2 &&
        checkpoints.first().isStart &&
        checkpoints.last().isStart &&
        (checkpoints.first().position.latitude != checkpoints.last().position.latitude ||
            checkpoints.first().position.longitude != checkpoints.last().position.longitude)
    checkpoints.forEachIndexed { index, checkpoint ->
        val isTrailingFinish =
            checkpoint.isStart && index == lastIndex && index != firstStartIndex
        // Return-to-start: hide duplicate finish badge at same coords.
        // One-way: show finish at a different place.
        if (checkpoint.isStart && index != firstStartIndex && !(oneWayDistinct && isTrailingFinish)) {
            return@forEachIndexed
        }
        val label = when {
            isTrailingFinish && oneWayDistinct -> "F"
            checkpoint.isStart -> "S"
            else -> "${checkpoint.id}"
        }
        val icon = CheckpointMarkerIcon.create(
            context = ctx,
            label = label,
            isStart = checkpoint.isStart,
            primaryColor = primaryColor,
            startColor = startColor,
            scale = markerScale
        )
        map.overlays.add(
            Marker(map).apply {
                position = OsmGeoPoint(checkpoint.position.latitude, checkpoint.position.longitude)
                setIcon(icon)
                infoWindow = null
                setOnMarkerClickListener { _, _ -> true }
                title = when {
                    isTrailingFinish && oneWayDistinct -> "Finish"
                    checkpoint.isStart && oneWayDistinct -> "Start"
                    checkpoint.isStart -> "Start / Finish"
                    else -> "Checkpoint ${checkpoint.id}"
                }
                snippet = checkpoint.description
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
        )
    }
}

/**
 * Optional active-run path lead. When non-null and walking path is shown, draws a directional
 * gradient on [guidancePolyline] (session live path) instead of the solid course polyline.
 * PDF/preview callers omit this and keep the solid path.
 */
data class PathLeadDrawState(
    val guidancePolyline: List<GeoPoint>,
    val progressLocation: GeoPoint?
)

/** Draw route preview overlays: walking path, air legs, distance labels, checkpoint badges. */
fun applyRouteMapOverlays(
    map: MapView,
    route: Route,
    prefs: MapDisplayPreferences,
    mapColors: MapOverlayColors,
    primaryColorArgb: Int,
    startColorArgb: Int,
    basemapStyle: BasemapStyle = prefs.basemapStyle,
    markerScale: Float = 1f,
    pathLead: PathLeadDrawState? = null
) {
    clearRouteOverlays(map)
    val airColor = mapColors.airLeg.toArgb()
    val airHaloColor = mapColors.airLegHalo.toArgb()
    val useIof = prefs.useIofCourseOverprint

    if (prefs.showWalkingPathPolyline) {
        val paint = walkingPathPaintForBasemap(basemapStyle)
        if (pathLead != null && pathLead.guidancePolyline.size >= 2) {
            val progressLoc = pathLead.progressLocation ?: pathLead.guidancePolyline.first()
            val progress = com.orienteer.app.domain.RoutePolylineProgress.project(
                pathLead.guidancePolyline,
                progressLoc
            )
            map.overlays.add(
                DirectionalWalkingPathOverlay(
                    polyline = pathLead.guidancePolyline,
                    progress = progress,
                    paintColors = paint
                )
            )
        } else if (route.polylinePoints.isNotEmpty()) {
            addWalkingPathPolyline(map, route.polylinePoints, paint)
        }
    }

    if (useIof) {
        map.overlays.add(
            IofCourseOverprintOverlay(
                checkpoints = route.checkpoints,
                purpleArgb = airColor,
                showLegs = prefs.showAirLegLines,
                markerScale = markerScale
            )
        )
        if (prefs.showAirLegLines && prefs.showAirLegDistanceLabels) {
            addAirLegDistanceLabels(map, route, airLegSegments(route))
        }
    } else {
        if (prefs.showAirLegLines) {
            val legs = airLegSegments(route)
            map.addOrienteeringAirLegOverlays(legs, airColor, airHaloColor)
            if (prefs.showAirLegDistanceLabels) {
                addAirLegDistanceLabels(map, route, legs)
            }
        }
        addCheckpointMarkers(
            map,
            route.checkpoints,
            primaryColorArgb,
            startColorArgb,
            markerScale = markerScale
        )
    }
    map.invalidate()
}

/** Solid foot path with a contrast halo so it stays readable on topo/dark/standard tiles. */
fun addWalkingPathPolyline(
    map: MapView,
    points: List<GeoPoint>,
    paint: WalkingPathPaint
) {
    if (points.size < 2) return
    val osmPoints = points.map { OsmGeoPoint(it.latitude, it.longitude) }
    map.overlays.add(
        Polyline(map).apply {
            setPoints(osmPoints)
            outlinePaint.color = paint.haloArgb
            outlinePaint.strokeWidth = 12f
            outlinePaint.isAntiAlias = true
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            setOnClickListener { _, _, _ -> false }
        }
    )
    map.overlays.add(
        Polyline(map).apply {
            setPoints(osmPoints)
            outlinePaint.color = paint.strokeArgb
            outlinePaint.strokeWidth = 7f
            outlinePaint.isAntiAlias = true
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            setOnClickListener { _, _, _ -> false }
        }
    )
}

fun airLegSegmentsFromCheckpoints(checkpoints: List<Checkpoint>): List<Pair<GeoPoint, GeoPoint>> {
    if (checkpoints.size < 2) return emptyList()
    val segments = checkpoints.zipWithNext { a, b -> a.position to b.position }.toMutableList()
    val hasTrailingFinish = checkpoints.last().isStart && checkpoints.count { it.isStart } >= 2
    // Closing leg only for older routes that omit an explicit finish checkpoint.
    if (!hasTrailingFinish) {
        segments.add(checkpoints.last().position to checkpoints.first().position)
    }
    return segments
}

private fun airLegSegments(route: Route): List<Pair<GeoPoint, GeoPoint>> =
    airLegSegmentsFromCheckpoints(route.checkpoints)

/** Dashed magenta/purple straight legs with a light/dark halo for basemap contrast. */
fun MapView.addOrienteeringAirLegOverlays(
    legs: List<Pair<GeoPoint, GeoPoint>>,
    airColor: Int,
    haloColor: Int
) {
    legs.forEach { (from, to) ->
        val points = listOf(
            OsmGeoPoint(from.latitude, from.longitude),
            OsmGeoPoint(to.latitude, to.longitude)
        )
        overlays.add(
            Polyline(this).apply {
                setPoints(points)
                outlinePaint.color = haloColor
                outlinePaint.strokeWidth = 11f
                outlinePaint.isAntiAlias = true
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
        )
        overlays.add(
            Polyline(this).apply {
                setPoints(points)
                outlinePaint.color = airColor
                outlinePaint.strokeWidth = 5.5f
                outlinePaint.isAntiAlias = true
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.pathEffect = DashPathEffect(floatArrayOf(22f, 14f), 0f)
            }
        )
    }
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
