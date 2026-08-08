package com.orienteer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.orienteer.app.MainActivity
import com.orienteer.app.R
import com.orienteer.app.data.local.ActiveRunStore
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.model.RunSession
import com.orienteer.app.data.repository.LocationRepository
import com.orienteer.app.data.repository.RouteRepository
import com.orienteer.app.data.repository.RunRepository
import com.orienteer.app.domain.usecase.TrackRunUseCase
import com.orienteer.app.util.GeoUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : LifecycleService() {

    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var routeRepository: RouteRepository
    @Inject lateinit var runRepository: RunRepository
    @Inject lateinit var trackRunUseCase: TrackRunUseCase
    @Inject lateinit var activeRunStore: ActiveRunStore

    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    private val binder = LocalBinder()

    private val _runSession = MutableStateFlow<RunSession?>(null)
    val runSession: StateFlow<RunSession?> = _runSession.asStateFlow()

    private var route: Route? = null
    private var trackingJob: Job? = null
    private var runStartTime = 0L
    private var shouldSaveRun = true
    /** Survives [lifecycleScope] cancel so Room insert completes before [stopSelf]. */
    private val persistJob = SupervisorJob()
    private val persistScope = CoroutineScope(Dispatchers.IO + persistJob)

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        persistJob.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val routeId = intent.getStringExtra(EXTRA_ROUTE_ID) ?: return START_STICKY
                shouldSaveRun = intent.getBooleanExtra(EXTRA_SAVE_RUN, true)
                if (isAlreadyTrackingRoute(routeId)) {
                    return START_STICKY
                }
                lifecycleScope.launch {
                    startTrackingForRouteId(routeId)
                }
            }
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun isAlreadyTrackingRoute(routeId: String): Boolean =
        trackingJob?.isActive == true &&
            route?.id == routeId &&
            _runSession.value?.isActive == true

    /**
     * Loads route and starts a new session here (not from ViewModel bind) so reconnecting does not reset GPS state.
     */
    private suspend fun startTrackingForRouteId(routeId: String) {
        cancelTrackingJobOnly()
        val loaded = routeRepository.getRoute(routeId)
        if (loaded == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            activeRunStore.clear()
            stopSelf()
            return
        }

        route = loaded
        val started = System.currentTimeMillis()
        runStartTime = started
        _runSession.value = RunSession(
            routeId = loaded.id,
            startedAt = started,
            checkpointReachedAtMs = mapOf(0 to started)
        )
        activeRunStore.setActive(loaded.id, started)

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                contentText = "Preparing GPS…",
                startedAt = started
            )
        )
        startTracking()
    }

    private fun cancelTrackingJobOnly() {
        trackingJob?.cancel()
        trackingJob = null
    }

    fun skipCurrentCheckpoint() {
        val currentRoute = route ?: return
        val currentSession = _runSession.value ?: return
        if (!currentSession.isActive) return

        val targetIdx = currentSession.currentCheckpointIdx
        val checkpoint = currentRoute.checkpoints.getOrNull(targetIdx) ?: return
        val newSkipped = currentSession.skippedCheckpointIds + checkpoint.id
        val newCheckpointIdx = targetIdx + 1
        val allDone = newCheckpointIdx >= currentRoute.checkpoints.size
        val finishedAt = if (allDone) System.currentTimeMillis() else null

        _runSession.value = currentSession.copy(
            currentCheckpointIdx = newCheckpointIdx,
            skippedCheckpointIds = newSkipped,
            isActive = !allDone,
            finishedAt = finishedAt
        )
        updateNotification(_runSession.value!!)
        if (allDone) {
            finishAndPersist(_runSession.value!!)
        }
    }

    private fun startTracking() {
        trackingJob = lifecycleScope.launch {
            locationRepository.locationUpdates(intervalMs = 2_000L).collect { point ->
                processLocation(point)
            }
        }
    }

    private fun processLocation(point: GeoPoint) {
        val currentRoute = route ?: return
        val currentSession = _runSession.value ?: return
        val now = System.currentTimeMillis()
        val elapsed = now - runStartTime

        val updated = trackRunUseCase(currentSession, currentRoute, point, elapsed, now)
        _runSession.value = updated

        updateNotification(updated)

        if (!updated.isActive) {
            finishAndPersist(updated)
        }
    }

    private fun stopTracking() {
        cancelTrackingJobOnly()
        val session = _runSession.value
        persistThenStop(session)
    }

    /**
     * Persist (if enabled) then tear down. Uses [persistScope] so the insert is not cancelled
     * when [lifecycleScope] is torn down by [stopSelf].
     */
    private fun finishAndPersist(session: RunSession) {
        cancelTrackingJobOnly()
        persistThenStop(session)
    }

    private fun persistThenStop(session: RunSession?) {
        persistScope.launch {
            try {
                if (session != null && shouldSaveRun) {
                    persistSession(session)
                }
                activeRunStore.clear()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun persistSession(session: RunSession) {
        runRepository.saveSession(
            routeId = session.routeId,
            totalDistanceM = session.totalDistanceM,
            elapsedTimeMs = session.elapsedTimeMs,
            startedAt = session.startedAt,
            finishedAt = session.finishedAt,
            checkpointsReached = session.currentCheckpointIdx - 1,
            trackedPoints = session.trackedPoints,
            trackedPointTimestampsMs = session.trackedPointTimestampsMs,
            checkpointReachedAtMs = session.checkpointReachedAtMs
        )
    }

    private fun updateNotification(session: RunSession) {
        val distance = GeoUtils.formatDistance(session.totalDistanceM)
        val time = GeoUtils.formatDuration(session.elapsedTimeMs)
        val checkpointLine = checkpointProgressLabel(session)
        val contentText = "$distance  ·  $time  ·  $checkpointLine"
        val bigText = buildString {
            appendLine("$distance  ·  $time")
            appendLine(checkpointLine)
            nextCheckpointDetail(session)?.let { append(it) }
        }.trimEnd()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(
                contentText = contentText,
                bigText = bigText,
                startedAt = session.startedAt
            )
        )
    }

    /** e.g. "CP 2/5" or "Finish". */
    private fun checkpointProgressLabel(session: RunSession): String {
        val currentRoute = route ?: return "CP —"
        val total = currentRoute.waypointCount
        if (total <= 0) return "CP —"
        val next = currentRoute.checkpoints.getOrNull(session.currentCheckpointIdx)
        val reached = currentRoute.checkpoints
            .take(session.currentCheckpointIdx)
            .count { !it.isStart }
        return when {
            next == null -> "Done ($total/$total)"
            next.isStart && session.currentCheckpointIdx >= currentRoute.checkpoints.lastIndex ->
                "Finish ($reached/$total)"
            next.isStart -> "Start"
            else -> "CP ${next.id}/$total"
        }
    }

    private fun nextCheckpointDetail(session: RunSession): String? {
        val currentRoute = route ?: return null
        val next = currentRoute.checkpoints.getOrNull(session.currentCheckpointIdx) ?: return null
        val label = when {
            next.isStart && session.currentCheckpointIdx >= currentRoute.checkpoints.lastIndex ->
                "Finish"
            next.isStart -> "Start"
            else -> "Checkpoint ${next.id}"
        }
        val description = next.description.takeIf { it.isNotBlank() }
        return if (description != null) "Next: $label — $description" else "Next: $label"
    }

    private fun buildNotification(
        contentText: String,
        bigText: String = contentText,
        startedAt: Long = System.currentTimeMillis()
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OrienteerRun — Active Run")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_run_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Run Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows live distance, time, and checkpoint during an active run"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.orienteer.app.START_TRACKING"
        const val ACTION_STOP = "com.orienteer.app.STOP_TRACKING"
        const val EXTRA_SAVE_RUN = "com.orienteer.app.EXTRA_SAVE_RUN"
        const val EXTRA_ROUTE_ID = "com.orienteer.app.EXTRA_ROUTE_ID"
        private const val CHANNEL_ID = "run_tracking_channel"
        private const val NOTIFICATION_ID = 101
    }
}
