package com.orienteer.app.presentation.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NorthLinesOverlayTest {

    @Test
    fun longitudesAcrossSpan_coversWestAndEast() {
        val lines = NorthLinesOverlay.longitudesAcrossSpan(
            lonWest = 24.0,
            lonEast = 24.08,
            lineCount = 3
        )
        assertEquals(3, lines.size)
        assertEquals(24.0, lines.first(), 1e-9)
        assertEquals(24.08, lines.last(), 1e-9)
        assertEquals(24.04, lines[1], 1e-9)
    }

    @Test
    fun longitudesAcrossSpan_doesNotClusterOnlyOnWest() {
        // Old bug: tiny step + max 4 lines → all near west. Even span must reach east.
        val west = 10.0
        val east = 10.5
        val lines = NorthLinesOverlay.longitudesAcrossSpan(west, east, lineCount = 4)
        assertEquals(4, lines.size)
        assertEquals(west, lines.first(), 1e-9)
        assertEquals(east, lines.last(), 1e-9)
        val midSpan = (east - west) / 2.0
        assertTrue(
            "second line should not sit in the first quarter of the span",
            lines[1] >= west + midSpan * 0.2
        )
    }

    @Test
    fun longitudesAcrossSpan_twoLines_areEndpoints() {
        val lines = NorthLinesOverlay.longitudesAcrossSpan(1.0, 2.0, lineCount = 2)
        assertEquals(listOf(1.0, 2.0), lines)
    }

    @Test
    fun meridianArrowAlongFractions_sparseSpacingOnTallSegment() {
        val fracs = NorthLinesOverlay.meridianArrowAlongFractions(900f)
        assertTrue("expected a few sparse arrows", fracs.isNotEmpty())
        assertTrue(fracs.size <= 3)
        assertTrue(fracs.first() > 0.05f)
        assertTrue(fracs.last() < 0.95f)
        for (i in 1 until fracs.size) {
            assertTrue(fracs[i] > fracs[i - 1])
        }
    }

    @Test
    fun meridianArrowAlongFractions_skipsVeryShortSegment() {
        assertTrue(NorthLinesOverlay.meridianArrowAlongFractions(30f).isEmpty())
    }
}
