package com.orienteer.app.util

import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.Route
import kotlin.math.min
import kotlin.math.sqrt

/** Legacy text legend row (kept for GPX / older callers). */
data class PdfLegendLine(val label: String, val text: String)

sealed class ControlDescriptionRow {
    data class Start(
        val description: String,
        val legDistanceLabel: String? = null
    ) : ControlDescriptionRow()
    data class Control(
        val number: Int,
        val feature: IofControlFeature,
        /** Human-readable feature name for the Feature column. */
        val featureLabel: String,
        val description: String,
        /** Air distance from previous control (legend column, not map pills). */
        val legDistanceLabel: String?
    ) : ControlDescriptionRow()
    data class Finish(
        val distanceFromLastLabel: String?,
        val description: String
    ) : ControlDescriptionRow()
}

/**
 * Readable control description table for PDF (not map overprint).
 * Columns: No. | Dist. | Feature | Description — each labeled in the sheet header.
 */
data class ControlDescriptionSheet(
    val eventTitle: String,
    val lengthLabel: String,
    val climbLabel: String,
    val rows: List<ControlDescriptionRow>
) {
    /** Title row + column-header row + data rows. */
    val gridRowCount: Int get() = HEADER_ROWS + rows.size

    companion object {
        const val HEADER_ROWS = 2
        /** No. | Dist. | Feature | Description */
        const val COLUMN_COUNT = 4
        val COLUMN_HEADERS = listOf("No.", "Dist.", "Feature", "Description")
    }
}

object ControlDescriptionSheetBuilder {
    private val checkpointPrefix = Regex("""^Checkpoint\s+\d+:\s*""", RegexOption.IGNORE_CASE)

    fun fromRoute(route: Route): ControlDescriptionSheet {
        val distanceM = route.pathDistanceM ?: route.airDistanceM.coerceAtLeast(route.totalDistanceM)
        val lengthLabel = formatLengthKm(distanceM)
        return ControlDescriptionSheet(
            eventTitle = "OrienteerRun",
            lengthLabel = lengthLabel,
            climbLabel = "",
            rows = rows(route.checkpoints)
        )
    }

    fun rows(checkpoints: List<Checkpoint>): List<ControlDescriptionRow> {
        if (checkpoints.isEmpty()) return emptyList()
        val firstStart = checkpoints.indexOfFirst { it.isStart }
        val last = checkpoints.lastIndex
        val oneWay = checkpoints.size >= 2 &&
            checkpoints.first().isStart &&
            checkpoints.last().isStart &&
            (checkpoints.first().position.latitude != checkpoints.last().position.latitude ||
                checkpoints.first().position.longitude != checkpoints.last().position.longitude)

        return checkpoints.mapIndexedNotNull { index, cp ->
            val isTrailingFinish = cp.isStart && index == last && index != firstStart
            when {
                isTrailingFinish && !oneWay -> null
                isTrailingFinish && oneWay -> {
                    val dist = cp.distanceFromPrev.takeIf { it > 0.0 }?.let { formatMetres(it) }
                    ControlDescriptionRow.Finish(
                        distanceFromLastLabel = dist,
                        description = cleanDescription(cp.description)
                    )
                }
                cp.isStart -> ControlDescriptionRow.Start(
                    description = cleanDescription(cp.description),
                    legDistanceLabel = null
                )
                else -> {
                    val feature = IofFeatureMapper.fromLandmarkType(cp.landmarkType)
                    val label = IofFeatureMapper.displayLabel(feature).ifBlank {
                        cp.landmarkName.orEmpty()
                    }
                    ControlDescriptionRow.Control(
                        number = cp.id,
                        feature = feature,
                        featureLabel = label,
                        description = cleanDescription(cp.description),
                        legDistanceLabel = cp.distanceFromPrev.takeIf { it > 0.0 }?.let { formatMetres(it) }
                    )
                }
            }
        }
    }

    /** Drop redundant "Checkpoint N:" so the Description column stays readable. */
    internal fun cleanDescription(raw: String): String =
        checkpointPrefix.replace(raw.trim(), "").trim()

    private fun formatLengthKm(distanceM: Double): String {
        val km = distanceM / 1000.0
        return if (km >= 10.0) {
            "${km.toInt()} km"
        } else {
            String.format(java.util.Locale.US, "%.1f km", km)
        }
    }

    private fun formatMetres(m: Double): String = "${m.toInt()} m"
}

object RoutePdfLegendBuilder {
    fun lines(checkpoints: List<Checkpoint>): List<PdfLegendLine> {
        if (checkpoints.isEmpty()) return emptyList()
        val firstStart = checkpoints.indexOfFirst { it.isStart }
        val last = checkpoints.lastIndex
        val oneWay = checkpoints.size >= 2 &&
            checkpoints.first().isStart &&
            checkpoints.last().isStart &&
            (checkpoints.first().position.latitude != checkpoints.last().position.latitude ||
                checkpoints.first().position.longitude != checkpoints.last().position.longitude)
        return checkpoints.mapIndexed { index, cp ->
            val isTrailingFinish = cp.isStart && index == last && index != firstStart
            val label = when {
                isTrailingFinish && oneWay -> "F"
                // Hide duplicate return-to-start finish row in legend (same as start).
                isTrailingFinish && !oneWay -> null
                cp.isStart -> "S"
                else -> "${cp.id}"
            }
            label?.let { PdfLegendLine(label = it, text = cp.description) }
        }.filterNotNull()
    }
}

/** Axis-aligned rect in PDF points or map pixels (pure; safe for JVM unit tests). */
data class PdfRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
}

data class PdfPageRegions(
    val titleBaseline: Float,
    val mapRect: PdfRect,
    val legendRect: PdfRect?
)

/**
 * A4-ish PDF layout in points (72 dpi). Matches [RoutePdfExporter] page size.
 */
object PdfPageLayout {
    const val PAGE_WIDTH = 595f
    const val PAGE_HEIGHT = 842f
    const val MARGIN = 36f
    private const val TITLE_TEXT_SIZE = 14f
    private const val TITLE_PADDING = 24f
    private const val LEGEND_LINE_HEIGHT = 11f
    private const val LEGEND_GAP = 8f
    private const val MIN_MAP_HEIGHT = 280f
    /** Preferred IOF cell height; may shrink when many controls. */
    const val IOF_CELL_HEIGHT = 14f

    fun compute(includeLegends: Boolean, legendLineCount: Int): PdfPageRegions {
        val titleBaseline = MARGIN + TITLE_PADDING
        val contentTop = MARGIN + TITLE_PADDING * 2 + TITLE_TEXT_SIZE
        val contentBottom = PAGE_HEIGHT - MARGIN
        val contentWidth = PAGE_WIDTH - 2 * MARGIN

        if (!includeLegends || legendLineCount <= 0) {
            return PdfPageRegions(
                titleBaseline = titleBaseline,
                mapRect = PdfRect(MARGIN, contentTop, MARGIN + contentWidth, contentBottom),
                legendRect = null
            )
        }

        val neededLegend = LEGEND_GAP + legendLineCount * LEGEND_LINE_HEIGHT + LEGEND_GAP
        val maxLegend = (contentBottom - contentTop - MIN_MAP_HEIGHT).coerceAtLeast(40f)
        val legendHeight = min(neededLegend, maxLegend)
        val legendTop = contentBottom - legendHeight
        val mapBottom = legendTop - LEGEND_GAP

        return PdfPageRegions(
            titleBaseline = titleBaseline,
            mapRect = PdfRect(MARGIN, contentTop, MARGIN + contentWidth, mapBottom),
            legendRect = PdfRect(MARGIN, legendTop, MARGIN + contentWidth, contentBottom)
        )
    }

    /** Layout with room for an IOF-style control description grid ([gridRowCount] rows). */
    fun computeForControlSheet(includeSheet: Boolean, gridRowCount: Int): PdfPageRegions {
        val titleBaseline = MARGIN + TITLE_PADDING
        val contentTop = MARGIN + TITLE_PADDING * 2 + TITLE_TEXT_SIZE
        val contentBottom = PAGE_HEIGHT - MARGIN
        val contentWidth = PAGE_WIDTH - 2 * MARGIN

        if (!includeSheet || gridRowCount <= 0) {
            return PdfPageRegions(
                titleBaseline = titleBaseline,
                mapRect = PdfRect(MARGIN, contentTop, MARGIN + contentWidth, contentBottom),
                legendRect = null
            )
        }

        val preferredRow = IOF_CELL_HEIGHT + 2f
        val neededLegend = LEGEND_GAP + gridRowCount * preferredRow + LEGEND_GAP
        val maxLegend = (contentBottom - contentTop - MIN_MAP_HEIGHT).coerceAtLeast(40f)
        val legendHeight = min(neededLegend, maxLegend)
        val legendTop = contentBottom - legendHeight
        val mapBottom = legendTop - LEGEND_GAP

        return PdfPageRegions(
            titleBaseline = titleBaseline,
            mapRect = PdfRect(MARGIN, contentTop, MARGIN + contentWidth, mapBottom),
            legendRect = PdfRect(MARGIN, legendTop, MARGIN + contentWidth, contentBottom)
        )
    }

    /** Row height for the labeled control table. */
    fun controlSheetRowHeight(legendRect: PdfRect, gridRowCount: Int): Float {
        val rows = gridRowCount.coerceAtLeast(1)
        return (legendRect.height() / rows).coerceIn(10f, 18f)
    }

    /** Convert a layout width/height in PDF points to millimetres. */
    fun pointsToMm(points: Float): Double = points / 72.0 * 25.4

    fun mapFrameMm(mapRect: PdfRect): MapFrameMm = MapFrameMm(
        widthMm = pointsToMm(mapRect.width()),
        heightMm = pointsToMm(mapRect.height())
    )

    /**
     * Destination rect that fits [srcWidth]×[srcHeight] inside [dst] without stretching
     * (letterbox / pillarbox), centered.
     */
    fun fitRectPreserveAspect(srcWidth: Float, srcHeight: Float, dst: PdfRect): PdfRect {
        val sw = srcWidth.coerceAtLeast(1f)
        val sh = srcHeight.coerceAtLeast(1f)
        val scale = min(dst.width() / sw, dst.height() / sh)
        val w = sw * scale
        val h = sh * scale
        val left = dst.left + (dst.width() - w) / 2f
        val top = dst.top + (dst.height() - h) / 2f
        return PdfRect(left, top, left + w, top + h)
    }

    /**
     * Source crop (in bitmap pixels) that covers [dst] aspect without stretching.
     * Center-crops the taller phone capture into the PDF map band.
     */
    fun centerCropSrcRect(srcWidth: Int, srcHeight: Int, dst: PdfRect): PdfRect {
        val sw = srcWidth.coerceAtLeast(1).toFloat()
        val sh = srcHeight.coerceAtLeast(1).toFloat()
        val dstAspect = (dst.width() / dst.height().coerceAtLeast(1f)).coerceAtLeast(0.01f)
        val srcAspect = sw / sh
        return if (srcAspect > dstAspect) {
            val newW = sh * dstAspect
            val left = (sw - newW) / 2f
            PdfRect(left, 0f, left + newW, sh)
        } else {
            val newH = sw / dstAspect
            val top = (sh - newH) / 2f
            PdfRect(0f, top, sw, top + newH)
        }
    }

    /**
     * Pixel size of the region that survives [centerCropSrcRect] for a map view of
     * [mapWidthPx]×[mapHeightPx] into a PDF band with width/height = [pdfAspect].
     * Export camera must fit the route into this viewport or the crop will clip it.
     */
    fun pdfViewportSizePx(mapWidthPx: Int, mapHeightPx: Int, pdfAspect: Float): Pair<Int, Int> {
        val w = mapWidthPx.coerceAtLeast(1)
        val h = mapHeightPx.coerceAtLeast(1)
        val aspect = pdfAspect.coerceAtLeast(0.01f)
        val mapAspect = w.toFloat() / h
        return if (mapAspect > aspect) {
            val vw = (h * aspect).toInt().coerceAtLeast(1)
            vw to h
        } else {
            val vh = (w / aspect).toInt().coerceAtLeast(1)
            w to vh
        }
    }

    /**
     * Border for [org.osmdroid.views.MapView.zoomToBoundingBox] against a [vw]×[vh]
     * viewport. Must keep (vw − 2·pad) and (vh − 2·pad) positive — oversized pad
     * hangs forever inside Projection.getCloserPixel (ORIENTEERRUN-J).
     */
    fun safeZoomBorderPx(viewportW: Int, viewportH: Int, requestedPadPx: Int): Int {
        val vw = viewportW.coerceAtLeast(1)
        val vh = viewportH.coerceAtLeast(1)
        val maxPad = (minOf(vw, vh) / 2 - 1).coerceAtLeast(0)
        return requestedPadPx.coerceIn(0, maxPad)
    }
}

object PdfMapScaleBar {
    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    data class ScaleBarSpec(
        val distanceM: Double,
        val widthPx: Int,
        /** e.g. "500 m" or "1 km" under the bar */
        val distanceLabel: String,
        /** Orienteering-style ratio, e.g. "1:10 000" */
        val ratioLabel: String,
        /** Number of alternating black/white segments on the bar (even, ≥2). */
        val segmentCount: Int
    )

    private val NICE_DISTANCES_M = listOf(
        50.0, 100.0, 200.0, 250.0, 500.0, 1000.0, 2000.0, 2500.0, 5000.0
    )

    fun formatRatioLabel(ratio: Int): String {
        val n = ratio.coerceAtLeast(1)
        val grouped = n.toString()
            .reversed()
            .chunked(3)
            .joinToString(" ")
            .reversed()
        return "1:$grouped"
    }

    /**
     * Approximate print ratio from capture metres-per-pixel and the PDF map frame width.
     */
    fun approximateRatio(metresPerPixel: Double, mapWidthPx: Int, frameWidthMm: Double): Int {
        val groundM = metresPerPixel.coerceAtLeast(1e-6) * mapWidthPx.coerceAtLeast(1)
        val mm = frameWidthMm.coerceAtLeast(1.0)
        return ((groundM / mm) * 1000.0).toInt().coerceIn(500, 100_000)
    }

    fun compute(
        metresPerPixel: Double,
        maxBarWidthPx: Int,
        ratioLabel: String
    ): ScaleBarSpec {
        val mpp = metresPerPixel.coerceAtLeast(1e-6)
        val maxGroundM = mpp * maxBarWidthPx.coerceAtLeast(1)
        val distance = NICE_DISTANCES_M.lastOrNull { it <= maxGroundM }
            ?: NICE_DISTANCES_M.first()
        val widthPx = (distance / mpp).toInt().coerceIn(1, maxBarWidthPx.coerceAtLeast(1))
        val distanceLabel = when {
            distance >= 1000.0 -> {
                val km = distance / 1000.0
                if (km == km.toLong().toDouble()) "${km.toLong()} km" else "${"%.1f".format(km)} km"
            }
            else -> "${distance.toInt()} m"
        }
        val segments = when {
            distance >= 1000.0 -> 4
            distance >= 200.0 -> 4
            else -> 2
        }
        return ScaleBarSpec(
            distanceM = distance,
            widthPx = widthPx,
            distanceLabel = distanceLabel,
            ratioLabel = ratioLabel,
            segmentCount = segments
        )
    }

    fun pickCorner(
        mapWidth: Float,
        mapHeight: Float,
        barWidth: Float,
        barHeight: Float,
        controlPointsPx: List<Pair<Float, Float>>,
        paddingPx: Float = 8f
    ): Corner {
        val candidates = Corner.entries.map { corner ->
            val rect = rectForCorner(corner, mapWidth, mapHeight, barWidth, barHeight, paddingPx)
            val clearance = controlPointsPx.minOfOrNull { (x, y) ->
                distanceOutsideRect(x, y, rect)
            } ?: Float.MAX_VALUE
            corner to clearance
        }
        return candidates.maxBy { it.second }.first
    }

    fun rectForCorner(
        corner: Corner,
        mapWidth: Float,
        mapHeight: Float,
        barWidth: Float,
        barHeight: Float,
        paddingPx: Float
    ): PdfRect {
        val left = when (corner) {
            Corner.TOP_LEFT, Corner.BOTTOM_LEFT -> paddingPx
            Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT -> mapWidth - paddingPx - barWidth
        }
        val top = when (corner) {
            Corner.TOP_LEFT, Corner.TOP_RIGHT -> paddingPx
            Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT -> mapHeight - paddingPx - barHeight
        }
        return PdfRect(left, top, left + barWidth, top + barHeight)
    }

    /** Positive when point is outside rect (edge distance); negative when inside. */
    private fun distanceOutsideRect(x: Float, y: Float, rect: PdfRect): Float {
        val dx = when {
            x < rect.left -> rect.left - x
            x > rect.right -> x - rect.right
            else -> 0f
        }
        val dy = when {
            y < rect.top -> rect.top - y
            y > rect.bottom -> y - rect.bottom
            else -> 0f
        }
        return if (dx == 0f && dy == 0f) {
            -min(
                min(x - rect.left, rect.right - x),
                min(y - rect.top, rect.bottom - y)
            )
        } else if (dx > 0f && dy > 0f) {
            sqrt(dx * dx + dy * dy)
        } else {
            maxOf(dx, dy)
        }
    }
}
