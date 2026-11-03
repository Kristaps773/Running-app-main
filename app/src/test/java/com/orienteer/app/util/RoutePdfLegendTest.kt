package com.orienteer.app.util

import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePdfLegendTest {

    @Test
    fun includePdfLegends_defaultsOff() {
        assertFalse(UserPreferencesStore.DEFAULT_INCLUDE_PDF_LEGENDS)
        assertFalse(MapDisplayPreferences().includePdfLegends)
    }

    @Test
    fun legendLines_useCheckpointDescriptionsAndLabels() {
        val checkpoints = listOf(
            Checkpoint(
                id = 0,
                position = GeoPoint(56.95, 24.1),
                description = "Start / Finish — return here.",
                isStart = true
            ),
            Checkpoint(
                id = 1,
                position = GeoPoint(56.951, 24.11),
                description = "Checkpoint 1: Near a bench called \"Park\".",
                isStart = false
            ),
            Checkpoint(
                id = 2,
                position = GeoPoint(56.952, 24.12),
                description = "Checkpoint 2: Near a fountain.",
                isStart = false
            )
        )
        val lines = RoutePdfLegendBuilder.lines(checkpoints)
        assertEquals(3, lines.size)
        assertEquals("S", lines[0].label)
        assertTrue(lines[0].text.contains("Start / Finish"))
        assertEquals("1", lines[1].label)
        assertTrue(lines[1].text.contains("bench"))
        assertEquals("2", lines[2].label)
    }

    @Test
    fun controlSheet_mapsLandmarkTypesToFeatureColumn() {
        val checkpoints = listOf(
            Checkpoint(
                id = 0,
                position = GeoPoint(56.95, 24.1),
                description = "Start / Finish — return here.",
                isStart = true
            ),
            Checkpoint(
                id = 1,
                position = GeoPoint(56.951, 24.11),
                description = "Checkpoint 1: Near a bench.",
                landmarkType = "amenity=bench",
                isStart = false,
                distanceFromPrev = 420.0
            ),
            Checkpoint(
                id = 2,
                position = GeoPoint(56.952, 24.12),
                description = "Checkpoint 2",
                landmarkType = null,
                isStart = false,
                distanceFromPrev = 310.0
            )
        )
        val rows = ControlDescriptionSheetBuilder.rows(checkpoints)
        assertEquals(3, rows.size)
        assertTrue(rows[0] is ControlDescriptionRow.Start)
        val cp1 = rows[1] as ControlDescriptionRow.Control
        assertEquals(1, cp1.number)
        assertEquals(IofControlFeature.BENCH, cp1.feature)
        assertEquals("Bench", cp1.featureLabel)
        assertEquals("Near a bench.", cp1.description)
        assertEquals("420 m", cp1.legDistanceLabel)
        val cp2 = rows[2] as ControlDescriptionRow.Control
        assertEquals(IofControlFeature.NONE, cp2.feature)
        assertEquals("", cp2.featureLabel)
        assertEquals("310 m", cp2.legDistanceLabel)
    }

    @Test
    fun controlSheet_columnHeadersExplainEachColumn() {
        assertEquals(
            listOf("No.", "Dist.", "Feature", "Description"),
            ControlDescriptionSheet.COLUMN_HEADERS
        )
        assertEquals(4, ControlDescriptionSheet.COLUMN_COUNT)
    }

    @Test
    fun controlSheet_hidesReturnToStartFinishRow() {
        val start = GeoPoint(56.95, 24.1)
        val checkpoints = listOf(
            Checkpoint(id = 0, position = start, description = "Start", isStart = true),
            Checkpoint(id = 1, position = GeoPoint(56.951, 24.11), description = "CP1"),
            Checkpoint(
                id = 2,
                position = start,
                description = "Finish",
                isStart = true,
                distanceFromPrev = 100.0
            )
        )
        val rows = ControlDescriptionSheetBuilder.rows(checkpoints)
        assertEquals(2, rows.size)
        assertTrue(rows[0] is ControlDescriptionRow.Start)
        assertTrue(rows[1] is ControlDescriptionRow.Control)
    }

    @Test
    fun controlSheet_oneWayIncludesFinishRow() {
        val checkpoints = listOf(
            Checkpoint(
                id = 0,
                position = GeoPoint(56.95, 24.1),
                description = "Start",
                isStart = true
            ),
            Checkpoint(id = 1, position = GeoPoint(56.951, 24.11), description = "CP1"),
            Checkpoint(
                id = 2,
                position = GeoPoint(56.96, 24.2),
                description = "Finish",
                isStart = true,
                distanceFromPrev = 350.0
            )
        )
        val rows = ControlDescriptionSheetBuilder.rows(checkpoints)
        assertEquals(3, rows.size)
        val finish = rows[2] as ControlDescriptionRow.Finish
        assertEquals("350 m", finish.distanceFromLastLabel)
    }

    @Test
    fun pageLayout_controlSheet_reservesBottomBand() {
        val without = PdfPageLayout.computeForControlSheet(includeSheet = false, gridRowCount = 0)
        val with = PdfPageLayout.computeForControlSheet(includeSheet = true, gridRowCount = 8)
        assertNotNull(with.legendRect)
        assertTrue(with.mapRect.height() < without.mapRect.height())
        assertTrue(with.legendRect!!.top >= with.mapRect.bottom)
        val rowH = PdfPageLayout.controlSheetRowHeight(with.legendRect!!, 8)
        assertTrue(rowH >= 10f)
    }

    @Test
    fun pageLayout_withoutLegends_mapUsesFullContentBand() {
        val layout = PdfPageLayout.compute(
            includeLegends = false,
            legendLineCount = 0
        )
        assertNull(layout.legendRect)
        assertTrue(layout.mapRect.height() > 500f)
        assertTrue(layout.mapRect.top > layout.titleBaseline)
    }

    @Test
    fun fitRectPreserveAspect_doesNotStretchTallCaptureIntoWideBand() {
        val dst = PdfRect(0f, 0f, 500f, 600f)
        val fitted = PdfPageLayout.fitRectPreserveAspect(1080f, 2200f, dst)
        val srcAspect = 1080f / 2200f
        val outAspect = fitted.width() / fitted.height()
        assertEquals(srcAspect, outAspect, 0.001f)
        assertTrue(fitted.width() <= dst.width() + 0.5f)
        assertTrue(fitted.height() <= dst.height() + 0.5f)
    }

    @Test
    fun centerCropSrcRect_matchesDestinationAspect() {
        val dst = PdfRect(36f, 100f, 559f, 800f) // ~ PDF map band aspect
        val crop = PdfPageLayout.centerCropSrcRect(1080, 2200, dst)
        val cropAspect = crop.width() / crop.height()
        val dstAspect = dst.width() / dst.height()
        assertEquals(dstAspect, cropAspect, 0.01f)
        assertTrue(crop.height() < 2200f) // tall source is cropped
        assertEquals(0f, crop.left, 0.1f)
        assertEquals(1080f, crop.right, 0.1f)
    }

    @Test
    fun pdfViewportSizePx_tallPhone_usesFullWidthCroppedHeight() {
        val pdfAspect = 523f / 500f // wider than tall phone content
        val (vw, vh) = PdfPageLayout.pdfViewportSizePx(1080, 2200, pdfAspect)
        assertEquals(1080, vw)
        assertTrue(vh < 2200)
        assertEquals(pdfAspect, vw.toFloat() / vh, 0.02f)
    }

    @Test
    fun safeZoomBorder_keepsUsableViewportPositive_whenCropChromeIsHuge() {
        val mapW = 1080
        val mapH = 2200
        // Short PDF map band (legends on) → wide aspect → large vertical crop.
        val pdfAspect = 523f / 280f
        val (vw, vh) = PdfPageLayout.pdfViewportSizePx(mapW, mapH, pdfAspect)
        val cropChromePad = 64 + maxOf((mapW - vw) / 2, (mapH - vh) / 2)
        // Regression: treating crop chrome as zoom border vs full map ANRs (ORIENTEERRUN-J).
        assertTrue(mapW - 2 * cropChromePad <= 0)

        val pad = PdfPageLayout.safeZoomBorderPx(vw, vh, 64)
        assertTrue(vw - 2 * pad >= 2)
        assertTrue(vh - 2 * pad >= 2)
        assertTrue(pad in 0..64)
    }

    @Test
    fun pageLayout_withLegends_reservesBottomBand() {
        val without = PdfPageLayout.compute(includeLegends = false, legendLineCount = 0)
        val with = PdfPageLayout.compute(includeLegends = true, legendLineCount = 5)
        assertNotNull(with.legendRect)
        assertTrue(with.mapRect.height() < without.mapRect.height())
        assertTrue(with.legendRect!!.top >= with.mapRect.bottom)
        assertTrue(with.legendRect!!.bottom <= PdfPageLayout.PAGE_HEIGHT)
    }

    @Test
    fun scaleBar_picksNiceRoundDistanceAndRatio() {
        // 1:10000 → 1 mm = 10 m; at 2 px/mm, 1 px = 5 m
        val bar = PdfMapScaleBar.compute(
            metresPerPixel = 5.0,
            maxBarWidthPx = 200,
            ratioLabel = PdfMapScaleBar.formatRatioLabel(10000)
        )
        assertTrue(bar.distanceM in setOf(100.0, 200.0, 250.0, 500.0, 1000.0))
        assertTrue(bar.widthPx in 1..200)
        assertTrue(bar.distanceLabel.contains("m") || bar.distanceLabel.contains("km"))
        assertEquals("1:10 000", bar.ratioLabel)
        assertTrue(bar.segmentCount >= 2)
    }

    @Test
    fun formatRatioLabel_groupsThousands() {
        assertEquals("1:4 000", PdfMapScaleBar.formatRatioLabel(4000))
        assertEquals("1:15 000", PdfMapScaleBar.formatRatioLabel(15000))
    }

    @Test
    fun scaleBar_cornerAvoidsControlPoints() {
        val mapW = 400f
        val mapH = 600f
        val keyW = 80f
        val keyH = 28f
        // Cluster of points near top-left and top-right and bottom-left → prefer bottom-right
        val points = listOf(
            20f to 20f,
            40f to 30f,
            mapW - 30f to 25f,
            25f to mapH - 30f
        )
        val corner = PdfMapScaleBar.pickCorner(
            mapWidth = mapW,
            mapHeight = mapH,
            barWidth = keyW,
            barHeight = keyH,
            controlPointsPx = points,
            paddingPx = 8f
        )
        assertEquals(PdfMapScaleBar.Corner.BOTTOM_RIGHT, corner)
    }
}
