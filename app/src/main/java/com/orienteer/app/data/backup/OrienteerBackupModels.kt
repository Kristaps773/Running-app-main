package com.orienteer.app.data.backup

import com.orienteer.app.data.local.entity.RouteEntity
import com.orienteer.app.data.local.entity.RunSessionEntity

/**
 * Versioned on-device backup of run history, routes, and settings.
 * Bump [BACKUP_SCHEMA_VERSION] when the JSON shape changes incompatibly.
 */
const val BACKUP_SCHEMA_VERSION = 1

data class OrienteerBackupFile(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAt: Long = 0L,
    val routes: List<RouteEntity> = emptyList(),
    val runSessions: List<RunSessionEntity> = emptyList(),
    val settings: BackupSettingsSnapshot = BackupSettingsSnapshot()
)

data class BackupSettingsSnapshot(
    val themeMode: String? = null,
    val maxPathToAirRatio: Float? = null,
    val airDistanceToleranceM: Float? = null,
    val showAirLegLines: Boolean? = null,
    val showAirLegDistanceLabels: Boolean? = null,
    val showWalkingPathPolyline: Boolean? = null,
    val showYouAreHereDuringRun: Boolean? = null,
    val basemapStyle: String? = null,
    val includePdfLegends: Boolean? = null,
    val useIofCourseOverprint: Boolean? = null
)
