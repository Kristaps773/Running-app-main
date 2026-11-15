package com.orienteer.app.data.local

import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.OverpassElement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class PointObjectCacheTest {

    private fun element(id: Long, lat: Double, lon: Double): OverpassElement =
        OverpassElement(
            type = "node",
            id = id,
            lat = lat,
            lon = lon,
            tags = mapOf("amenity" to "bench"),
            center = null,
        )

    @Test
    fun cellKey_roundsToGridCell() {
        val cache = PointObjectCache(tempCacheDir(), cellSizeDeg = 0.002)
        assertEquals("56.950000,24.100000", cache.cellKey(56.9512, 24.1007))
        assertEquals(cache.cellKey(56.9512, 24.1007), cache.cellKey(56.9508, 24.1003))
    }

    @Test
    fun objectsNear_cachedCellReturnedWithoutSecondFetch() = runBlocking {
        val cache = PointObjectCache(tempCacheDir(), ttlMs = 60_000)
        val point = GeoPoint(56.951, 24.1005)
        val key = cache.cellKey(point.latitude, point.longitude)
        val bench = element(42L, point.latitude, point.longitude)
        var fetchCount = 0

        cache.objectsNear(listOf(point)) { _ ->
            fetchCount++
            mapOf(key to listOf(bench))
        }
        val second = cache.objectsNear(listOf(point)) { _ ->
            fetchCount++
            mapOf(key to listOf(element(99L, point.latitude, point.longitude)))
        }

        assertEquals("cached cell should not trigger another fetch", 1, fetchCount)
        assertEquals(1, second[key]?.size)
        assertEquals(42L, second[key]?.first()?.id)
    }

    @Test
    fun objectsNear_expiredCellIsRefetched() = runBlocking {
        val cache = PointObjectCache(tempCacheDir(), ttlMs = 1L)
        val point = GeoPoint(56.951, 24.1005)
        val key = cache.cellKey(point.latitude, point.longitude)
        var fetchCount = 0
        val first = element(1L, point.latitude, point.longitude)
        val fresher = element(2L, point.latitude, point.longitude)

        cache.objectsNear(listOf(point)) { _ ->
            fetchCount++
            mapOf(key to listOf(first))
        }
        Thread.sleep(5)
        val afterExpiry = cache.objectsNear(listOf(point)) { _ ->
            fetchCount++
            mapOf(key to listOf(fresher))
        }

        assertEquals(2, fetchCount)
        assertEquals(2L, afterExpiry[key]?.first()?.id)
        assertNotEquals(first.id, afterExpiry[key]?.first()?.id)
    }

    @Test
    fun objectsNear_withinTtl_reusesCacheWithoutRefetch() = runBlocking {
        val cache = PointObjectCache(tempCacheDir(), ttlMs = 60_000)
        val point = GeoPoint(56.951, 24.1005)
        val key = cache.cellKey(point.latitude, point.longitude)
        var fetchCount = 0
        val cachedBench = element(7L, point.latitude, point.longitude)
        val uncachedBench = element(8L, point.latitude, point.longitude)

        cache.objectsNear(listOf(point)) { _ ->
            fetchCount++
            mapOf(key to listOf(cachedBench))
        }
        val stillCached = cache.objectsNear(listOf(point)) { _ ->
            fetchCount++
            mapOf(key to listOf(uncachedBench))
        }

        assertEquals(1, fetchCount)
        assertEquals(7L, stillCached[key]?.first()?.id)
    }

    @Test
    fun objectsNear_failedCellNotCached_otherCellsStillReturned() = runBlocking {
        val cache = PointObjectCache(tempCacheDir(), ttlMs = 60_000)
        val p1 = GeoPoint(56.950, 24.100)
        val p2 = GeoPoint(56.960, 24.200)
        val k1 = cache.cellKey(p1.latitude, p1.longitude)
        val k2 = cache.cellKey(p2.latitude, p2.longitude)
        var fetchCount = 0

        val first = cache.objectsNear(listOf(p1, p2)) { _ ->
            fetchCount++
            mapOf(k2 to listOf(element(22L, p2.latitude, p2.longitude)))
        }
        assertNull(first[k1])
        assertEquals(1, first[k2]?.size)

        val retry = cache.objectsNear(listOf(p1, p2)) { _ ->
            fetchCount++
            mapOf(
                k1 to listOf(element(11L, p1.latitude, p1.longitude)),
                k2 to listOf(element(22L, p2.latitude, p2.longitude)),
            )
        }

        assertEquals(2, fetchCount)
        assertEquals(11L, retry[k1]?.first()?.id)
        assertEquals(22L, retry[k2]?.first()?.id)
    }

    @Test
    fun objectsNear_warmCell_staysWarmAcrossLaterBatch() = runBlocking {
        val cache = PointObjectCache(tempCacheDir(), ttlMs = 60_000)
        val p1 = GeoPoint(56.950, 24.100)
        val p2 = GeoPoint(56.960, 24.200)
        val k2 = cache.cellKey(p2.latitude, p2.longitude)
        var fetchCount = 0

        cache.objectsNear(listOf(p2)) { _ ->
            fetchCount++
            mapOf(k2 to listOf(element(30L, p2.latitude, p2.longitude)))
        }
        cache.objectsNear(listOf(p1, p2)) { _ ->
            fetchCount++
            mapOf(
                cache.cellKey(p1.latitude, p1.longitude) to listOf(element(31L, p1.latitude, p1.longitude)),
            )
        }

        assertEquals(2, fetchCount)
    }

    @Test
    fun objectsNear_fetchThrowForCell_retriesOnNextCall() = runBlocking {
        val cache = PointObjectCache(tempCacheDir(), ttlMs = 60_000)
        val p1 = GeoPoint(56.950, 24.100)
        val k1 = cache.cellKey(p1.latitude, p1.longitude)
        var fetchCount = 0

        val failed = runCatching {
            cache.objectsNear(listOf(p1)) { _ ->
                fetchCount++
                throw IOException("simulated shard failure")
            }
        }
        assertTrue(failed.isFailure)

        val recovered = cache.objectsNear(listOf(p1)) { _ ->
            fetchCount++
            mapOf(k1 to listOf(element(11L, p1.latitude, p1.longitude)))
        }

        assertEquals(2, fetchCount)
        assertEquals(11L, recovered[k1]?.first()?.id)
    }

    @Test
    fun objectsNear_omittedKeyNotCachedOnRetry() = runBlocking {
        val cache = PointObjectCache(tempCacheDir(), ttlMs = 60_000)
        val p1 = GeoPoint(56.950, 24.100)
        val p2 = GeoPoint(56.960, 24.200)
        val k1 = cache.cellKey(p1.latitude, p1.longitude)
        val k2 = cache.cellKey(p2.latitude, p2.longitude)
        var fetchCount = 0

        cache.objectsNear(listOf(p1, p2)) { _ ->
            fetchCount++
            mapOf(k2 to listOf(element(5L, p2.latitude, p2.longitude)))
        }
        val second = cache.objectsNear(listOf(p1)) { _ ->
            fetchCount++
            mapOf(k1 to listOf(element(6L, p1.latitude, p1.longitude)))
        }

        assertEquals(2, fetchCount)
        assertEquals(6L, second[k1]?.first()?.id)
    }

    private fun tempCacheDir() = Files.createTempDirectory("point-object-cache").toFile()
}