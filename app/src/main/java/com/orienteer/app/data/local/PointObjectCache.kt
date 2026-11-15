package com.orienteer.app.data.local

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.OverpassElement
import java.io.File
import java.util.Locale
import kotlin.math.floor

/**
 * Disk-backed grid cache for Overpass objects near course points.
 */
class PointObjectCache(
    private val cacheDir: File,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxCells: Int = DEFAULT_MAX_CELLS,
    private val cellSizeDeg: Double = DEFAULT_CELL_SIZE_DEG,
    private val gson: Gson = Gson(),
) {
    init {
        cacheDir.mkdirs()
    }

    fun cellKey(lat: Double, lon: Double): String {
        val cellLat = floor(lat / cellSizeDeg) * cellSizeDeg
        val cellLon = floor(lon / cellSizeDeg) * cellSizeDeg
        return String.format(Locale.US, "%.6f,%.6f", cellLat, cellLon)
    }

    suspend fun objectsNear(
        points: List<GeoPoint>,
        fetch: suspend (List<GeoPoint>) -> Map<String, List<OverpassElement>>,
    ): Map<String, List<OverpassElement>> {
        if (points.isEmpty()) return emptyMap()

        val now = System.currentTimeMillis()
        val byCell = points.groupBy { cellKey(it.latitude, it.longitude) }
        val result = mutableMapOf<String, List<OverpassElement>>()
        val fetchPoints = mutableListOf<GeoPoint>()

        for ((key, cellPoints) in byCell) {
            val cached = loadValidCell(key, now)
            if (cached != null) {
                result[key] = cached
            } else {
                fetchPoints.add(cellPoints.first())
            }
        }

        if (fetchPoints.isEmpty()) {
            return result
        }

        val fetched = fetch(fetchPoints)
        for ((key, elements) in fetched) {
            saveCell(key, elements, now)
            result[key] = elements
        }
        evictIfNeeded()

        return result
    }

    private fun loadValidCell(key: String, now: Long): List<OverpassElement>? {
        val file = cellFile(key)
        if (!file.isFile) return null
        val payload = try {
            gson.fromJson(file.readText(), CellCachePayload::class.java)
        } catch (_: Exception) {
            return null
        } ?: return null
        if (now - payload.savedAtMs > ttlMs) return null
        return payload.elements
    }

    private fun saveCell(key: String, elements: List<OverpassElement>, savedAtMs: Long) {
        val payload = CellCachePayload(savedAtMs = savedAtMs, elements = elements)
        cellFile(key).writeText(gson.toJson(payload))
    }

    private fun evictIfNeeded() {
        val files = cacheDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
        if (files.size <= maxCells) return
        val ranked = files
            .map { file ->
                val age = try {
                    gson.fromJson(file.readText(), CellCachePayload::class.java)?.savedAtMs
                } catch (_: Exception) {
                    null
                } ?: file.lastModified()
                file to age
            }
            .sortedBy { it.second }
        var excess = files.size - maxCells
        for ((file, _) in ranked) {
            if (excess <= 0) break
            file.delete()
            excess--
        }
    }

    private fun cellFile(key: String): File =
        File(cacheDir, key.replace(",", "_") + ".json")

    @Keep
    private data class CellCachePayload(
        @SerializedName("savedAtMs") val savedAtMs: Long,
        @SerializedName("elements") val elements: List<OverpassElement>,
    )

    companion object {
        const val DEFAULT_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val DEFAULT_MAX_CELLS = 2000
        const val DEFAULT_CELL_SIZE_DEG = 0.002
    }
}