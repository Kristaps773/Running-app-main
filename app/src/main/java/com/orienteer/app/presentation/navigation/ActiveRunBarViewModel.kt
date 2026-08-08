package com.orienteer.app.presentation.navigation

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orienteer.app.data.local.ActiveRunStore
import com.orienteer.app.service.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ActiveRunBarViewModel @Inject constructor(
    private val activeRunStore: ActiveRunStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val activeRouteId: StateFlow<String?> = activeRunStore.activeRouteId
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val routeId = activeRunStore.readActiveRouteId()
            val serviceRunning = isTrackingServiceRunning()
            if (routeId != null && !serviceRunning) {
                activeRunStore.clear()
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun isTrackingServiceRunning(): Boolean = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == LocationTrackingService::class.java.name }
    }
}
