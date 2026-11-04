package com.orienteer.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunMapCameraModeTest {

    @Test
    fun startsFollowLocked_whenLeadEnabled() {
        val s = RunMapCameraMode.initial(leadEnabled = true)
        assertEquals(RunMapCameraMode.State.FollowLocked, s)
    }

    @Test
    fun startsFreeLook_whenLeadDisabled() {
        val s = RunMapCameraMode.initial(leadEnabled = false)
        assertEquals(RunMapCameraMode.State.FreeLook, s)
    }

    @Test
    fun userGesture_unlocksFollow() {
        val next = RunMapCameraMode.onUserGesture(RunMapCameraMode.State.FollowLocked)
        assertEquals(RunMapCameraMode.State.FreeLook, next)
    }

    @Test
    fun userGesture_inFreeLook_staysFree() {
        val next = RunMapCameraMode.onUserGesture(RunMapCameraMode.State.FreeLook)
        assertEquals(RunMapCameraMode.State.FreeLook, next)
    }

    @Test
    fun recenter_returnsToFollow() {
        val next = RunMapCameraMode.onRecenter(RunMapCameraMode.State.FreeLook)
        assertEquals(RunMapCameraMode.State.FollowLocked, next)
    }

    @Test
    fun noAutoRelock_withoutRecenter() {
        var s = RunMapCameraMode.State.FollowLocked
        s = RunMapCameraMode.onUserGesture(s)
        // Simulate time / GPS ticks — state must stay free until recenter.
        repeat(5) {
            assertEquals(RunMapCameraMode.State.FreeLook, s)
        }
        assertFalse(RunMapCameraMode.shouldAutoFollow(s))
        s = RunMapCameraMode.onRecenter(s)
        assertTrue(RunMapCameraMode.shouldAutoFollow(s))
    }

    @Test
    fun leadDisabled_forcesFreeLook() {
        assertEquals(
            RunMapCameraMode.State.FreeLook,
            RunMapCameraMode.onLeadEnabledChanged(
                current = RunMapCameraMode.State.FollowLocked,
                leadEnabled = false
            )
        )
    }

    @Test
    fun leadEnabledAgain_doesNotAutoLock() {
        assertEquals(
            RunMapCameraMode.State.FreeLook,
            RunMapCameraMode.onLeadEnabledChanged(
                current = RunMapCameraMode.State.FreeLook,
                leadEnabled = true
            )
        )
    }
}
