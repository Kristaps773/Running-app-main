package com.orienteer.app.presentation.map

import android.content.Context
import android.graphics.Color
import com.orienteer.app.data.local.BasemapStyle
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

private class OomapContourTilesOverlay(context: Context) : TilesOverlay(
    MapTileProviderBasic(context, OomapLidar5Contours),
    context
) {
    init {
        loadingBackgroundColor = Color.TRANSPARENT
        loadingLineColor = Color.TRANSPARENT
    }
}

internal fun isBasemapContourOverlay(overlay: Any): Boolean = overlay is OomapContourTilesOverlay

private val OpenTopoMapRaster = XYTileSource(
    "OpenTopoMap",
    0,
    17,
    256,
    ".png",
    arrayOf(
        "https://a.tile.opentopomap.org/",
        "https://b.tile.opentopomap.org/",
        "https://c.tile.opentopomap.org/"
    ),
    "© OpenStreetMap contributors, SRTM | Style: © OpenTopoMap (CC BY-SA 3.0)"
)

private val CyclOsmRaster = XYTileSource(
    "CyclOSM",
    0,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/",
        "https://b.tile-cyclosm.openstreetmap.fr/cyclosm/",
        "https://c.tile-cyclosm.openstreetmap.fr/cyclosm/"
    ),
    "© OpenStreetMap contributors © CyclOSM"
)

/** OOMap public LiDAR-5 contour XYZ (transparent overlay). Pseud-O full rasters are not public. */
private val OomapLidar5Contours = XYTileSource(
    "OOMapLidar5",
    0,
    18,
    256,
    ".png",
    arrayOf("https://oomap.dna-software.co.uk/LIDAR-5/"),
    "Contours © OpenOrienteeringMap · OSM © contributors"
)

fun baseBasemapTileSource(style: BasemapStyle): OnlineTileSourceBase = when (style) {
    // Dark uses Mapnik + invert filter — CARTO dark_all now requires an API key.
    BasemapStyle.DARK,
    BasemapStyle.STANDARD,
    BasemapStyle.PSEUDO_O -> TileSourceFactory.MAPNIK
    BasemapStyle.OPEN_TOPO -> OpenTopoMapRaster
    BasemapStyle.CYCLOSM -> CyclOsmRaster
}

/** User-visible attribution line for the active style (map chrome + docs). */
fun basemapAttributionLine(style: BasemapStyle): String = when (style) {
    BasemapStyle.STANDARD ->
        "© OpenStreetMap contributors"
    BasemapStyle.DARK ->
        "© OpenStreetMap contributors (inverted)"
    BasemapStyle.PSEUDO_O ->
        "© OpenStreetMap contributors · Contours © OpenOrienteeringMap (LiDAR where available) · Pseud-O approximation"
    BasemapStyle.OPEN_TOPO ->
        "© OpenStreetMap contributors, SRTM · © OpenTopoMap (CC BY-SA 3.0)"
    BasemapStyle.CYCLOSM ->
        "© OpenStreetMap contributors © CyclOSM"
}

fun applyBasemapStyle(map: MapView, context: Context, style: BasemapStyle) {
    map.setTileSource(baseBasemapTileSource(style))
    map.overlayManager.tilesOverlay.setColorFilter(
        if (style == BasemapStyle.DARK) TilesOverlay.INVERT_COLORS else null
    )
    map.overlays.removeAll { it is OomapContourTilesOverlay }
    if (style == BasemapStyle.PSEUDO_O) {
        map.overlays.add(0, OomapContourTilesOverlay(context))
    }
    map.invalidate()
}

/** Drop route markers/polylines but keep contour tiles and rotation gesture. */
fun clearRouteOverlays(map: MapView) {
    map.overlays.removeAll {
        !isBasemapContourOverlay(it) && it !is RotationGestureOverlay
    }
}
