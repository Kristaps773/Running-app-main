package com.orienteer.app.util

import com.orienteer.app.data.model.GeoPoint
import kotlin.math.cos
import kotlin.math.max

/** Printable map frame size in millimetres. */
data class MapFrameMm(val widthMm: Double, val heightMm: Double)

/** Route geographic span in metres (padded bbox). */
data class RouteSpanM(val widthM: Double, val heightM: Double)

/** User-selected PDF export scale (fixed IOF ratio or fit-to-page fallback). */
sealed class PdfExportScale {
    data class Fixed(val scale: OrienteeringPrintScale) : PdfExportScale()
    data object FitToPage : PdfExportScale()

    val label: String
        get() = when (this) {
            is Fixed -> scale.label
            FitToPage -> "Fit to page"
        }
}

/**
 * Fixed orienteering print scales (ISSprOM / ISOM common ratios).
 * Ordered fine → coarse.
 */
enum class OrienteeringPrintScale(val ratio: Int) {
    ONE_TO_4000(4000),
    ONE_TO_5000(5000),
    ONE_TO_7500(7500),
    ONE_TO_10000(10000),
    ONE_TO_15000(15000);

    val label: String get() = "1:$ratio"

    fun groundWidthM(frame: MapFrameMm): Double = (frame.widthMm / 1000.0) * ratio

    fun groundHeightM(frame: MapFrameMm): Double = (frame.heightMm / 1000.0) * ratio

    fun fits(span: RouteSpanM, frame: MapFrameMm): Boolean =
        span.widthM <= groundWidthM(frame) && span.heightM <= groundHeightM(frame)

    companion object {
        private const val SHORT_COURSE_M = 3000.0
        private const val MEDIUM_COURSE_M = 6000.0

        /** On-screen badge scale is 1f; PDF 1:15000 baseline is smaller. */
        private const val PDF_BASELINE_AT_15000 = 0.42f
        private const val PDF_MARKER_MAX = 0.85f

        fun fittingScales(span: RouteSpanM, frame: MapFrameMm): List<OrienteeringPrintScale> =
            entries.filter { it.fits(span, frame) }

        /**
         * Preferred scale for [courseDistanceM] that appears in [fitting].
         * If the preferred band is unavailable, uses the finest (smallest ratio) fitting scale.
         */
        fun recommend(
            courseDistanceM: Double,
            fitting: List<OrienteeringPrintScale>
        ): OrienteeringPrintScale? {
            if (fitting.isEmpty()) return null
            val preferredOrder = preferredRatios(courseDistanceM)
            for (ratio in preferredOrder) {
                val match = fitting.find { it.ratio == ratio }
                if (match != null) return match
            }
            return fitting.minByOrNull { it.ratio }
        }

        fun resolveExportScale(
            courseDistanceM: Double,
            span: RouteSpanM,
            frame: MapFrameMm
        ): PdfExportScale {
            val fitting = fittingScales(span, frame)
            val recommended = recommend(courseDistanceM, fitting)
            return if (recommended != null) {
                PdfExportScale.Fixed(recommended)
            } else {
                PdfExportScale.FitToPage
            }
        }

        /**
         * Relative badge size for PDF overlays. Finer maps (smaller ratio) get larger badges.
         * Always less than on-screen UI scale (1f) at the 1:15000 baseline.
         */
        fun markerScaleForPrint(scale: OrienteeringPrintScale): Float {
            val relative = 15000.0 / scale.ratio
            return (PDF_BASELINE_AT_15000 * relative.toFloat()).coerceIn(
                PDF_BASELINE_AT_15000,
                PDF_MARKER_MAX
            )
        }

        /** Fit-to-page uses the coarse baseline badge size. */
        fun markerScaleForExport(exportScale: PdfExportScale): Float = when (exportScale) {
            is PdfExportScale.Fixed -> markerScaleForPrint(exportScale.scale)
            PdfExportScale.FitToPage -> PDF_BASELINE_AT_15000
        }

        private fun preferredRatios(courseDistanceM: Double): List<Int> = when {
            courseDistanceM <= SHORT_COURSE_M -> listOf(4000, 5000, 7500, 10000, 15000)
            courseDistanceM <= MEDIUM_COURSE_M -> listOf(7500, 10000, 5000, 15000, 4000)
            else -> listOf(10000, 15000, 7500, 5000, 4000)
        }
    }
}

/**
 * Axis-aligned geographic span of [points] in metres, with fractional padding on each side.
 */
fun routeSpanMeters(
    points: List<GeoPoint>,
    padFraction: Double = 0.06
): RouteSpanM? {
    if (points.isEmpty()) return null
    var minLat = points.first().latitude
    var maxLat = minLat
    var minLon = points.first().longitude
    var maxLon = minLon
    for (p in points) {
        minLat = minOf(minLat, p.latitude)
        maxLat = maxOf(maxLat, p.latitude)
        minLon = minOf(minLon, p.longitude)
        maxLon = maxOf(maxLon, p.longitude)
    }
    val midLat = (minLat + maxLat) / 2.0
    val midLon = (minLon + maxLon) / 2.0
    val heightM = max(
        GeoUtils.distanceMeters(GeoPoint(minLat, midLon), GeoPoint(maxLat, midLon)),
        1.0
    )
    val widthM = max(
        GeoUtils.distanceMeters(GeoPoint(midLat, minLon), GeoPoint(midLat, maxLon)),
        1.0
    )
    val padW = widthM * padFraction
    val padH = heightM * padFraction
    return RouteSpanM(widthM = widthM + 2 * padW, heightM = heightM + 2 * padH)
}

/**
 * Span used to decide which print scales fit. Uses the control footprint so a wiggly
 * walking path does not hide finer scales that still fit the course.
 * Includes the polyline only when it stays within ~25% of the control box.
 */
fun routeSpanForPrintScale(
    checkpoints: List<GeoPoint>,
    polyline: List<GeoPoint> = emptyList(),
    padFraction: Double = 0.06
): RouteSpanM? {
    val controlSpan = routeSpanMeters(checkpoints, padFraction) ?: return null
    if (polyline.isEmpty()) return controlSpan
    val pathSpan = routeSpanMeters(checkpoints + polyline, padFraction) ?: return controlSpan
    val widthOk = pathSpan.widthM <= controlSpan.widthM * 1.25
    val heightOk = pathSpan.heightM <= controlSpan.heightM * 1.25
    return if (widthOk && heightOk) pathSpan else controlSpan
}

/**
 * Geometry for PDF Fit-to-page / camera framing.
 * Always keeps checkpoints; keeps polyline vertices only when they stay within
 * [maxExtraM] of the control envelope so one bad coordinate cannot zoom the
 * export out to country scale.
 */
fun pointsForPdfExportBounds(
    checkpoints: List<GeoPoint>,
    polyline: List<GeoPoint>,
    maxExtraM: Double = 8_000.0
): List<GeoPoint> {
    if (checkpoints.isEmpty()) return polyline
    if (polyline.isEmpty()) return checkpoints
    var minLat = checkpoints.first().latitude
    var maxLat = minLat
    var minLon = checkpoints.first().longitude
    var maxLon = minLon
    for (p in checkpoints) {
        minLat = minOf(minLat, p.latitude)
        maxLat = maxOf(maxLat, p.latitude)
        minLon = minOf(minLon, p.longitude)
        maxLon = maxOf(maxLon, p.longitude)
    }
    val midLat = (minLat + maxLat) / 2.0
    val padLat = maxExtraM / 111_320.0
    val cosLat = kotlin.math.cos(Math.toRadians(midLat)).coerceAtLeast(1e-6)
    val padLon = maxExtraM / (111_320.0 * cosLat)
    val south = minLat - padLat
    val north = maxLat + padLat
    val west = minLon - padLon
    val east = maxLon + padLon
    val out = ArrayList<GeoPoint>(checkpoints.size + polyline.size)
    out.addAll(checkpoints)
    for (p in polyline) {
        if (p.latitude in south..north && p.longitude in west..east) {
            out.add(p)
        }
    }
    return out
}

/**
 * OSM web-mercator metres-per-pixel at [latitudeDeg] for zoom [zoom].
 */
fun metresPerPixelAtZoom(latitudeDeg: Double, zoom: Double): Double {
    val cosLat = cos(Math.toRadians(latitudeDeg)).coerceAtLeast(1e-6)
    return 156543.03392 * cosLat / Math.pow(2.0, zoom)
}

/**
 * Zoom level so that [metresPerPixel] matches the target ground resolution.
 */
fun zoomForMetresPerPixel(latitudeDeg: Double, metresPerPixel: Double): Double {
    val cosLat = cos(Math.toRadians(latitudeDeg)).coerceAtLeast(1e-6)
    val mpp = metresPerPixel.coerceAtLeast(1e-6)
    return Math.log(156543.03392 * cosLat / mpp) / Math.log(2.0)
}

/**
 * Snaps a raw print ratio to a "nice" orienteering label (ending in 0 or 5).
 * Prefers the nearest value **not finer** than [raw] (ceil on the step grid).
 */
fun nicePrintRatio(raw: Int): Int {
    val r = raw.coerceIn(500, 100_000)
    val step = when {
        r < 10_000 -> 500
        r < 25_000 -> 1_000
        else -> 2_500
    }
    return ((r + step - 1) / step) * step
}
