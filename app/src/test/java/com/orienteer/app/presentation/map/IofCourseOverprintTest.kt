package com.orienteer.app.presentation.map

import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IofCourseOverprintTest {

    @Test
    fun shortenLeg_trimsBothEnds() {
        val from = GeoPoint(56.95, 24.10)
        val to = GeoUtils.offsetPoint(from, 500.0, 90.0)
        val shortened = IofCourseGeometry.shortenLeg(from, to, 37.5, 37.5)
        assertNotNull(shortened)
        val (a, b) = shortened!!
        val remaining = GeoUtils.distanceMeters(a, b)
        assertEquals(425.0, remaining, 2.0)
    }

    @Test
    fun shortenLeg_returnsNullWhenTooShort() {
        val from = GeoPoint(56.95, 24.10)
        val to = GeoUtils.offsetPoint(from, 50.0, 0.0)
        assertNull(IofCourseGeometry.shortenLeg(from, to, 37.5, 37.5))
    }

    @Test
    fun build_loopCourse_hasStartAndNumberedControls() {
        val start = GeoPoint(56.95, 24.10)
        val c1 = GeoUtils.offsetPoint(start, 200.0, 45.0)
        val c2 = GeoUtils.offsetPoint(start, 200.0, 135.0)
        val checkpoints = listOf(
            Checkpoint(id = 0, position = start, description = "S", isStart = true),
            Checkpoint(id = 1, position = c1, description = "1"),
            Checkpoint(id = 2, position = c2, description = "2"),
            Checkpoint(id = 0, position = start, description = "F", isStart = true)
        )
        val plan = IofCourseGeometry.build(checkpoints)
        assertTrue(plan.symbols.any { it is IofCourseGeometry.Symbol.Start })
        assertEquals(2, plan.symbols.count { it is IofCourseGeometry.Symbol.Control })
        assertTrue(plan.symbols.none { it is IofCourseGeometry.Symbol.Finish })
        assertTrue(plan.legs.isNotEmpty())
    }

    @Test
    fun build_oneWay_hasFinishSymbol() {
        val start = GeoPoint(56.95, 24.10)
        val mid = GeoUtils.offsetPoint(start, 300.0, 90.0)
        val finish = GeoUtils.offsetPoint(start, 500.0, 90.0)
        val checkpoints = listOf(
            Checkpoint(id = 0, position = start, description = "S", isStart = true),
            Checkpoint(id = 1, position = mid, description = "1"),
            Checkpoint(id = 0, position = finish, description = "F", isStart = true)
        )
        val plan = IofCourseGeometry.build(checkpoints)
        assertTrue(plan.symbols.any { it is IofCourseGeometry.Symbol.Finish })
    }

    @Test
    fun fromStorage_migratesLegacyOrienteering() {
        assertEquals(
            com.orienteer.app.data.local.BasemapStyle.PSEUDO_O,
            com.orienteer.app.data.local.BasemapStyle.fromStorage("ORIENTEERING")
        )
    }
}
