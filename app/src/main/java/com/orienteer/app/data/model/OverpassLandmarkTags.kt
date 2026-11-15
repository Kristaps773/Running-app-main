package com.orienteer.app.data.model

/**
 * Shared OSM landmark tag weights and filters for Overpass fetch, primary-tag resolution, and control enrich.
 */
object OverpassLandmarkTags {

    val TAG_WEIGHTS: Map<String, Double> = mapOf(
        "historic=monument" to 1.6,
        "historic=memorial" to 1.5,
        "historic=statue" to 1.55,
        "historic=ruins" to 1.5,
        "historic=wayside_cross" to 1.45,
        "historic=archaeological_site" to 1.45,
        "historic=castle" to 1.5,
        "tourism=viewpoint" to 1.5,
        "tourism=artwork" to 1.4,
        "tourism=information" to 1.35,
        "tourism=picnic_site" to 1.25,
        "tourism=wilderness_hut" to 1.45,
        "tourism=alpine_hut" to 1.45,
        "amenity=fountain" to 1.3,
        "amenity=bench" to 1.2,
        "amenity=shelter" to 1.25,
        "amenity=drinking_water" to 1.2,
        "amenity=post_box" to 1.15,
        "natural=peak" to 1.45,
        "natural=saddle" to 1.4,
        "natural=cliff" to 1.5,
        "natural=cave_entrance" to 1.5,
        "natural=rock" to 1.4,
        "natural=stone" to 1.35,
        "natural=spring" to 1.4,
        "natural=tree" to 1.2,
        "natural=wetland" to 1.25,
        "natural=water" to 1.25,
        "waterway=waterfall" to 1.5,
        "waterway=dam" to 1.35,
        "landuse=quarry" to 1.35,
        "man_made=tower" to 1.45,
        "man_made=water_tower" to 1.35,
        "man_made=windmill" to 1.4,
        "man_made=cross" to 1.4,
        "man_made=bridge" to 1.4,
        "man_made=cairn" to 1.45,
        "man_made=hunting_stand" to 1.35,
        "man_made=water_well" to 1.35,
        "man_made=embankment" to 1.3,
        "man_made=flagpole" to 1.25,
        "man_made=mast" to 1.25,
        "leisure=playground" to 1.2,
        "leisure=picnic_table" to 1.2,
        "leisure=fitness_station" to 1.15,
        "barrier=bollard" to 1.15,
        "barrier=stile" to 1.35,
        "barrier=gate" to 1.25,
        "barrier=fence" to 1.2,
        "barrier=wall" to 1.25,
        "barrier=hedge" to 1.15,
        "barrier=ditch" to 1.25,
        "power=tower" to 1.4,
        "power=pole" to 1.1,
        "highway=bus_stop" to 1.1,
        "highway=street_lamp" to 1.05,
        "highway=steps" to 1.2,
        "highway=ford" to 1.35,
        "building=church" to 1.4,
        "building=chapel" to 1.4,
        "building=hut" to 1.35,
        "building=cabin" to 1.35,
        "building=shed" to 1.2,
        "building=ruins" to 1.4
    )

    /** When a full tag is not in [TAG_WEIGHTS], break ties by key family (amenity over highway, etc.). */
    private val KEY_PRIORITY: Map<String, Double> = mapOf(
        "historic" to 9.0,
        "tourism" to 8.0,
        "amenity" to 7.0,
        "natural" to 6.0,
        "man_made" to 5.0,
        "leisure" to 4.0,
        "waterway" to 4.0,
        "landuse" to 3.5,
        "barrier" to 3.0,
        "power" to 2.5,
        "shop" to 2.0,
        "craft" to 2.0,
        "sport" to 2.0,
        "highway" to 1.0,
        "building" to 0.5
    )

    val EXCLUDED_PRIMARY_TAGS: Set<String> = setOf(
        "amenity=parking",
        "amenity=fuel",
        "amenity=bank",
        "amenity=hospital",
        "amenity=police",
        "amenity=prison",
        "leisure=pitch",
        "leisure=stadium"
    )

    val DENSE_LANDMARK_TAGS: Set<String> = setOf(
        "highway=street_lamp",
        "highway=crossing",
        "highway=traffic_signals",
        "power=pole",
        "barrier=fence",
        "barrier=wall",
        "barrier=hedge"
    )

    fun primaryTagFromTags(tags: Map<String, String>?): String? {
        if (tags.isNullOrEmpty()) return null
        return tags.entries
            .asSequence()
            .filter { (k, _) -> k in OverpassElement.INTERESTING_KEYS }
            .map { (k, v) -> "$k=$v" to primaryTagScore(k, v) }
            .maxWithOrNull(
                compareBy<Pair<String, Double>> { it.second }
                    .thenBy { it.first }
            )
            ?.first
    }

    fun primaryTagScore(key: String, value: String): Double {
        val full = "$key=$value"
        val weighted = TAG_WEIGHTS[full] ?: 0.0
        return weighted * 1_000.0 + (KEY_PRIORITY[key] ?: 0.0)
    }

    fun isWhitelistedPrimaryTag(tag: String): Boolean {
        if (tag in EXCLUDED_PRIMARY_TAGS) return false
        if (tag in TAG_WEIGHTS) return true
        val key = tag.substringBefore('=')
        return key in OverpassElement.INTERESTING_KEYS
    }

    fun tagWeight(tag: String): Double = TAG_WEIGHTS[tag] ?: 1.0
}
