package com.orienteer.app.debug

import com.orienteer.app.BuildConfig
import com.orienteer.app.data.local.AppThemeMode
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.data.local.CoursePointsStore
import com.orienteer.app.data.local.RouteGenerationPreset
import com.orienteer.app.data.local.UserPreferencesStore
import com.orienteer.app.data.local.dao.RunSessionDao
import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.repository.RouteRepository
import com.orienteer.app.data.repository.RunRepository
import com.orienteer.app.domain.CoursePointsSet
import com.orienteer.app.util.GeoUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds demo routes, fake run history, favorites, and showcase settings for store screenshots.
 * Public park geometry only — not personal coordinates. Debug builds only.
 */
@Singleton
class ScreenshotDemoSeeder @Inject constructor(
    private val routeRepository: RouteRepository,
    private val runRepository: RunRepository,
    private val runSessionDao: RunSessionDao,
    private val coursePointsStore: CoursePointsStore,
    private val userPreferencesStore: UserPreferencesStore
) {
    suspend fun seed() {
        if (!BuildConfig.DEBUG) return

        seedShowcaseSettings()

        val berlin = buildBerlinRoute()
        val hyde = buildHydeParkRoute()
        val riga = buildRigaVictoryParkRoute()
        listOf(berlin, hyde, riga).forEach { routeRepository.saveRoute(it) }

        // Stable History across captures.
        runSessionDao.deleteSessionsForRoute(berlin.id)
        runSessionDao.deleteSessionsForRoute(hyde.id)
        runSessionDao.deleteSessionsForRoute(riga.id)

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        // Yesterday has 3 runs (store screenshot); older days one each (collapsed headers).
        val hourMs = 3_600_000L
        val samples = listOf(
            // Newest day first in UI — staggered morning / midday / evening.
            DemoRun(berlin.id, 5_100.0, 32 * 60_000L, 4, daysAgo = 1, hoursAgoWithinDay = 7),
            DemoRun(hyde.id, 4_800.0, 28 * 60_000L + 40_000L, 4, daysAgo = 1, hoursAgoWithinDay = 11),
            DemoRun(riga.id, 3_400.0, 21 * 60_000L + 10_000L, 3, daysAgo = 1, hoursAgoWithinDay = 15),
            DemoRun(hyde.id, 8_200.0, 49 * 60_000L + 30_000L, 5, daysAgo = 2, hoursAgoWithinDay = 10),
            DemoRun(berlin.id, 6_500.0, 39 * 60_000L + 15_000L, 4, daysAgo = 3, hoursAgoWithinDay = 9),
            DemoRun(riga.id, 9_100.0, 54 * 60_000L + 5_000L, 5, daysAgo = 4, hoursAgoWithinDay = 8),
            DemoRun(berlin.id, 7_200.0, 43 * 60_000L + 20_000L, 4, daysAgo = 5, hoursAgoWithinDay = 12),
            DemoRun(hyde.id, 5_600.0, 33 * 60_000L + 50_000L, 4, daysAgo = 6, hoursAgoWithinDay = 14)
        )
        samples.forEach { sample ->
            val route = when (sample.routeId) {
                berlin.id -> berlin
                hyde.id -> hyde
                else -> riga
            }
            val started = now - sample.daysAgo * dayMs - sample.hoursAgoWithinDay * hourMs
            val trackLen = (12 + sample.daysAgo * 2).coerceAtMost(route.polylinePoints.size)
            runRepository.saveSession(
                routeId = sample.routeId,
                totalDistanceM = sample.distanceM,
                elapsedTimeMs = sample.elapsedMs,
                startedAt = started,
                finishedAt = started + sample.elapsedMs,
                checkpointsReached = sample.checkpoints,
                trackedPoints = route.polylinePoints.take(trackLen),
                trackedPointTimestampsMs = emptyList(),
                checkpointReachedAtMs = emptyMap()
            )
        }

        seedFavorites(berlin, hyde, riga, now)
    }

    private suspend fun seedShowcaseSettings() {
        userPreferencesStore.setThemeMode(AppThemeMode.LIGHT)
        userPreferencesStore.setRouteGenerationPreset(RouteGenerationPreset.BALANCED)
        userPreferencesStore.setShowAirLegLines(true)
        userPreferencesStore.setShowAirLegDistanceLabels(true)
        userPreferencesStore.setShowWalkingPathPolyline(true)
        userPreferencesStore.setShowYouAreHereDuringRun(true)
        userPreferencesStore.setIncludePdfLegends(true)
        userPreferencesStore.setUseIofCourseOverprint(true)
        // Standard OSM loads tiles more reliably on emulator for store captures.
        userPreferencesStore.setBasemapStyle(BasemapStyle.STANDARD)
        userPreferencesStore.setCheckpointWindow(3, 6)
    }

    private suspend fun seedFavorites(
        berlin: Route,
        hyde: Route,
        riga: Route,
        now: Long
    ) {
        // Clear prior demo favorites by replacing with a known list via remove+add.
        coursePointsStore.readFavorites().forEach { coursePointsStore.removeFavorite(it.id) }

        val sets = listOf(
            CoursePointsSet(
                id = "demo-fav-tiergarten",
                savedAtMs = now - dayMs(1),
                start = berlin.checkpoints.first().position,
                finish = berlin.checkpoints.first().position,
                locked = berlin.checkpoints.drop(1).dropLast(1).map { it.position },
                anyOrder = false,
                usedGpsStart = true,
                targetDistanceKm = 5.0,
                minCheckpoints = 3,
                maxCheckpoints = 5,
                maxPathToAirRatio = 1.8f,
                airDistanceToleranceM = 1500f,
                displayName = "Berlin Tiergarten 5 km"
            ),
            CoursePointsSet(
                id = "demo-fav-hyde",
                savedAtMs = now - dayMs(2),
                start = hyde.checkpoints.first().position,
                finish = hyde.checkpoints.first().position,
                locked = hyde.checkpoints.drop(1).dropLast(1).map { it.position },
                anyOrder = false,
                usedGpsStart = true,
                targetDistanceKm = 8.0,
                minCheckpoints = 4,
                maxCheckpoints = 6,
                maxPathToAirRatio = 1.8f,
                airDistanceToleranceM = 1500f,
                displayName = "Hyde Park loop 8 km"
            ),
            CoursePointsSet(
                id = "demo-fav-riga",
                savedAtMs = now - dayMs(3),
                start = riga.checkpoints.first().position,
                finish = riga.checkpoints.first().position,
                locked = riga.checkpoints.drop(1).dropLast(1).map { it.position },
                anyOrder = true,
                usedGpsStart = true,
                targetDistanceKm = 3.5,
                minCheckpoints = 2,
                maxCheckpoints = 4,
                maxPathToAirRatio = 1.5f,
                airDistanceToleranceM = 700f,
                displayName = "Riga Victory Park 3.5 km"
            )
        )
        sets.forEach { coursePointsStore.addFavorite(it) }
    }

    private fun dayMs(days: Int): Long = days * 86_400_000L

    private data class DemoRun(
        val routeId: String,
        val distanceM: Double,
        val elapsedMs: Long,
        val checkpoints: Int,
        val daysAgo: Int,
        /** Hours before “now” within that calendar day (staggers clock times). */
        val hoursAgoWithinDay: Int
    )

    companion object {
        fun buildBerlinRoute(): Route = buildParkLoop(
            id = ScreenshotDemoMode.ROUTE_ID,
            targetDistanceM = 5_000.0,
            corners = listOf(
                GeoPoint(52.5145, 13.3501), // Straße des 17. Juni
                GeoPoint(52.5178, 13.3472), // Bellevue
                GeoPoint(52.5162, 13.3588), // Brandenburg approach
                GeoPoint(52.5124, 13.3570) // south park
            ),
            landmarks = listOf(
                Landmark("Straße des 17. Juni", "highway=primary", "Start / Finish — return here to complete your run."),
                Landmark("Schloss Bellevue", "tourism=attraction", "Path junction near Schloss Bellevue"),
                Landmark("Tiergarten path", "highway=footway", "Park path toward Brandenburg Gate"),
                Landmark("Tiergarten", "leisure=park", "Southern Tiergarten loop")
            )
        )

        fun buildHydeParkRoute(): Route = buildParkLoop(
            id = "demo-hyde-park",
            targetDistanceM = 8_000.0,
            corners = listOf(
                GeoPoint(51.5073, -0.1657), // Speakers' Corner side
                GeoPoint(51.5122, -0.1600), // north path
                GeoPoint(51.5088, -0.1520), // Serpentine east
                GeoPoint(51.5035, -0.1585) // south
            ),
            landmarks = listOf(
                Landmark("Hyde Park Corner path", "highway=footway", "Start / Finish — Hyde Park loop."),
                Landmark("North Carriage Drive", "highway=tertiary", "North park carriage path"),
                Landmark("Serpentine", "natural=water", "Path by the Serpentine"),
                Landmark("South Carriage Drive", "highway=tertiary", "Southern park path")
            )
        )

        fun buildRigaVictoryParkRoute(): Route = buildParkLoop(
            id = "demo-riga-victory",
            targetDistanceM = 3_500.0,
            corners = listOf(
                GeoPoint(56.9365, 24.0855),
                GeoPoint(56.9398, 24.0810),
                GeoPoint(56.9415, 24.0885),
                GeoPoint(56.9372, 24.0920)
            ),
            landmarks = listOf(
                Landmark("Uzvaras parks", "leisure=park", "Start / Finish — Victory Park."),
                Landmark("Park path NW", "highway=footway", "Northwest park junction"),
                Landmark("Daugaava side path", "highway=footway", "Path toward the river"),
                Landmark("Park path SE", "highway=footway", "Southeast loop")
            )
        )

        /** Mid-run fake session for Active Run screenshots (no FGS). */
        fun fakeMidRunSession(route: Route): com.orienteer.app.data.model.RunSession {
            val started = System.currentTimeMillis() - 14 * 60_000L - 20_000L
            val track = route.polylinePoints.take((route.polylinePoints.size * 0.35).toInt().coerceAtLeast(8))
            return com.orienteer.app.data.model.RunSession(
                routeId = route.id,
                trackedPoints = track,
                trackedPointTimestampsMs = track.indices.map { started + it * 8_000L },
                currentCheckpointIdx = 2,
                checkpointReachedAtMs = mapOf(1 to started + 6 * 60_000L),
                totalDistanceM = 2_450.0,
                elapsedTimeMs = 14 * 60_000L + 20_000L,
                isActive = true,
                isPaused = false,
                startedAt = started,
                finishedAt = null
            )
        }

        private data class Landmark(
            val name: String,
            val type: String,
            val description: String
        )

        private fun buildParkLoop(
            id: String,
            targetDistanceM: Double,
            corners: List<GeoPoint>,
            landmarks: List<Landmark>
        ): Route {
            require(corners.size == 4 && landmarks.size == 4)
            val closed = corners + corners.first()
            val polyline = densify(closed, stepsPerLeg = 14)

            val checkpoints = buildList {
                add(
                    Checkpoint(
                        id = 0,
                        position = corners[0],
                        description = landmarks[0].description,
                        landmarkName = landmarks[0].name,
                        landmarkType = landmarks[0].type,
                        isStart = true,
                        distanceFromPrev = 0.0
                    )
                )
                for (i in 1 until corners.size) {
                    add(
                        Checkpoint(
                            id = i,
                            position = corners[i],
                            description = landmarks[i].description,
                            landmarkName = landmarks[i].name,
                            landmarkType = landmarks[i].type,
                            distanceFromPrev = GeoUtils.distanceMeters(corners[i - 1], corners[i])
                        )
                    )
                }
                add(
                    Checkpoint(
                        id = corners.size,
                        position = corners[0],
                        description = "Finish — return to start to complete your run.",
                        landmarkName = landmarks[0].name,
                        landmarkType = landmarks[0].type,
                        isStart = true,
                        distanceFromPrev = GeoUtils.distanceMeters(corners.last(), corners[0])
                    )
                )
            }

            val air = checkpoints.zipWithNext().sumOf { (a, b) ->
                GeoUtils.distanceMeters(a.position, b.position)
            }
            val path = polyline.zipWithNext().sumOf { (a, b) ->
                GeoUtils.distanceMeters(a, b)
            }.coerceAtLeast(air * 1.18)

            return Route(
                id = id,
                checkpoints = checkpoints,
                polylinePoints = polyline,
                targetDistanceM = targetDistanceM,
                totalDistanceM = air,
                actualPathDistanceM = path,
                estimatedDurationS = path / 1.4,
                createdAt = System.currentTimeMillis()
            )
        }

        private fun densify(corners: List<GeoPoint>, stepsPerLeg: Int): List<GeoPoint> {
            if (corners.size < 2) return corners
            val out = mutableListOf<GeoPoint>()
            for (i in 0 until corners.lastIndex) {
                val a = corners[i]
                val b = corners[i + 1]
                for (s in 0 until stepsPerLeg) {
                    val t = s.toDouble() / stepsPerLeg
                    out += GeoPoint(
                        latitude = a.latitude + (b.latitude - a.latitude) * t,
                        longitude = a.longitude + (b.longitude - a.longitude) * t
                    )
                }
            }
            out += corners.last()
            return out
        }
    }
}
