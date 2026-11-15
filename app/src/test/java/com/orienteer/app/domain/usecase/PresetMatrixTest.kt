package com.orienteer.app.domain.usecase

import com.google.gson.GsonBuilder
import com.orienteer.app.data.api.OsrmApiService
import com.orienteer.app.data.api.OverpassApiService
import com.orienteer.app.data.local.RouteGenerationPreferences
import com.orienteer.app.data.local.RouteGenerationPreset
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.di.RetryInterceptor
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.system.measureTimeMillis

/**
 * Opt-in live preset matrix (loop routes) against public OSRM/Overpass.
 *
 * Run (PowerShell):
 * ```
 * $env:ORIENTEER_ROUTE_MATRIX="true"
 * ./gradlew :app:testDebugUnitTest --tests com.orienteer.app.domain.usecase.PresetMatrixTest
 * ```
 */
class PresetMatrixTest {

    private lateinit var useCase: GenerateRouteUseCase
    private lateinit var reportDir: File

    @Before
    fun setUp() {
        val enabled = System.getProperty("orienteer.routeMatrix") == "true" ||
            System.getenv("ORIENTEER_ROUTE_MATRIX") == "true"
        assumeTrue(
            "Set ORIENTEER_ROUTE_MATRIX=true (or -Dorienteer.routeMatrix=true) to run live preset matrix",
            enabled
        )
        Locale.setDefault(Locale.US)

        val gson = GsonBuilder().create()
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "OrienteerRun/preset-matrix-test (Android unit; routing)")
                        .build()
                )
            }
            .addInterceptor(RetryInterceptor(maxRetries = 2))
            .build()

        val osrm = Retrofit.Builder()
            .baseUrl(OSRM_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OsrmApiService::class.java)

        val overpass = Retrofit.Builder()
            .baseUrl(OVERPASS_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OverpassApiService::class.java)

        useCase = GenerateRouteUseCase(osrm, overpass, gson)
        reportDir = File("build/reports/preset-matrix").apply { mkdirs() }
    }

    @Test
    fun presetMatrix_berlin_loopRoutes() = runBlocking {
        val runs = mutableListOf<RunRow>()
        val prefsByPreset = PRESETS.associateWith { preset ->
            RouteGenerationPreferences(
                maxPathToAirRatio = preset.maxPathToAirRatio,
                airDistanceToleranceM = preset.airDistanceToleranceM
            )
        }

        println("=== Preset matrix: ${PRESETS.size} presets × ${PLACES.size} places × ${DISTANCES_KM.size} km × $SAMPLES_PER_CELL runs ===")
        for (preset in PRESETS) {
            val prefs = prefsByPreset.getValue(preset)
            for ((placeName, origin) in PLACES) {
                for (targetKm in DISTANCES_KM) {
                    val targetM = targetKm * 1000.0
                    repeat(SAMPLES_PER_CELL) { sampleIndex ->
                        var row: RunRow
                        val timeMs = measureTimeMillis {
                            val result = useCase(origin, targetM, prefs)
                            row = result.fold(
                                onSuccess = { route ->
                                    rowFromRoute(preset, placeName, targetKm, sampleIndex, route, ok = true, failCategory = null)
                                },
                                onFailure = { err ->
                                    RunRow(
                                        preset = preset.name,
                                        place = placeName,
                                        targetKm = targetKm,
                                        sampleIndex = sampleIndex,
                                        ok = false,
                                        failCategory = categorizeFailure(err.message),
                                        timeMs = 0,
                                        airM = null,
                                        pathM = null,
                                        pathAir = null,
                                        airErrorM = null,
                                        checkpoints = 0,
                                        onObjectCount = 0,
                                        nearCount = 0,
                                        genericCount = 0,
                                        reRoutes = 0,
                                        reverts = 0
                                    )
                                }
                            )
                        }
                        row = row.copy(timeMs = timeMs)
                        runs += row
                        println(row.toLogLine())
                        Thread.sleep(CALL_DELAY_MS)
                    }
                }
            }
        }

        val skipSnap = System.getProperty("orienteer.skipObjectSnap") == "true" ||
            System.getenv("ORIENTEER_SKIP_OBJECT_SNAP") == "true"
        val tag = if (skipSnap) "baseline" else "after"
        writeCsv(File(reportDir, "$tag-runs.csv"), runs)
        val summary = buildSummary(runs, tag)
        File(reportDir, "$tag-summary.txt").writeText(summary)
        println(summary)
    }

    private fun rowFromRoute(
        preset: RouteGenerationPreset,
        place: String,
        targetKm: Double,
        sampleIndex: Int,
        route: Route,
        ok: Boolean,
        failCategory: String?
    ): RunRow {
        val air = route.totalDistanceM
        val path = route.actualPathDistanceM.takeIf { it > 0.0 }
        val waypoints = route.checkpoints.filter { !it.isStart }
        val onObject = waypoints.count { isOnObject(it) }
        val near = waypoints.count { isNear(it) }
        val generic = waypoints.count { isGeneric(it) }
        return RunRow(
            preset = preset.name,
            place = place,
            targetKm = targetKm,
            sampleIndex = sampleIndex,
            ok = ok && path != null,
            failCategory = failCategory ?: if (path == null) "no_routed_path" else null,
            timeMs = 0,
            airM = air,
            pathM = path,
            pathAir = path?.let { it / air.coerceAtLeast(1.0) },
            airErrorM = abs(air - targetKm * 1000.0),
            checkpoints = waypoints.size,
            onObjectCount = onObject,
            nearCount = near,
            genericCount = generic,
            reRoutes = 0,
            reverts = 0
        )
    }

    /** Infer from description / landmarkType (Checkpoint has no onObject field). */
    private fun isOnObject(cp: Checkpoint): Boolean {
        val d = cp.description.trim()
        return d.contains(": At ", ignoreCase = true) || d.startsWith("At ", ignoreCase = true)
    }

    private fun isGeneric(cp: Checkpoint): Boolean {
        val tag = cp.landmarkType
        return tag.isNullOrBlank() || tag.startsWith("generic=", ignoreCase = true)
    }

    private fun isNear(cp: Checkpoint): Boolean {
        if (isOnObject(cp) || isGeneric(cp)) return false
        val d = cp.description.trim()
        return d.contains(": Near ", ignoreCase = true) ||
            d.startsWith("Near ", ignoreCase = true) ||
            !cp.landmarkType.isNullOrBlank()
    }

    private fun categorizeFailure(message: String?): String {
        val m = message?.lowercase().orEmpty()
        return when {
            m.contains("invalid data") -> "response_parse"
            m.contains("acceptable") || m.contains("path/air") -> "settings_reject"
            m.contains("timed out") || m.contains("timeout") -> "timeout"
            m.contains("network") || m.contains("connection") || m.contains("reach") -> "network"
            m.contains("snap") -> "road_snap"
            else -> "other"
        }
    }

    private fun writeCsv(file: File, runs: List<RunRow>) {
        file.writeText(
            buildString {
                appendLine(
                    "preset,place,targetKm,sampleIndex,ok,failCategory,timeMs,airM,pathM,pathAir,airErrorM," +
                        "checkpoints,onObjectCount,nearCount,genericCount,reRoutes,reverts"
                )
                for (r in runs) {
                    appendLine(
                        listOf(
                            r.preset,
                            r.place,
                            r.targetKm,
                            r.sampleIndex,
                            r.ok,
                            r.failCategory.orEmpty(),
                            r.timeMs,
                            r.airM ?: "",
                            r.pathM ?: "",
                            r.pathAir?.let { "%.4f".format(Locale.US, it) } ?: "",
                            r.airErrorM?.let { "%.1f".format(Locale.US, it) } ?: "",
                            r.checkpoints,
                            r.onObjectCount,
                            r.nearCount,
                            r.genericCount,
                            r.reRoutes,
                            r.reverts
                        ).joinToString(",")
                    )
                }
            }
        )
    }

    private fun buildSummary(runs: List<RunRow>, tag: String): String = buildString {
        appendLine("preset_matrix_$tag total_runs=${runs.size}")
        appendLine("places=${PLACES.keys.joinToString()} distances_km=${DISTANCES_KM.joinToString()}")
        appendLine()
        for (preset in PRESETS) {
            val group = runs.filter { it.preset == preset.name }
            val success = group.count { it.ok }
            val times = group.map { it.timeMs.toDouble() }.sorted()
            val pathAirs = group.filter { it.ok && it.pathAir != null }.map { it.pathAir!! }.sorted()
            val airErrors = group.filter { it.ok && it.airErrorM != null }.map { it.airErrorM!! }.sorted()
            val totalCps = group.sumOf { it.onObjectCount + it.nearCount + it.genericCount }
            val onObjectCps = group.sumOf { it.onObjectCount }
            val pctOnObject = if (totalCps > 0) 100.0 * onObjectCps / totalCps else 0.0
            val fails = group.filter { !it.ok }.groupingBy { it.failCategory.orEmpty() }.eachCount()

            appendLine("=== ${preset.name} (ratio=${preset.maxPathToAirRatio}, tol=${preset.airDistanceToleranceM.toInt()}m) ===")
            appendLine(
                "success_rate=${success}/${group.size} (${"%.1f".format(Locale.US, 100.0 * success / group.size.coerceAtLeast(1))}%)"
            )
            appendLine(
                "time_ms median=${median(times).toLong()} max=${times.maxOrNull()?.toLong() ?: 0}"
            )
            appendLine("pathAir median=${median(pathAirs).let { "%.3f".format(Locale.US, it) }}")
            appendLine("airError_m median=${median(airErrors).let { "%.1f".format(Locale.US, it) }}")
            appendLine("%_onObject_among_cps=${"%.1f".format(Locale.US, pctOnObject)}")
            appendLine("fail_categories: ${fails.entries.joinToString { "${it.key}=${it.value}" }}")
            appendLine()
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mid = values.size / 2
        return if (values.size % 2 == 1) {
            values[mid]
        } else {
            (values[mid - 1] + values[mid]) / 2.0
        }
    }

    private data class RunRow(
        val preset: String,
        val place: String,
        val targetKm: Double,
        val sampleIndex: Int,
        val ok: Boolean,
        val failCategory: String?,
        val timeMs: Long,
        val airM: Double?,
        val pathM: Double?,
        val pathAir: Double?,
        val airErrorM: Double?,
        val checkpoints: Int,
        val onObjectCount: Int,
        val nearCount: Int,
        val genericCount: Int,
        val reRoutes: Int,
        val reverts: Int
    ) {
        fun toLogLine(): String =
            if (ok) {
                "RUN ok preset=$preset place=$place target=${targetKm}km#$sampleIndex " +
                    "timeMs=$timeMs air=${airM?.toInt()} path=${pathM?.toInt()} " +
                    "pathAir=${pathAir?.let { "%.2f".format(Locale.US, it) }} " +
                    "onObj=$onObjectCount near=$nearCount generic=$genericCount"
            } else {
                "RUN FAIL preset=$preset place=$place target=${targetKm}km#$sampleIndex cat=$failCategory timeMs=$timeMs"
            }
    }

    companion object {
        private const val OSRM_BASE_URL = "https://routing.openstreetmap.de/routed-foot/"
        private const val OVERPASS_BASE_URL = "https://overpass-api.de/api/"
        private const val SAMPLES_PER_CELL = 3
        private const val CALL_DELAY_MS = 400L

        private val PRESETS = listOf(
            RouteGenerationPreset.PRECISE,
            RouteGenerationPreset.BALANCED,
            RouteGenerationPreset.FLEXIBLE
        )

        private val PLACES = linkedMapOf(
            "Berlin_Mitte" to GeoPoint(latitude = 52.5200, longitude = 13.4050),
            "Spandau" to GeoPoint(latitude = 52.5350, longitude = 13.2000)
        )

        private val DISTANCES_KM = listOf(2.0, 4.0, 7.0, 10.0)
    }
}