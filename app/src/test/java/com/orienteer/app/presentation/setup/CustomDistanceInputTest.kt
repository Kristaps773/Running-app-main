package com.orienteer.app.presentation.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomDistanceInputTest {

    @Test
    fun sanitize_keepsDigitsAndSingleDot() {
        assertEquals("7.5", CustomDistanceInput.sanitize("7.5km"))
        assertEquals("12.3", CustomDistanceInput.sanitize("12..3"))
        assertEquals("", CustomDistanceInput.sanitize("abc"))
        assertEquals("3.14", CustomDistanceInput.sanitize("3.14"))
        assertEquals("10", CustomDistanceInput.sanitize("10"))
    }

    @Test
    fun parseKm_validAndBlank() {
        assertEquals(3.14, CustomDistanceInput.parseKm("3.14")!!, 0.0001)
        assertNull(CustomDistanceInput.parseKm(""))
        assertNull(CustomDistanceInput.parseKm("."))
        assertNull(CustomDistanceInput.parseKm("  "))
    }
}
