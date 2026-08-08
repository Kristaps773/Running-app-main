package com.orienteer.app.presentation.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * Default OSM-style raster when O-map tiles are off. Dark uses Carto basemaps (OSM + CARTO attribution).
 */
fun defaultBasemapTileSource(useDark: Boolean): OnlineTileSourceBase =
    if (useDark) CartoDarkRaster else TileSourceFactory.MAPNIK

private val CartoDarkRaster = XYTileSource(
    "CartoDark",
    0,
    19,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)
