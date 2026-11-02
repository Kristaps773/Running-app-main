package com.orienteer.app.util

import com.orienteer.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class GeoUtilsOsrmFormatTest {

    @Test
    fun toOsrmCoordinate_usesDotDecimalEvenUnderLatvianLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("lv-LV"))
            val formatted = GeoUtils.toOsrmCoordinate(GeoPoint(56.949, 24.105))
            assertEquals("24.105000,56.949000", formatted)
            assertFalse(
                "must not use locale decimal comma",
                formatted.contains("24,105")
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun toOsrmCoordinates_joinsWithSemicolonAndDots() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("lv-LV"))
            val formatted = GeoUtils.toOsrmCoordinates(
                listOf(GeoPoint(56.949, 24.105), GeoPoint(56.95, 24.12))
            )
            assertEquals("24.105000,56.949000;24.120000,56.950000", formatted)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
