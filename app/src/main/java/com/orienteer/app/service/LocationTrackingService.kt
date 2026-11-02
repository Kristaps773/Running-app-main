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
import androidx.media.app.NotificationCompat.MediaStyle
import com.orienteer.app.MainActivity
import com.orienteer.app.R
import com.orienteer.app.data.local.ActiveRunStore
import com.orienteer.app.data.model.GeoPoint
import com.orienteer.app.data.model.Route
import com.orienteer.app.data.model.RunSession
import com.orienteer.app.data.repository.LocationRepository
import com.orienteer.app.data.repository.RouteRepository
import com.orienteer.app.data.repository.RunRepository
import com.orienteer.app.BuildConfig
import com.orienteer.app.domain.RunPersistOnce
import com.orienteer.app.domain.usecase.TrackRunUseCase
import com.orienteer.app.util.GeoUtils
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var shouldSaveRun = true
    /** After resume, re-anchor the track so travel during the pause is not counted. */
    private var reanchorTrackOnNextFix = false
    /** Survives [lifecycleScope] cancel so Room insert completes before [stopSelf]. */
    private val persistJob = SupervisorJob()
    private val persistScope = CoroutineScope(Dispatchers.IO + persistJob)
    private val persistOnce = RunPersistOnce()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        // Do not cancel persistJob here — NonCancellable insert must finish.
        // Scope ends when process tears down; children complete after stopSelf.
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
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
            ACTION_SKIP -> skipCurrentCheckpoint()
        }
        return START_STICKY
    }

    private fun isAlreadyTrackingRoute(routeId: String): Boolean =
        route?.id == routeId &&
            _runSession.value?.isActive == true

    /**
     * Loads route and starts a new session here (not from ViewModel bind) so reconnecting does not reset GPS state.
     */
    private suspend fun startTrackingForRouteId(routeId: String) {
        cancelTrackingJobOnly()
        persistOnce.reset()
        val loaded = routeRepository.getRoute(routeId)
        if (loaded == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            activeRunStore.clear()
            stopSelf()
            return
        }

        // Old stored routes may lack a trailing finish — require return-to-start.
        val course = loaded.withFinishCheckpoint()
        route = course
        val started = System.currentTimeMillis()
        _runSession.value = RunSession(
            routeId = course.id,
            startedAt = started,
            checkpointReachedAtMs = mapOf(0 to started)
        )
        activeRunStore.setActive(course.id, started)
        reportRunCheckpointEvent(
            action = "run_started",
            data = mapOf(
                "route_id" to course.id.take(8),
                "waypoint_count" to course.waypointCount,
                "checkpoint_count" to course.checkpoints.size
            )
        )

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                contentText = "Preparing GPS…",
                startedAt = started,
                allowSkip = canSkipCurrentCheckpoint(_runSession.value!!),
                routeId = course.id
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
        if (!canSkipCurrentCheckpoint(currentSession)) return

        val targetIdx = currentSession.currentCheckpointIdx
        val checkpoint = currentRoute.checkpoints[targetIdx]
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
        reportRunCheckpointEvent(
            action = "checkpoint_skipped",
            data = mapOf(
                "checkpoint_id" to checkpoint.id,
                "next_idx" to newCheckpointIdx,
                "all_done" to allDone
            )
        )
        updateNotification(_runSession.value!!)
        if (allDone) {
            finishAndPersist(_runSession.value!!)
        }
    }

    /** Finish must be reached in person — do not allow skipping the return-to-start. */
    private fun canSkipCurrentCheckpoint(session: RunSession): Boolean {
        if (!session.isActive || session.isPaused) return false
        val currentRoute = route ?: return false
        val targetIdx = session.currentCheckpointIdx
        val checkpoint = currentRoute.checkpoints.getOrNull(targetIdx) ?: return false
        val targetingFinish = checkpoint.isStart && targetIdx >= currentRoute.checkpoints.lastIndex
        return !targetingFinish
    }

    fun pauseTracking() {
        val session = _runSession.value ?: return
        if (!session.isActive || session.isPaused) return

        cancelTrackingJobOnly()
        val now = System.currentTimeMillis()
        val paused = session.copy(
            isPaused = true,
            pausedAt = now,
            elapsedTimeMs = session.activeElapsedMs(now)
        )
        _runSession.value = paused
        reportRunCheckpointEvent(action = "run_paused")
        updateNotification(paused)
    }

    fun resumeTracking() {
        val session = _runSession.value ?: return
        if (!session.isActive || !session.isPaused) return

        val now = System.currentTimeMillis()
        val resumed = session.withPauseClosed(now)
        _runSession.value = resumed
        reanchorTrackOnNextFix = true
        reportRunCheckpointEvent(action = "run_resumed")
        updateNotification(resumed)
        startTracking()
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = lifecycleScope.launch {
            locationRepository.locationUpdates(intervalMs = 2_000L).collect { point ->
                processLocation(point)
            }
        }
    }

    private fun processLocation(point: GeoPoint) {
        val currentRoute = route ?: return
        val currentSession = _runSession.value ?: return
        if (currentSession.isPaused) return
        val now = System.currentTimeMillis()
        val elapsed = currentSession.activeElapsedMs(now)

        // Replace last crumb with the post-pause fix so distance during pause is not added.
        val sessionForUpdate = if (reanchorTrackOnNextFix && currentSession.trackedPoints.isNotEmpty()) {
            reanchorTrackOnNextFix = false
            currentSession.copy(
                trackedPoints = currentSession.trackedPoints.dropLast(1) + point,
                trackedPointTimestampsMs = currentSession.trackedPointTimestampsMs.dropLast(1) + now
            )
        } else {
            reanchorTrackOnNextFix = false
            currentSession
        }

        val updated = trackRunUseCase(sessionForUpdate, currentRoute, point, elapsed, now)
        if (updated.currentCheckpointIdx > currentSession.currentCheckpointIdx) {
            val reachedId = currentRoute.checkpoints
                .getOrNull(currentSession.currentCheckpointIdx)?.id
            reportRunCheckpointEvent(
                action = "checkpoint_reached",
                data = mapOf(
                    "checkpoint_id" to (reachedId ?: -1),
                    "next_idx" to updated.currentCheckpointIdx,
                    "all_done" to !updated.isActive
                )
            )
        }
        _runSession.value = updated

        updateNotification(updated)

        if (!updated.isActive) {
            finishAndPersist(updated)
        }
    }

    private fun stopTracking() {
        cancelTrackingJobOnly()
        val now = System.currentTimeMillis()
        val finalSession = _runSession.value?.let { raw ->
            val closed = raw.withPauseClosed(now)
            closed.copy(
                isActive = false,
                finishedAt = closed.finishedAt ?: now,
                elapsedTimeMs = closed.elapsedTimeMs
            )
        }
        if (finalSession != null) {
            _runSession.value = finalSession
            reportRunCheckpointEvent(
                action = "run_stopped",
                data = mapOf(
                    "reached_ids" to finalSession.checkpointReachedAtMs.keys.sorted().joinToString(","),
                    "skipped_ids" to finalSession.skippedCheckpointIds.sorted().joinToString(","),
                    "next_idx" to finalSession.currentCheckpointIdx,
                    "finished" to false
                )
            )
        }
        persistThenStop(finalSession)
    }

    /**
     * Persist (if enabled) then tear down. Uses [persistScope] so the insert is not cancelled
     * when [lifecycleScope] is torn down by [stopSelf].
     */
    private fun finishAndPersist(session: RunSession) {
        cancelTrackingJobOnly()
        val now = System.currentTimeMillis()
        val finalized = session.withPauseClosed(now).let { closed ->
            closed.copy(
                isActive = false,
                finishedAt = closed.finishedAt ?: now,
                elapsedTimeMs = closed.elapsedTimeMs
            )
        }
        _runSession.value = finalized
        reportRunCheckpointEvent(
            action = "run_finished",
            data = mapOf(
                "reached_ids" to finalized.checkpointReachedAtMs.keys.sorted().joinToString(","),
                "skipped_ids" to finalized.skippedCheckpointIds.sorted().joinToString(","),
                "finished" to (finalized.finishedAt != null)
            )
        )
        persistThenStop(finalized)
    }

    private fun persistThenStop(session: RunSession?) {
        if (!persistOnce.tryStart()) return
        persistScope.launch {
            try {
                if (session != null && shouldSaveRun) {
                    withContext(NonCancellable) {
                        persistSession(session)
                    }
                }
                activeRunStore.clear()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun persistSession(session: RunSession) {
        val currentRoute = route
        val gpsReachedWaypoints = currentRoute?.checkpoints?.count { cp ->
            !cp.isStart && cp.id in session.checkpointReachedAtMs
        } ?: (session.checkpointReachedAtMs.keys.count { it != 0 })
        runRepository.saveSession(
            routeId = session.routeId,
            totalDistanceM = session.totalDistanceM,
            elapsedTimeMs = session.elapsedTimeMs,
            startedAt = session.startedAt,
            finishedAt = session.finishedAt,
            checkpointsReached = gpsReachedWaypoints,
            trackedPoints = session.trackedPoints,
            trackedPointTimestampsMs = session.trackedPointTimestampsMs,
            checkpointReachedAtMs = session.checkpointReachedAtMs,
            skippedCheckpointIds = session.skippedCheckpointIds
        )
    }

    /**
     * Searchable Sentry events for run/checkpoint debugging (ids only, no GPS).
     * Breadcrumbs alone were not enough — they only appear attached to other errors.
     */
    private fun reportRunCheckpointEvent(
        action: String,
        data: Map<String, Any> = emptyMap()
    ) {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                category = "run.checkpoint"
                message = action
                level = SentryLevel.INFO
                data.forEach { (k, v) -> setData(k, v) }
            }
        )
        Sentry.withScope { scope ->
            scope.level = SentryLevel.INFO
            scope.setTag("feature", "run_checkpoint")
            scope.setTag("run_action", action)
            data["checkpoint_id"]?.let { scope.setTag("checkpoint_id", it.toString()) }
            data["all_done"]?.let { scope.setTag("all_done", it.toString()) }
            scope.setContexts("run_checkpoint", data + mapOf("action" to action))
            scope.fingerprint = listOf("run_checkpoint", action)
            Sentry.captureMessage("OrienteerRun $action")
        }
    }

    private fun updateNotification(session: RunSession) {
        val distance = GeoUtils.formatDistance(session.totalDistanceM)
        val time = GeoUtils.formatDuration(session.activeElapsedMs())
        val pace = GeoUtils.formatRunPace(session.totalDistanceM, session.activeElapsedMs())
        val checkpointLine = checkpointProgressLabel(session)
        val pausedLabel = if (session.isPaused) getString(R.string.run_paused) else null
        val nextDetail = nextCheckpointDetail(session)
        val contentText = listOfNotNull(pausedLabel, distance, time, pace, checkpointLine)
            .joinToString("  ·  ")
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(
                contentText = contentText,
                subText = nextDetail,
                startedAt = session.startedAt,
                totalPausedMs = session.totalPausedMs,
                isPaused = session.isPaused,
                allowSkip = canSkipCurrentCheckpoint(session),
                routeId = route?.id
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
        subText: String? = null,
        startedAt: Long = System.currentTimeMillis(),
        totalPausedMs: Long = 0L,
        isPaused: Boolean = false,
        allowSkip: Boolean = false,
        routeId: String? = null
    ): Notification {
        val openRunIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!routeId.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_OPEN_ACTIVE_RUN_ROUTE_ID, routeId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, REQUEST_CONTENT,
            openRunIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (isPaused) {
            getString(R.string.notification_title_paused)
        } else {
            getString(R.string.notification_title)
        }
        // Chronometer counts from setWhen; shift by completed pauses so it matches moving time.
        val chronometerBase = startedAt + totalPausedMs
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_run_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(chronometerBase)
            .setShowWhen(true)
            .setUsesChronometer(!isPaused)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (!subText.isNullOrBlank()) {
            builder.setSubText(subText)
        }
        var actionCount = 0
        if (isPaused) {
            builder.addAction(
                0,
                getString(R.string.notification_action_resume),
                serviceActionPendingIntent(REQUEST_RESUME, ACTION_RESUME)
            )
            builder.addAction(
                0,
                getString(R.string.notification_action_end),
                serviceActionPendingIntent(REQUEST_STOP, ACTION_STOP)
            )
            actionCount = 2
        } else {
            builder.addAction(
                0,
                getString(R.string.notification_action_pause),
                serviceActionPendingIntent(REQUEST_PAUSE, ACTION_PAUSE)
            )
            actionCount = 1
            if (allowSkip) {
                builder.addAction(
                    0,
                    getString(R.string.notification_action_unreachable),
                    serviceActionPendingIntent(REQUEST_SKIP, ACTION_SKIP)
                )
                actionCount = 2
            }
        }
        // MediaStyle keeps Pause/Unreachable (or Resume/End) visible in the compact lock-screen row.
        val compactActions = if (actionCount >= 2) intArrayOf(0, 1) else intArrayOf(0)
        builder.setStyle(MediaStyle().setShowActionsInCompactView(*compactActions))
        return builder.build()
    }

    private fun serviceActionPendingIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, LocationTrackingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createNotificationChannel() {
        // New id: Android does not raise importance of an already-created LOW channel.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.run_tracking_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.run_tracking_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.orienteer.app.START_TRACKING"
        const val ACTION_PAUSE = "com.orienteer.app.PAUSE_TRACKING"
        const val ACTION_RESUME = "com.orienteer.app.RESUME_TRACKING"
        const val ACTION_STOP = "com.orienteer.app.STOP_TRACKING"
        const val ACTION_SKIP = "com.orienteer.app.SKIP_CHECKPOINT"
        const val EXTRA_SAVE_RUN = "com.orienteer.app.EXTRA_SAVE_RUN"
        const val EXTRA_ROUTE_ID = "com.orienteer.app.EXTRA_ROUTE_ID"
        private const val CHANNEL_ID = "run_tracking_channel_v2"
        private const val NOTIFICATION_ID = 101
        private const val REQUEST_CONTENT = 0
        private const val REQUEST_PAUSE = 1
        private const val REQUEST_RESUME = 2
        private const val REQUEST_STOP = 3
        private const val REQUEST_SKIP = 4
    }
}
