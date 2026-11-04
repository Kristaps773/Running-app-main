package com.orienteer.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orienteer.app.domain.CoursePointsCatalog
import com.orienteer.app.domain.CoursePointsSet
import com.orienteer.app.domain.FavoriteAddResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.coursePointsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "course_points_catalog"
)

/**
 * Persists Recent (max 5) and Favorites (max 10) course-point sets.
 * Active pins live in SetupViewModel only — Reset clears active, not these lists.
 */
@Singleton
class CoursePointsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val dataStore = context.coursePointsDataStore
    private val keyRecent = stringPreferencesKey("recent_json")
    private val keyFavorites = stringPreferencesKey("favorites_json")
    private val listType = object : TypeToken<List<CoursePointsSet>>() {}.type

    val recent: Flow<List<CoursePointsSet>> = dataStore.data.map { prefs ->
        decodeList(prefs[keyRecent])
    }.distinctUntilChanged()

    val favorites: Flow<List<CoursePointsSet>> = dataStore.data.map { prefs ->
        decodeList(prefs[keyFavorites])
    }.distinctUntilChanged()

    suspend fun readRecent(): List<CoursePointsSet> = recent.first()
    suspend fun readFavorites(): List<CoursePointsSet> = favorites.first()

    suspend fun pushRecent(set: CoursePointsSet) {
        dataStore.edit { prefs ->
            val next = CoursePointsCatalog.pushRecent(decodeList(prefs[keyRecent]), set)
            prefs[keyRecent] = gson.toJson(next)
        }
    }

    suspend fun removeRecent(id: String) {
        dataStore.edit { prefs ->
            prefs[keyRecent] = gson.toJson(
                CoursePointsCatalog.removeById(decodeList(prefs[keyRecent]), id)
            )
        }
    }

    /** @return null when added; current favorites when full (caller must replace or cancel). */
    suspend fun addFavorite(set: CoursePointsSet): List<CoursePointsSet>? {
        var full: List<CoursePointsSet>? = null
        dataStore.edit { prefs ->
            when (val result = CoursePointsCatalog.addFavorite(decodeList(prefs[keyFavorites]), set)) {
                is FavoriteAddResult.Ok -> prefs[keyFavorites] = gson.toJson(result.list)
                is FavoriteAddResult.Full -> full = result.list
            }
        }
        return full
    }

    suspend fun replaceFavorite(removeId: String, set: CoursePointsSet) {
        dataStore.edit { prefs ->
            prefs[keyFavorites] = gson.toJson(
                CoursePointsCatalog.replaceFavorite(decodeList(prefs[keyFavorites]), removeId, set)
            )
        }
    }

    suspend fun removeFavorite(id: String) {
        dataStore.edit { prefs ->
            prefs[keyFavorites] = gson.toJson(
                CoursePointsCatalog.removeById(decodeList(prefs[keyFavorites]), id)
            )
        }
    }

    private fun decodeList(json: String?): List<CoursePointsSet> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<CoursePointsSet>>(json, listType) ?: emptyList()
        }.getOrElse { emptyList() }
    }
}
