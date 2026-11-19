package com.orienteer.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunMapCameraModeTest {

    @Test
    fun startsFreeLook() {
        assertEquals(RunMapCameraMode.State.FreeLook, RunMapCameraMode.initial())
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
}
