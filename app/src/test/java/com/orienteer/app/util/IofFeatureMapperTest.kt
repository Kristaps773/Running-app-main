package com.orienteer.app.util

import org.junit.Assert.assertEquals
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
    }

    @Test
    fun unknownTag_mapsToSpecial() {
        assertEquals(
            IofControlFeature.SPECIAL,
            IofFeatureMapper.fromLandmarkType("amenity=library")
        )
    }

    @Test
    fun displayLabel_isReadableEnglish() {
        assertEquals("Bench", IofFeatureMapper.displayLabel(IofControlFeature.BENCH))
        assertEquals("Tree", IofFeatureMapper.displayLabel(IofControlFeature.TREE))
        assertEquals("", IofFeatureMapper.displayLabel(IofControlFeature.NONE))
    }
}
