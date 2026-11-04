package com.orienteer.app.presentation.setup

import android.graphics.DashPathEffect
import android.view.View
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
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
import com.orienteer.app.domain.CoursePointsCatalog
import com.orienteer.app.domain.CoursePointsSet
import com.orienteer.app.domain.LockedCoursePoints
import com.orienteer.app.presentation.components.PrimaryGreenButton
import com.orienteer.app.presentation.map.CheckpointMarkerIcon
import com.orienteer.app.presentation.map.CourseEndpointMarkerIcon
import com.orienteer.app.presentation.map.IOF_PURPLE_ARGB
import com.orienteer.app.presentation.map.MapAttributionBar
import com.orienteer.app.presentation.map.applyBasemapStyle
import com.orienteer.app.ui.icons.OrienteerIcons
import com.orienteer.app.ui.theme.OrienteerSpacing
import com.orienteer.app.util.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog
import java.util.UUID
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private enum class ActivePin { Start, Checkpoint, Finish }

/**
 * Course points picker: Start, Finish, locked checkpoints, Any order.
 * Chrome sits above/below the map so controls stay tappable.
 */
@Composable
fun EndpointPickerScreen(
    initialStart: GeoPoint?,
    initialFinish: GeoPoint?,
    initialLocked: List<GeoPoint> = emptyList(),
    initialAnyOrder: Boolean = false,
    recentSets: List<CoursePointsSet> = emptyList(),
    favoriteSets: List<CoursePointsSet> = emptyList(),
    basemapStyle: BasemapStyle,
    /** Same setting as map preview — classic IOF purple vs filled app badges. */
    useIofCourseOverprint: Boolean = false,
    resolveGps: suspend () -> GeoPoint?,
    onCycleBasemap: () -> Unit,
    onApplySavedSet: (CoursePointsSet) -> Unit = {},
    onDeleteRecent: (String) -> Unit = {},
    onDeleteFavorite: (String) -> Unit = {},
    onSaveFavorite: suspend () -> List<CoursePointsSet>? = { null },
    onReplaceFavorite: (removeId: String, set: CoursePointsSet) -> Unit = { _, _ -> },
    onConfirm: (
        start: GeoPoint?,
        finish: GeoPoint?,
        locked: List<GeoPoint>,
        anyOrder: Boolean
    ) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var start by remember { mutableStateOf(initialStart) }
    var finish by remember { mutableStateOf(initialFinish) }
    var finishAtStart by remember { mutableStateOf(initialFinish == null) }
    var locked by remember { mutableStateOf(initialLocked) }
    // Default: Any order on. Respect an explicit saved false when reopening with 2+ pins.
    var anyOrder by remember { mutableStateOf(initialAnyOrder) }
    var selectedLockedIndex by remember { mutableStateOf<Int?>(null) }
    var activePin by remember {
        mutableStateOf(
            if (initialStart != null) ActivePin.Checkpoint else ActivePin.Start
        )
    }
    val activePinLatest by rememberUpdatedState(activePin)
    val lockedLatest by rememberUpdatedState(locked)
    val selectedLockedLatest by rememberUpdatedState(selectedLockedIndex)
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var replaceCandidates by remember { mutableStateOf<List<CoursePointsSet>?>(null) }
    var pendingFavorite by remember { mutableStateOf<CoursePointsSet?>(null) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        if (start == null) {
            status = "Getting GPS…"
            val gps = withContext(Dispatchers.IO) { resolveGps() }
            if (gps != null) {
                start = gps
                mapView?.controller?.setCenter(OsmGeoPoint(gps.latitude, gps.longitude))
                status = null
                activePin = ActivePin.Checkpoint
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
        val ctx = map.context
        // Match map preview: IOF purple vs forest filled badges (Settings → Classic IOF).
        val badgeColor = if (useIofCourseOverprint) {
            IOF_PURPLE_ARGB
        } else {
            CourseEndpointMarkerIcon.APP_BADGE_ARGB
        }
        map.overlays.removeAll { it is Marker || it is Polyline }
        start?.let { s ->
            val kind = if (finishAtStart) {
                CourseEndpointMarkerIcon.Kind.Combined
            } else {
                CourseEndpointMarkerIcon.Kind.Start
            }
            map.overlays.add(
                Marker(map).apply {
                    position = OsmGeoPoint(s.latitude, s.longitude)
                    title = if (finishAtStart) "Start / Finish" else "Start"
                    setIcon(
                        CourseEndpointMarkerIcon.create(
                            context = ctx,
                            kind = kind,
                            useIofStyle = useIofCourseOverprint,
                            selected = activePin == ActivePin.Start ||
                                (finishAtStart && activePin == ActivePin.Finish)
                        )
                    )
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    infoWindow = null
                    setOnMarkerClickListener { _, _ -> true }
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
                        setIcon(
                            CourseEndpointMarkerIcon.create(
                                context = ctx,
                                kind = CourseEndpointMarkerIcon.Kind.Finish,
                                useIofStyle = useIofCourseOverprint,
                                selected = activePin == ActivePin.Finish
                            )
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                        relatedObject = "finish"
                    }
                )
            }
        }
        locked.forEachIndexed { index, point ->
            val selected = selectedLockedIndex == index
            val label = if (anyOrder) "CP" else "${index + 1}"
            map.overlays.add(
                Marker(map).apply {
                    position = OsmGeoPoint(point.latitude, point.longitude)
                    title = if (anyOrder) "Checkpoint" else "Checkpoint ${index + 1}"
                    setIcon(
                        CheckpointMarkerIcon.create(
                            context = ctx,
                            label = label,
                            isStart = false,
                            primaryColor = badgeColor,
                            startColor = badgeColor,
                            scale = if (selected) 1.12f else 1f,
                            selected = selected
                        )
                    )
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    infoWindow = null
                    setOnMarkerClickListener { _, _ -> true }
                    relatedObject = "locked:$index"
                }
            )
        }
        if (!anyOrder && locked.size >= 2 && start != null) {
            val end = if (finishAtStart) start!! else finish
            if (end != null) {
                // Air-leg magenta — same as map straight-line legs.
                val guide = Polyline(map).apply {
                    outlinePaint.strokeWidth = 5f
                    outlinePaint.color = IOF_PURPLE_ARGB
                    outlinePaint.alpha = 180
                    outlinePaint.pathEffect = DashPathEffect(floatArrayOf(28f, 18f), 0f)
                    setPoints(
                        buildList {
                            add(OsmGeoPoint(start!!.latitude, start!!.longitude))
                            locked.forEach { add(OsmGeoPoint(it.latitude, it.longitude)) }
                            add(OsmGeoPoint(end.latitude, end.longitude))
                        }
                    )
                }
                map.overlays.add(0, guide)
            }
        }
        map.invalidate()
    }

    fun loadSet(set: CoursePointsSet) {
        start = set.start
        finish = set.finish
        finishAtStart = set.finish == null
        locked = set.locked
        anyOrder = set.anyOrder
        selectedLockedIndex = null
        activePin = if (set.start != null) ActivePin.Checkpoint else ActivePin.Start
        onApplySavedSet(set)
        mapView?.let { refreshMarkers(it) }
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

    val statusLabel = remember(start, finish, finishAtStart, locked, anyOrder) {
        val s = start
        val f = finish
        when {
            s == null -> "Set Start"
            locked.isEmpty() && (finishAtStart || f == null) -> "Return to start"
            locked.isEmpty() && f != null ->
                "Air to finish: ${GeoUtils.formatDistance(CourseEndpoints.minAirDistanceM(s, f))}"
            anyOrder && locked.size >= 2 ->
                "${locked.size} locked · any order each Generate"
            else -> {
                val air = LockedCoursePoints.minVisitAirM(
                    s,
                    if (finishAtStart) null else f,
                    locked,
                    anyOrder = false
                )
                "${locked.size} locked · in order · ${GeoUtils.formatDistance(air)} min"
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
            Column(
                modifier = Modifier.padding(
                    horizontal = OrienteerSpacing.sm,
                    vertical = OrienteerSpacing.xs
                )
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Text(
                        text = "Course points",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activePin == ActivePin.Start,
                        onClick = { activePin = ActivePin.Start },
                        label = { Text("Start") }
                    )
                    FilterChip(
                        selected = activePin == ActivePin.Checkpoint && selectedLockedIndex == null,
                        onClick = {
                            // Add mode: next map tap places a new locked CP (does not move the last one).
                            activePin = ActivePin.Checkpoint
                            selectedLockedIndex = null
                        },
                        label = { Text("Checkpoint") }
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
                                    mapView?.controller?.animateTo(
                                        OsmGeoPoint(gps.latitude, gps.longitude)
                                    )
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
                if (locked.size >= 2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = anyOrder,
                            onCheckedChange = {
                                anyOrder = it
                                mapView?.let { m -> refreshMarkers(m) }
                            }
                        )
                        Text(
                            text = "Any order",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = " — locked points may reshuffle each Generate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (recentSets.isNotEmpty() || favoriteSets.isNotEmpty()) {
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = OrienteerSpacing.xxs)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        favoriteSets.forEach { set ->
                            FilterChip(
                                selected = false,
                                onClick = { loadSet(set) },
                                label = {
                                    Text(
                                        "★ ${CoursePointsCatalog.autoLabel(set)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                        recentSets.forEach { set ->
                            FilterChip(
                                selected = false,
                                onClick = { loadSet(set) },
                                label = {
                                    Text(
                                        CoursePointsCatalog.autoLabel(set),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
                if (locked.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        locked.forEachIndexed { index, _ ->
                            FilterChip(
                                selected = selectedLockedIndex == index,
                                onClick = {
                                    // Select to move; tap again to return to add mode.
                                    selectedLockedIndex =
                                        if (selectedLockedIndex == index) null else index
                                    activePin = ActivePin.Checkpoint
                                },
                                label = {
                                    Text(if (anyOrder) "CP" else "${index + 1}")
                                },
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            locked = locked.toMutableList().also { it.removeAt(index) }
                                            selectedLockedIndex = null
                                            mapView?.let { refreshMarkers(it) }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) { Text("×") }
                                }
                            )
                        }
                        FilterChip(
                            selected = activePin == ActivePin.Checkpoint && selectedLockedIndex == null,
                            onClick = {
                                activePin = ActivePin.Checkpoint
                                selectedLockedIndex = null
                            },
                            label = { Text("+ Add") }
                        )
                    }
                }
                Text(
                    text = when {
                        activePin == ActivePin.Checkpoint && selectedLockedIndex != null -> {
                            val n = selectedLockedIndex!! + 1
                            if (anyOrder) {
                                "Tap map to move the selected checkpoint"
                            } else {
                                "Tap map to move checkpoint #$n"
                            }
                        }
                        activePin == ActivePin.Checkpoint ->
                            if (locked.isEmpty()) {
                                "Tap map to place a locked checkpoint"
                            } else {
                                "Tap map to add another locked checkpoint"
                            }
                        else -> statusLabel
                    },
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
                                    ActivePin.Checkpoint -> {
                                        val idx = selectedLockedLatest
                                        val list = lockedLatest.toMutableList()
                                        if (idx != null && idx in list.indices) {
                                            // Explicitly selected → move that pin, then return to add mode.
                                            list[idx] = point
                                            locked = list
                                            selectedLockedIndex = null
                                        } else if (list.size < LockedCoursePoints.MAX_LOCKED_INTERMEDIATES) {
                                            // No selection → always append a new locked checkpoint.
                                            list.add(point)
                                            locked = list
                                            selectedLockedIndex = null
                                        } else {
                                            status =
                                                "At most ${LockedCoursePoints.MAX_LOCKED_INTERMEDIATES} locked checkpoints."
                                            return true
                                        }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryGreenButton(
                        text = "Use these points",
                        onClick = onClick@{
                            val s = start
                            if (s == null) {
                                status = "Set a Start point (tap map or use GPS)."
                                activePin = ActivePin.Start
                                return@onClick
                            }
                            val f = if (finishAtStart) null else finish
                            if (!finishAtStart && f == null) {
                                status = "Tap the map to place Finish, or choose Same place."
                                activePin = ActivePin.Finish
                                return@onClick
                            }
                            when (
                                val decision = LockedCoursePoints.evaluateTargetDistance(
                                    targetM = CourseEndpoints.MAX_TARGET_M,
                                    origin = s,
                                    finish = f,
                                    locked = locked,
                                    anyOrder = anyOrder && locked.size >= 2
                                )
                            ) {
                                is CourseEndpoints.TargetDistanceDecision.EndpointsExceedMax -> {
                                    status = "Points are too far apart (over 50 km spine)."
                                    return@onClick
                                }
                                else -> Unit
                            }
                            onConfirm(s, f, locked, anyOrder)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val s = start
                            if (s == null) {
                                status = "Set Start before starring."
                                return@IconButton
                            }
                            val set = CoursePointsSet(
                                id = UUID.randomUUID().toString(),
                                savedAtMs = System.currentTimeMillis(),
                                start = s,
                                finish = if (finishAtStart) null else finish,
                                locked = locked,
                                anyOrder = anyOrder,
                                usedGpsStart = false
                            )
                            scope.launch {
                                // Sync local draft into active before favorite write.
                                onApplySavedSet(set)
                                val full = onSaveFavorite()
                                if (full != null) {
                                    pendingFavorite = set
                                    replaceCandidates = full
                                    status = "Favorites full — pick one to replace."
                                } else {
                                    status = "Saved to favorites"
                                }
                            }
                        }
                    ) {
                        Icon(
                            OrienteerIcons.Star,
                            contentDescription = "Save to favorites"
                        )
                    }
                }
            }
        }
    }

    val candidates = replaceCandidates
    val pending = pendingFavorite
    if (candidates != null && pending != null) {
        AlertDialog(
            onDismissRequest = {
                replaceCandidates = null
                pendingFavorite = null
            },
            title = { Text("Favorites full") },
            text = {
                Column {
                    Text("Replace an existing favorite with the current points:")
                    candidates.forEach { set ->
                        TextButton(
                            onClick = {
                                onReplaceFavorite(set.id, pending)
                                replaceCandidates = null
                                pendingFavorite = null
                                status = "Favorite replaced"
                            }
                        ) {
                            Text(CoursePointsCatalog.autoLabel(set))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        replaceCandidates = null
                        pendingFavorite = null
                    }
                ) { Text("Cancel") }
            }
        )
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
