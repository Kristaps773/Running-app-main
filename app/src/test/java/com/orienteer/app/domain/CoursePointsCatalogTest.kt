package com.orienteer.app.domain

import com.orienteer.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoursePointsCatalogTest {

    private val start = GeoPoint(56.95, 24.10)
    private val finish = GeoPoint(56.96, 24.12)
    private val a = GeoPoint(56.951, 24.101)
    private val b = GeoPoint(56.952, 24.102)

    private fun set(
        id: String,
        locked: List<GeoPoint> = listOf(a),
        anyOrder: Boolean = true,
        savedAtMs: Long = 1L
    ) = CoursePointsSet(
        id = id,
        savedAtMs = savedAtMs,
        start = start,
        finish = finish,
        locked = locked,
        anyOrder = anyOrder,
        usedGpsStart = false
    )

    @Test
    fun pushRecent_newestFirst_capsAtFive() {
        var list = emptyList<CoursePointsSet>()
        repeat(7) { i ->
            list = CoursePointsCatalog.pushRecent(
                list,
                set(id = "r$i", locked = listOf(GeoPoint(56.95 + i * 0.001, 24.10)), savedAtMs = i.toLong())
            )
        }
        assertEquals(5, list.size)
        assertEquals("r6", list.first().id)
        assertEquals("r2", list.last().id)
    }

    @Test
    fun pushRecent_sameGeometry_movesToFront() {
        val first = set(id = "a", locked = listOf(a, b), savedAtMs = 1)
        val again = set(id = "b", locked = listOf(a, b), savedAtMs = 2)
        val other = set(id = "c", locked = listOf(a), savedAtMs = 3)
        val list = CoursePointsCatalog.pushRecent(
            CoursePointsCatalog.pushRecent(listOf(first), other),
            again
        )
        assertEquals(2, list.size)
        assertEquals("b", list.first().id)
        assertEquals("c", list[1].id)
    }

    @Test
    fun removeById_dropsMatching() {
        val list = listOf(set("1"), set("2"), set("3"))
        assertEquals(listOf("1", "3"), CoursePointsCatalog.removeById(list, "2").map { it.id })
    }

    @Test
    fun addFavorite_underCap_prepends() {
        val result = CoursePointsCatalog.addFavorite(emptyList(), set("f1"))
        assertTrue(result is FavoriteAddResult.Ok)
        assertEquals(listOf("f1"), (result as FavoriteAddResult.Ok).list.map { it.id })
    }

    @Test
    fun addFavorite_whenFull_returnsFullUnchanged() {
        val full = (1..CoursePointsCatalog.MAX_FAVORITES).map { set("f$it", locked = listOf(GeoPoint(56.95, 24.10 + it * 0.001))) }
        val incoming = set("new", locked = listOf(GeoPoint(57.0, 24.0)))
        val result = CoursePointsCatalog.addFavorite(full, incoming)
        assertTrue(result is FavoriteAddResult.Full)
        assertEquals(full, (result as FavoriteAddResult.Full).list)
    }

    @Test
    fun replaceFavorite_removesChosen_andPrepends() {
        val full = (1..CoursePointsCatalog.MAX_FAVORITES).map { set("f$it", locked = listOf(GeoPoint(56.95, 24.10 + it * 0.001))) }
        val incoming = set("new", locked = listOf(GeoPoint(57.0, 24.0)))
        val next = CoursePointsCatalog.replaceFavorite(full, removeId = "f5", incoming = incoming)
        assertEquals(CoursePointsCatalog.MAX_FAVORITES, next.size)
        assertEquals("new", next.first().id)
        assertTrue(next.none { it.id == "f5" })
    }

    @Test
    fun autoLabel_includesLockedCountAndOrder() {
        val label = CoursePointsCatalog.autoLabel(set("x", locked = listOf(a, b), anyOrder = true))
        assertTrue(label.startsWith("2 locked"))
        assertTrue(label.contains("any"))
    }

    @Test
    fun autoLabel_prefersDisplayNameWhenPresent() {
        val labeled = CoursePointsCatalog.autoLabel(set("x").copy(displayName = "  Park loop  "))
        assertEquals("Park loop", labeled)
    }

    @Test
    fun autoLabel_fallsBackWhenDisplayNameBlank() {
        val auto = CoursePointsCatalog.autoLabel(set("x", locked = listOf(a)))
        val blank = CoursePointsCatalog.autoLabel(set("x", locked = listOf(a)).copy(displayName = "   "))
        assertEquals(auto, blank)
    }

    @Test
    fun renameFavorite_updatesDisplayName_onlyMatchingId() {
        val list = listOf(set("a"), set("b"))
        val next = CoursePointsCatalog.renameFavorite(list, "a", "  My route  ")
        assertEquals("My route", next[0].displayName)
        assertEquals(null, next[1].displayName)
    }

    @Test
    fun renameFavorite_blankClearsDisplayName() {
        val list = listOf(set("a").copy(displayName = "Old"))
        val next = CoursePointsCatalog.renameFavorite(list, "a", "  ")
        assertEquals(null, next[0].displayName)
    }

    @Test
    fun autoLabel_prefersTargetDistanceWhenPresent() {
        val labeled = CoursePointsCatalog.autoLabel(
            set("x", locked = listOf(a, b)).copy(targetDistanceKm = 5.0)
        )
        assertTrue(labeled.contains("5.0 km"))
        assertTrue(!labeled.contains("~"))
    }

    @Test
    fun fingerprint_includesGenerateKnobs() {
        val base = set("x", locked = listOf(a, b))
        val withKm = base.copy(targetDistanceKm = 5.0)
        assertTrue(CoursePointsCatalog.fingerprint(base) != CoursePointsCatalog.fingerprint(withKm))
    }
}
