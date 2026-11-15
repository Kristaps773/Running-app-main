package com.orienteer.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IofFeatureMapperTest {

    @Test
    fun nullOrBlank_mapsToNone() {
        assertEquals(IofControlFeature.NONE, IofFeatureMapper.fromLandmarkType(null))
        assertEquals(IofControlFeature.NONE, IofFeatureMapper.fromLandmarkType(""))
        assertEquals(IofControlFeature.NONE, IofFeatureMapper.fromLandmarkType("  "))
    }

    @Test
    fun knownTags_mapToExpectedFeatures() {
        assertEquals(IofControlFeature.BENCH, IofFeatureMapper.fromLandmarkType("amenity=bench"))
        assertEquals(IofControlFeature.FOUNTAIN, IofFeatureMapper.fromLandmarkType("amenity=fountain"))
        assertEquals(IofControlFeature.TREE, IofFeatureMapper.fromLandmarkType("natural=tree"))
        assertEquals(IofControlFeature.CROSSING, IofFeatureMapper.fromLandmarkType("highway=crossing"))
        assertEquals(IofControlFeature.MEMORIAL, IofFeatureMapper.fromLandmarkType("historic=memorial"))
        assertEquals(IofControlFeature.PEAK, IofFeatureMapper.fromLandmarkType("natural=peak"))
        assertEquals(IofControlFeature.ROCK, IofFeatureMapper.fromLandmarkType("natural=rock"))
        assertEquals(IofControlFeature.CLIFF, IofFeatureMapper.fromLandmarkType("natural=cliff"))
        assertEquals(IofControlFeature.MARSH, IofFeatureMapper.fromLandmarkType("natural=wetland"))
        assertEquals(IofControlFeature.FENCE, IofFeatureMapper.fromLandmarkType("barrier=fence"))
        assertEquals(IofControlFeature.BRIDGE, IofFeatureMapper.fromLandmarkType("man_made=bridge"))
        assertEquals(IofControlFeature.CAIRN, IofFeatureMapper.fromLandmarkType("man_made=cairn"))
        assertEquals(IofControlFeature.POWER_PYLON, IofFeatureMapper.fromLandmarkType("power=tower"))
        assertEquals(IofControlFeature.STEPS, IofFeatureMapper.fromLandmarkType("highway=steps"))
        assertEquals(IofControlFeature.BUILDING, IofFeatureMapper.fromLandmarkType("building=hut"))
    }

    @Test
    fun unknownTag_mapsToSpecial() {
        assertEquals(
            IofControlFeature.SPECIAL,
            IofFeatureMapper.fromLandmarkType("amenity=library")
        )
    }

    @Test
    fun genericLandmarkTags_mapToGenericIofFeatures() {
        assertEquals(IofControlFeature.PATH_JUNCTION, IofFeatureMapper.fromLandmarkType("generic=path_junction"))
        assertEquals(IofControlFeature.ROAD_BEND, IofFeatureMapper.fromLandmarkType("generic=road_bend"))
        assertEquals(IofControlFeature.PATH_END, IofFeatureMapper.fromLandmarkType("generic=path_end"))
    }

    @Test
    fun genericIofFeatures_haveNonEmptyDisplayLabels() {
        val labels = listOf(
            IofFeatureMapper.displayLabel(IofControlFeature.PATH_JUNCTION),
            IofFeatureMapper.displayLabel(IofControlFeature.ROAD_BEND),
            IofFeatureMapper.displayLabel(IofControlFeature.PATH_END),
        )
        labels.forEach { label ->
            assertTrue("expected readable generic feature label, got '$label'", label.isNotBlank())
        }
    }

    @Test
    fun displayLabel_isReadableEnglish() {
        assertEquals("Bench", IofFeatureMapper.displayLabel(IofControlFeature.BENCH))
        assertEquals("Distinct tree", IofFeatureMapper.displayLabel(IofControlFeature.TREE))
        assertEquals("Boulder", IofFeatureMapper.displayLabel(IofControlFeature.ROCK))
        assertEquals("Cliff", IofFeatureMapper.displayLabel(IofControlFeature.CLIFF))
        assertEquals("Power pylon", IofFeatureMapper.displayLabel(IofControlFeature.POWER_PYLON))
        assertEquals("", IofFeatureMapper.displayLabel(IofControlFeature.NONE))
    }
}
