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
    pathLeadBucket: Int? = null,
    guidancePointCount: Int = 0
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
    append(prefs.useIofCourseOverprint)
    append('|')
    append(basemapStyle.name)
    append('|')
    append(pathLeadBucket ?: -1)
    append('|')
    append(guidancePointCount)
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
    /** Heading-up follow while locked; ignored when null / free-look. */
    followCamera: Boolean = false,
    mapBearingDeg: Float = 0f,
    onUserMapGesture: () -> Unit = {},
    onMapReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val mapColors = mapOverlayColors()
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val startArgb = MaterialTheme.colorScheme.tertiary.toArgb()
    val gestureHolder = remember { object { var onGesture: () -> Unit = {} } }
    gestureHolder.onGesture = onUserMapGesture
    val pathLeadBucket = remember(pathLead?.progressLocation, pathLead?.guidancePolyline) {
        val lead = pathLead ?: return@remember null
        if (lead.guidancePolyline.size < 2) return@remember null
        val loc = lead.progressLocation ?: lead.guidancePolyline.first()
        val progress = com.orienteer.app.domain.RoutePolylineProgress.project(
            lead.guidancePolyline,
            loc
        )
        com.orienteer.app.domain.RoutePolylineProgress.progressBucket(progress.distanceAlongM)
    }
    val overlayFingerprint = remember(
        route,
        mapPrefs,
        basemapStyle,
        pathLeadBucket,
        pathLead?.guidancePolyline?.size
    ) {
        routeOverlayFingerprint(
            route,
            mapPrefs,
            basemapStyle,
            pathLeadBucket = pathLeadBucket,
            guidancePointCount = pathLead?.guidancePolyline?.size ?: 0
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
                        updateLocationMarker(this, currentLocation)
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
                }

                updateLocationMarker(view, currentLocation)

                if (followCamera && currentLocation != null) {
                    view.setTag(R.id.map_tag_suppress_gesture, true)
                    view.controller.setCenter(
                        OsmGeoPoint(currentLocation.latitude, currentLocation.longitude)
                    )
                    view.mapOrientation = mapBearingDeg
                    view.post { view.setTag(R.id.map_tag_suppress_gesture, false) }
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

private fun updateLocationMarker(mapView: MapView, location: GeoPoint?) {
    val existing = mapView.overlays
        .filterIsInstance<Marker>()
        .firstOrNull { it.title == LOCATION_MARKER_TITLE }

    if (location == null) {
        if (existing != null) {
            mapView.overlays.remove(existing)
            mapView.invalidate()
        }
        return
    }

    val osmPoint = OsmGeoPoint(location.latitude, location.longitude)
    val icon = YouAreHereMarkerIcon.create(mapView.context)
    if (existing != null) {
        var dirty = false
        if (existing.icon !== icon) {
            existing.icon = icon
            dirty = true
        }
        val prev = existing.position
        if (prev.latitude != osmPoint.latitude || prev.longitude != osmPoint.longitude) {
            existing.position = osmPoint
            dirty = true
        }
        if (dirty) mapView.invalidate()
        return
    }

    mapView.overlays.add(
        Marker(mapView).apply {
            position = osmPoint
            title = LOCATION_MARKER_TITLE
            setIcon(icon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> true }
            infoWindow = null
        }
    )
    mapView.invalidate()
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
                view.addOrienteeringAirLegOverlays(
                    airLegSegmentsFromCheckpoints(route.checkpoints),
                    airColor,
                    airHaloColor
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
