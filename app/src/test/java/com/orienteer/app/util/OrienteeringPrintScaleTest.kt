package com.orienteer.app.util

import com.orienteer.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrienteeringPrintScaleTest {

    @Test
    fun catalog_isIofAlignedRatiosFineToCoarse() {
        assertEquals(
            listOf(4000, 5000, 7500, 10000, 15000),
            OrienteeringPrintScale.entries.map { it.ratio }
        )
        assertEquals("1:10000", OrienteeringPrintScale.ONE_TO_10000.label)
    }

    @Test
    fun fittingScales_smallSpan_includesFineScales() {
        // ~400 m x 400 m into a large frame → even 1:4000 fits
        val frame = MapFrameMm(widthMm = 160.0, heightMm = 200.0)
        val span = RouteSpanM(widthM = 400.0, heightM = 400.0)
        val fitting = OrienteeringPrintScale.fittingScales(span, frame)
        assertTrue(fitting.contains(OrienteeringPrintScale.ONE_TO_4000))
        assertTrue(fitting.contains(OrienteeringPrintScale.ONE_TO_15000))
        assertEquals(OrienteeringPrintScale.entries, fitting)
    }

    @Test
    fun fittingScales_largeSpan_onlyCoarse() {
        // 1:4000 on 160mm covers 640 m; 1:15000 covers 2400 m
        val frame = MapFrameMm(widthMm = 160.0, heightMm = 200.0)
        val span = RouteSpanM(widthM = 1800.0, heightM = 1800.0)
        val fitting = OrienteeringPrintScale.fittingScales(span, frame)
        assertFalse(fitting.contains(OrienteeringPrintScale.ONE_TO_4000))
        assertFalse(fitting.contains(OrienteeringPrintScale.ONE_TO_5000))
        assertTrue(fitting.contains(OrienteeringPrintScale.ONE_TO_15000))
    }

    @Test
    fun fittingScales_mediumSpan_offersAllCoarserThatFit() {
        // Typical PDF map band ≈ 184×250 mm; 1.2 km box fits 1:7500 and coarser
        val frame = MapFrameMm(widthMm = 184.0, heightMm = 250.0)
        val span = RouteSpanM(widthM = 1200.0, heightM = 1200.0)
        val fitting = OrienteeringPrintScale.fittingScales(span, frame)
        assertTrue(fitting.size >= 3)
        assertTrue(fitting.contains(OrienteeringPrintScale.ONE_TO_7500))
        assertTrue(fitting.contains(OrienteeringPrintScale.ONE_TO_10000))
        assertTrue(fitting.contains(OrienteeringPrintScale.ONE_TO_15000))
        assertFalse(fitting.contains(OrienteeringPrintScale.ONE_TO_4000))
    }

    @Test
    fun routeSpanForPrintScale_ignoresExplodingPolyline() {
        val controls = listOf(
            GeoPoint(56.95, 24.10),
            GeoPoint(56.952, 24.10),
            GeoPoint(56.952, 24.103)
        )
        // Far-away polyline vertex would otherwise force only the coarsest scale
        val wildPath = controls + GeoPoint(57.05, 24.30)
        val span = routeSpanForPrintScale(controls, wildPath)!!
        val controlOnly = routeSpanMeters(controls)!!
        assertTrue(span.widthM <= controlOnly.widthM * 1.01)
        assertTrue(span.heightM <= controlOnly.heightM * 1.01)
    }

    @Test
    fun pdfVectorCourse_circleMm_matchesIsomFootprint() {
        // 75 m at 1:15 000 → 5 mm; at 1:10 000 → 7.5 mm
        assertEquals(5.0, PdfVectorCourse.controlCircleDiameterMm(15_000), 0.01)
        assertEquals(7.5, PdfVectorCourse.controlCircleDiameterMm(10_000), 0.01)
    }

    @Test
    fun pointsForPdfExportBounds_dropsCountryScaleOutlier() {
        val controls = listOf(
            GeoPoint(56.95, 24.10),
            GeoPoint(56.96, 24.12),
            GeoPoint(56.955, 24.14)
        )
        val near = GeoPoint(56.958, 24.11)
        val far = GeoPoint(57.5, 25.5) // ~tens of km away — must not frame the PDF
        val pts = pointsForPdfExportBounds(controls, listOf(near, far))
        assertTrue(pts.containsAll(controls))
        assertTrue(pts.contains(near))
        assertFalse(pts.contains(far))
    }

    @Test
    fun fittingScales_oversized_returnsEmpty() {
        val frame = MapFrameMm(widthMm = 160.0, heightMm = 200.0)
        val span = RouteSpanM(widthM = 5000.0, heightM = 5000.0)
        assertTrue(OrienteeringPrintScale.fittingScales(span, frame).isEmpty())
    }

    @Test
    fun recommend_shortCourse_prefersSprintScaleAmongFitting() {
        val fitting = listOf(
            OrienteeringPrintScale.ONE_TO_4000,
            OrienteeringPrintScale.ONE_TO_5000,
            OrienteeringPrintScale.ONE_TO_15000
        )
        assertEquals(
            OrienteeringPrintScale.ONE_TO_4000,
            OrienteeringPrintScale.recommend(courseDistanceM = 2500.0, fitting = fitting)
        )
    }

    @Test
    fun recommend_longCourse_prefersCoarseAmongFitting() {
        val fitting = listOf(
            OrienteeringPrintScale.ONE_TO_10000,
            OrienteeringPrintScale.ONE_TO_15000
        )
        assertEquals(
            OrienteeringPrintScale.ONE_TO_10000,
            OrienteeringPrintScale.recommend(courseDistanceM = 9000.0, fitting = fitting)
        )
    }

    @Test
    fun recommend_preferredMissing_usesFinestFitting() {
        val fitting = listOf(
            OrienteeringPrintScale.ONE_TO_10000,
            OrienteeringPrintScale.ONE_TO_15000
        )
        // Short course would prefer 4k, but only coarse fit → finest fitting
        assertEquals(
            OrienteeringPrintScale.ONE_TO_10000,
            OrienteeringPrintScale.recommend(courseDistanceM = 2000.0, fitting = fitting)
        )
    }

    @Test
    fun recommend_emptyFitting_returnsNull() {
        assertNull(
            OrienteeringPrintScale.recommend(courseDistanceM = 5000.0, fitting = emptyList())
        )
    }

    @Test
    fun resolveExportScale_usesFitToPageWhenNothingFits() {
        assertEquals(
            PdfExportScale.FitToPage,
            OrienteeringPrintScale.resolveExportScale(
                courseDistanceM = 5000.0,
                span = RouteSpanM(5000.0, 5000.0),
                frame = MapFrameMm(160.0, 200.0)
            )
        )
    }

    @Test
    fun resolveExportScale_neverReturnsNonFittingFixed() {
        val frame = MapFrameMm(160.0, 200.0)
        val span = RouteSpanM(1800.0, 1800.0)
        val resolved = OrienteeringPrintScale.resolveExportScale(8000.0, span, frame)
        val fixed = resolved as PdfExportScale.Fixed
        assertTrue(
            OrienteeringPrintScale.fittingScales(span, frame).contains(fixed.scale)
        )
    }

    @Test
    fun markerScaleForPrint_finerIsLargerThan15000() {
        val baseline = OrienteeringPrintScale.markerScaleForPrint(
            OrienteeringPrintScale.ONE_TO_15000
        )
        val sprint = OrienteeringPrintScale.markerScaleForPrint(
            OrienteeringPrintScale.ONE_TO_4000
        )
        val mid = OrienteeringPrintScale.markerScaleForPrint(
            OrienteeringPrintScale.ONE_TO_10000
        )
        assertTrue(baseline < 1f) // smaller than on-screen UI (1f)
        assertTrue(mid > baseline)
        assertTrue(sprint > mid)
    }

    @Test
    fun groundCoverageMeters_matchesFrameTimesRatio() {
        val frame = MapFrameMm(100.0, 200.0)
        assertEquals(
            1000.0,
            OrienteeringPrintScale.ONE_TO_10000.groundWidthM(frame),
            0.01
        )
        assertEquals(
            2000.0,
            OrienteeringPrintScale.ONE_TO_10000.groundHeightM(frame),
            0.01
        )
    }

    @Test
    fun nicePrintRatio_snapsUpOn500Below10k() {
        assertEquals(7_500, nicePrintRatio(7_500))
        assertEquals(8_000, nicePrintRatio(7_501))
        assertEquals(8_500, nicePrintRatio(8_201))
        assertEquals(9_500, nicePrintRatio(9_100))
    }

    @Test
    fun nicePrintRatio_uses1kThen2500Above10k() {
        assertEquals(10_000, nicePrintRatio(9_999))
        assertEquals(11_000, nicePrintRatio(10_001))
        assertEquals(13_000, nicePrintRatio(12_001))
        assertEquals(15_000, nicePrintRatio(14_500))
        assertEquals(25_000, nicePrintRatio(24_001))
        assertEquals(27_500, nicePrintRatio(26_200))
    }
}
