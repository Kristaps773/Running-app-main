package com.orienteer.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.orienteer.app.data.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Get a single best-available location.
     * Uses last known location first; if unavailable, requests a fresh fix.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GeoPoint =
        suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(GeoPoint(location.latitude, location.longitude))
                    } else {
                        // Request a fresh single fix
                        val request = CurrentLocationRequest.Builder()
                            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                            .setMaxUpdateAgeMillis(5_000L)
                            .build()
                        fusedClient.getCurrentLocation(request, null)
                            .addOnSuccessListener { loc ->
                                if (loc != null) {
                                    cont.resume(GeoPoint(loc.latitude, loc.longitude))
                                } else {
                                    cont.resumeWithException(
                                        IllegalStateException("Could not obtain GPS fix")
                                    )
                                }
                            }
                            .addOnFailureListener { cont.resumeWithException(it) }
                    }
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    /**
     * Continuous location updates stream for run tracking.
     * Emits a new [GeoPoint] every [intervalMs] milliseconds.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 2_000L): Flow<GeoPoint> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(GeoPoint(loc.latitude, loc.longitude))
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }.conflate()
}
