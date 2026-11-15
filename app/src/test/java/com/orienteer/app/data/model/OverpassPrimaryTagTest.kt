package com.orienteer.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OverpassPrimaryTagTest {

    @Test
    fun primaryTag_prefersAmenityOverHighwayWhenBothPresent() {
        val element = OverpassElement(
            type = "node",
            id = 42L,
            lat = 56.95,
            lon = 24.10,
            tags = mapOf(
                "highway" to "footway",
                "amenity" to "bench"
            ),
            center = null
        )
        assertEquals("amenity=bench", element.primaryTag)
    }
}
