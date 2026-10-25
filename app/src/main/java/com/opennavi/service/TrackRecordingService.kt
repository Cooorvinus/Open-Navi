package com.opennavi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.opennavi.MainActivity
import com.opennavi.OpenNaviApplication
import com.opennavi.R
import com.opennavi.data.gpx.GpxSerializer
import com.opennavi.domain.model.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class ActiveRecordingSession(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val startTimeMillis: Long = 0L,
    val elapsedMillis: Long = 0L,
    val totalDistanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val currentSpeedKmH: Float = 0f,
    val points: List<TrackPoint> = emptyList()
)

class TrackRecordingController {
    private val _session = MutableStateFlow(ActiveRecordingSession())
    val session: StateFlow<ActiveRecordingSession> = _session.asStateFlow()

    fun updateSession(transform: (ActiveRecordingSession) -> ActiveRecordingSession) {
        _session.update(transform)
    }

    fun reset() {
        _session.value = ActiveRecordingSession()
    }
}

/**
 * Usługa pierwszoplanowa (Foreground Service) dla Androida 15–17 (API 35–37)
 * z jawnym typem `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`.
 */
class TrackRecordingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val appContainer by lazy {
        (application as OpenNaviApplication).container
    }

    private val locationTracker: LocationAndCompassTracker
        get() = appContainer.locationTracker

    private val recordingController: TrackRecordingController
        get() = appContainer.recordingController

    private var timerJob: Job? = null
    private var locationCollectorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecordingService()
        }
        return START_STICKY
    }

    private fun startRecording() {
        val now = System.currentTimeMillis()
        recordingController.updateSession {
            ActiveRecordingSession(
                isRecording = true,
                isPaused = false,
                startTimeMillis = now,
                elapsedMillis = 0L
            )
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(0.0, 0L),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        locationTracker.startTracking()
        startCollectors()
    }

    private fun pauseRecording() {
        recordingController.updateSession { it.copy(isPaused = true) }
    }

    private fun resumeRecording() {
        recordingController.updateSession { it.copy(isPaused = false) }
    }

    private fun stopRecordingService() {
        timerJob?.cancel()
        locationCollectorJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startCollectors() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000L)
                val current = recordingController.session.value
                if (current.isRecording && !current.isPaused) {
                    val newElapsed = current.elapsedMillis + 1000L
                    recordingController.updateSession { it.copy(elapsedMillis = newElapsed) }
                    updateNotification(current.totalDistanceMeters, newElapsed)
                }
            }
        }

        locationCollectorJob?.cancel()
        locationCollectorJob = serviceScope.launch {
            locationTracker.gpsStatus.collect { gps ->
                val state = recordingController.session.value
                if (!state.isRecording || state.isPaused || !gps.hasValidFix) return@collect

                val newPoint = TrackPoint(
                    latitude = gps.latitude,
                    longitude = gps.longitude,
                    altitudeMeters = gps.altitudeMeters,
                    timestampMillis = System.currentTimeMillis(),
                    speedMetersPerSec = gps.speedMetersPerSec,
                    accuracyMeters = gps.accuracyMeters
                )

                val prevPoint = state.points.lastOrNull()
                if (prevPoint == null) {
                    recordingController.updateSession {
                        it.copy(
                            points = listOf(newPoint),
                            currentSpeedKmH = gps.speedKmH
                        )
                    }
                } else {
                    val dist = GpxSerializer.haversineDistanceMeters(
                        prevPoint.latitude,
                        prevPoint.longitude,
                        newPoint.latitude,
                        newPoint.longitude
                    )
                    if (dist >= 2.0) {
                        val altDiff = newPoint.altitudeMeters - prevPoint.altitudeMeters
                        val addGain = if (altDiff > 1.5) altDiff else 0.0
                        val addLoss = if (altDiff < -1.5) -altDiff else 0.0

                        recordingController.updateSession {
                            it.copy(
                                totalDistanceMeters = it.totalDistanceMeters + dist,
                                elevationGainMeters = it.elevationGainMeters + addGain,
                                elevationLossMeters = it.elevationLossMeters + addLoss,
                                currentSpeedKmH = gps.speedKmH,
                                points = it.points + newPoint
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateNotification(distanceMeters: Double, elapsedMillis: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(distanceMeters, elapsedMillis))
    }

    private fun buildNotification(distanceMeters: Double, elapsedMillis: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val distKm = distanceMeters / 1000.0
        val totalSec = elapsedMillis / 1000L
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        val text = String.format(Locale.US, "Dystans: %.2f km | Czas: %02d:%02d:%02d", distKm, h, m, s)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Open Navi – Nagrywanie śladu GPX")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_track),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "open_navi_track_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.opennavi.action.START_RECORDING"
        const val ACTION_PAUSE = "com.opennavi.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.opennavi.action.RESUME_RECORDING"
        const val ACTION_STOP = "com.opennavi.action.STOP_RECORDING"
    }
}
