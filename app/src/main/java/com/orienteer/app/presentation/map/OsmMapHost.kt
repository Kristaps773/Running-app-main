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
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.ui.theme.mapOverlayColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private const val LOCATION_MARKER_TITLE = "__user_location__"

private val cartoDarkTiles = XYTileSource(
    "CartoDark",
    0,
    20,
    256,
    ".png",
    arrayOf("https://basemaps.cartocdn.com/dark_all/"),
    "CartoDB"
)

private fun basemapTileSource(useDark: Boolean) =
    if (useDark) cartoDarkTiles else TileSourceFactory.MAPNIK

/** Stable fingerprint — overlay refresh without recreating the MapView. */
internal fun routeOverlayFingerprint(
    route: Route,
    prefs: MapDisplayPreferences
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
    useDarkBasemap: Boolean = false,
    interactive: Boolean = true,
    initialZoom: Double = 15.0,
    onMapReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val mapColors = mapOverlayColors()
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val startArgb = MaterialTheme.colorScheme.tertiary.toArgb()
    val overlayFingerprint = remember(route, mapPrefs) {
        routeOverlayFingerprint(route, mapPrefs)
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
                    setTileSource(basemapTileSource(useDarkBasemap))
                    setTag(R.id.map_tag_dark_basemap, useDarkBasemap)
                    setTag(R.id.map_tag_route_overlay_key, overlayFingerprint)
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
                    post {
                        applyRouteMapOverlays(
                            map = this,
                            route = route,
                            prefs = mapPrefs,
                            mapColors = mapColors,
                            primaryColorArgb = primaryArgb,
                            startColorArgb = startArgb
                        )
                        updateLocationMarker(this, currentLocation)
                    }
                }
            },
            update = { view ->
                mapView = view

                val lastBasemap = view.getTag(R.id.map_tag_dark_basemap) as? Boolean
                if (lastBasemap != useDarkBasemap) {
                    view.setTileSource(basemapTileSource(useDarkBasemap))
                    view.setTag(R.id.map_tag_dark_basemap, useDarkBasemap)
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
                        startColorArgb = startArgb
                    )
                }

                updateLocationMarker(view, currentLocation)
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
    if (existing != null) {
        val prev = existing.position
        if (prev.latitude == osmPoint.latitude && prev.longitude == osmPoint.longitude) {
            return
        }
        existing.position = osmPoint
        mapView.invalidate()
        return
    }

    mapView.overlays.add(
        Marker(mapView).apply {
            position = osmPoint
            title = LOCATION_MARKER_TITLE
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
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
    val plannedColor = mapColors.plannedPath.toArgb()
    val actualColor = mapColors.walkingPath.toArgb()

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
                view.overlays.clear()
                route.checkpoints.zipWithNext { a, b ->
                    view.overlays.add(
                        org.osmdroid.views.overlay.Polyline(view).apply {
                            setPoints(
                                listOf(
                                    OsmGeoPoint(a.position.latitude, a.position.longitude),
                                    OsmGeoPoint(b.position.latitude, b.position.longitude)
                                )
                            )
                            outlinePaint.color = plannedColor
                            outlinePaint.strokeWidth = 4f
                            outlinePaint.pathEffect =
                                android.graphics.DashPathEffect(floatArrayOf(20f, 12f), 0f)
                        }
                    )
                }
                if (route.polylinePoints.isNotEmpty()) {
                    view.overlays.add(
                        org.osmdroid.views.overlay.Polyline(view).apply {
                            setPoints(route.polylinePoints.map { OsmGeoPoint(it.latitude, it.longitude) })
                            outlinePaint.color = actualColor
                            outlinePaint.strokeWidth = 7f
                        }
                    )
                }
                view.invalidate()
            }
        )
    }
}
