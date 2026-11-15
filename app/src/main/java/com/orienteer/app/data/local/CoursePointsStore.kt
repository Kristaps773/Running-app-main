package com.orienteer.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.domain.CoursePointsCatalog
import com.orienteer.app.domain.CoursePointsSet
import com.orienteer.app.domain.FavoriteAddResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.coursePointsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "course_points_catalog"
)

/**
 * Persists Recent (max 5) and Favorites (max 10) course-point sets.
 * Active pins live in SetupViewModel only — Reset clears active, not these lists.
 *
 * Decode uses hand-rolled JSON → [GeoPoint] mapping. Gson TypeToken for nested
 * `List<GeoPoint>` inside [CoursePointsSet] yields LinkedTreeMap at runtime (R8 / erasure),
 * which then ClassCastExceptions in the picker and Generate.
 */
@Singleton
class CoursePointsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val dataStore = context.coursePointsDataStore
    private val keyRecent = stringPreferencesKey("recent_json")
    private val keyFavorites = stringPreferencesKey("favorites_json")

    val recent: Flow<List<CoursePointsSet>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decodeList(prefs[keyRecent]) }
        .distinctUntilChanged()

    val favorites: Flow<List<CoursePointsSet>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> decodeList(prefs[keyFavorites]) }
        .distinctUntilChanged()

    suspend fun readRecent(): List<CoursePointsSet> = recent.first()
    suspend fun readFavorites(): List<CoursePointsSet> = favorites.first()

    suspend fun pushRecent(set: CoursePointsSet) {
        dataStore.edit { prefs ->
            val next = CoursePointsCatalog.pushRecent(decodeList(prefs[keyRecent]), set)
            prefs[keyRecent] = encodeList(next)
        }
    }

    suspend fun removeRecent(id: String) {
        dataStore.edit { prefs ->
            prefs[keyRecent] = encodeList(
                CoursePointsCatalog.removeById(decodeList(prefs[keyRecent]), id)
            )
        }
    }

    /** @return null when added; current favorites when full (caller must pick replace). */
    suspend fun addFavorite(set: CoursePointsSet): List<CoursePointsSet>? {
        var full: List<CoursePointsSet>? = null
        dataStore.edit { prefs ->
            when (val result = CoursePointsCatalog.addFavorite(decodeList(prefs[keyFavorites]), set)) {
                is FavoriteAddResult.Ok -> prefs[keyFavorites] = encodeList(result.list)
                is FavoriteAddResult.Full -> full = result.list
            }
        }
        return full
    }

    suspend fun replaceFavorite(removeId: String, set: CoursePointsSet) {
        dataStore.edit { prefs ->
            prefs[keyFavorites] = encodeList(
                CoursePointsCatalog.replaceFavorite(decodeList(prefs[keyFavorites]), removeId, set)
            )
        }
    }

    suspend fun removeFavorite(id: String) {
        dataStore.edit { prefs ->
            prefs[keyFavorites] = encodeList(
                CoursePointsCatalog.removeById(decodeList(prefs[keyFavorites]), id)
            )
        }
    }

    suspend fun renameFavorite(id: String, name: String) {
        dataStore.edit { prefs ->
            prefs[keyFavorites] = encodeList(
                CoursePointsCatalog.renameFavorite(decodeList(prefs[keyFavorites]), id, name)
            )
        }
    }

    private fun encodeList(list: List<CoursePointsSet>): String = gson.toJson(list)

    private fun decodeList(json: String?): List<CoursePointsSet> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JsonParser.parseString(json)
            if (!root.isJsonArray) return emptyList()
            root.asJsonArray.mapNotNull { decodeSet(it) }
        }.getOrElse { emptyList() }
    }

    private fun decodeSet(el: JsonElement): CoursePointsSet? {
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        val id = obj.get("id")?.asString ?: return null
        return CoursePointsSet(
            id = id,
            savedAtMs = obj.get("savedAtMs")?.asLong ?: 0L,
            start = decodePoint(obj.get("start")),
            finish = decodePoint(obj.get("finish")),
            locked = decodePointList(obj.get("locked")),
            anyOrder = obj.get("anyOrder")?.asBoolean ?: true,
            usedGpsStart = obj.get("usedGpsStart")?.asBoolean ?: false,
            targetDistanceKm = obj.get("targetDistanceKm")?.takeIf { !it.isJsonNull }?.asDouble,
            minCheckpoints = obj.get("minCheckpoints")?.takeIf { !it.isJsonNull }?.asInt,
            maxCheckpoints = obj.get("maxCheckpoints")?.takeIf { !it.isJsonNull }?.asInt,
            maxPathToAirRatio = obj.get("maxPathToAirRatio")?.takeIf { !it.isJsonNull }?.asFloat,
            airDistanceToleranceM = obj.get("airDistanceToleranceM")?.takeIf { !it.isJsonNull }?.asFloat,
            displayName = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString
        )
    }

    private fun decodePointList(el: JsonElement?): List<GeoPoint> {
        if (el == null || !el.isJsonArray) return emptyList()
        return el.asJsonArray.mapNotNull { decodePoint(it) }
    }

    private fun decodePoint(el: JsonElement?): GeoPoint? {
        if (el == null || el.isJsonNull || !el.isJsonObject) return null
        val obj = el.asJsonObject
        val lat = obj.get("latitude")?.asDouble ?: return null
        val lon = obj.get("longitude")?.asDouble ?: return null
        return GeoPoint(lat, lon)
    }
}
