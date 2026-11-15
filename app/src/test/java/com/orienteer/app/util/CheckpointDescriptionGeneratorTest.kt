package com.orienteer.app.util

import com.orienteer.app.data.model.Checkpoint
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.OverpassElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class CheckpointDescriptionGeneratorTest {

    private fun element(tagKey: String, tagValue: String, name: String? = null): OverpassElement {
        val tags = buildMap {
            put(tagKey, tagValue)
            if (name != null) put("name", name)
        }
        return OverpassElement(
            type = "node",
            id = 1L,
            lat = 56.95,
            lon = 24.1,
            tags = tags,
            center = null
        )
    }

    @Test
    fun onObject_usesAtWording_notNthWaypoint() {
        val (desc, name, tag) = CheckpointDescriptionGenerator.generate(
            index = 0,
            elements = listOf(element("amenity", "bench")),
            onObject = true
        )
        assertEquals("amenity=bench", tag)
        assertTrue(desc.contains("At a bench", ignoreCase = false))
        assertFalse(desc.contains("waypoint", ignoreCase = true))
        assertFalse(desc.contains("Near ", ignoreCase = false))
    }

    @Test
    fun nearObject_usesNearWording() {
        val (desc, _, tag) = CheckpointDescriptionGenerator.generate(
            index = 1,
            elements = listOf(element("amenity", "fountain")),
            onObject = false
        )
        assertEquals("amenity=fountain", tag)
        assertTrue(desc.contains("Near a drinking fountain"))
        assertFalse(desc.contains("waypoint", ignoreCase = true))
    }

    @Test
    fun namedObject_includesName() {
        val (desc, name, _) = CheckpointDescriptionGenerator.generate(
            index = 0,
            elements = listOf(element("historic", "memorial", name = "Freedom")),
            onObject = true
        )
        assertEquals("Freedom", name)
        assertTrue(desc.contains("Freedom"))
        assertTrue(desc.contains("At "))
    }

    @Test
    fun noPoi_allowsNthWaypoint() {
        val (desc, name, tag) = CheckpointDescriptionGenerator.generate(
            index = 2,
            elements = emptyList(),
            onObject = false
        )
        assertNullSafe(name)
        assertNullSafe(tag)
        assertTrue(desc.contains("waypoint", ignoreCase = true))
        assertTrue(desc.contains("third", ignoreCase = true))
    }

    @Test
    fun forestObjects_useOrienteeringWording() {
        val boulder = CheckpointDescriptionGenerator.generate(
            0, listOf(element("natural", "rock")), onObject = true
        )
        assertTrue(boulder.first.contains("At a boulder"))
        assertEquals("natural=rock", boulder.third)

        val cliff = CheckpointDescriptionGenerator.generate(
            1, listOf(element("natural", "cliff")), onObject = true
        )
        assertTrue(cliff.first.contains("At a cliff"))

        val marsh = CheckpointDescriptionGenerator.generate(
            2, listOf(element("natural", "wetland")), onObject = false
        )
        assertTrue(marsh.first.contains("Near a marsh"))

        val cairn = CheckpointDescriptionGenerator.generate(
            3, listOf(element("man_made", "cairn")), onObject = true
        )
        assertTrue(cairn.first.contains("At a cairn"))

        val saddle = CheckpointDescriptionGenerator.generate(
            4, listOf(element("natural", "saddle")), onObject = true
        )
        assertTrue(saddle.first.contains("At a saddle"))
    }

    @Test
    fun cityObjects_useStreetOWording() {
        val fence = CheckpointDescriptionGenerator.generate(
            0, listOf(element("barrier", "fence")), onObject = true
        )
        assertTrue(fence.first.contains("At a fence"))

        val steps = CheckpointDescriptionGenerator.generate(
            1, listOf(element("highway", "steps")), onObject = false
        )
        assertTrue(steps.first.contains("Near a flight of steps"))

        val pylon = CheckpointDescriptionGenerator.generate(
            2, listOf(element("power", "tower")), onObject = true
        )
        assertTrue(pylon.first.contains("At a power pylon"))

        val bridge = CheckpointDescriptionGenerator.generate(
            3, listOf(element("man_made", "bridge")), onObject = true
        )
        assertTrue(bridge.first.contains("At a bridge"))
    }

    @Test
    fun pdfSheet_reflectsAtAndNearDescriptions() {
        val rows = ControlDescriptionSheetBuilder.rows(
            listOf(
                Checkpoint(
                    id = 0,
                    position = GeoPoint(56.95, 24.1),
                    description = "Start / Finish — return here.",
                    isStart = true
                ),
                Checkpoint(
                    id = 1,
                    position = GeoPoint(56.951, 24.11),
                    description = "Checkpoint 1: At a bench.",
                    landmarkType = "amenity=bench",
                    isStart = false,
                    distanceFromPrev = 100.0
                ),
                Checkpoint(
                    id = 2,
                    position = GeoPoint(56.952, 24.12),
                    description = "Checkpoint 2: Near a fountain.",
                    landmarkType = "amenity=fountain",
                    isStart = false,
                    distanceFromPrev = 120.0
                ),
                Checkpoint(
                    id = 3,
                    position = GeoPoint(56.953, 24.13),
                    description = "Checkpoint 3: At a boulder.",
                    landmarkType = "natural=rock",
                    isStart = false,
                    distanceFromPrev = 90.0
                ),
                Checkpoint(
                    id = 4,
                    position = GeoPoint(56.954, 24.14),
                    description = "Checkpoint 4: Near a cliff.",
                    landmarkType = "natural=cliff",
                    isStart = false,
                    distanceFromPrev = 110.0
                )
            )
        )
        val cp1 = rows[1] as ControlDescriptionRow.Control
        assertEquals("At a bench.", cp1.description)
        assertEquals("Bench", cp1.featureLabel)
        val cp2 = rows[2] as ControlDescriptionRow.Control
        assertEquals("Near a fountain.", cp2.description)
        assertEquals("Fountain", cp2.featureLabel)
        val cp3 = rows[3] as ControlDescriptionRow.Control
        assertEquals("At a boulder.", cp3.description)
        assertEquals("Boulder", cp3.featureLabel)
        val cp4 = rows[4] as ControlDescriptionRow.Control
        assertEquals("Near a cliff.", cp4.description)
        assertEquals("Cliff", cp4.featureLabel)
    }

    private fun assertNullSafe(value: Any?) {
        assertEquals(null, value)
    }
}

@RunWith(Parameterized::class)
class CheckpointDescriptionObjectCatalogTest(
    private val key: String,
    private val value: String,
    private val expectedPhrase: String
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}={1} → {2}")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("natural", "rock", "a boulder"),
            arrayOf("natural", "cliff", "a cliff"),
            arrayOf("natural", "saddle", "a saddle"),
            arrayOf("natural", "cave_entrance", "a cave entrance"),
            arrayOf("natural", "wetland", "a marsh"),
            arrayOf("natural", "water", "a pond"),
            arrayOf("natural", "tree", "a distinct tree"),
            arrayOf("man_made", "cairn", "a cairn"),
            arrayOf("man_made", "bridge", "a bridge"),
            arrayOf("man_made", "embankment", "an earth bank"),
            arrayOf("man_made", "hunting_stand", "a hunting stand"),
            arrayOf("man_made", "water_well", "a well"),
            arrayOf("barrier", "fence", "a fence"),
            arrayOf("barrier", "wall", "a wall"),
            arrayOf("barrier", "stile", "a stile"),
            arrayOf("barrier", "ditch", "a ditch"),
            arrayOf("power", "tower", "a power pylon"),
            arrayOf("highway", "steps", "a flight of steps"),
            arrayOf("highway", "ford", "a ford"),
            arrayOf("building", "hut", "a hut"),
            arrayOf("building", "church", "a church"),
            arrayOf("waterway", "waterfall", "a waterfall"),
            arrayOf("landuse", "quarry", "a quarry"),
            arrayOf("tourism", "wilderness_hut", "a wilderness hut")
        )
    }

    @Test
    fun phrase_matchesOrienteeringStyle() {
        val element = OverpassElement(
            type = "node",
            id = 1L,
            lat = 56.95,
            lon = 24.1,
            tags = mapOf(key to value),
            center = null
        )
        val (desc, _, tag) = CheckpointDescriptionGenerator.generate(
            index = 0,
            elements = listOf(element),
            onObject = true
        )
        assertEquals("$key=$value", tag)
        assertTrue(
            "Expected \"$expectedPhrase\" in \"$desc\"",
            desc.contains("At $expectedPhrase")
        )
    }
}
