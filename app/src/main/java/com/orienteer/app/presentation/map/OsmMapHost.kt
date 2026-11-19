package com.orienteer.app.presentation.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orienteer.app.R
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.ui.theme.mapOverlayColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Title used for the GPS “you are here” marker — hidden during PDF export. */
internal const val LOCATION_MARKER_TITLE = "__user_location__"

/** Temporarily remove the user-location marker (for PDF capture). Returns it for restore. */
fun MapView.detachLocationMarker(): Marker? {
    SmoothLocationMarker.cancel(this)
    val existing = overlays
        .filterIsInstance<Marker>()
        .firstOrNull { it.title == LOCATION_MARKER_TITLE }
        ?: return null
    overlays.remove(existing)
    return existing
}

fun MapView.reattachLocationMarker(marker: Marker?) {
    if (marker == null) return
    if (overlays.none { it === marker }) {
        overlays.add(marker)
    }
}

/** Stable fingerprint — overlay refresh without recreating the MapView. */
internal fun routeOverlayFingerprint(
    route: Route,
    prefs: MapDisplayPreferences,
    basemapStyle: BasemapStyle = prefs.basemapStyle,
    pathLeadActive: Boolean = false,
    guidancePointCount: Int = 0,
    nextCheckpointIdx: Int = -1
): String = buildString {
    append(route.id)
    append('|')
    append(route.polylinePoints.size)
    append('|')
    append(route.checkpoints.size)
    append('|')
    append(route.actualPathDistanceM.toLong())
    append('|')
    append(route.isDraft)
    append('|')
    append(prefs.showAirLegLines)
    append('|')
    append(prefs.showAirLegDistanceLabels)
    append('|')
    append(prefs.showWalkingPathPolyline)
    append('|')
    append(prefs.showNorthLines)
    append('|')
    append(prefs.useIofCourseOverprint)
    append('|')
    append(basemapStyle.name)
    append('|')
    // Lead geometry refreshes every GPS fix via [updatePathLeadOverlay]; fingerprint
    // only tracks lead on/off + guidance identity (not runner progress).
    append(if (pathLeadActive) 1 else 0)
    append('|')
    append(guidancePointCount)
    append('|')
    append(nextCheckpointIdx)
}

/**
 * Lifecycle-aware OSMDroid host. The MapView is recreated only when [route.id] changes;
 * route geometry / prefs updates refresh overlays in-place (preserves pan & zoom).
 */
@Composable
fun OsmMapHost(
    route: Route,
    modifier: Modifier = Modifier,
    currentLocation: GeoPoint? = null,
    mapPrefs: MapDisplayPreferences = MapDisplayPreferences(),
    basemapStyle: BasemapStyle = BasemapStyle.STANDARD,
    interactive: Boolean = true,
    initialZoom: Double = 15.0,
    /** When set, draw directional gradient on this polyline (active-run lead). PDF omits. */
    pathLead: PathLeadDrawState? = null,
    /** Follow while locked; pans only (does not change map rotation). */
    followCamera: Boolean = false,
    /** Camera follow target; defaults to [currentLocation] when null. */
    followLocation: GeoPoint? = null,
    mapBearingDeg: Float = 0f,
    /** Bottom sheet / dashboard height so follow centers in the visible map band. */
    followBottomInsetPx: Float = 0f,
    onUserMapGesture: () -> Unit = {},
    onMapReady: (MapView) -> Unit = {}
) {
    val cameraFollowPoint = followLocation ?: currentLocation
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val mapColors = mapOverlayColors()
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val startArgb = MaterialTheme.colorScheme.tertiary.toArgb()
    val gestureHolder = remember { object { var onGesture: () -> Unit = {} } }
    gestureHolder.onGesture = onUserMapGesture
    val overlayFingerprint = remember(
        route,
        mapPrefs,
        basemapStyle,
        pathLead != null,
        pathLead?.guidancePolyline?.size,
        pathLead?.nextCheckpointIdx
    ) {
        routeOverlayFingerprint(
            route,
            mapPrefs,
            basemapStyle,
            pathLeadActive = pathLead != null && (pathLead.guidancePolyline.size >= 2),
            guidancePointCount = pathLead?.guidancePolyline?.size ?: 0,
            nextCheckpointIdx = pathLead?.nextCheckpointIdx ?: -1
        )
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onPause()
        }
    }

    key(route.id, interactive) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                MapView(ctx).apply {
                    applyBasemapStyle(this, ctx, basemapStyle)
                    setTag(R.id.map_tag_basemap_style, basemapStyle.name)
                    setTag(R.id.map_tag_route_overlay_key, overlayFingerprint)
                    disableBuiltInZoomButtons()
                    setMultiTouchControls(interactive)
                    isClickable = interactive
                    isFocusable = interactive
                    if (interactive) {
                        enableRotationGestures()
                    }
                    controller.setZoom(initialZoom)
                    controller.setCenter(
                        OsmGeoPoint(route.startPoint.latitude, route.startPoint.longitude)
                    )
                    onResume()
                    mapView = this
                    onMapReady(this)
                    if (getTag(R.id.map_tag_run_gesture_listener) == null) {
                        addMapListener(object : org.osmdroid.events.MapListener {
                            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                if (getTag(R.id.map_tag_suppress_gesture) != true) {
                                    gestureHolder.onGesture()
                                }
                                return false
                            }

                            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                if (getTag(R.id.map_tag_suppress_gesture) != true) {
                                    gestureHolder.onGesture()
                                }
                                return false
                            }
                        })
                        setTag(R.id.map_tag_run_gesture_listener, true)
                    }
                    post {
                        applyRouteMapOverlays(
                            map = this,
                            route = route,
                            prefs = mapPrefs,
                            mapColors = mapColors,
                            primaryColorArgb = primaryArgb,
                            startColorArgb = startArgb,
                            basemapStyle = basemapStyle,
                            pathLead = pathLead
                        )
                        SmoothLocationMarker.apply(this, currentLocation)
                    }
                }
            },
            update = { view ->
                mapView = view

                val lastBasemap = view.getTag(R.id.map_tag_basemap_style) as? String
                if (lastBasemap != basemapStyle.name) {
                    applyBasemapStyle(view, view.context, basemapStyle)
                    view.setTag(R.id.map_tag_basemap_style, basemapStyle.name)
                }

                val lastOverlayKey = view.getTag(R.id.map_tag_route_overlay_key) as? String
                if (lastOverlayKey != overlayFingerprint) {
                    view.setTag(R.id.map_tag_route_overlay_key, overlayFingerprint)
                    applyRouteMapOverlays(
                        map = view,
                        route = route,
                        prefs = mapPrefs,
                        mapColors = mapColors,
                        primaryColorArgb = primaryArgb,
                        startColorArgb = startArgb,
                        basemapStyle = basemapStyle,
                        pathLead = pathLead
                    )
                    val loc = pathLead?.progressLocation
                    view.setTag(
                        R.id.map_tag_path_lead_key,
                        if (pathLead == null) null
                        else "${pathLead.nextCheckpointIdx}|${pathLead.guidancePolyline.size}|" +
                            (loc?.let { "${it.latitude},${it.longitude}" } ?: "none")
                    )
                } else if (pathLead != null) {
                    // Refresh lead on every GPS fix (not the old 70 m progress bucket).
                    val loc = pathLead.progressLocation
                    val leadKey = buildString {
                        append(pathLead.nextCheckpointIdx)
                        append('|')
                        append(pathLead.guidancePolyline.size)
                        append('|')
                        if (loc == null) append("none") else {
                            append(loc.latitude)
                            append(',')
                            append(loc.longitude)
                        }
                    }
                    val lastLeadKey = view.getTag(R.id.map_tag_path_lead_key) as? String
                    if (lastLeadKey != leadKey) {
                        view.setTag(R.id.map_tag_path_lead_key, leadKey)
                        updatePathLeadOverlay(view, pathLead, basemapStyle)
                        // #region agent log
                        com.orienteer.app.debug.AgentDebugLog.log(
                            hypothesisId = "F",
                            location = "OsmMapHost.update.pathLead",
                            message = "lead refreshed with location",
                            data = mapOf(
                                "leadKey" to leadKey,
                                "guidancePts" to pathLead.guidancePolyline.size,
                                "cpIdx" to pathLead.nextCheckpointIdx
                            ),
                            runId = "post-fix"
                        )
                        // #endregion
                    }
                }

                SmoothLocationMarker.apply(view, currentLocation)

                if (followCamera && cameraFollowPoint != null) {
                    val followKey =
                        "${cameraFollowPoint.latitude},${cameraFollowPoint.longitude}"
                    val lastFollow = view.getTag(R.id.map_tag_follow_target) as? String
                    if (lastFollow != followKey) {
                        view.setTag(R.id.map_tag_follow_target, followKey)
                        view.setTag(R.id.map_tag_suppress_gesture, true)
                        // Same target + duration as the puck glide so camera tracks with it.
                        view.animateCenterOnVisibleArea(
                            OsmGeoPoint(
                                cameraFollowPoint.latitude,
                                cameraFollowPoint.longitude
                            ),
                            followBottomInsetPx,
                            SmoothLocationMarker.ANIM_MS
                        )
                        view.postDelayed({
                            view.setTag(R.id.map_tag_suppress_gesture, false)
                        }, SmoothLocationMarker.ANIM_MS)
                    }
                } else {
                    view.setTag(R.id.map_tag_follow_target, null)
                }
            }
        )
    }
}

@Composable
fun OsmSimpleMapHost(
    route: Route,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    initialZoom: Double = 14.0
) {
    OsmMapHost(
        route = route,
        modifier = modifier,
        currentLocation = null,
        mapPrefs = MapDisplayPreferences(
            showAirLegLines = true,
            showAirLegDistanceLabels = false,
            showWalkingPathPolyline = false
        ),
        interactive = interactive,
        initialZoom = initialZoom
    )
}


/** Replay map: planned air legs (dashed) + actual walking path (solid). */
@Composable
fun OsmReplayMapHost(
    route: Route,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val mapColors = mapOverlayColors()
    val airColor = mapColors.airLeg.toArgb()
    val airHaloColor = mapColors.airLegHalo.toArgb()

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onPause()
        }
    }

    key(route.id) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    disableBuiltInZoomButtons()
                    setMultiTouchControls(true)
                    controller.setZoom(14.0)
                    controller.setCenter(
                        OsmGeoPoint(route.startPoint.latitude, route.startPoint.longitude)
                    )
                    onResume()
                    mapView = this
                }
            },
            update = { view ->
                mapView = view
                clearRouteOverlays(view)
                view.overlays.add(
                    AirLegsOverlay(
                        checkpoints = route.checkpoints,
                        airColorArgb = airColor,
                        haloColorArgb = airHaloColor
                    )
                )
                if (route.polylinePoints.isNotEmpty()) {
                    addWalkingPathPolyline(
                        view,
                        route.polylinePoints,
                        walkingPathPaintForBasemap(BasemapStyle.STANDARD)
                    )
                }
                view.invalidate()
            }
        )
    }
}
