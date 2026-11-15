package com.orienteer.app.util

import com.orienteer.app.data.model.OverpassElement

/**
 * Converts raw OSM element tags into human-friendly checkpoint descriptions.
 * Wording leans toward orienteering control-description vocabulary (Street-O and forest).
 */
object CheckpointDescriptionGenerator {

    private val tagDescriptions: Map<String, String> = mapOf(
        // City / Street-O amenities
        "amenity=bench" to "a bench",
        "amenity=fountain" to "a drinking fountain",
        "amenity=waste_basket" to "a waste basket",
        "amenity=bicycle_parking" to "bicycle parking",
        "amenity=post_box" to "a post box",
        "amenity=telephone" to "a telephone box",
        "amenity=shelter" to "a shelter",
        "amenity=drinking_water" to "a drinking water point",
        "amenity=parking" to "a car park entrance",
        "amenity=bus_station" to "a bus station",
        // Historic / tourism
        "historic=monument" to "a monument",
        "historic=memorial" to "a memorial",
        "historic=ruins" to "ruins",
        "historic=milestone" to "a historic milestone",
        "historic=wayside_cross" to "a wayside cross",
        "historic=archaeological_site" to "an archaeological site",
        "historic=castle" to "a castle",
        "historic=fort" to "a fort",
        "historic=boundary_stone" to "a boundary stone",
        "historic=statue" to "a statue",
        "tourism=artwork" to "a piece of public art",
        "tourism=viewpoint" to "a scenic viewpoint",
        "tourism=information" to "an information board",
        "tourism=picnic_site" to "a picnic area",
        "tourism=clock" to "a public clock",
        "tourism=wilderness_hut" to "a wilderness hut",
        "tourism=alpine_hut" to "an alpine hut",
        // Landforms / vegetation / water (forest + park)
        "natural=tree" to "a distinct tree",
        "natural=spring" to "a spring",
        "natural=rock" to "a boulder",
        "natural=stone" to "a boulder",
        "natural=peak" to "a hilltop",
        "natural=saddle" to "a saddle",
        "natural=cliff" to "a cliff",
        "natural=cave_entrance" to "a cave entrance",
        "natural=wetland" to "a marsh",
        "natural=water" to "a pond",
        "waterway=waterfall" to "a waterfall",
        "waterway=dam" to "a dam",
        "landuse=quarry" to "a quarry",
        // Leisure / man-made
        "leisure=playground" to "a playground",
        "leisure=garden" to "a garden",
        "leisure=fitness_station" to "an outdoor gym station",
        "leisure=picnic_table" to "a picnic table",
        "man_made=tower" to "a tower",
        "man_made=windmill" to "a windmill",
        "man_made=cross" to "a cross",
        "man_made=survey_point" to "a survey marker",
        "man_made=obelisk" to "an obelisk",
        "man_made=water_tower" to "a water tower",
        "man_made=surveillance" to "a CCTV camera post",
        "man_made=bridge" to "a bridge",
        "man_made=cairn" to "a cairn",
        "man_made=hunting_stand" to "a hunting stand",
        "man_made=flagpole" to "a flagpole",
        "man_made=mast" to "a mast",
        "man_made=pier" to "a pier",
        "man_made=embankment" to "an earth bank",
        "man_made=water_well" to "a well",
        // Barriers / power / highway / buildings
        "barrier=bollard" to "a bollard",
        "barrier=fence" to "a fence",
        "barrier=wall" to "a wall",
        "barrier=gate" to "a gate",
        "barrier=stile" to "a stile",
        "barrier=hedge" to "a hedge",
        "barrier=ditch" to "a ditch",
        "power=tower" to "a power pylon",
        "power=pole" to "a power pole",
        "highway=bus_stop" to "a bus stop",
        "highway=traffic_signals" to "a set of traffic lights",
        "highway=crossing" to "a pedestrian crossing",
        "highway=street_lamp" to "a street lamp",
        "highway=mini_roundabout" to "a mini roundabout",
        "highway=steps" to "a flight of steps",
        "highway=ford" to "a ford",
        "building=hut" to "a hut",
        "building=cabin" to "a cabin",
        "building=church" to "a church",
        "building=chapel" to "a chapel",
        "building=shed" to "a shed",
        "building=ruins" to "building ruins"
    )

    /**
     * Produce a description for the checkpoint at position [index] given nearby [elements].
     * Falls back to a generic directional description if nothing useful is found.
     *
     * @param onObject when true, control is focused onto the landmark ("At …");
     *                 when false with elements, landmark is nearby ("Near …").
     */
    fun generate(
        index: Int,
        elements: List<OverpassElement>,
        onObject: Boolean = false
    ): Triple<String, String?, String?> {
        val best = elements.firstOrNull { it.primaryTag != null }

        return if (best != null) {
            val tag = best.primaryTag!!
            val shortDesc = tagDescriptions[tag] ?: tag.replace("=", ": ")
            val name = best.displayName
            val relation = if (onObject) "At" else "Near"
            val fullDesc = if (name != null) {
                "Checkpoint ${index + 1}: $relation $shortDesc called \"$name\"."
            } else {
                "Checkpoint ${index + 1}: $relation $shortDesc."
            }
            Triple(fullDesc, name, tag)
        } else {
            val ordinal = ordinal(index + 1)
            Triple("Checkpoint ${index + 1}: The $ordinal waypoint on your route.", null, null)
        }
    }

    private fun ordinal(n: Int): String = when (n) {
        1 -> "first"; 2 -> "second"; 3 -> "third"
        4 -> "fourth"; 5 -> "fifth"; 6 -> "sixth"
        7 -> "seventh"; 8 -> "eighth"; else -> "${n}th"
    }
}
