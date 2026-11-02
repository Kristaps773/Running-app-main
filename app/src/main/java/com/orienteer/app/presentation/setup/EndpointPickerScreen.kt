package com.orienteer.app.presentation.setup

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orienteer.app.R
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.domain.CourseEndpoints
import com.orienteer.app.presentation.components.PrimaryGreenButton
import com.orienteer.app.presentation.map.MapAttributionBar
import com.orienteer.app.presentation.map.applyBasemapStyle
import com.orienteer.app.ui.icons.OrienteerIcons
import com.orienteer.app.ui.theme.OrienteerSpacing
import com.orienteer.app.util.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

private enum class ActivePin { Start, Finish }

/**
 * Start/finish picker. Chrome sits above/below the map (not overlaid) so taps always hit buttons.
 */
@Composable
fun EndpointPickerScreen(
    initialStart: GeoPoint?,
    initialFinish: GeoPoint?,
    basemapStyle: BasemapStyle,
    resolveGps: suspend () -> GeoPoint?,
    onCycleBasemap: () -> Unit,
    onConfirm: (start: GeoPoint?, finish: GeoPoint?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var start by remember { mutableStateOf(initialStart) }
    var finish by remember { mutableStateOf(initialFinish) }
    var finishAtStart by remember { mutableStateOf(initialFinish == null) }
    var activePin by remember { mutableStateOf(ActivePin.Finish) }
    val activePinLatest by rememberUpdatedState(activePin)
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        if (start == null) {
            status = "Getting GPS…"
            val gps = withContext(Dispatchers.IO) { resolveGps() }
            if (gps != null) {
                start = gps
                mapView?.controller?.setCenter(OsmGeoPoint(gps.latitude, gps.longitude))
                status = null
            } else {
                status = "GPS unavailable — tap the map to set Start."
                activePin = ActivePin.Start
            }
        }
    }

    LaunchedEffect(basemapStyle, mapView) {
        val map = mapView ?: return@LaunchedEffect
        applyBasemapStyle(map, map.context, basemapStyle)
        map.invalidate()
    }

    fun refreshMarkers(map: MapView) {
        map.overlays.removeAll { it is Marker }
        start?.let { s ->
            map.overlays.add(
                Marker(map).apply {
                    position = OsmGeoPoint(s.latitude, s.longitude)
                    title = "Start"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    relatedObject = "start"
                }
            )
        }
        if (!finishAtStart) {
            finish?.let { f ->
                map.overlays.add(
                    Marker(map).apply {
                        position = OsmGeoPoint(f.latitude, f.longitude)
                        title = "Finish"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        relatedObject = "finish"
                    }
                )
            }
        }
        map.invalidate()
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

    val airLabel = remember(start, finish, finishAtStart) {
        val s = start
        val f = finish
        when {
            finishAtStart || f == null || s == null -> "Return to start"
            else -> {
                val m = CourseEndpoints.minAirDistanceM(s, f)
                "Air to finish: ${GeoUtils.formatDistance(m)}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = OrienteerSpacing.sm, vertical = OrienteerSpacing.xs)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Text(
                        text = "Start / finish",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onCycleBasemap) {
                        Icon(
                            imageVector = OrienteerIcons.Layers,
                            contentDescription = stringResource(R.string.settings_map_style_title),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(basemapShortLabel(basemapStyle), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activePin == ActivePin.Start,
                        onClick = { activePin = ActivePin.Start },
                        label = { Text("Start") }
                    )
                    FilterChip(
                        selected = activePin == ActivePin.Finish && !finishAtStart,
                        onClick = {
                            finishAtStart = false
                            activePin = ActivePin.Finish
                        },
                        label = { Text("Finish") }
                    )
                    FilterChip(
                        selected = finishAtStart,
                        onClick = {
                            finishAtStart = true
                            finish = null
                            mapView?.let { refreshMarkers(it) }
                        },
                        label = { Text("Same place") }
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            scope.launch {
                                status = "Getting GPS…"
                                val gps = withContext(Dispatchers.IO) { resolveGps() }
                                if (gps != null) {
                                    start = gps
                                    activePin = ActivePin.Start
                                    mapView?.controller?.animateTo(OsmGeoPoint(gps.latitude, gps.longitude))
                                    mapView?.let { refreshMarkers(it) }
                                    status = null
                                } else {
                                    status = "GPS unavailable"
                                }
                            }
                        }
                    ) {
                        Icon(OrienteerIcons.MyLocation, contentDescription = "Use GPS for Start")
                    }
                }
                Text(
                    text = airLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                status?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Map only in the middle band — never under the chrome, so no hover/punch-through.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        applyBasemapStyle(this, ctx, basemapStyle)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        val center = start ?: GeoPoint(56.95, 24.10)
                        controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
                        val receiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean {
                                if (p == null) return false
                                val point = GeoPoint(p.latitude, p.longitude)
                                when (activePinLatest) {
                                    ActivePin.Start -> start = point
                                    ActivePin.Finish -> {
                                        finishAtStart = false
                                        finish = point
                                    }
                                }
                                status = null
                                refreshMarkers(this@apply)
                                return true
                            }

                            override fun longPressHelper(p: OsmGeoPoint?): Boolean = false
                        }
                        overlays.add(0, MapEventsOverlay(receiver))
                        onResume()
                        mapView = this
                        post { refreshMarkers(this) }
                    }
                },
                update = { view ->
                    mapView = view
                    applyBasemapStyle(view, view.context, basemapStyle)
                    refreshMarkers(view)
                }
            )
            MapAttributionBar(
                basemapStyle = basemapStyle,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = OrienteerSpacing.sm, bottom = OrienteerSpacing.sm)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(OrienteerSpacing.sm)) {
                PrimaryGreenButton(
                    text = "Use these points",
                    onClick = onClick@{
                        val s = start
                        if (s == null) {
                            status = "Set a Start point (tap map or use GPS)."
                            activePin = ActivePin.Start
                            return@onClick
                        }
                        when {
                            finishAtStart -> onConfirm(s, null)
                            finish == null -> {
                                status = "Tap the map to place Finish, or choose Same place."
                                activePin = ActivePin.Finish
                            }
                            CourseEndpoints.minAirDistanceM(s, finish!!) >
                                CourseEndpoints.MAX_TARGET_M -> {
                                status = "Start and finish are too far apart (over 50 km)."
                            }
                            else -> onConfirm(s, finish)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun basemapShortLabel(style: BasemapStyle): String = when (style) {
    BasemapStyle.STANDARD -> stringResource(R.string.map_style_standard)
    BasemapStyle.DARK -> stringResource(R.string.map_style_dark)
    BasemapStyle.PSEUDO_O -> stringResource(R.string.map_style_pseudo_o)
    BasemapStyle.STREET_O -> stringResource(R.string.map_style_street_o)
    BasemapStyle.OPEN_TOPO -> stringResource(R.string.map_style_open_topo)
    BasemapStyle.CYCLOSM -> stringResource(R.string.map_style_cyclosm)
}
