package com.orienteer.app.service

import android.content.Context
import android.content.Intent

/** Starts the foreground GPS run (shared by Run screen and full-screen run map). */
object RunSessionStarter {
    fun start(context: Context, routeId: String, saveRun: Boolean = true) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_ROUTE_ID, routeId)
            putExtra(LocationTrackingService.EXTRA_SAVE_RUN, saveRun)
        }
        context.startForegroundService(intent)
    }
}
