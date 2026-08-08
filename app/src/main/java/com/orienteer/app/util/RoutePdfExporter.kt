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
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.util.GeoUtils
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * Exports the current map view (route with checkpoints and lines) as a PDF
 * so the user can print it and run without their phone.
 */
object RoutePdfExporter {

    private const val PDF_PAGE_WIDTH = 595   // A4-ish at 72 dpi
    private const val PDF_PAGE_HEIGHT = 842
    private const val TITLE_TEXT_SIZE = 14f
    private const val TITLE_PADDING = 24
    private const val OOMAP_MAP_ID = "69c2676672a49"
    private const val EXPORT_CAPTURE_DELAY_MS = 700L

    /**
     * Fits the map to the full route, then captures and exports PDF.
     * Runs asynchronously: zooms map to route bounds, then after redraw captures and writes PDF.
     * @param onComplete Called with the created file or null when done (on main thread).
     */
    fun exportToPdf(
        context: Context,
        mapView: View,
        route: Route,
        onComplete: (File?) -> Unit = {}
    ) {
        if (mapView !is MapView) {
            onComplete(null)
            return
        }
        val box = boundingBoxFromRoute(route)
        val paddingPx = 80

        mapView.post {
            dismissAllMarkerInfoWindows(mapView)
            mapView.zoomToBoundingBox(box, false, paddingPx)
            mapView.post {
                // Allow map to redraw after zoom
                mapView.postDelayed({
                    dismissAllMarkerInfoWindows(mapView)
                    mapView.invalidate()
                    val file = captureAndWritePdf(context, mapView, route)
                    file?.let { openPdf(context, it) }
                    onComplete(file)
                }, EXPORT_CAPTURE_DELAY_MS)
            }
        }
    }

    private fun dismissAllMarkerInfoWindows(mapView: MapView) {
        InfoWindow.closeAllInfoWindowsOn(mapView)
        mapView.overlays.forEach { overlay ->
            if (overlay is Marker) overlay.closeInfoWindow()
        }
    }

    private fun boundingBoxFromRoute(route: Route): BoundingBox {
        val checkpointPoints = route.checkpoints.map { OsmGeoPoint(it.position.latitude, it.position.longitude) }
        val optimalPathPoints = route.polylinePoints.map { OsmGeoPoint(it.latitude, it.longitude) }
        val points = checkpointPoints + optimalPathPoints
        val lats = points.map { it.latitude }
        val lons = points.map { it.longitude }
        var minLat = lats.minOrNull() ?: 0.0
        var maxLat = lats.maxOrNull() ?: 0.0
        var minLon = lons.minOrNull() ?: 0.0
        var maxLon = lons.maxOrNull() ?: 0.0
        // Add padding so route isn't at the edge
        val padLat = (maxLat - minLat).coerceAtLeast(0.0005) * 0.15
        val padLon = (maxLon - minLon).coerceAtLeast(0.0005) * 0.15
        minLat -= padLat
        maxLat += padLat
        minLon -= padLon
        maxLon += padLon
        return BoundingBox(maxLat, maxLon, minLat, minLon)
    }

    private fun captureAndWritePdf(context: Context, mapView: View, route: Route): File? {
        val w = mapView.width.coerceAtLeast(100)
        val h = mapView.height.coerceAtLeast(100)

        // Capture at 2x and downscale into PDF for better print readability.
        val captureScale = 2
        val bitmap = Bitmap.createBitmap(w * captureScale, h * captureScale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(captureScale.toFloat(), captureScale.toFloat())
        mapView.draw(canvas)

        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val fileName = "OrienteerRun_route_${dateStr}.pdf"
        val file = File(docsDir, fileName)

        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val pageCanvas = page.canvas

            // Scale map to fit page: title at top, map fills rest
            val margin = 36
            val titleHeight = TITLE_PADDING * 2 + TITLE_TEXT_SIZE.toInt()
            val contentTop = margin + titleHeight
            val contentWidth = PDF_PAGE_WIDTH - 2 * margin
            val contentHeight = (PDF_PAGE_HEIGHT - contentTop - margin).coerceAtLeast(100)

            val scale = minOf(
                contentWidth.toFloat() / w,
                contentHeight.toFloat() / h
            )
            val scaledW = (w * scale).toInt()
            val scaledH = (h * scale).toInt()
            val left = margin + (contentWidth - scaledW) / 2
            val top = contentTop + (contentHeight - scaledH) / 2

            pageCanvas.save()
            pageCanvas.translate(left.toFloat(), top.toFloat())
            pageCanvas.scale(scale, scale)
            pageCanvas.drawBitmap(bitmap, 0f, 0f, null)
            pageCanvas.restore()

            // Title line: route summary only (no checkpoint list)
            val titlePaint = Paint().apply {
                isAntiAlias = true
                textSize = TITLE_TEXT_SIZE
                textAlign = Paint.Align.LEFT
                color = Color.BLACK
            }
            val title = "OrienteerRun — ${GeoUtils.formatDistance(route.totalDistanceM)} • ${route.waypointCount} checkpoints • ${(route.estimatedDurationS / 60).toInt()} min est."
            pageCanvas.drawText(title, margin.toFloat(), (margin + TITLE_PADDING).toFloat(), titlePaint)

            pdfDocument.finishPage(page)
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            bitmap.recycle()

            file
        } catch (e: Exception) {
            null
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
        val file = runCatching { writeGpxFile(context, route) }.getOrNull() ?: return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "OrienteerRun route ${route.id.take(8)}")
            clipData = ClipData.newUri(context.contentResolver, "OrienteerRun route", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_gpx_chooser)))
            true
        }.getOrDefault(false)
    }

    private fun writeGpxFile(context: Context, route: Route): File {
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val file = File(docsDir, "OrienteerRun_route_${dateStr}.gpx")
        file.writeText(buildGpxDocument(route), Charsets.UTF_8)
        return file
    }

    private fun buildGpxDocument(route: Route): String {
        val name = escapeXml("OrienteerRun ${route.id.take(8)}")
        val sb = StringBuilder(4096)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="OrienteerRun" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <metadata><name>$name</name><time>${Instant.now()}</time></metadata>")

        for (cp in route.checkpoints) {
            val lat = formatLatLon(cp.position.latitude)
            val lon = formatLatLon(cp.position.longitude)
            val wptName = escapeXml(
                when {
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
        val box = boundingBoxFromRoute(route)
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
