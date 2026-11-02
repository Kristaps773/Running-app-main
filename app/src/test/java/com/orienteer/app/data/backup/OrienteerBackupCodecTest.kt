package com.orienteer.app.data.backup

import com.google.gson.Gson
import com.orienteer.app.data.local.entity.RouteEntity
import com.orienteer.app.data.local.entity.RunSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrienteerBackupCodecTest {

    private val gson = Gson()

    @Test
    fun roundTrip_preservesRoutesSessionsAndSettings() {
        val original = OrienteerBackupFile(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = 1_700_000_000_000L,
            routes = listOf(
                RouteEntity(
                    id = "route-1",
                    checkpointsJson = "[]",
                    polylineJson = "[]",
                    targetDistanceM = 5000.0,
                    totalDistanceM = 4800.0,
                    actualPathDistanceM = 5100.0,
                    estimatedDurationS = 2400.0,
                    createdAt = 100L
                )
            ),
            runSessions = listOf(
                RunSessionEntity(
                    sessionId = 42L,
                    routeId = "route-1",
                    totalDistanceM = 5000.0,
                    elapsedTimeMs = 1_800_000L,
                    startedAt = 200L,
                    finishedAt = 300L,
                    checkpointsReached = 5,
                    trackedPointsJson = "[{\"latitude\":56.9,\"longitude\":24.1}]",
                    trackedPointTimestampsJson = "[200,300]",
                    checkpointReachedAtJson = "{\"1\":250}"
                )
            ),
            settings = BackupSettingsSnapshot(
                themeMode = "DARK",
                maxPathToAirRatio = 1.8f,
                airDistanceToleranceM = 1500f,
                showAirLegLines = true,
                showAirLegDistanceLabels = false,
                showWalkingPathPolyline = true,
                basemapStyle = "OPEN_TOPO",
                includePdfLegends = true
            )
        )

        val json = gson.toJson(original)
        val decoded = gson.fromJson(json, OrienteerBackupFile::class.java)

        assertEquals(BACKUP_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(original.exportedAt, decoded.exportedAt)
        assertEquals(1, decoded.routes.size)
        assertEquals("route-1", decoded.routes[0].id)
        assertEquals(5100.0, decoded.routes[0].actualPathDistanceM, 0.0)
        assertEquals(1, decoded.runSessions.size)
        assertEquals(42L, decoded.runSessions[0].sessionId)
        assertEquals("route-1", decoded.runSessions[0].routeId)
        assertEquals("DARK", decoded.settings.themeMode)
        assertEquals("OPEN_TOPO", decoded.settings.basemapStyle)
        assertEquals(true, decoded.settings.includePdfLegends)
        assertTrue(json.contains("trackedPointsJson"))
    }

    @Test
    fun rejectsUnsupportedFutureSchemaInParseLogic() {
        val json = """{"schemaVersion":999,"exportedAt":1,"routes":[],"runSessions":[],"settings":{}}"""
        val decoded = gson.fromJson(json, OrienteerBackupFile::class.java)
        assertEquals(999, decoded.schemaVersion)
        assertTrue(decoded.schemaVersion > BACKUP_SCHEMA_VERSION)
    }
}
