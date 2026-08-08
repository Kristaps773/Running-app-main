package com.orienteer.app.util

import com.orienteer.app.data.model.OverpassElement

/**
 * Converts raw OSM element tags into human-friendly checkpoint descriptions.
 */
object CheckpointDescriptionGenerator {

    private val tagDescriptions: Map<String, String> = mapOf(
        "amenity=bench" to "a bench",
        "amenity=fountain" to "a drinking fountain",
        "amenity=waste_basket" to "a waste basket",
        "amenity=bicycle_parking" to "bicycle parking",
        "amenity=post_box" to "a post box",
        "amenity=telephone" to "a telephone box",
        "amenity=shelter" to "a shelter",
        "amenity=parking" to "a car park entrance",
        "amenity=bus_station" to "a bus station",
        "historic=monument" to "a monument",
        "historic=memorial" to "a memorial",
        "historic=ruins" to "ancient ruins",
        "historic=milestone" to "a historic milestone",
        "historic=wayside_cross" to "a wayside cross",
        "historic=statue" to "a statue",
        "tourism=artwork" to "a piece of public art",
        "tourism=viewpoint" to "a scenic viewpoint",
        "tourism=information" to "an information board",
        "tourism=picnic_site" to "a picnic area",
        "natural=tree" to "a notable tree",
        "natural=spring" to "a natural spring",
        "natural=rock" to "a distinctive rock",
        "natural=peak" to "a hilltop",
        "leisure=playground" to "a playground",
        "leisure=fitness_station" to "an outdoor gym station",
        "leisure=picnic_table" to "a picnic table",
        "man_made=tower" to "a tower",
        "man_made=water_tower" to "a water tower",
        "man_made=surveillance" to "a CCTV camera post",
        "barrier=bollard" to "a bollard",
        "highway=bus_stop" to "a bus stop",
        "highway=traffic_signals" to "a set of traffic lights",
        "highway=crossing" to "a pedestrian crossing"
    )

    /**
     * Produce a description for the checkpoint at position [index] given nearby [elements].
     * Falls back to a generic directional description if nothing useful is found.
     */
    fun generate(index: Int, elements: List<OverpassElement>): Triple<String, String?, String?> {
        val best = elements.firstOrNull { it.primaryTag != null }

        return if (best != null) {
            val tag = best.primaryTag!!
            val shortDesc = tagDescriptions[tag] ?: tag.replace("=", ": ")
            val name = best.displayName
            val fullDesc = if (name != null) {
                "Checkpoint ${index + 1}: Near $shortDesc called \"$name\"."
            } else {
                "Checkpoint ${index + 1}: Near $shortDesc."
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
