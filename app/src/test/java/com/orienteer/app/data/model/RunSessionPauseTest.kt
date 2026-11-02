package com.orienteer.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSessionPauseTest {

    @Test
    fun activeElapsedExcludesCompletedAndOpenPauses() {
        val session = RunSession(
            routeId = "r1",
            startedAt = 1_000L,
            totalPausedMs = 2_000L,
            isPaused = true,
            pausedAt = 8_000L
        )

        // now=10_000 → wall 9_000 − completed 2_000 − open 2_000 = 5_000
        assertEquals(5_000L, session.activeElapsedMs(nowMs = 10_000L))
    }

    @Test
    fun withPauseClosedFoldsOpenPauseIntoTotal() {
        val session = RunSession(
            routeId = "r1",
            startedAt = 1_000L,
            totalPausedMs = 500L,
            isPaused = true,
            pausedAt = 4_000L,
            elapsedTimeMs = 2_500L
        )

        val closed = session.withPauseClosed(nowMs = 6_000L)

        assertFalse(closed.isPaused)
        assertNull(closed.pausedAt)
        assertEquals(2_500L, closed.totalPausedMs) // 500 + 2000
        assertEquals(2_500L, closed.elapsedTimeMs) // 6000 - 1000 - 2500
        assertTrue(closed.isActive)
    }
}
