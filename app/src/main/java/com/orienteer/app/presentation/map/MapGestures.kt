package com.orienteer.app.presentation.map

import com.orienteer.app.R
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

/** Two-finger rotate for interactive maps. Idempotent; keeps overlay above route chrome. */
fun MapView.enableRotationGestures() {
    overlays.removeAll { it is RotationGestureOverlay }
    val overlay = RotationGestureOverlay(this).apply { isEnabled = true }
    overlays.add(overlay)
    setTag(R.id.map_tag_rotation_gesture, true)
}
