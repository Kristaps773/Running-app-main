package com.orienteer.app.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.R
import com.orienteer.app.data.backup.BackupException
import com.orienteer.app.data.backup.DataBackupRepository
import com.orienteer.app.data.local.AppThemeMode
import com.orienteer.app.data.local.BasemapStyle
import com.orienteer.app.data.local.MapDisplayPreferences
import com.orienteer.app.data.local.RouteGenerationPreset
import com.orienteer.app.data.local.RouteGenerationRatios
import com.orienteer.app.data.local.UserPreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val routeRatios: RouteGenerationRatios,
    val mapDisplay: MapDisplayPreferences,
    val backupBusy: Boolean = false
)

sealed interface BackupUserMessage {
    data class SuccessRes(val messageRes: Int) : BackupUserMessage
    data class ErrorRes(val messageRes: Int) : BackupUserMessage
    data class ErrorText(val text: String) : BackupUserMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesStore: UserPreferencesStore,
    private val dataBackupRepository: DataBackupRepository
) : ViewModel() {

    private val backupBusy = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesStore.routeRatios,
        userPreferencesStore.mapDisplayPreferences,
        backupBusy
    ) { ratios, map, busy ->
        SettingsUiState(routeRatios = ratios, mapDisplay = map, backupBusy = busy)
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
                showNorthLines = UserPreferencesStore.DEFAULT_SHOW_NORTH_LINES,
                basemapStyle = UserPreferencesStore.DEFAULT_BASEMAP_STYLE,
                includePdfLegends = UserPreferencesStore.DEFAULT_INCLUDE_PDF_LEGENDS,
                showYouAreHereDuringRun = UserPreferencesStore.DEFAULT_SHOW_YOU_ARE_HERE_DURING_RUN,
                useIofCourseOverprint = UserPreferencesStore.DEFAULT_USE_IOF_COURSE_OVERPRINT
            )
        )
    )

    private val _backupMessages = MutableSharedFlow<BackupUserMessage>(extraBufferCapacity = 1)
    val backupMessages: SharedFlow<BackupUserMessage> = _backupMessages.asSharedFlow()

    fun setMaxPathToAirRatio(value: Float) {
        viewModelScope.launch { userPreferencesStore.setMaxPathToAirRatio(value) }
    }

    fun setAirDistanceToleranceM(value: Float) {
        viewModelScope.launch { userPreferencesStore.setAirDistanceToleranceM(value) }
    }

    fun setRouteGenerationPreset(preset: RouteGenerationPreset) {
        viewModelScope.launch { userPreferencesStore.setRouteGenerationPreset(preset) }
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

    fun setShowNorthLines(value: Boolean) {
        viewModelScope.launch { userPreferencesStore.setShowNorthLines(value) }
    }

    fun setShowYouAreHereDuringRun(value: Boolean) {
        viewModelScope.launch { userPreferencesStore.setShowYouAreHereDuringRun(value) }
    }

    fun setIncludePdfLegends(value: Boolean) {
        viewModelScope.launch { userPreferencesStore.setIncludePdfLegends(value) }
    }

    fun setUseIofCourseOverprint(value: Boolean) {
        viewModelScope.launch { userPreferencesStore.setUseIofCourseOverprint(value) }
    }

    fun setBasemapStyle(style: BasemapStyle) {
        viewModelScope.launch { userPreferencesStore.setBasemapStyle(style) }
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

    fun exportBackup() {
        if (backupBusy.value) return
        viewModelScope.launch {
            backupBusy.value = true
            try {
                val file = dataBackupRepository.writeBackupFile()
                val shared = dataBackupRepository.shareBackupFile(file)
                _backupMessages.emit(
                    if (shared) {
                        BackupUserMessage.SuccessRes(R.string.backup_export_success)
                    } else {
                        BackupUserMessage.ErrorRes(R.string.backup_export_share_failed)
                    }
                )
            } catch (e: Exception) {
                _backupMessages.emit(
                    BackupUserMessage.ErrorText(e.message ?: "Export failed")
                )
            } finally {
                backupBusy.value = false
            }
        }
    }

    fun importBackup(uri: Uri) {
        if (backupBusy.value) return
        viewModelScope.launch {
            backupBusy.value = true
            try {
                dataBackupRepository.importFromUri(uri)
                _backupMessages.emit(
                    BackupUserMessage.SuccessRes(R.string.backup_import_success)
                )
            } catch (e: BackupException) {
                _backupMessages.emit(BackupUserMessage.ErrorText(e.message ?: "Import failed"))
            } catch (_: Exception) {
                _backupMessages.emit(BackupUserMessage.ErrorRes(R.string.backup_import_failed))
            } finally {
                backupBusy.value = false
            }
        }
    }
}
