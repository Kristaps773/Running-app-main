package com.orienteer.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val airDistanceToleranceM: Float = UserPreferencesStore.DEFAULT_AIR_TOLERANCE_M
)

data class MapDisplayPreferences(
    val showAirLegLines: Boolean = true,
    val showAirLegDistanceLabels: Boolean = true,
    val showWalkingPathPolyline: Boolean = true,
    val useDarkBasemap: Boolean = false
) {
    /** Alias used by map screens. */
    val showAirLegs: Boolean get() = showAirLegLines
}

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.userPreferencesDataStore

    private val keyMaxPathToAir = floatPreferencesKey("max_path_to_air_ratio")
    private val keyAirToleranceM = floatPreferencesKey("air_distance_tolerance_m")
    private val keyMaxTotalLegacy = floatPreferencesKey("max_total_path_to_air_ratio")
    private val keyShowAirLegs = booleanPreferencesKey("show_air_leg_lines")
    private val keyShowAirLabels = booleanPreferencesKey("show_air_leg_distance_labels")
    private val keyShowWalkingPath = booleanPreferencesKey("show_walking_path_polyline")
    private val keyDarkBasemap = booleanPreferencesKey("use_dark_basemap")
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
        routeRatios.map { ratios ->
            RouteGenerationPreferences(
                maxPathToAirRatio = ratios.maxPathToAirRatio.toFloat()
                    .coerceIn(MIN_RATIO.toFloat(), MAX_RATIO.toFloat()),
                airDistanceToleranceM = ratios.airDistanceToleranceM.toFloat()
                    .coerceIn(MIN_AIR_TOLERANCE_M.toFloat(), MAX_AIR_TOLERANCE_M.toFloat())
            )
        }.distinctUntilChanged()

    val mapDisplayPreferences: Flow<MapDisplayPreferences> = dataStore.data.map { p ->
        val air = p[keyShowAirLegs] ?: DEFAULT_SHOW_AIR_LEGS
        MapDisplayPreferences(
            showAirLegLines = air,
            showAirLegDistanceLabels = p[keyShowAirLabels] ?: DEFAULT_SHOW_AIR_LABELS,
            showWalkingPathPolyline = p[keyShowWalkingPath] ?: DEFAULT_SHOW_WALKING_PATH,
            useDarkBasemap = p[keyDarkBasemap] ?: DEFAULT_DARK_BASEMAP
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

    suspend fun setUseDarkBasemap(value: Boolean) {
        dataStore.edit { it[keyDarkBasemap] = value }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[keyThemeMode] = mode.name }
    }

    companion object {
        const val DEFAULT_MAX_PATH_TO_AIR = 1.5f
        const val MIN_RATIO = 1.1
        const val MAX_RATIO = 2.5
        const val DEFAULT_AIR_TOLERANCE_M = 700f
        const val MIN_AIR_TOLERANCE_M = 200.0
        const val MAX_AIR_TOLERANCE_M = 2500.0
        const val DEFAULT_SHOW_AIR_LEGS = true
        const val DEFAULT_SHOW_AIR_LABELS = true
        const val DEFAULT_SHOW_WALKING_PATH = true
        const val DEFAULT_DARK_BASEMAP = false
    }
}
