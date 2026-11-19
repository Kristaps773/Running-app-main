package com.orienteer.app.presentation.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.orienteer.app.R
import com.orienteer.app.data.model.GeoPoint
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Glides the “you are here” puck between GPS fixes (Google/Strava-style) instead of jumping.
 */
internal object SmoothLocationMarker {

    const val ANIM_MS = 900L

    /** Linear interpolate in lat/lon (fine for ~metre steps between fixes). */
    fun lerp(from: GeoPoint, to: GeoPoint, t: Float): GeoPoint {
        val u = t.coerceIn(0f, 1f)
        return GeoPoint(
            from.latitude + (to.latitude - from.latitude) * u,
            from.longitude + (to.longitude - from.longitude) * u
        )
    }

    fun cancel(mapView: MapView) {
        cancelAnimator(mapView)
    }

    fun apply(mapView: MapView, location: GeoPoint?) {
        if (location == null) {
            cancelAnimator(mapView)
            mapView.setTag(R.id.map_tag_location_target, null)
            mapView.setTag(R.id.map_tag_location_displayed, null)
            removeMarker(mapView)
            return
        }

        val targetKey = targetKey(location)
        val lastTarget = mapView.getTag(R.id.map_tag_location_target) as? String
        if (lastTarget == targetKey) {
            ensureMarker(mapView, displayedOr(mapView, location))
            return
        }

        val from = displayedOr(mapView, location)
        mapView.setTag(R.id.map_tag_location_target, targetKey)
        cancelAnimator(mapView)

        if (from.latitude == location.latitude && from.longitude == location.longitude) {
            mapView.setTag(R.id.map_tag_location_displayed, location)
            ensureMarker(mapView, location)
            return
        }

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_MS
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val mid = lerp(from, location, anim.animatedValue as Float)
                mapView.setTag(R.id.map_tag_location_displayed, mid)
                ensureMarker(mapView, mid)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mapView.setTag(R.id.map_tag_location_displayed, location)
                    ensureMarker(mapView, location)
                    if (mapView.getTag(R.id.map_tag_location_animator) === animation) {
                        mapView.setTag(R.id.map_tag_location_animator, null)
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (mapView.getTag(R.id.map_tag_location_animator) === animation) {
                        mapView.setTag(R.id.map_tag_location_animator, null)
                    }
                }
            })
        }
        mapView.setTag(R.id.map_tag_location_animator, animator)
        animator.start()
    }

    fun displayedOr(mapView: MapView, fallback: GeoPoint): GeoPoint {
        (mapView.getTag(R.id.map_tag_location_displayed) as? GeoPoint)?.let { return it }
        val marker = locationMarker(mapView) ?: return fallback
        return GeoPoint(marker.position.latitude, marker.position.longitude)
    }

    private fun targetKey(location: GeoPoint): String =
        "${location.latitude},${location.longitude}"

    private fun cancelAnimator(mapView: MapView) {
        (mapView.getTag(R.id.map_tag_location_animator) as? ValueAnimator)?.cancel()
        mapView.setTag(R.id.map_tag_location_animator, null)
    }

    private fun locationMarker(mapView: MapView): Marker? =
        mapView.overlays
            .filterIsInstance<Marker>()
            .firstOrNull { it.title == LOCATION_MARKER_TITLE }

    private fun removeMarker(mapView: MapView) {
        val existing = locationMarker(mapView) ?: return
        mapView.overlays.remove(existing)
        mapView.invalidate()
    }

    private fun ensureMarker(mapView: MapView, location: GeoPoint) {
        val osmPoint = OsmGeoPoint(location.latitude, location.longitude)
        val icon = YouAreHereMarkerIcon.create(mapView.context)
        val existing = locationMarker(mapView)
        if (existing != null) {
            var dirty = false
            if (existing.icon !== icon) {
                existing.icon = icon
                dirty = true
            }
            val prev = existing.position
            if (prev.latitude != osmPoint.latitude || prev.longitude != osmPoint.longitude) {
                existing.position = osmPoint
                dirty = true
            }
            if (dirty) mapView.invalidate()
            return
        }
        mapView.overlays.add(
            Marker(mapView).apply {
                position = osmPoint
                title = LOCATION_MARKER_TITLE
                setIcon(icon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
                infoWindow = null
            }
        )
        mapView.invalidate()
    }
}
