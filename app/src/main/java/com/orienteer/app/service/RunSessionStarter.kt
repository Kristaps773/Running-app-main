package com.orienteer.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/** Starts the foreground GPS run (shared by Run screen and full-screen run map). */
object RunSessionStarter {
    private const val TAG = "RunSessionStarter"

    fun start(context: Context, routeId: String, saveRun: Boolean = true) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted; foreground notification may be hidden")
            }
        }
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_ROUTE_ID, routeId)
            putExtra(LocationTrackingService.EXTRA_SAVE_RUN, saveRun)
        }
        context.startForegroundService(intent)
    }
}
