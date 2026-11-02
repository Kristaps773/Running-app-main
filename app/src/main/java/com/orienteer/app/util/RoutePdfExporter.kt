package com.orienteer.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.view.View
import androidx.core.content.FileProvider
import com.orienteer.app.R
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.presentation.map.applyRouteMapOverlays
import com.orienteer.app.presentation.map.boundingBoxForPoints
import com.orienteer.app.presentation.map.detachLocationMarker
import com.orienteer.app.ui.theme.MapOverlayColors
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * Exports a printable PDF at a fixed orienteering print scale (or fit-to-page).
 * Captures the on-screen MapView after temporarily shrinking control badges
 * and hiding the GPS location marker.
 */
object RoutePdfExporter {

    private const val OOMAP_MAP_ID = "69c2676672a49"
    /** Wait after camera settle so OSM tiles can download into the export MapView. */
    private const val EXPORT_CAPTURE_DELAY_MS = 2800L
    private const val EXPORT_FIT_PADDING_PX = 64
    private const val CAPTURE_SCALE = 2

    /**
     * Every fixed IOF scale that fits [route] on the PDF map frame (fine → coarse),
     * always ending with [PdfExportScale.FitToPage].
     */
    fun availableExportScales(route: Route, includePdfLegends: Boolean): List<PdfExportScale> {
        val sheet = ControlDescriptionSheetBuilder.fromRoute(route)
        val layout = PdfPageLayout.computeForControlSheet(includePdfLegends, sheet.gridRowCount)
        val frame = PdfPageLayout.mapFrameMm(layout.mapRect)
        val span = printScaleSpan(route) ?: return listOf(PdfExportScale.FitToPage)
        val fitting = OrienteeringPrintScale.fittingScales(span, frame)
            .map { PdfExportScale.Fixed(it) }
        return fitting + PdfExportScale.FitToPage
    }

    fun recommendedExportScale(route: Route, includePdfLegends: Boolean): PdfExportScale {
        val sheet = ControlDescriptionSheetBuilder.fromRoute(route)
        val layout = PdfPageLayout.computeForControlSheet(includePdfLegends, sheet.gridRowCount)
        val frame = PdfPageLayout.mapFrameMm(layout.mapRect)
        val span = printScaleSpan(route) ?: return PdfExportScale.FitToPage
        val distance = route.pathDistanceM ?: route.airDistanceM.coerceAtLeast(route.totalDistanceM)
        return OrienteeringPrintScale.resolveExportScale(distance, span, frame)
    }

    private fun printScaleSpan(route: Route): RouteSpanM? =
        routeSpanForPrintScale(
            checkpoints = route.checkpoints.map { it.position },
            polyline = route.polylinePoints
        )

    /**
     * Fits the live map to [exportScale], waits for basemap tiles, captures, then restores
     * the previous camera. Uses the on-screen MapView so OSM tiles actually load (off-screen
     * MapViews often export overlays only).
     */
    fun exportToPdf(
        context: Context,
        mapView: View,
        route: Route,
        includePdfLegends: Boolean = false,
        exportScale: PdfExportScale = recommendedExportScale(route, includePdfLegends),
        mapPrefs: MapDisplayPreferences = MapDisplayPreferences(),
        mapColors: MapOverlayColors = MapOverlayColors(
            plannedPath = androidx.compose.ui.graphics.Color(0xFF2E7D32),
            actualPath = androidx.compose.ui.graphics.Color(0xFF1565C0),
            airLeg = androidx.compose.ui.graphics.Color(0xFFAD0078),
            airLegHalo = androidx.compose.ui.graphics.Color(0x66FFFFFF)
        ),
        primaryColorArgb: Int = 0xFF2E7D32.toInt(),
        startColorArgb: Int = 0xFF00897B.toInt(),
        onComplete: (File?) -> Unit = {}
    ) {
        val liveMap = mapView as? MapView
        val box = boundingBoxFromRoute(route)
        if (liveMap == null || box == null || liveMap.width <= 0 || liveMap.height <= 0) {
            onComplete(null)
            return
        }

        val controlSheet = ControlDescriptionSheetBuilder.fromRoute(route)
        val layout = PdfPageLayout.computeForControlSheet(includePdfLegends, controlSheet.gridRowCount)
        val frameMm = PdfPageLayout.mapFrameMm(layout.mapRect)
        val printMarkerScale = OrienteeringPrintScale.markerScaleForExport(exportScale)
        val basemapStyle = mapPrefs.basemapStyle
        // Leg distances belong in the control sheet, not as pills on the PDF map lines.
        val pdfMapPrefs = mapPrefs.copy(showAirLegDistanceLabels = false)

        Configuration.getInstance().userAgentValue = context.packageName

        val savedZoom = liveMap.zoomLevelDouble
        val savedCenter = org.osmdroid.util.GeoPoint(
            liveMap.mapCenter.latitude,
            liveMap.mapCenter.longitude
        )

        liveMap.post {
            // Smaller control badges for print; location marker removed again right before draw
            // (Compose may re-add it while tiles load).
            applyRouteMapOverlays(
                map = liveMap,
                route = route,
                prefs = pdfMapPrefs,
                mapColors = mapColors,
                primaryColorArgb = primaryColorArgb,
                startColorArgb = startColorArgb,
                basemapStyle = basemapStyle,
                markerScale = printMarkerScale
            )
            liveMap.detachLocationMarker()
            applyExportCamera(liveMap, box, exportScale, frameMm)
            liveMap.invalidate()
            liveMap.postDelayed({ liveMap.invalidate() }, 400L)
            liveMap.postDelayed({
                // Ensure print-sized badges + no GPS pin at capture time.
                applyRouteMapOverlays(
                    map = liveMap,
                    route = route,
                    prefs = pdfMapPrefs,
                    mapColors = mapColors,
                    primaryColorArgb = primaryColorArgb,
                    startColorArgb = startColorArgb,
                    basemapStyle = basemapStyle,
                    markerScale = printMarkerScale
                )
                liveMap.detachLocationMarker()
                liveMap.invalidate()
                val w = liveMap.width.coerceAtLeast(100)
                val h = liveMap.height.coerceAtLeast(100)
                val bitmap = Bitmap.createBitmap(
                    w * CAPTURE_SCALE,
                    h * CAPTURE_SCALE,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.scale(CAPTURE_SCALE.toFloat(), CAPTURE_SCALE.toFloat())
                liveMap.draw(canvas)
                val pdfAspect = (frameMm.widthMm / frameMm.heightMm).toFloat()
                val (viewportW, _) = PdfPageLayout.pdfViewportSizePx(w, h, pdfAspect)
                drawScaleBarOnMap(
                    canvas = canvas,
                    map = liveMap,
                    route = route,
                    exportScale = exportScale,
                    frameMm = frameMm,
                    captureW = viewportW
                )
                canvas.restore()

                // Restore interactive preview overlays (Compose will refresh location marker).
                applyRouteMapOverlays(
                    map = liveMap,
                    route = route,
                    prefs = mapPrefs,
                    mapColors = mapColors,
                    primaryColorArgb = primaryColorArgb,
                    startColorArgb = startColorArgb,
                    basemapStyle = basemapStyle,
                    markerScale = 1f
                )
                liveMap.controller.setZoom(savedZoom)
                liveMap.controller.setCenter(savedCenter)
                liveMap.invalidate()

                val file = writePdf(
                    context = context,
                    bitmap = bitmap,
                    route = route,
                    layout = layout,
                    controlSheet = if (includePdfLegends) controlSheet else null,
                    exportScale = exportScale
                )
                bitmap.recycle()
                file?.let { openPdf(context, it) }
                onComplete(file)
            }, EXPORT_CAPTURE_DELAY_MS)
        }
    }

    /**
     * Frame the live map for the region that will remain after PDF center-crop
     * (phone aspect ≠ page map band). Otherwise Fit-to-page / 1:N looks fine on
     * the tall screen and then the top/bottom of the course are chopped off.
     */
    private fun applyExportCamera(
        map: MapView,
        box: BoundingBox,
        exportScale: PdfExportScale,
        frameMm: MapFrameMm
    ) {
        val pdfAspect = (frameMm.widthMm / frameMm.heightMm.coerceAtLeast(0.01)).toFloat()
        val (viewportW, viewportH) = PdfPageLayout.pdfViewportSizePx(
            map.width,
            map.height,
            pdfAspect
        )
        val centerLat = (box.latNorth + box.latSouth) / 2.0
        val centerLon = (box.lonEast + box.lonWest) / 2.0
        val tileMin = map.tileProvider.minimumZoomLevel.toDouble()
        val tileMax = map.tileProvider.maximumZoomLevel.toDouble()
        when (exportScale) {
            is PdfExportScale.Fixed -> {
                val groundWidthM = exportScale.scale.groundWidthM(frameMm)
                val mpp = groundWidthM / viewportW.coerceAtLeast(1)
                val zoom = zoomForMetresPerPixel(centerLat, mpp).coerceIn(tileMin, tileMax)
                // Temporarily allow zoom-out if preview had raised minZoom.
                val prevMin = map.minZoomLevel
                if (zoom < prevMin) {
                    map.setMinZoomLevel(zoom.coerceAtLeast(tileMin))
                }
                map.controller.setZoom(zoom)
                map.controller.setCenter(
                    org.osmdroid.util.GeoPoint(centerLat, centerLon)
                )
                // If 1:N still clips the bbox in the PDF viewport, fall back to fit.
                if (!bboxFitsViewport(map, box, viewportW, viewportH)) {
                    zoomToPdfViewport(map, box, viewportW, viewportH, tileMax)
                }
            }
            PdfExportScale.FitToPage -> {
                val prevMin = map.minZoomLevel
                map.setMinZoomLevel(tileMin)
                zoomToPdfViewport(map, box, viewportW, viewportH, tileMax)
                // Keep minZoom lowered through capture; restore happens with overlays.
                if (map.zoomLevelDouble < prevMin) {
                    map.setMinZoomLevel(map.zoomLevelDouble.coerceAtLeast(tileMin))
                }
            }
        }
    }

    /**
     * Fit [box] so it remains inside the PDF center-crop ([vw]×[vh]), using osmdroid's
     * zoomToBoundingBox with extra padding for the cropped phone chrome.
     */
    private fun zoomToPdfViewport(
        map: MapView,
        box: BoundingBox,
        vw: Int,
        vh: Int,
        maxZoom: Double
    ) {
        val extraPadX = ((map.width - vw).coerceAtLeast(0)) / 2
        val extraPadY = ((map.height - vh).coerceAtLeast(0)) / 2
        val pad = EXPORT_FIT_PADDING_PX + maxOf(extraPadX, extraPadY)
        map.zoomToBoundingBox(box, false, pad, maxZoom, null)
    }

    /** True when [box] lies inside the center [vw]×[vh] region of the map view. */
    private fun bboxFitsViewport(
        map: MapView,
        box: BoundingBox,
        vw: Int,
        vh: Int
    ): Boolean {
        val proj = map.projection ?: return true
        val pts = listOf(
            org.osmdroid.util.GeoPoint(box.latNorth, box.lonWest),
            org.osmdroid.util.GeoPoint(box.latNorth, box.lonEast),
            org.osmdroid.util.GeoPoint(box.latSouth, box.lonWest),
            org.osmdroid.util.GeoPoint(box.latSouth, box.lonEast)
        )
        val left = (map.width - vw) / 2f
        val top = (map.height - vh) / 2f
        val right = left + vw
        val bottom = top + vh
        val pad = EXPORT_FIT_PADDING_PX.toFloat()
        val point = android.graphics.Point()
        for (gp in pts) {
            proj.toPixels(gp, point)
            if (point.x < left + pad || point.x > right - pad ||
                point.y < top + pad || point.y > bottom - pad
            ) {
                return false
            }
        }
        return true
    }

    private fun drawScaleBarOnMap(
        canvas: Canvas,
        map: MapView,
        route: Route,
        exportScale: PdfExportScale,
        frameMm: MapFrameMm,
        captureW: Int
    ) {
        val mpp = when (exportScale) {
            is PdfExportScale.Fixed ->
                exportScale.scale.groundWidthM(frameMm) / captureW.coerceAtLeast(1)
            PdfExportScale.FitToPage -> {
                val centerLat = map.mapCenter.latitude
                metresPerPixelAtZoom(centerLat, map.zoomLevelDouble)
            }
        }
        val ratioLabel = when (exportScale) {
            is PdfExportScale.Fixed ->
                PdfMapScaleBar.formatRatioLabel(exportScale.scale.ratio)
            PdfExportScale.FitToPage ->
                PdfMapScaleBar.formatRatioLabel(
                    PdfMapScaleBar.approximateRatio(mpp, captureW, frameMm.widthMm)
                )
        }
        val bar = PdfMapScaleBar.compute(
            metresPerPixel = mpp,
            maxBarWidthPx = (captureW * 0.32).toInt().coerceAtLeast(48),
            ratioLabel = ratioLabel
        )
        // Orienteering-style block: ratio on top, segmented bar, distance under.
        val pad = 10f
        val ratioTextSize = 13f
        val distTextSize = 10f
        val barThickness = 8f
        val blockW = bar.widthPx + pad * 2
        val blockH = pad + ratioTextSize + 4f + barThickness + 4f + distTextSize + pad
        val controls = route.checkpoints.mapNotNull { cp ->
            val pt = android.graphics.Point()
            map.projection.toPixels(
                org.osmdroid.util.GeoPoint(cp.position.latitude, cp.position.longitude),
                pt
            )
            pt.x.toFloat() to pt.y.toFloat()
        }
        val mapW = canvas.width.toFloat() / CAPTURE_SCALE
        val mapH = canvas.height.toFloat() / CAPTURE_SCALE
        // Canvas is scaled by CAPTURE_SCALE when this runs — use view-pixel sizes.
        val corner = PdfMapScaleBar.pickCorner(
            mapWidth = mapW,
            mapHeight = mapH,
            barWidth = blockW,
            barHeight = blockH,
            controlPointsPx = controls,
            paddingPx = 12f
        )
        val rect = PdfMapScaleBar.rectForCorner(
            corner,
            mapW,
            mapH,
            blockW,
            blockH,
            paddingPx = 12f
        )
        val bg = Paint().apply {
            isAntiAlias = true
            color = Color.argb(220, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val border = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#0F1A13")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val box = android.graphics.RectF(rect.left, rect.top, rect.right, rect.bottom)
        canvas.drawRoundRect(box, 4f, 4f, bg)
        canvas.drawRoundRect(box, 4f, 4f, border)

        val ratioPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#0F1A13")
            textSize = ratioTextSize
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText(
            bar.ratioLabel,
            rect.centerX(),
            rect.top + pad + ratioTextSize,
            ratioPaint
        )

        val barLeft = rect.left + pad
        val barRight = barLeft + bar.widthPx
        val barTop = rect.top + pad + ratioTextSize + 4f
        val barBottom = barTop + barThickness
        val fillBlack = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val fillWhite = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val segW = (barRight - barLeft) / bar.segmentCount
        for (i in 0 until bar.segmentCount) {
            val l = barLeft + i * segW
            val r = if (i == bar.segmentCount - 1) barRight else l + segW
            canvas.drawRect(l, barTop, r, barBottom, if (i % 2 == 0) fillBlack else fillWhite)
        }
        canvas.drawRect(barLeft, barTop, barRight, barBottom, border)

        val distPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#131E17")
            textSize = distTextSize
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("0", barLeft, barBottom + 4f + distTextSize, distPaint)
        distPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(bar.distanceLabel, barRight, barBottom + 4f + distTextSize, distPaint)
    }

    private fun routePoints(route: Route): List<GeoPoint> {
        val points = ArrayList<GeoPoint>(route.checkpoints.size + route.polylinePoints.size)
        route.checkpoints.forEach { points.add(it.position) }
        points.addAll(route.polylinePoints)
        return points
    }

    private fun boundingBoxFromRoute(route: Route): BoundingBox? =
        boundingBoxForPoints(routePoints(route))

    private fun writePdf(
        context: Context,
        bitmap: Bitmap,
        route: Route,
        layout: PdfPageRegions,
        controlSheet: ControlDescriptionSheet?,
        exportScale: PdfExportScale
    ): File? {
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val file = File(docsDir, "OrienteerRun_route_${dateStr}.pdf")

        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(
                PdfPageLayout.PAGE_WIDTH.toInt(),
                PdfPageLayout.PAGE_HEIGHT.toInt(),
                1
            ).create()
            val page = pdfDocument.startPage(pageInfo)
            val pageCanvas = page.canvas

            val mapRect = layout.mapRect
            // Phone capture is taller than the PDF map band — center-crop, never stretch.
            val srcCrop = PdfPageLayout.centerCropSrcRect(bitmap.width, bitmap.height, mapRect)
            pageCanvas.drawBitmap(
                bitmap,
                android.graphics.Rect(
                    srcCrop.left.toInt(),
                    srcCrop.top.toInt(),
                    srcCrop.right.toInt(),
                    srcCrop.bottom.toInt()
                ),
                android.graphics.RectF(mapRect.left, mapRect.top, mapRect.right, mapRect.bottom),
                null
            )

            // Alpine Trail: forest brand title, pine-charcoal body (DESIGN.md).
            val titlePaint = Paint().apply {
                isAntiAlias = true
                textSize = 14f
                isFakeBoldText = true
                textAlign = Paint.Align.LEFT
                color = Color.parseColor("#1B5E20")
            }
            val title =
                "OrienteerRun — ${GeoUtils.formatDistance(route.totalDistanceM)} • " +
                    "${route.waypointCount} CP • ${(route.estimatedDurationS / 60).toInt()} min • ${exportScale.label}"
            pageCanvas.drawText(title, PdfPageLayout.MARGIN, layout.titleBaseline, titlePaint)

            if (controlSheet != null) {
                layout.legendRect?.let { legendRect ->
                    drawControlDescriptionSheet(pageCanvas, legendRect, controlSheet)
                }
            }

            pdfDocument.finishPage(page)
            FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
            pdfDocument.close()
            file
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Labeled companion sheet under the map. Not map overprint.
     * Columns: No. | Dist. | Feature (pictogram + name) | Description.
     */
    private fun drawControlDescriptionSheet(
        canvas: Canvas,
        legendRect: PdfRect,
        sheet: ControlDescriptionSheet
    ) {
        val rowCount = sheet.gridRowCount
        val rowH = PdfPageLayout.controlSheetRowHeight(legendRect, rowCount)
        val gridWidth = legendRect.width()
        val gridHeight = rowH * rowCount
        val left = legendRect.left
        val top = legendRect.top + (legendRect.height() - gridHeight).coerceAtLeast(0f) / 2f

        // No. ~10% | Dist. ~14% | Feature ~28% | Description ~48%
        val colNoW = gridWidth * 0.10f
        val colDistW = gridWidth * 0.14f
        val colFeatW = gridWidth * 0.28f
        val colDescW = gridWidth - colNoW - colDistW - colFeatW
        val colXs = floatArrayOf(
            left,
            left + colNoW,
            left + colNoW + colDistW,
            left + colNoW + colDistW + colFeatW,
            left + gridWidth
        )

        val linePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
            color = Color.parseColor("#6A1B9A")
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#131E17")
            textAlign = Paint.Align.LEFT
        }
        val featurePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
            color = Color.parseColor("#131E17")
        }

        fun rowTop(row: Int): Float = top + row * rowH
        fun baseline(row: Int): Float = rowTop(row) + rowH * 0.68f

        // Grid
        for (r in 0..rowCount) {
            val y = top + r * rowH
            canvas.drawLine(left, y, left + gridWidth, y, linePaint)
        }
        for (x in colXs) {
            canvas.drawLine(x, top, x, top + gridHeight, linePaint)
        }

        // Row 0: title + length
        textPaint.isFakeBoldText = true
        textPaint.textSize = (rowH * 0.48f).coerceIn(7f, 11f)
        textPaint.textAlign = Paint.Align.LEFT
        val titleLine = buildString {
            append(sheet.eventTitle)
            append("  ·  ")
            append(sheet.lengthLabel)
            if (sheet.climbLabel.isNotBlank()) {
                append("  ·  ")
                append(sheet.climbLabel)
            }
        }
        canvas.drawText(titleLine, left + 4f, baseline(0), textPaint)

        // Row 1: column meanings
        textPaint.isFakeBoldText = true
        textPaint.textSize = (rowH * 0.42f).coerceIn(6.5f, 9.5f)
        ControlDescriptionSheet.COLUMN_HEADERS.forEachIndexed { i, header ->
            canvas.drawText(header, colXs[i] + 4f, baseline(1), textPaint)
        }

        textPaint.isFakeBoldText = false
        sheet.rows.forEachIndexed { index, row ->
            val gridRow = ControlDescriptionSheet.HEADER_ROWS + index
            val yBase = baseline(gridRow)
            val yTop = rowTop(gridRow)
            when (row) {
                is ControlDescriptionRow.Start -> {
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.textSize = (rowH * 0.45f).coerceIn(7f, 10f)
                    canvas.drawText("S", colXs[0] + colNoW / 2f, yBase, textPaint)
                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.textSize = (rowH * 0.38f).coerceIn(6f, 8.5f)
                    canvas.drawText("—", colXs[1] + 4f, yBase, textPaint)

                    val iconLeft = colXs[2] + 2f
                    val iconRight = iconLeft + rowH * 0.85f
                    val path = android.graphics.Path()
                    val cx = (iconLeft + iconRight) / 2f
                    val cy = yTop + rowH / 2f
                    val s = rowH * 0.28f
                    path.moveTo(cx, cy - s)
                    path.lineTo(cx + s, cy + s * 0.75f)
                    path.lineTo(cx - s, cy + s * 0.75f)
                    path.close()
                    canvas.drawPath(path, featurePaint)

                    textPaint.textSize = (rowH * 0.4f).coerceIn(6.5f, 9f)
                    canvas.drawText("Start", iconRight + 3f, yBase, textPaint)
                    canvas.drawText(
                        ellipsize(row.description, textPaint, colDescW - 8f),
                        colXs[3] + 4f,
                        yBase,
                        textPaint
                    )
                }
                is ControlDescriptionRow.Control -> {
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.textSize = (rowH * 0.45f).coerceIn(7f, 10f)
                    canvas.drawText("${row.number}", colXs[0] + colNoW / 2f, yBase, textPaint)
                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.textSize = (rowH * 0.38f).coerceIn(6f, 8.5f)
                    canvas.drawText(
                        ellipsize(row.legDistanceLabel ?: "—", textPaint, colDistW - 8f),
                        colXs[1] + 4f,
                        yBase,
                        textPaint
                    )

                    val iconLeft = colXs[2] + 2f
                    val iconRight = iconLeft + rowH * 0.85f
                    IofFeaturePictogram.draw(
                        canvas = canvas,
                        feature = row.feature,
                        left = iconLeft,
                        top = yTop + 1f,
                        right = iconRight,
                        bottom = yTop + rowH - 1f,
                        paint = featurePaint
                    )
                    textPaint.textSize = (rowH * 0.4f).coerceIn(6.5f, 9f)
                    val featText = row.featureLabel.ifBlank { "—" }
                    canvas.drawText(
                        ellipsize(featText, textPaint, colFeatW - (iconRight - colXs[2]) - 6f),
                        iconRight + 3f,
                        yBase,
                        textPaint
                    )
                    canvas.drawText(
                        ellipsize(row.description.ifBlank { featText }, textPaint, colDescW - 8f),
                        colXs[3] + 4f,
                        yBase,
                        textPaint
                    )
                }
                is ControlDescriptionRow.Finish -> {
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.textSize = (rowH * 0.45f).coerceIn(7f, 10f)
                    canvas.drawText("F", colXs[0] + colNoW / 2f, yBase, textPaint)
                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.textSize = (rowH * 0.38f).coerceIn(6f, 8.5f)
                    canvas.drawText(
                        ellipsize(row.distanceFromLastLabel ?: "—", textPaint, colDistW - 8f),
                        colXs[1] + 4f,
                        yBase,
                        textPaint
                    )

                    val iconLeft = colXs[2] + 2f
                    val iconRight = iconLeft + rowH * 0.85f
                    val cx = (iconLeft + iconRight) / 2f
                    val cy = yTop + rowH / 2f
                    canvas.drawCircle(cx, cy, rowH * 0.28f, featurePaint)
                    canvas.drawCircle(cx, cy, rowH * 0.18f, featurePaint)

                    textPaint.textSize = (rowH * 0.4f).coerceIn(6.5f, 9f)
                    canvas.drawText("Finish", iconRight + 3f, yBase, textPaint)
                    canvas.drawText(
                        ellipsize(row.description, textPaint, colDescW - 8f),
                        colXs[3] + 4f,
                        yBase,
                        textPaint
                    )
                }
            }
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 4f || text.isEmpty()) return text
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end) + ellipsis) > maxWidth) {
            end--
        }
        return if (end <= 0) ellipsis else text.take(end) + ellipsis
    }

    // Pictograms for column D
    private object IofFeaturePictogram {
        fun draw(
            canvas: Canvas,
            feature: IofControlFeature,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            paint: Paint
        ) {
            if (feature == IofControlFeature.NONE) return
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            val s = minOf(right - left, bottom - top) * 0.32f
            val prevStyle = paint.style
            val prevWidth = paint.strokeWidth
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (s * 0.18f).coerceAtLeast(0.8f)

            when (feature) {
                IofControlFeature.TREE -> {
                    val path = android.graphics.Path()
                    path.moveTo(cx, cy - s)
                    path.lineTo(cx + s * 0.85f, cy + s * 0.25f)
                    path.lineTo(cx - s * 0.85f, cy + s * 0.25f)
                    path.close()
                    canvas.drawPath(path, paint)
                    canvas.drawLine(cx, cy + s * 0.25f, cx, cy + s, paint)
                }
                IofControlFeature.BENCH -> {
                    canvas.drawLine(cx - s, cy, cx + s, cy, paint)
                    canvas.drawLine(cx - s * 0.7f, cy, cx - s * 0.7f, cy + s * 0.7f, paint)
                    canvas.drawLine(cx + s * 0.7f, cy, cx + s * 0.7f, cy + s * 0.7f, paint)
                }
                IofControlFeature.FOUNTAIN, IofControlFeature.SPRING -> {
                    canvas.drawCircle(cx, cy + s * 0.15f, s * 0.55f, paint)
                    canvas.drawLine(cx, cy - s, cx, cy - s * 0.2f, paint)
                    canvas.drawLine(cx - s * 0.35f, cy - s * 0.55f, cx, cy - s * 0.2f, paint)
                    canvas.drawLine(cx + s * 0.35f, cy - s * 0.55f, cx, cy - s * 0.2f, paint)
                }
                IofControlFeature.CROSSING -> {
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, paint)
                    canvas.drawLine(cx + s, cy - s, cx - s, cy + s, paint)
                }
                IofControlFeature.MEMORIAL, IofControlFeature.CROSS -> {
                    canvas.drawLine(cx, cy - s, cx, cy + s, paint)
                    canvas.drawLine(
                        cx - s * 0.55f, cy - s * 0.35f,
                        cx + s * 0.55f, cy - s * 0.35f,
                        paint
                    )
                }
                IofControlFeature.PEAK, IofControlFeature.ROCK -> {
                    val path = android.graphics.Path()
                    path.moveTo(cx, cy - s)
                    path.lineTo(cx + s, cy + s)
                    path.lineTo(cx - s, cy + s)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                IofControlFeature.TOWER -> {
                    canvas.drawRect(cx - s * 0.35f, cy - s, cx + s * 0.35f, cy + s, paint)
                    canvas.drawLine(cx - s * 0.55f, cy - s, cx + s * 0.55f, cy - s, paint)
                }
                else -> {
                    canvas.drawCircle(cx, cy, s * 0.75f, paint)
                    val fill = Paint(paint).apply { style = Paint.Style.FILL }
                    canvas.drawCircle(cx, cy, s * 0.18f, fill)
                }
            }

            paint.style = prevStyle
            paint.strokeWidth = prevWidth
        }
    }

    private fun openPdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, null))
        } catch (_: Exception) { }
    }

    /**
     * Opens OpenOrienteeringMap (Oomap) in the browser **centered** on the route start.
     *
     * Oomap does **not** read route geometry from this URL — there is no supported deep-link for
     * passing checkpoints or polylines. To get the generated route into Oomap, use [shareRouteAsGpx]
     * and drag the GPX onto the map (desktop) or follow Oomap’s GPX import help.
     */
    fun openPseudoPdfInBrowser(context: Context, route: Route) {
        val center = route.startPoint
        val zoom = suggestedZoom(route)
        val url = "https://oomap.dna-software.co.uk/#/$OOMAP_MAP_ID/oterrain-SRTM-5/$zoom/${center.longitude}/${center.latitude}/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Writes a GPX (waypoints = checkpoints, track = OSRM polyline) and opens the system share sheet.
     * Recipients can save the file and drag it onto [Oomap](https://oomap.dna-software.co.uk/) in a browser.
     */
    fun shareRouteAsGpx(context: Context, route: Route): Boolean {
        val file = runCatching { writeRouteGpxFile(context, route) }.getOrNull() ?: return false
        return openGpxShareSheet(
            context,
            file,
            subject = "OrienteerRun route ${route.id.take(8)}"
        )
    }

    /**
     * GPX for a saved or in-progress run: checkpoint waypoints plus GPS track (with timestamps when available).
     * Falls back to the planned OSRM polyline when no GPS breadcrumbs were recorded.
     */
    fun shareRunAsGpx(
        context: Context,
        route: Route?,
        trackedPoints: List<GeoPoint>,
        trackedPointTimestampsMs: List<Long>,
        startedAt: Long,
        sessionLabel: String
    ): Boolean {
        val file = runCatching {
            writeRunGpxFile(
                context,
                route,
                trackedPoints,
                trackedPointTimestampsMs,
                startedAt,
                sessionLabel
            )
        }.getOrNull() ?: return false
        return openGpxShareSheet(context, file, subject = "OrienteerRun $sessionLabel")
    }

    private fun openGpxShareSheet(context: Context, file: File, subject: String): Boolean {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newUri(context.contentResolver, subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_gpx_chooser)))
            true
        }.getOrDefault(false)
    }

    private fun writeRouteGpxFile(context: Context, route: Route): File {
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val file = File(docsDir, "OrienteerRun_route_${dateStr}.gpx")
        file.writeText(buildGpxDocument(route), Charsets.UTF_8)
        return file
    }

    private fun writeRunGpxFile(
        context: Context,
        route: Route?,
        trackedPoints: List<GeoPoint>,
        trackedPointTimestampsMs: List<Long>,
        startedAt: Long,
        sessionLabel: String
    ): File {
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val safeLabel = sessionLabel.filter { it.isLetterOrDigit() || it == '_' }.take(32)
        val file = File(docsDir, "OrienteerRun_${safeLabel}_$dateStr.gpx")
        file.writeText(
            buildRunGpxDocument(route, trackedPoints, trackedPointTimestampsMs, startedAt, sessionLabel),
            Charsets.UTF_8
        )
        return file
    }

    private fun buildGpxDocument(route: Route): String {
        val name = escapeXml("OrienteerRun ${route.id.take(8)}")
        val sb = StringBuilder(4096)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="OrienteerRun" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <metadata><name>$name</name><time>${Instant.now()}</time></metadata>")

        route.checkpoints.forEachIndexed { index, cp ->
            val lat = formatLatLon(cp.position.latitude)
            val lon = formatLatLon(cp.position.longitude)
            val isTrailingFinish = cp.isStart && index == route.checkpoints.lastIndex &&
                route.hasTrailingFinish
            val wptName = escapeXml(
                when {
                    isTrailingFinish && route.isOneWayCourse -> "Finish"
                    cp.isStart && route.isOneWayCourse -> "Start"
                    cp.isStart -> "Start / Finish"
                    else -> "CP ${cp.id}"
                }
            )
            val desc = escapeXml(cp.description)
            sb.appendLine("  <wpt lat=\"$lat\" lon=\"$lon\">")
            sb.appendLine("    <name>$wptName</name>")
            sb.appendLine("    <desc>$desc</desc>")
            sb.appendLine("  </wpt>")
        }

        val track = subsamplePolyline(route.polylinePoints)
        if (track.isNotEmpty()) {
            sb.appendLine("  <trk>")
            sb.appendLine("    <name>$name</name>")
            sb.appendLine("    <trkseg>")
            for (p in track) {
                sb.appendLine("      <trkpt lat=\"${formatLatLon(p.latitude)}\" lon=\"${formatLatLon(p.longitude)}\"></trkpt>")
            }
            sb.appendLine("    </trkseg>")
            sb.appendLine("  </trk>")
        }

        sb.appendLine("</gpx>")
        return sb.toString()
    }

    private fun buildRunGpxDocument(
        route: Route?,
        trackedPoints: List<GeoPoint>,
        trackedPointTimestampsMs: List<Long>,
        startedAt: Long,
        sessionLabel: String
    ): String {
        val name = escapeXml("OrienteerRun $sessionLabel")
        val sb = StringBuilder(4096)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="OrienteerRun" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <metadata><name>$name</name><time>${Instant.ofEpochMilli(startedAt)}</time></metadata>")

        route?.checkpoints?.forEachIndexed { index, cp ->
            val lat = formatLatLon(cp.position.latitude)
            val lon = formatLatLon(cp.position.longitude)
            val isTrailingFinish = cp.isStart && index == route.checkpoints.lastIndex &&
                route.hasTrailingFinish
            val wptName = escapeXml(
                when {
                    isTrailingFinish && route.isOneWayCourse -> "Finish"
                    cp.isStart && route.isOneWayCourse -> "Start"
                    cp.isStart -> "Start / Finish"
                    else -> "CP ${cp.id}"
                }
            )
            sb.appendLine("  <wpt lat=\"$lat\" lon=\"$lon\">")
            sb.appendLine("    <name>$wptName</name>")
            sb.appendLine("    <desc>${escapeXml(cp.description)}</desc>")
            sb.appendLine("  </wpt>")
        }

        val gpsTrack = subsampleTrack(trackedPoints, trackedPointTimestampsMs)
        if (gpsTrack.isNotEmpty()) {
            appendTrackSegment(sb, "GPS track", gpsTrack)
        } else if (route != null) {
            val planned = subsamplePolyline(route.polylinePoints)
            if (planned.isNotEmpty()) {
                appendTrackSegment(
                    sb,
                    "Planned path",
                    planned.map { it to null }
                )
            }
        }

        sb.appendLine("</gpx>")
        return sb.toString()
    }

    private fun appendTrackSegment(
        sb: StringBuilder,
        trackName: String,
        points: List<Pair<GeoPoint, Long?>>
    ) {
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(trackName)}</name>")
        sb.appendLine("    <trkseg>")
        for ((p, timeMs) in points) {
            sb.append("      <trkpt lat=\"${formatLatLon(p.latitude)}\" lon=\"${formatLatLon(p.longitude)}\"")
            if (timeMs != null) {
                sb.appendLine(">")
                sb.appendLine("        <time>${Instant.ofEpochMilli(timeMs)}</time>")
                sb.appendLine("      </trkpt>")
            } else {
                sb.appendLine("></trkpt>")
            }
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
    }

    private fun subsampleTrack(
        points: List<GeoPoint>,
        times: List<Long>
    ): List<Pair<GeoPoint, Long?>> {
        if (points.isEmpty()) return emptyList()
        val hasTimes = points.size == times.size && times.isNotEmpty()
        val paired = points.mapIndexed { i, p ->
            p to if (hasTimes) times[i] else null
        }
        if (paired.size <= 2000) return paired
        val step = paired.size / 2000
        return paired.filterIndexed { i, _ -> i % step == 0 || i == paired.lastIndex }
    }

    private fun formatLatLon(v: Double): String = String.format(Locale.US, "%.7f", v)

    private fun escapeXml(s: String): String = buildString(s.length + 16) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    /** Keeps GPX size reasonable for mail / drag-drop (Oomap). */
    private fun subsamplePolyline(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size <= 2000) return points
        val step = points.size / 2000
        return points.filterIndexed { i, _ -> i % step == 0 || i == points.lastIndex }
    }

    private fun suggestedZoom(route: Route): Double {
        val box = boundingBoxFromRoute(route) ?: return 14.0
        val latSpan = (box.latNorth - box.latSouth).coerceAtLeast(0.002)
        val lonSpan = (box.lonEast - box.lonWest).coerceAtLeast(0.002)
        val span = maxOf(latSpan, lonSpan)
        return when {
            span < 0.01 -> 15.0
            span < 0.02 -> 14.0
            span < 0.05 -> 13.0
            span < 0.1 -> 12.0
            else -> 11.0
        }
    }
}
