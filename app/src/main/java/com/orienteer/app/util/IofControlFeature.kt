package com.orienteer.app.util

/**
 * Simplified control-feature kinds for PDF control-description pictograms.
 * Mapped from OSM landmarkType tags — not a full IOF 2024 symbol set.
 */
enum class IofControlFeature {
    BENCH,
    FOUNTAIN,
    WASTE_BASKET,
    SHELTER,
    POST_BOX,
    PARKING,
    BUS_STOP,
    MEMORIAL,
    RUINS,
    ARTWORK,
    VIEWPOINT,
    INFORMATION,
    PICNIC,
    TREE,
    SPRING,
    ROCK,
    PEAK,
    PLAYGROUND,
    FITNESS,
    TOWER,
    CROSS,
    SURVEY_POINT,
    BOLLARD,
    TRAFFIC_SIGNALS,
    CROSSING,
    STREET_LAMP,
    ROUNDABOUT,
    SPECIAL,
    NONE
}

object IofFeatureMapper {
    /** Short English label for the feature column (not IOF letter codes). */
    fun displayLabel(feature: IofControlFeature): String = when (feature) {
        IofControlFeature.BENCH -> "Bench"
        IofControlFeature.FOUNTAIN -> "Fountain"
        IofControlFeature.WASTE_BASKET -> "Waste basket"
        IofControlFeature.SHELTER -> "Shelter"
        IofControlFeature.POST_BOX -> "Post box"
        IofControlFeature.PARKING -> "Parking"
        IofControlFeature.BUS_STOP -> "Bus stop"
        IofControlFeature.MEMORIAL -> "Memorial"
        IofControlFeature.RUINS -> "Ruins"
        IofControlFeature.ARTWORK -> "Artwork"
        IofControlFeature.VIEWPOINT -> "Viewpoint"
        IofControlFeature.INFORMATION -> "Info board"
        IofControlFeature.PICNIC -> "Picnic site"
        IofControlFeature.TREE -> "Tree"
        IofControlFeature.SPRING -> "Spring"
        IofControlFeature.ROCK -> "Rock"
        IofControlFeature.PEAK -> "Hilltop"
        IofControlFeature.PLAYGROUND -> "Playground"
        IofControlFeature.FITNESS -> "Fitness station"
        IofControlFeature.TOWER -> "Tower"
        IofControlFeature.CROSS -> "Cross"
        IofControlFeature.SURVEY_POINT -> "Survey point"
        IofControlFeature.BOLLARD -> "Bollard"
        IofControlFeature.TRAFFIC_SIGNALS -> "Traffic lights"
        IofControlFeature.CROSSING -> "Crossing"
        IofControlFeature.STREET_LAMP -> "Street lamp"
        IofControlFeature.ROUNDABOUT -> "Mini roundabout"
        IofControlFeature.SPECIAL -> "Special item"
        IofControlFeature.NONE -> ""
    }

    fun fromLandmarkType(tag: String?): IofControlFeature {
        if (tag.isNullOrBlank()) return IofControlFeature.NONE
        return when (tag) {
            "amenity=bench" -> IofControlFeature.BENCH
            "amenity=fountain", "amenity=drinking_water" -> IofControlFeature.FOUNTAIN
            "amenity=waste_basket" -> IofControlFeature.WASTE_BASKET
            "amenity=shelter" -> IofControlFeature.SHELTER
            "amenity=post_box", "amenity=telephone" -> IofControlFeature.POST_BOX
            "amenity=parking", "amenity=bicycle_parking", "amenity=bus_station" ->
                IofControlFeature.PARKING
            "highway=bus_stop" -> IofControlFeature.BUS_STOP
            "historic=monument", "historic=memorial", "historic=statue",
            "historic=wayside_cross", "historic=milestone", "historic=boundary_stone" ->
                IofControlFeature.MEMORIAL
            "historic=ruins", "historic=archaeological_site",
            "historic=castle", "historic=fort" -> IofControlFeature.RUINS
            "tourism=artwork" -> IofControlFeature.ARTWORK
            "tourism=viewpoint" -> IofControlFeature.VIEWPOINT
            "tourism=information" -> IofControlFeature.INFORMATION
            "tourism=picnic_site", "leisure=picnic_table", "leisure=garden" ->
                IofControlFeature.PICNIC
            "natural=tree" -> IofControlFeature.TREE
            "natural=spring" -> IofControlFeature.SPRING
            "natural=rock" -> IofControlFeature.ROCK
            "natural=peak" -> IofControlFeature.PEAK
            "leisure=playground" -> IofControlFeature.PLAYGROUND
            "leisure=fitness_station" -> IofControlFeature.FITNESS
            "man_made=tower", "man_made=water_tower", "man_made=windmill",
            "man_made=obelisk", "man_made=surveillance", "tourism=clock" ->
                IofControlFeature.TOWER
            "man_made=cross" -> IofControlFeature.CROSS
            "man_made=survey_point" -> IofControlFeature.SURVEY_POINT
            "barrier=bollard" -> IofControlFeature.BOLLARD
            "highway=traffic_signals" -> IofControlFeature.TRAFFIC_SIGNALS
            "highway=crossing" -> IofControlFeature.CROSSING
            "highway=street_lamp" -> IofControlFeature.STREET_LAMP
            "highway=mini_roundabout" -> IofControlFeature.ROUNDABOUT
            else -> IofControlFeature.SPECIAL
        }
    }
}
