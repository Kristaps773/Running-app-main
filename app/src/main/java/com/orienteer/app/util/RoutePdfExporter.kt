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
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.FileProvider
import com.orienteer.app.R
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.presentation.map.IOF_CONTROL_DIAMETER_M
import com.orienteer.app.presentation.map.IofCourseGeometry
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.presentation.map.applyBasemapStyle
import com.orienteer.app.presentation.map.applyRouteMapOverlays
import com.orienteer.app.presentation.map.boundingBoxForPoints
import com.orienteer.app.presentation.map.clearRouteOverlays
import com.orienteer.app.presentation.map.detachLocationMarker
import com.orienteer.app.ui.theme.MapOverlayColors
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * Exports a printable PDF at a fixed orienteering print scale (or fit-to-page).
 * Basemap tiles are captured from the live MapView; the purple course, north
 * arrow, and scale bar are drawn as vectors on the PDF (not screen overlays).
 */
object RoutePdfExporter {

    private const val OOMAP_MAP_ID = "69c2676672a49"
    private const val EXPORT_FIT_PADDING_PX = 48
    private const val TILE_POLL_INTERVAL_MS = 75L
    private const val TILE_READY_TIMEOUT_MS = 10_000L
    private const val EXPORT_MAX_MEGAPIXELS = 16
    private const val EXPORT_PREFERRED_DPI = 200
    private const val EXPORT_MIN_DPI = 150

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
        val basemapStyle = mapPrefs.basemapStyle

        Configuration.getInstance().userAgentValue = context.packageName

        val savedZoom = liveMap.zoomLevelDouble
        val savedCenter = OsmGeoPoint(
            liveMap.mapCenter.latitude,
            liveMap.mapCenter.longitude
        )

        liveMap.post {
            // Frame camera with overlays on so bbox math matches the live map, then strip
            // them for a clean basemap capture (course is drawn as PDF vectors later).
            applyRouteMapOverlays(
                map = liveMap,
                route = route,
                prefs = mapPrefs.copy(
                    showAirLegDistanceLabels = false,
                    showWalkingPathPolyline = false,
                    useIofCourseOverprint = true,
                    showAirLegLines = true
                ),
                mapColors = mapColors,
                primaryColorArgb = primaryColorArgb,
                startColorArgb = startColorArgb,
                basemapStyle = basemapStyle,
                markerScale = OrienteeringPrintScale.markerScaleForExport(exportScale)
            )
            liveMap.detachLocationMarker()
            liveMap.invalidate()

            val mapRect = layout.mapRect
            var exportMapView: MapView? = null

            fun runCapture(
                captureMap: MapView,
                restoreLiveCamera: Boolean,
                rejectIfMostlyMissing: Boolean,
                onAfterCapture: (rejectedMissingTiles: Boolean) -> Unit
            ) {
                val fitNiceRatio = try {
                    applyExportCamera(captureMap, box, exportScale, frameMm)
                } catch (_: Exception) {
                    null
                }
                captureMap.invalidate()

                waitForMapTiles(captureMap) {
                    try {
                        clearRouteOverlays(captureMap)
                        captureMap.detachLocationMarker()
                        captureMap.invalidate()

                        if (rejectIfMostlyMissing && mostlyMissingTiles(captureMap)) {
                            onAfterCapture(true)
                            return@waitForMapTiles
                        }

                        val w = captureMap.width.coerceAtLeast(100)
                        val h = captureMap.height.coerceAtLeast(100)
                        val viewCrop = PdfPageLayout.centerCropSrcRect(w, h, mapRect)
                        val centerLat = captureMap.mapCenter.latitude
                        val mpp = metresPerPixelAtZoom(centerLat, captureMap.zoomLevelDouble)
                        val printRatio = when (exportScale) {
                            is PdfExportScale.Fixed -> exportScale.scale.ratio
                            PdfExportScale.FitToPage -> fitNiceRatio
                                ?: PdfVectorCourse.printRatioForExport(
                                    exportScale = exportScale,
                                    frameMm = frameMm,
                                    metresPerPixel = mpp,
                                    mapWidthPx = viewCrop.width().toInt().coerceAtLeast(1)
                                )
                        }
                        val scratch = android.graphics.Point()
                        fun projectView(geo: GeoPoint): Pair<Float, Float>? {
                            captureMap.projection.toPixels(
                                OsmGeoPoint(geo.latitude, geo.longitude),
                                scratch
                            )
                            return scratch.x.toFloat() to scratch.y.toFloat()
                        }
                        fun projectPdf(geo: GeoPoint): Pair<Float, Float>? {
                            val (vx, vy) = projectView(geo) ?: return null
                            return PdfVectorCourse.viewPxToPdf(vx, vy, viewCrop, mapRect)
                        }

                        val bitmap = captureMapBitmap(captureMap)

                        val file = writePdf(
                            context = context,
                            bitmap = bitmap,
                            route = route,
                            layout = layout,
                            controlSheet = if (includePdfLegends) controlSheet else null,
                            projectPdf = ::projectPdf,
                            printRatio = printRatio,
                            metresPerPdfPoint = mpp *
                                (viewCrop.width() / mapRect.width().coerceAtLeast(1f))
                        )
                        bitmap.recycle()

                        if (restoreLiveCamera) {
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
                        } else {
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
                            liveMap.invalidate()
                        }

                        file?.let { openPdf(context, it) }
                        onComplete(file)
                        onAfterCapture(false)
                    } catch (_: Exception) {
                        runCatching {
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
                            if (restoreLiveCamera) {
                                liveMap.controller.setZoom(savedZoom)
                                liveMap.controller.setCenter(savedCenter)
                            }
                            liveMap.invalidate()
                        }
                        onComplete(null)
                        onAfterCapture(false)
                    }
                }
            }

            fun detachExportMap() {
                val export = exportMapView ?: return
                runCatching {
                    export.onPause()
                    export.onDetach()
                    (export.parent as? ViewGroup)?.removeView(export)
                }
                exportMapView = null
            }

            val hiRes = tryCreateHiResExportMap(
                context = context,
                anchorView = mapView,
                mapRect = mapRect,
                basemapStyle = basemapStyle
            )
            exportMapView = hiRes

            if (hiRes != null) {
                runCapture(
                    captureMap = hiRes,
                    restoreLiveCamera = false,
                    rejectIfMostlyMissing = true
                ) { rejected ->
                    detachExportMap()
                    if (rejected) {
                        liveMap.post {
                            clearRouteOverlays(liveMap)
                            liveMap.detachLocationMarker()
                            runCapture(
                                captureMap = liveMap,
                                restoreLiveCamera = true,
                                rejectIfMostlyMissing = false
                            ) { _ -> }
                        }
                    }
                }
            } else {
                clearRouteOverlays(liveMap)
                liveMap.detachLocationMarker()
                runCapture(
                    captureMap = liveMap,
                    restoreLiveCamera = true,
                    rejectIfMostlyMissing = false
                ) { _ -> }
            }
        }
    }

    private fun exportDpiForMapRect(mapRect: PdfRect): Int {
        for (dpi in EXPORT_PREFERRED_DPI downTo EXPORT_MIN_DPI step 25) {
            val w = (mapRect.width() * dpi / 72f).toInt().coerceAtLeast(1)
            val h = (mapRect.height() * dpi / 72f).toInt().coerceAtLeast(1)
            if (w.toLong() * h <= EXPORT_MAX_MEGAPIXELS * 1_000_000L) return dpi
        }
        return EXPORT_MIN_DPI
    }

    private fun tryCreateHiResExportMap(
        context: Context,
        anchorView: View,
        mapRect: PdfRect,
        basemapStyle: BasemapStyle
    ): MapView? {
        val root = anchorView.rootView.findViewById<ViewGroup>(android.R.id.content) ?: return null
        val dpi = exportDpiForMapRect(mapRect)
        val widthPx = (mapRect.width() * dpi / 72f).toInt().coerceAtLeast(100)
        val heightPx = (mapRect.height() * dpi / 72f).toInt().coerceAtLeast(100)
        return try {
            MapView(context).apply {
                alpha = 0f
                isClickable = false
                isFocusable = false
                translationX = -widthPx.toFloat() * 2f
                layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
                applyBasemapStyle(this, context, basemapStyle)
                root.addView(this)
                measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, widthPx, heightPx)
                onResume()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun captureMapBitmap(map: MapView): Bitmap {
        val w = map.width.coerceAtLeast(1)
        val h = map.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        map.draw(canvas)
        return bitmap
    }

    private fun tileOverlaysForReadyCheck(map: MapView): List<TilesOverlay> {
        val base = map.overlayManager.tilesOverlay
        val extra = map.overlays.filterIsInstance<TilesOverlay>().filter { it !== base }
        return listOfNotNull(base) + extra
    }

    private fun mapTilesReady(map: MapView): Boolean =
        tileOverlaysForReadyCheck(map).all { overlay ->
            val states = overlay.tileStates
            states.isDone &&
                states.notFound == 0 &&
                states.upToDate + states.expired + states.scaled == states.total
        }

    private fun mostlyMissingTiles(map: MapView): Boolean {
        var total = 0
        var notFound = 0
        for (overlay in tileOverlaysForReadyCheck(map)) {
            val states = overlay.tileStates
            total += states.total
            notFound += states.notFound
        }
        return total > 0 && notFound * 2 > total
    }

    private fun waitForMapTiles(map: MapView, onReady: () -> Unit) {
        val deadline = SystemClock.uptimeMillis() + TILE_READY_TIMEOUT_MS
        fun tick() {
            map.invalidate()
            if (mapTilesReady(map) || SystemClock.uptimeMillis() >= deadline) {
                onReady()
                return
            }
            map.postDelayed(::tick, TILE_POLL_INTERVAL_MS)
        }
        map.post(::tick)
    }

    /**
     * Frame the map for the PDF center-crop viewport.
     * @return Snapped 1:N for [PdfExportScale.FitToPage] (for PDF scale bar), or null for fixed scales.
     */
    private fun applyExportCamera(
        map: MapView,
        box: BoundingBox,
        exportScale: PdfExportScale,
        frameMm: MapFrameMm
    ): Int? {
        val pdfAspect = (frameMm.widthMm / frameMm.heightMm.coerceAtLeast(0.01)).toFloat()
        val (viewportW, viewportH) = PdfPageLayout.pdfViewportSizePx(
            map.width,
            map.height,
            pdfAspect
        )
        val tileMax = map.tileProvider.maximumZoomLevel.toDouble()
        return when (exportScale) {
            is PdfExportScale.Fixed -> {
                applyFixedPrintRatio(
                    map = map,
                    box = box,
                    ratio = exportScale.scale.ratio,
                    frameMm = frameMm,
                    viewportW = viewportW,
                    viewportH = viewportH,
                    tileMax = tileMax
                )
                null
            }
            PdfExportScale.FitToPage -> {
                val tileMin = map.tileProvider.minimumZoomLevel.toDouble()
                val prevMin = map.minZoomLevel
                map.setMinZoomLevel(tileMin)
                zoomToPdfViewport(map, box, viewportW, viewportH, tileMax)
                if (map.zoomLevelDouble < prevMin) {
                    map.setMinZoomLevel(map.zoomLevelDouble.coerceAtLeast(tileMin))
                }
                val centerLat = (box.latNorth + box.latSouth) / 2.0
                val raw = PdfMapScaleBar.approximateRatio(
                    metresPerPixelAtZoom(centerLat, map.zoomLevelDouble),
                    viewportW,
                    frameMm.widthMm
                )
                val nice = nicePrintRatio(raw)
                applyFixedPrintRatio(
                    map = map,
                    box = box,
                    ratio = nice,
                    frameMm = frameMm,
                    viewportW = viewportW,
                    viewportH = viewportH,
                    tileMax = tileMax
                )
                // If fixed-ratio framing fell back to a tight fit, label from the actual zoom.
                nicePrintRatio(
                    PdfMapScaleBar.approximateRatio(
                        metresPerPixelAtZoom(centerLat, map.zoomLevelDouble),
                        viewportW,
                        frameMm.widthMm
                    )
                )
            }
        }
    }

    private fun applyFixedPrintRatio(
        map: MapView,
        box: BoundingBox,
        ratio: Int,
        frameMm: MapFrameMm,
        viewportW: Int,
        viewportH: Int,
        tileMax: Double
    ) {
        val centerLat = (box.latNorth + box.latSouth) / 2.0
        val centerLon = (box.lonEast + box.lonWest) / 2.0
        val tileMin = map.tileProvider.minimumZoomLevel.toDouble()
        val groundWidthM = (frameMm.widthMm / 1000.0) * ratio
        val mpp = groundWidthM / viewportW.coerceAtLeast(1)
        val zoom = zoomForMetresPerPixel(centerLat, mpp).coerceIn(tileMin, tileMax)
        val prevMin = map.minZoomLevel
        if (zoom < prevMin) {
            map.setMinZoomLevel(zoom.coerceAtLeast(tileMin))
        }
        map.controller.setZoom(zoom)
        map.controller.setCenter(org.osmdroid.util.GeoPoint(centerLat, centerLon))
        if (!bboxFitsViewport(map, box, viewportW, viewportH)) {
            zoomToPdfViewport(map, box, viewportW, viewportH, tileMax)
        }
    }

    /**
     * Fit [box] inside the PDF center-crop ([vw]×[vh]).
     *
     * Computes zoom from the crop viewport size directly. Our osmdroid build has
     * no width/height [MapView.zoomToBoundingBox] overload; stuffing phone-crop
     * chrome into borderSize against the full MapView either ANRs (ORIENTEERRUN-J)
     * or, after safe clamp, leaves ~2 usable pixels and zooms out to country scale.
     */
    private fun zoomToPdfViewport(
        map: MapView,
        box: BoundingBox,
        vw: Int,
        vh: Int,
        maxZoom: Double
    ) {
        val safeW = vw.coerceAtLeast(1)
        val safeH = vh.coerceAtLeast(1)
        val pad = PdfPageLayout.safeZoomBorderPx(safeW, safeH, EXPORT_FIT_PADDING_PX)
        fitExportCameraManually(map, box, safeW, safeH, pad, maxZoom)
    }

    private fun expectedFitZoom(
        box: BoundingBox,
        viewportW: Int,
        viewportH: Int,
        padPx: Int
    ): Double {
        val centerLat = (box.latNorth + box.latSouth) / 2.0
        val spanLatM = ((box.latNorth - box.latSouth) * 111_320.0).coerceAtLeast(50.0)
        val cosLat = kotlin.math.cos(Math.toRadians(centerLat)).coerceAtLeast(1e-6)
        val spanLonM = ((box.lonEast - box.lonWest) * 111_320.0 * cosLat).coerceAtLeast(50.0)
        val usableW = (viewportW - 2 * padPx).coerceAtLeast(1)
        val usableH = (viewportH - 2 * padPx).coerceAtLeast(1)
        val mpp = maxOf(spanLonM / usableW, spanLatM / usableH)
        return zoomForMetresPerPixel(centerLat, mpp)
    }

    private fun fitExportCameraManually(
        map: MapView,
        box: BoundingBox,
        viewportW: Int,
        viewportH: Int,
        padPx: Int,
        maxZoom: Double
    ) {
        val centerLat = (box.latNorth + box.latSouth) / 2.0
        val centerLon = (box.lonEast + box.lonWest) / 2.0
        val zoom = expectedFitZoom(box, viewportW, viewportH, padPx).coerceAtMost(maxZoom)
        map.controller.setZoom(zoom)
        map.controller.setCenter(org.osmdroid.util.GeoPoint(centerLat, centerLon))
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

    private fun routePoints(route: Route): List<GeoPoint> =
        pointsForPdfExportBounds(
            checkpoints = route.checkpoints.map { it.position },
            polyline = route.polylinePoints
        )

    private fun boundingBoxFromRoute(route: Route): BoundingBox? =
        boundingBoxForPoints(routePoints(route))

    private fun writePdf(
        context: Context,
        bitmap: Bitmap,
        route: Route,
        layout: PdfPageRegions,
        controlSheet: ControlDescriptionSheet?,
        projectPdf: (GeoPoint) -> Pair<Float, Float>?,
        printRatio: Int,
        metresPerPdfPoint: Double
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

            // Vector purple course (mm on paper) — not thick screen polylines.
            // No distance/title header — real O-map PDFs are map-first.
            val plan = IofCourseGeometry.build(
                route.checkpoints,
                controlDiameterM = IOF_CONTROL_DIAMETER_M
            )
            val circleMm = PdfVectorCourse.controlCircleDiameterMm(printRatio)
            PdfVectorCourse.draw(
                canvas = pageCanvas,
                plan = plan,
                project = projectPdf,
                circleDiameterMm = circleMm
            )
            PdfVectorCourse.drawNorthArrow(pageCanvas, mapRect)
            drawPdfScaleBar(
                canvas = pageCanvas,
                mapRect = mapRect,
                metresPerPdfPoint = metresPerPdfPoint,
                printRatio = printRatio
            )

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

    /** Compact scale bar in PDF points (bottom-left of the map band). */
    private fun drawPdfScaleBar(
        canvas: Canvas,
        mapRect: PdfRect,
        metresPerPdfPoint: Double,
        printRatio: Int
    ) {
        val mpp = metresPerPdfPoint.coerceAtLeast(1e-6)
        val maxBarPt = (mapRect.width() * 0.28f).coerceAtLeast(40f)
        val bar = PdfMapScaleBar.compute(
            metresPerPixel = mpp,
            maxBarWidthPx = maxBarPt.toInt(),
            ratioLabel = PdfMapScaleBar.formatRatioLabel(printRatio)
        )
        val pad = 6f
        val left = mapRect.left + pad
        val bottom = mapRect.bottom - pad
        val barTop = bottom - 10f
        val barBottom = bottom - 4f
        val right = left + bar.widthPx

        val bg = Paint().apply {
            isAntiAlias = true
            color = Color.argb(210, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val ink = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#0F1A13")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val text = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#0F1A13")
            textSize = 8f
            isFakeBoldText = true
        }
        val block = android.graphics.RectF(left - 4f, barTop - 12f, right + 4f, bottom + 2f)
        canvas.drawRoundRect(block, 3f, 3f, bg)
        canvas.drawRoundRect(block, 3f, 3f, ink)
        canvas.drawText(bar.ratioLabel, left, barTop - 3f, text)

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
        val segW = (right - left) / bar.segmentCount
        for (i in 0 until bar.segmentCount) {
            val l = left + i * segW
            val r = if (i == bar.segmentCount - 1) right else l + segW
            canvas.drawRect(l, barTop, r, barBottom, if (i % 2 == 0) fillBlack else fillWhite)
        }
        canvas.drawRect(left, barTop, right, barBottom, ink)
        text.textSize = 7f
        text.isFakeBoldText = false
        canvas.drawText("0", left, bottom + 1f, text)
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(bar.distanceLabel, right, bottom + 1f, text)
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
                    else -> "${cp.id}"
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
                    else -> "${cp.id}"
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
