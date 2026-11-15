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
    SADDLE,
    CLIFF,
    CAVE,
    MARSH,
    POND,
    WATERFALL,
    PLAYGROUND,
    FITNESS,
    TOWER,
    CROSS,
    SURVEY_POINT,
    BRIDGE,
    CAIRN,
    HUNTING_STAND,
    WELL,
    EARTH_BANK,
    FLAGPOLE,
    BOLLARD,
    FENCE,
    WALL,
    GATE,
    STILE,
    HEDGE,
    DITCH,
    POWER_PYLON,
    TRAFFIC_SIGNALS,
    CROSSING,
    STREET_LAMP,
    ROUNDABOUT,
    STEPS,
    FORD,
    BUILDING,
    QUARRY,
    PATH_JUNCTION,
    ROAD_BEND,
    PATH_END,
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
        IofControlFeature.TREE -> "Distinct tree"
        IofControlFeature.SPRING -> "Spring"
        IofControlFeature.ROCK -> "Boulder"
        IofControlFeature.PEAK -> "Hilltop"
        IofControlFeature.SADDLE -> "Saddle"
        IofControlFeature.CLIFF -> "Cliff"
        IofControlFeature.CAVE -> "Cave"
        IofControlFeature.MARSH -> "Marsh"
        IofControlFeature.POND -> "Pond"
        IofControlFeature.WATERFALL -> "Waterfall"
        IofControlFeature.PLAYGROUND -> "Playground"
        IofControlFeature.FITNESS -> "Fitness station"
        IofControlFeature.TOWER -> "Tower"
        IofControlFeature.CROSS -> "Cross"
        IofControlFeature.SURVEY_POINT -> "Survey point"
        IofControlFeature.BRIDGE -> "Bridge"
        IofControlFeature.CAIRN -> "Cairn"
        IofControlFeature.HUNTING_STAND -> "Hunting stand"
        IofControlFeature.WELL -> "Well"
        IofControlFeature.EARTH_BANK -> "Earth bank"
        IofControlFeature.FLAGPOLE -> "Flagpole"
        IofControlFeature.BOLLARD -> "Bollard"
        IofControlFeature.FENCE -> "Fence"
        IofControlFeature.WALL -> "Wall"
        IofControlFeature.GATE -> "Gate"
        IofControlFeature.STILE -> "Stile"
        IofControlFeature.HEDGE -> "Hedge"
        IofControlFeature.DITCH -> "Ditch"
        IofControlFeature.POWER_PYLON -> "Power pylon"
        IofControlFeature.TRAFFIC_SIGNALS -> "Traffic lights"
        IofControlFeature.CROSSING -> "Crossing"
        IofControlFeature.STREET_LAMP -> "Street lamp"
        IofControlFeature.ROUNDABOUT -> "Mini roundabout"
        IofControlFeature.STEPS -> "Steps"
        IofControlFeature.FORD -> "Ford"
        IofControlFeature.BUILDING -> "Building"
        IofControlFeature.QUARRY -> "Quarry"
        IofControlFeature.PATH_JUNCTION -> "Path junction"
        IofControlFeature.ROAD_BEND -> "Road bend"
        IofControlFeature.PATH_END -> "Path end"
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
            "historic=castle", "historic=fort", "building=ruins" -> IofControlFeature.RUINS
            "tourism=artwork" -> IofControlFeature.ARTWORK
            "tourism=viewpoint" -> IofControlFeature.VIEWPOINT
            "tourism=information" -> IofControlFeature.INFORMATION
            "tourism=picnic_site", "leisure=picnic_table", "leisure=garden" ->
                IofControlFeature.PICNIC
            "tourism=wilderness_hut", "tourism=alpine_hut",
            "building=hut", "building=cabin", "building=church",
            "building=chapel", "building=shed" -> IofControlFeature.BUILDING
            "natural=tree" -> IofControlFeature.TREE
            "natural=spring" -> IofControlFeature.SPRING
            "natural=rock", "natural=stone" -> IofControlFeature.ROCK
            "natural=peak" -> IofControlFeature.PEAK
            "natural=saddle" -> IofControlFeature.SADDLE
            "natural=cliff" -> IofControlFeature.CLIFF
            "natural=cave_entrance" -> IofControlFeature.CAVE
            "natural=wetland" -> IofControlFeature.MARSH
            "natural=water" -> IofControlFeature.POND
            "waterway=waterfall" -> IofControlFeature.WATERFALL
            "waterway=dam" -> IofControlFeature.SPECIAL
            "landuse=quarry" -> IofControlFeature.QUARRY
            "leisure=playground" -> IofControlFeature.PLAYGROUND
            "leisure=fitness_station" -> IofControlFeature.FITNESS
            "man_made=tower", "man_made=water_tower", "man_made=windmill",
            "man_made=obelisk", "man_made=surveillance", "tourism=clock",
            "man_made=mast" -> IofControlFeature.TOWER
            "man_made=cross" -> IofControlFeature.CROSS
            "man_made=survey_point" -> IofControlFeature.SURVEY_POINT
            "man_made=bridge" -> IofControlFeature.BRIDGE
            "man_made=cairn" -> IofControlFeature.CAIRN
            "man_made=hunting_stand" -> IofControlFeature.HUNTING_STAND
            "man_made=water_well" -> IofControlFeature.WELL
            "man_made=embankment" -> IofControlFeature.EARTH_BANK
            "man_made=flagpole", "man_made=pier" -> IofControlFeature.FLAGPOLE
            "barrier=bollard" -> IofControlFeature.BOLLARD
            "barrier=fence" -> IofControlFeature.FENCE
            "barrier=wall" -> IofControlFeature.WALL
            "barrier=gate" -> IofControlFeature.GATE
            "barrier=stile" -> IofControlFeature.STILE
            "barrier=hedge" -> IofControlFeature.HEDGE
            "barrier=ditch" -> IofControlFeature.DITCH
            "power=tower", "power=pole" -> IofControlFeature.POWER_PYLON
            "highway=traffic_signals" -> IofControlFeature.TRAFFIC_SIGNALS
            "highway=crossing" -> IofControlFeature.CROSSING
            "highway=street_lamp" -> IofControlFeature.STREET_LAMP
            "highway=mini_roundabout" -> IofControlFeature.ROUNDABOUT
            "highway=steps" -> IofControlFeature.STEPS
            "highway=ford" -> IofControlFeature.FORD
            "generic=path_junction" -> IofControlFeature.PATH_JUNCTION
            "generic=road_bend" -> IofControlFeature.ROAD_BEND
            "generic=path_end" -> IofControlFeature.PATH_END
            else -> IofControlFeature.SPECIAL
        }
    }
}
