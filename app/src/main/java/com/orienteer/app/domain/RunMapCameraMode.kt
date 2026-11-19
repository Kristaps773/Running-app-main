package com.orienteer.app.domain

/**
 * Google/Waze-style camera: follow while locked; user gesture → free look until recenter.
 * Never auto re-locks after the user looks around.
 * Independent of path-lead / walking-path prefs — only Center Me locks follow.
 */
object RunMapCameraMode {

    enum class State {
        FollowLocked,
        FreeLook
    }

    /** Start free; lock only after Center Me. */
    fun initial(): State = State.FreeLook

    fun onUserGesture(current: State): State = State.FreeLook

    fun onRecenter(current: State): State = State.FollowLocked

    fun shouldAutoFollow(state: State): Boolean = state == State.FollowLocked
}
