package com.orienteer.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orienteer.app.data.backup.BackupSettingsSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.find { it.name == value } ?: SYSTEM
    }
}

/**
 * Route generation: target is straight-line (air) loop distance; walking path may be up to
 * [maxPathToAirRatio] × that air distance. Air may deviate from target by [airDistanceToleranceM].
 */
data class RouteGenerationRatios(
    val maxPathToAirRatio: Double,
    val airDistanceToleranceM: Double
)

/** Used by route generation. */
data class RouteGenerationPreferences(
    val maxPathToAirRatio: Float = 1.5f,
    val airDistanceToleranceM: Float = UserPreferencesStore.DEFAULT_AIR_TOLERANCE_M,
    /** Intermediate controls only (excludes start/finish). */
    val minCheckpoints: Int = UserPreferencesStore.DEFAULT_MIN_CHECKPOINTS,
    val maxCheckpoints: Int = UserPreferencesStore.DEFAULT_MAX_CHECKPOINTS
)

data class MapDisplayPreferences(
    val showAirLegLines: Boolean = false,
    val showAirLegDistanceLabels: Boolean = false,
    val showWalkingPathPolyline: Boolean = false,
    val basemapStyle: BasemapStyle = BasemapStyle.STANDARD,
    val includePdfLegends: Boolean = false,
    /** GPS “you are here” disc while an active run is on the map. */
    val showYouAreHereDuringRun: Boolean = false,
    /**
     * Classic IOF purple overprint (triangle / hollow circles / finish) instead of app badges.
     * Independent of [basemapStyle]; user toggles in Settings.
     */
    val useIofCourseOverprint: Boolean = false
) {
    /** Alias used by map screens. */
    val showAirLegs: Boolean get() = showAirLegLines

    val useDarkBasemap: Boolean get() = basemapStyle == BasemapStyle.DARK
}

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.userPreferencesDataStore

    private val keyMaxPathToAir = floatPreferencesKey("max_path_to_air_ratio")
    private val keyAirToleranceM = floatPreferencesKey("air_distance_tolerance_m")
    private val keyMaxTotalLegacy = floatPreferencesKey("max_total_path_to_air_ratio")
    private val keyMinCheckpoints = intPreferencesKey("min_checkpoints")
    private val keyMaxCheckpoints = intPreferencesKey("max_checkpoints")
    private val keyShowAirLegs = booleanPreferencesKey("show_air_leg_lines")
    private val keyShowAirLabels = booleanPreferencesKey("show_air_leg_distance_labels")
    private val keyShowWalkingPath = booleanPreferencesKey("show_walking_path_polyline")
    private val keyShowYouAreHereDuringRun = booleanPreferencesKey("show_you_are_here_during_run")
    private val keyIncludePdfLegends = booleanPreferencesKey("include_pdf_legends")
    private val keyUseIofCourseOverprint = booleanPreferencesKey("use_iof_course_overprint")
    private val keyDarkBasemap = booleanPreferencesKey("use_dark_basemap")
    private val keyBasemapStyle = stringPreferencesKey("basemap_style")
    private val keyThemeMode = stringPreferencesKey("theme_mode")

    val themeMode: Flow<AppThemeMode> = dataStore.data.map { p ->
        AppThemeMode.fromStorage(p[keyThemeMode])
    }.distinctUntilChanged()

    val routeRatios: Flow<RouteGenerationRatios> = dataStore.data.map { p ->
        RouteGenerationRatios(
            maxPathToAirRatio = (p[keyMaxPathToAir] ?: p[keyMaxTotalLegacy] ?: DEFAULT_MAX_PATH_TO_AIR)
                .toDouble()
                .coerceIn(MIN_RATIO, MAX_RATIO),
            airDistanceToleranceM = (p[keyAirToleranceM] ?: DEFAULT_AIR_TOLERANCE_M)
                .toDouble()
                .coerceIn(MIN_AIR_TOLERANCE_M, MAX_AIR_TOLERANCE_M)
        )
    }.distinctUntilChanged()

    val routeGenerationPreferences: Flow<RouteGenerationPreferences> =
        dataStore.data.map { p ->
            val (lo, hi) = coerceCheckpointWindow(
                p[keyMinCheckpoints] ?: DEFAULT_MIN_CHECKPOINTS,
                p[keyMaxCheckpoints] ?: DEFAULT_MAX_CHECKPOINTS
            )
            RouteGenerationPreferences(
                maxPathToAirRatio = (p[keyMaxPathToAir] ?: p[keyMaxTotalLegacy] ?: DEFAULT_MAX_PATH_TO_AIR)
                    .coerceIn(MIN_RATIO.toFloat(), MAX_RATIO.toFloat()),
                airDistanceToleranceM = (p[keyAirToleranceM] ?: DEFAULT_AIR_TOLERANCE_M)
                    .coerceIn(MIN_AIR_TOLERANCE_M.toFloat(), MAX_AIR_TOLERANCE_M.toFloat()),
                minCheckpoints = lo,
                maxCheckpoints = hi
            )
        }.distinctUntilChanged()

    val mapDisplayPreferences: Flow<MapDisplayPreferences> = dataStore.data.map { p ->
        val air = p[keyShowAirLegs] ?: DEFAULT_SHOW_AIR_LEGS
        val basemapStyle = p[keyBasemapStyle]?.let(BasemapStyle::fromStorage)
            ?: if (p[keyDarkBasemap] == true) BasemapStyle.DARK else DEFAULT_BASEMAP_STYLE
        // Independent of basemap — do not auto-lock overprint to Pseud-O / Street-O.
        val iofOverprint = p[keyUseIofCourseOverprint] ?: DEFAULT_USE_IOF_COURSE_OVERPRINT
        MapDisplayPreferences(
            showAirLegLines = air,
            showAirLegDistanceLabels = p[keyShowAirLabels] ?: DEFAULT_SHOW_AIR_LABELS,
            showWalkingPathPolyline = p[keyShowWalkingPath] ?: DEFAULT_SHOW_WALKING_PATH,
            basemapStyle = basemapStyle,
            includePdfLegends = p[keyIncludePdfLegends] ?: DEFAULT_INCLUDE_PDF_LEGENDS,
            showYouAreHereDuringRun = p[keyShowYouAreHereDuringRun]
                ?: DEFAULT_SHOW_YOU_ARE_HERE_DURING_RUN,
            useIofCourseOverprint = iofOverprint
        ).let { prefs ->
            if (!air) prefs.copy(showAirLegDistanceLabels = false) else prefs
        }
    }.distinctUntilChanged()

    suspend fun routeRatiosSnapshot(): RouteGenerationRatios = routeRatios.first()

    suspend fun setMaxPathToAirRatio(value: Float) {
        dataStore.edit {
            it[keyMaxPathToAir] = value.coerceIn(MIN_RATIO.toFloat(), MAX_RATIO.toFloat())
        }
    }

    suspend fun setAirDistanceToleranceM(value: Float) {
        dataStore.edit {
            it[keyAirToleranceM] = value.coerceIn(MIN_AIR_TOLERANCE_M.toFloat(), MAX_AIR_TOLERANCE_M.toFloat())
        }
    }

    suspend fun setRouteGenerationPreset(preset: RouteGenerationPreset) {
        dataStore.edit {
            it[keyMaxPathToAir] = preset.maxPathToAirRatio
                .coerceIn(MIN_RATIO.toFloat(), MAX_RATIO.toFloat())
            it[keyAirToleranceM] = preset.airDistanceToleranceM
                .coerceIn(MIN_AIR_TOLERANCE_M.toFloat(), MAX_AIR_TOLERANCE_M.toFloat())
        }
    }

    suspend fun setCheckpointWindow(min: Int, max: Int) {
        val (lo, hi) = coerceCheckpointWindow(min, max)
        dataStore.edit {
            it[keyMinCheckpoints] = lo
            it[keyMaxCheckpoints] = hi
        }
    }

    suspend fun setShowAirLegLines(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keyShowAirLegs] = value
            if (!value) prefs[keyShowAirLabels] = false
        }
    }

    suspend fun setShowAirLegDistanceLabels(value: Boolean) {
        dataStore.edit { it[keyShowAirLabels] = value }
    }

    suspend fun setShowWalkingPathPolyline(value: Boolean) {
        dataStore.edit { it[keyShowWalkingPath] = value }
    }

    suspend fun setShowYouAreHereDuringRun(value: Boolean) {
        dataStore.edit { it[keyShowYouAreHereDuringRun] = value }
    }

    suspend fun setIncludePdfLegends(value: Boolean) {
        dataStore.edit { it[keyIncludePdfLegends] = value }
    }

    suspend fun setUseIofCourseOverprint(value: Boolean) {
        dataStore.edit { it[keyUseIofCourseOverprint] = value }
    }

    suspend fun setUseDarkBasemap(value: Boolean) {
        dataStore.edit {
            it[keyDarkBasemap] = value
            it[keyBasemapStyle] = if (value) BasemapStyle.DARK.name else BasemapStyle.STANDARD.name
        }
    }

    suspend fun setBasemapStyle(style: BasemapStyle) {
        dataStore.edit {
            it[keyBasemapStyle] = style.name
            it[keyDarkBasemap] = style == BasemapStyle.DARK
            // Basemap only — do not flip IOF overprint / air-leg prefs when cycling styles.
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[keyThemeMode] = mode.name }
    }

    suspend fun backupSnapshot(): BackupSettingsSnapshot {
        val ratios = routeRatios.first()
        val map = mapDisplayPreferences.first()
        val theme = themeMode.first()
        return BackupSettingsSnapshot(
            themeMode = theme.name,
            maxPathToAirRatio = ratios.maxPathToAirRatio.toFloat(),
            airDistanceToleranceM = ratios.airDistanceToleranceM.toFloat(),
            showAirLegLines = map.showAirLegLines,
            showAirLegDistanceLabels = map.showAirLegDistanceLabels,
            showWalkingPathPolyline = map.showWalkingPathPolyline,
            basemapStyle = map.basemapStyle.name,
            includePdfLegends = map.includePdfLegends,
            showYouAreHereDuringRun = map.showYouAreHereDuringRun,
            useIofCourseOverprint = map.useIofCourseOverprint
        )
    }

    suspend fun restoreFromBackup(snapshot: BackupSettingsSnapshot) {
        dataStore.edit { prefs ->
            snapshot.themeMode?.let { prefs[keyThemeMode] = AppThemeMode.fromStorage(it).name }
            snapshot.maxPathToAirRatio?.let {
                prefs[keyMaxPathToAir] = it.coerceIn(MIN_RATIO.toFloat(), MAX_RATIO.toFloat())
            }
            snapshot.airDistanceToleranceM?.let {
                prefs[keyAirToleranceM] =
                    it.coerceIn(MIN_AIR_TOLERANCE_M.toFloat(), MAX_AIR_TOLERANCE_M.toFloat())
            }
            snapshot.showWalkingPathPolyline?.let { prefs[keyShowWalkingPath] = it }
            snapshot.showYouAreHereDuringRun?.let { prefs[keyShowYouAreHereDuringRun] = it }
            snapshot.includePdfLegends?.let { prefs[keyIncludePdfLegends] = it }
            snapshot.useIofCourseOverprint?.let { prefs[keyUseIofCourseOverprint] = it }
            snapshot.basemapStyle?.let { name ->
                val style = BasemapStyle.fromStorage(name)
                prefs[keyBasemapStyle] = style.name
                prefs[keyDarkBasemap] = style == BasemapStyle.DARK
            }
            val showLegs = snapshot.showAirLegLines
            if (showLegs != null) {
                prefs[keyShowAirLegs] = showLegs
                prefs[keyShowAirLabels] = if (showLegs) {
                    snapshot.showAirLegDistanceLabels ?: DEFAULT_SHOW_AIR_LABELS
                } else {
                    false
                }
            } else {
                snapshot.showAirLegDistanceLabels?.let { prefs[keyShowAirLabels] = it }
            }
        }
    }

    companion object {
        /** Defaults match [RouteGenerationPreset.BALANCED] (~1 in 10 strict-gate miss in matrix). */
        const val DEFAULT_MAX_PATH_TO_AIR = 1.8f
        const val MIN_RATIO = 1.1
        const val MAX_RATIO = 2.5
        const val DEFAULT_AIR_TOLERANCE_M = 1500f
        const val MIN_AIR_TOLERANCE_M = 200.0
        const val MAX_AIR_TOLERANCE_M = 2500.0
        /** Off by default: orienteering-style course without routing “hints”. */
        const val DEFAULT_SHOW_AIR_LEGS = false
        const val DEFAULT_SHOW_AIR_LABELS = false
        const val DEFAULT_SHOW_WALKING_PATH = false
        const val DEFAULT_SHOW_YOU_ARE_HERE_DURING_RUN = false
        const val DEFAULT_INCLUDE_PDF_LEGENDS = false
        const val DEFAULT_USE_IOF_COURSE_OVERPRINT = false
        const val DEFAULT_DARK_BASEMAP = false
        val DEFAULT_BASEMAP_STYLE: BasemapStyle = BasemapStyle.STANDARD
        const val MIN_CHECKPOINTS = 2
        const val MAX_CHECKPOINTS = 15
        const val DEFAULT_MIN_CHECKPOINTS = 3
        const val DEFAULT_MAX_CHECKPOINTS = 6

        fun coerceCheckpointWindow(min: Int, max: Int): Pair<Int, Int> {
            val lo = min.coerceIn(MIN_CHECKPOINTS, MAX_CHECKPOINTS)
            val hi = max.coerceIn(MIN_CHECKPOINTS, MAX_CHECKPOINTS)
            return if (lo <= hi) lo to hi else hi to lo
        }
    }
}
