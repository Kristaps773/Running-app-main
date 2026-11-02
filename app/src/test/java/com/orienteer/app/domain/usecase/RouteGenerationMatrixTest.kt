package com.orienteer.app.domain.usecase

import com.google.gson.GsonBuilder
import com.orienteer.app.data.api.OsrmApiService
import com.orienteer.app.data.api.OverpassApiService
import com.orienteer.app.data.local.RouteGenerationPreferences
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.di.RetryInterceptor
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Opt-in live matrix against public OSRM/Overpass.
 *
 * Run (PowerShell):
 * ```
 * $env:ORIENTEER_ROUTE_MATRIX="true"
 * ./gradlew :app:testDebugUnitTest --tests com.orienteer.app.domain.usecase.RouteGenerationMatrixTest
 * ```
 *
 * Phase A: live samples at each distance with widest prefs.
 * Phase B: offline score every fine (ratio × airTol) cell.
 * Phase C: live confirm near recommended frontier.
 */
class RouteGenerationMatrixTest {

    private lateinit var useCase: GenerateRouteUseCase
    private lateinit var reportDir: File

    @Before
    fun setUp() {
        val enabled = System.getProperty("orienteer.routeMatrix") == "true" ||
            System.getenv("ORIENTEER_ROUTE_MATRIX") == "true"
        assumeTrue(
            "Set ORIENTEER_ROUTE_MATRIX=true (or -Dorienteer.routeMatrix=true) to run live route matrix",
            enabled
        )
        Locale.setDefault(Locale.forLanguageTag("lv-LV"))

        val gson = GsonBuilder().create()
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "OrienteerRun/matrix-test (Android unit; routing)")
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
        reportDir = File("build/reports/route-matrix").apply { mkdirs() }
    }

    @Test
    fun fineGrainMatrix_twoPhase_riga() = runBlocking {
        val samples = mutableListOf<Sample>()
        val widest = RouteGenerationPreferences(
            maxPathToAirRatio = UserPreferencesStore.MAX_RATIO.toFloat(),
            airDistanceToleranceM = UserPreferencesStore.MAX_AIR_TOLERANCE_M.toFloat()
        )

        println("=== Phase A: live samples (Riga, widest prefs) ===")
        for (targetKm in DISTANCES_KM) {
            val targetM = targetKm * 1000.0
            repeat(SAMPLES_PER_DISTANCE) { sampleIndex ->
                val result = useCase(RIGA, targetM, widest)
                val sample = result.fold(
                    onSuccess = { route -> sampleFromRoute(targetKm, sampleIndex, route) },
                    onFailure = { err ->
                        Sample(
                            targetKm = targetKm,
                            sampleIndex = sampleIndex,
                            ok = false,
                            failCategory = categorizeFailure(err.message),
                            airM = null,
                            pathM = null,
                            pathAir = null,
                            airErrorM = null,
                            checkpoints = 0
                        )
                    }
                )
                samples += sample
                println(sample.toLogLine())
                Thread.sleep(CALL_DELAY_MS)
            }
        }

        writeCsv(File(reportDir, "phase-a-samples.csv"), samples)

        println("=== Phase B: offline score fine grids ===")
        val ratios = RATIOS
        val airTols = AIR_TOLERANCES_M
        val acceptCounts = Array(ratios.size) { IntArray(airTols.size) }
        val routedSamples = samples.filter { it.ok && it.pathM != null && it.airM != null }
        assertTrue(
            "Need at least one successful routed sample for offline scoring; failures=${samples.count { !it.ok }}",
            routedSamples.isNotEmpty()
        )

        for ((ri, ratio) in ratios.withIndex()) {
            for ((ti, tol) in airTols.withIndex()) {
                var accepts = 0
                for (s in routedSamples) {
                    if (wouldAccept(s.airM!!, s.pathM!!, s.targetKm * 1000.0, ratio, tol)) {
                        accepts++
                    }
                }
                acceptCounts[ri][ti] = accepts
            }
        }

        val needed = ((routedSamples.size * 2) + 2) / 3 // ≥2/3
        var frontierRatio = ratios.last()
        var frontierTol = airTols.last()
        var foundFrontier = false
        for (ri in ratios.indices) {
            for (ti in airTols.indices) {
                if (acceptCounts[ri][ti] >= needed) {
                    frontierRatio = ratios[ri]
                    frontierTol = airTols[ti]
                    foundFrontier = true
                    break
                }
            }
            if (foundFrontier) break
        }

        val summary = buildString {
            appendLine("routed_samples=${routedSamples.size} total_samples=${samples.size} needed_for_frontier=$needed")
            appendLine(
                "frontier_min_ratio=${"%.2f".format(Locale.US, frontierRatio)} " +
                    "frontier_min_air_tol_m=${frontierTol.toInt()} found=$foundFrontier"
            )
            appendLine("default_1.5x_700m_accepts=${countAccepts(routedSamples, 1.5, 700.0)}/${routedSamples.size}")
            appendLine("current_default_check ratio=${UserPreferencesStore.DEFAULT_MAX_PATH_TO_AIR} tol=${UserPreferencesStore.DEFAULT_AIR_TOLERANCE_M}")
            appendLine()
            appendLine("accept_rate_by_distance_km (routed only, at frontier):")
            for (km in DISTANCES_KM) {
                val atDist = routedSamples.filter { it.targetKm == km }
                if (atDist.isEmpty()) {
                    appendLine("  $km: no routed samples")
                    continue
                }
                val ok = atDist.count {
                    wouldAccept(it.airM!!, it.pathM!!, km * 1000.0, frontierRatio, frontierTol)
                }
                appendLine("  $km: $ok/${atDist.size}")
            }
            appendLine()
            appendLine("heatmap_accept_count (sample ratios × key air tols):")
            fun tolIndex(m: Double): Int =
                airTols.indexOfFirst { abs(it - m) < 1e-6 }.coerceAtLeast(0)
            for ((ri, ratio) in ratios.withIndex()) {
                if (ri % 2 != 0) continue
                append(
                    "r=${"%.2f".format(Locale.US, ratio)}: " +
                        "tol200=${acceptCounts[ri][tolIndex(200.0)]} " +
                        "tol700=${acceptCounts[ri][tolIndex(700.0)]} " +
                        "tol1500=${acceptCounts[ri][tolIndex(1500.0)]} " +
                        "tol2500=${acceptCounts[ri][airTols.lastIndex]}\n"
                )
            }
        }
        println(summary)
        File(reportDir, "phase-b-summary.txt").writeText(summary)

        writeAcceptGridCsv(File(reportDir, "phase-b-accept-grid.csv"), ratios, airTols, acceptCounts)

        println("=== Phase C: live confirm near frontier ===")
        val confirmPrefs = listOf(
            RouteGenerationPreferences(frontierRatio.toFloat(), frontierTol.toFloat()),
            RouteGenerationPreferences(
                (frontierRatio - 0.05).coerceAtLeast(UserPreferencesStore.MIN_RATIO).toFloat(),
                frontierTol.toFloat()
            ),
            RouteGenerationPreferences(
                frontierRatio.toFloat(),
                (frontierTol - 50.0).coerceAtLeast(UserPreferencesStore.MIN_AIR_TOLERANCE_M).toFloat()
            ),
            RouteGenerationPreferences(
                UserPreferencesStore.DEFAULT_MAX_PATH_TO_AIR,
                UserPreferencesStore.DEFAULT_AIR_TOLERANCE_M
            ),
            RouteGenerationPreferences(1.8f, 1000f)
        )
        val confirmTargetsKm = listOf(3.0, 5.0, 7.0)
        val confirmLines = mutableListOf<String>()
        for (prefs in confirmPrefs) {
            for (km in confirmTargetsKm) {
                val result = useCase(
                    RIGA,
                    km * 1000.0,
                    prefs
                )
                val line = result.fold(
                    onSuccess = { r ->
                        "CONFIRM ok prefs(ratio=${prefs.maxPathToAirRatio},tol=${prefs.airDistanceToleranceM}) " +
                            "target=${km}km air=${r.totalDistanceM.toInt()} path=${r.actualPathDistanceM.toInt()} " +
                            "ratio=${r.pathToAirRatio?.let { "%.2f".format(Locale.US, it) }}"
                    },
                    onFailure = { e ->
                        "CONFIRM FAIL prefs(ratio=${prefs.maxPathToAirRatio},tol=${prefs.airDistanceToleranceM}) " +
                            "target=${km}km msg=${e.message}"
                    }
                )
                println(line)
                confirmLines += line
                Thread.sleep(CALL_DELAY_MS)
            }
        }
        File(reportDir, "phase-c-confirm.txt").writeText(confirmLines.joinToString("\n"))

        // Persist frontier recommendation for defaults step
        File(reportDir, "recommended-defaults.properties").writeText(
            buildString {
                appendLine("frontier.maxPathToAirRatio=${"%.2f".format(Locale.US, frontierRatio)}")
                appendLine("frontier.airDistanceToleranceM=${frontierTol.toInt()}")
                appendLine("suggested.maxPathToAirRatio=${"%.2f".format(Locale.US, frontierRatio.coerceAtLeast(1.8))}")
                appendLine(
                    "suggested.airDistanceToleranceM=${
                        frontierTol.coerceAtLeast(1000.0).toInt()
                    }"
                )
            }
        )

        assertTrue("Frontier should accept ≥2/3 routed samples", foundFrontier || routedSamples.isEmpty())
    }

    private fun countAccepts(samples: List<Sample>, ratio: Double, tol: Double): Int =
        samples.count { wouldAccept(it.airM!!, it.pathM!!, it.targetKm * 1000.0, ratio, tol) }

    private fun sampleFromRoute(targetKm: Double, sampleIndex: Int, route: Route): Sample {
        val air = route.totalDistanceM
        val path = route.actualPathDistanceM.takeIf { it > 0.0 }
        return Sample(
            targetKm = targetKm,
            sampleIndex = sampleIndex,
            ok = path != null,
            failCategory = if (path == null) "no_routed_path" else null,
            airM = air,
            pathM = path,
            pathAir = path?.let { it / air.coerceAtLeast(1.0) },
            airErrorM = abs(air - targetKm * 1000.0),
            checkpoints = route.waypointCount
        )
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

    private fun writeCsv(file: File, samples: List<Sample>) {
        file.writeText(
            buildString {
                appendLine("targetKm,sampleIndex,ok,failCategory,airM,pathM,pathAir,airErrorM,checkpoints")
                for (s in samples) {
                    appendLine(
                        listOf(
                            s.targetKm,
                            s.sampleIndex,
                            s.ok,
                            s.failCategory.orEmpty(),
                            s.airM ?: "",
                            s.pathM ?: "",
                            s.pathAir ?: "",
                            s.airErrorM ?: "",
                            s.checkpoints
                        ).joinToString(",")
                    )
                }
            }
        )
    }

    private fun writeAcceptGridCsv(
        file: File,
        ratios: List<Double>,
        airTols: List<Double>,
        acceptCounts: Array<IntArray>
    ) {
        file.writeText(
            buildString {
                append("ratio")
                for (tol in airTols) append(",").append(tol.toInt())
                appendLine()
                for ((ri, ratio) in ratios.withIndex()) {
                    append("%.2f".format(Locale.US, ratio))
                    for (ti in airTols.indices) {
                        append(",").append(acceptCounts[ri][ti])
                    }
                    appendLine()
                }
            }
        )
    }

    private data class Sample(
        val targetKm: Double,
        val sampleIndex: Int,
        val ok: Boolean,
        val failCategory: String?,
        val airM: Double?,
        val pathM: Double?,
        val pathAir: Double?,
        val airErrorM: Double?,
        val checkpoints: Int
    ) {
        fun toLogLine(): String =
            if (ok) {
                "SAMPLE ok target=${targetKm}km#$sampleIndex air=${airM?.toInt()} path=${pathM?.toInt()} " +
                    "pathAir=${pathAir?.let { "%.2f".format(Locale.US, it) }} err=${airErrorM?.toInt()} cp=$checkpoints"
            } else {
                "SAMPLE FAIL target=${targetKm}km#$sampleIndex cat=$failCategory"
            }
    }

    companion object {
        private const val OSRM_BASE_URL = "https://routing.openstreetmap.de/routed-foot/"
        private const val OVERPASS_BASE_URL = "https://overpass-api.de/api/"
        private val RIGA = GeoPoint(latitude = 56.9496, longitude = 24.1052)
        private const val SAMPLES_PER_DISTANCE = 3
        private const val CALL_DELAY_MS = 400L

        /** 1.0–10.0 km step 0.5 */
        private val DISTANCES_KM: List<Double> =
            generateSequence(1.0) { it + 0.5 }.takeWhile { it <= 10.0 + 1e-9 }.toList()

        /** 1.1–2.5 step 0.05 */
        private val RATIOS: List<Double> =
            generateSequence(1.1) { it + 0.05 }.takeWhile { it <= 2.5 + 1e-9 }
                .map { (it * 100).toInt() / 100.0 }
                .toList()

        /** 200–2500 m step 50 */
        private val AIR_TOLERANCES_M: List<Double> =
            generateSequence(200.0) { it + 50.0 }.takeWhile { it <= 2500.0 + 1e-9 }.toList()

        /** Mirrors GenerateRouteUseCase.selectAcceptableRoute rules. */
        fun wouldAccept(
            airM: Double,
            pathM: Double,
            targetM: Double,
            maxRatio: Double,
            airToleranceM: Double
        ): Boolean {
            if (pathM <= 0.0) return false
            if (abs(airM - targetM) > airToleranceM) return false
            if (pathM > airM.coerceAtLeast(1.0) * maxRatio) return false
            return true
        }
    }
}
