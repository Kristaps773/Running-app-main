package com.orienteer.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.activeRunDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "active_run"
)

/**
 * Persists which route has an active tracked run so UI can show a banner and reconnect after navigation.
 * Cleared when [com.orienteer.app.service.LocationTrackingService] stops tracking.
 */
@Singleton
class ActiveRunStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.activeRunDataStore

    private val keyRouteId = stringPreferencesKey("active_route_id")
    private val keyStartedAt = longPreferencesKey("started_at")

    /** Non-empty route id when a foreground tracking session is active. */
    val activeRouteId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[keyRouteId]?.takeIf { it.isNotBlank() }
    }.distinctUntilChanged()

    suspend fun readActiveRouteId(): String? = activeRouteId.first()

    suspend fun setActive(routeId: String, startedAtMs: Long) {
        dataStore.edit { prefs ->
            prefs[keyRouteId] = routeId
            prefs[keyStartedAt] = startedAtMs
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(keyRouteId)
            prefs.remove(keyStartedAt)
        }
    }
}
