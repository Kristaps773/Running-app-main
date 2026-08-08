package com.orienteer.app.presentation.map

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.orienteer.app.R
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView

/**
 * MapView inside a clipping FrameLayout so zoom/pan tiles cannot paint outside the card bounds.
 */
fun createReplayMapHost(
    context: Context,
    useDarkBasemap: Boolean,
    compactPreview: Boolean
): FrameLayout {
    Configuration.getInstance().userAgentValue = context.packageName
    return FrameLayout(context).apply {
        clipChildren = true
        clipToPadding = true
        clipToOutline = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        addView(
            MapView(context).apply {
                setTileSource(defaultBasemapTileSource(useDarkBasemap))
                controller.setZoom(15.0)
                applyMapViewClipping(this)
                attachScrollLimitListener(this)
                configureReplayMapInteraction(this, compactPreview)
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
}

/** Compact preview is static; full-screen replay allows pinch/pan with scroll limits. */
fun configureReplayMapInteraction(map: MapView, compactPreview: Boolean) {
    map.setTag(R.id.map_tag_compact_preview, compactPreview)
    if (compactPreview) {
        map.setMultiTouchControls(false)
        map.isClickable = false
        map.isFocusable = false
    } else {
        map.setMultiTouchControls(true)
        map.isClickable = true
        map.isFocusable = true
        val maxTileZoom = map.tileProvider.maximumZoomLevel.toDouble()
        val minTileZoom = map.tileProvider.minimumZoomLevel.toDouble()
        map.setMaxZoomLevel(maxTileZoom)
        map.setMinZoomLevel(minTileZoom)
    }
}

fun FrameLayout.requireMapViewFromHost(): MapView {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (child is MapView) return child
    }
    error("MapView child missing from replay host")
}
