package com.orienteer.app.domain

/**
 * Google/Waze-style camera: follow while locked; user gesture → free look until recenter.
 * Never auto re-locks after the user looks around.
 */
object RunMapCameraMode {

    enum class State {
        FollowLocked,
        FreeLook
    }

    fun initial(leadEnabled: Boolean): State =
        if (leadEnabled) State.FollowLocked else State.FreeLook

    fun onUserGesture(current: State): State = State.FreeLook

    fun onRecenter(current: State): State = State.FollowLocked

    fun shouldAutoFollow(state: State): Boolean = state == State.FollowLocked

    /**
     * Turning lead off drops follow. Turning lead on again does **not** auto-lock
     * (user must recenter), matching “don’t lock back automatically.”
     */
    fun onLeadEnabledChanged(current: State, leadEnabled: Boolean): State {
        if (!leadEnabled) return State.FreeLook
        return current
    }
}
