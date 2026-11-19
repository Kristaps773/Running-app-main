package com.orienteer.app.data.local

/**
 * Selectable raster basemaps (all free for app use with attribution — see [store-assets/MAP_STYLES_LICENSES.md]).
 *
 * Pseud-O full OOMap XYZ tiles are not publicly served; the style uses OSM Mapnik
 * (+ OOMap LiDAR contour overlay) as the closest free approximation of ISOM colour intent.
 */
enum class BasemapStyle {
    /** OSM Mapnik via OSMDroid default tile source. */
    STANDARD,
    /** OSM Mapnik with inverted tile colors (no third-party dark CDN key). */
    DARK,
    /**
     * Forest-leaning O-map approximation: Mapnik + OpenOrienteeringMap LiDAR-5 contours
     * (where available). Not a surveyed ISOM map.
     */
    PSEUDO_O,
    /** OpenTopoMap — topography + OSM (CC BY-SA style, ODbL data). */
    OPEN_TOPO,
    /** CyclOSM — cycling/outdoors emphasis on OSM. */
    CYCLOSM;

    companion object {
        fun fromStorage(value: String?): BasemapStyle = when (value) {
            // Legacy name from earlier “O contours” style.
            "ORIENTEERING", "PSEUDO_O" -> PSEUDO_O
            "STREET_O" -> PSEUDO_O
            "DARK" -> DARK
            "OPEN_TOPO" -> OPEN_TOPO
            "CYCLOSM" -> CYCLOSM
            "STANDARD" -> STANDARD
            else -> entries.find { it.name == value } ?: STANDARD
        }

        /** Styles that approximate orienteering cartography (enable classic overprint by default). */
        fun isOMapStyle(style: BasemapStyle): Boolean = style == PSEUDO_O
    }
}
