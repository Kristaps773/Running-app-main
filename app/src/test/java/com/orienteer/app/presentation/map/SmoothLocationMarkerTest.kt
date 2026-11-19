package com.orienteer.app.presentation.map

import com.orienteer.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SmoothLocationMarkerTest {

    @Test
    fun lerp_midpoint() {
        val from = GeoPoint(56.0, 24.0)
        val to = GeoPoint(57.0, 26.0)
        val mid = SmoothLocationMarker.lerp(from, to, 0.5f)
        assertEquals(56.5, mid.latitude, 1e-9)
        assertEquals(25.0, mid.longitude, 1e-9)
    }

    @Test
    fun lerp_endpoints() {
        val from = GeoPoint(10.0, 20.0)
        val to = GeoPoint(11.0, 21.0)
        assertEquals(from, SmoothLocationMarker.lerp(from, to, 0f))
        assertEquals(to, SmoothLocationMarker.lerp(from, to, 1f))
    }

    @Test
    fun lerp_clampsOutsideUnitInterval() {
        val from = GeoPoint(0.0, 0.0)
        val to = GeoPoint(10.0, 10.0)
        assertEquals(from, SmoothLocationMarker.lerp(from, to, -1f))
        assertEquals(to, SmoothLocationMarker.lerp(from, to, 2f))
    }
}
