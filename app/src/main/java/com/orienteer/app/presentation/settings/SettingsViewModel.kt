package com.orienteer.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.data.local.AppThemeMode
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.local.RouteGenerationRatios
import com.orienteer.app.data.local.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val routeRatios: RouteGenerationRatios,
    val mapDisplay: MapDisplayPreferences
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesStore: UserPreferencesStore
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesStore.routeRatios,
        userPreferencesStore.mapDisplayPreferences
    ) { ratios, map ->
        SettingsUiState(routeRatios = ratios, mapDisplay = map)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState(
            routeRatios = RouteGenerationRatios(
                maxPathToAirRatio = UserPreferencesStore.DEFAULT_MAX_PATH_TO_AIR.toDouble(),
                airDistanceToleranceM = UserPreferencesStore.DEFAULT_AIR_TOLERANCE_M.toDouble()
            ),
            mapDisplay = MapDisplayPreferences(
                showAirLegLines = UserPreferencesStore.DEFAULT_SHOW_AIR_LEGS,
                showAirLegDistanceLabels = UserPreferencesStore.DEFAULT_SHOW_AIR_LABELS,
                showWalkingPathPolyline = UserPreferencesStore.DEFAULT_SHOW_WALKING_PATH,
                useDarkBasemap = UserPreferencesStore.DEFAULT_DARK_BASEMAP
            )
        )
    )

    fun setMaxPathToAirRatio(value: Float) {
        viewModelScope.launch { userPreferencesStore.setMaxPathToAirRatio(value) }
    }

    fun setAirDistanceToleranceM(value: Float) {
        viewModelScope.launch { userPreferencesStore.setAirDistanceToleranceM(value) }
    }

    fun setShowAirLegLines(value: Boolean) {
        viewModelScope.launch { userPreferencesStore.setShowAirLegLines(value) }
    }

    fun setShowAirLegDistanceLabels(value: Boolean) {
        viewModelScope.launch { userPreferencesStore.setShowAirLegDistanceLabels(value) }
    }

    fun setShowWalkingPathPolyline(value: Boolean) {
        viewModelScope.launch { userPreferencesStore.setShowWalkingPathPolyline(value) }
    }

    fun setUseDarkBasemap(value: Boolean) {
        viewModelScope.launch { userPreferencesStore.setUseDarkBasemap(value) }
    }

    val themeMode: StateFlow<AppThemeMode> =
        userPreferencesStore.themeMode.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppThemeMode.SYSTEM
        )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { userPreferencesStore.setThemeMode(mode) }
    }
}
