package io.issac.pleosandroid.driving

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import io.issac.pleosandroid.MainActivity
import io.issac.pleosandroid.R
import io.issac.pleosandroid.core.domain.collection.repository.VisitRecorder
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.RecordingPhase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Explicit local-recording opt-in keeps speed observation alive while another app is in front. */
@AndroidEntryPoint
class DrivingRecordingService : Service() {
    @Inject lateinit var recorder: DrivingRecorder

    @Inject lateinit var visits: VisitRecorder
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitoring: Job? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "여정 기록", NotificationManager.IMPORTANCE_LOW),
        )
        promoteToForeground()
        scope.launch { recorder.enabled.collect { if (!it) stopSelf() } }
        scope.launch {
            recorder.state.collect { state ->
                val text = recordingMessage(state.phase)
                if (Build.VERSION.SDK_INT < 33 ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(text))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        if (intent?.action == STOP) {
            scope.launch {
                recorder.setEnabled(false)
                stopSelf()
            }
        } else if (monitoring?.isActive != true && recorder.enabled.value) {
            monitoring = scope.launch {
                try {
                    coroutineScope {
                        launch { visits.monitor() }
                        recorder.monitor()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    recorder.monitoringUnavailable()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (recorder.state.value.phase !in setOf(RecordingPhase.Recording, RecordingPhase.Saving)) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun recordingMessage(phase: RecordingPhase): String = when (phase) {
        RecordingPhase.Recording -> "여정을 기록하고 있어요 · 결과는 주차 후 확인"
        RecordingPhase.Saving -> "이번 여정을 저장하고 있어요"
        RecordingPhase.Error -> "기록 저장을 확인해 주세요"
        RecordingPhase.Demo -> "데모에서는 실제 주행을 저장하지 않아요"
        else -> "다음 출발부터 운전습관을 기록해요"
    }

    private fun promoteToForeground() {
        val notification = notification(recordingMessage(recorder.state.value.phase))
        if (Build.VERSION.SDK_INT >= 29) {
            val location = if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            val specialUse = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            startForeground(NOTIFICATION, notification, location or specialUse)
        } else {
            startForeground(NOTIFICATION, notification)
        }
    }

    private fun notification(message: String): Notification {
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, DrivingRecordingService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_driving_record)
            .setContentTitle(getString(R.string.driving_recording_notification_title, getString(R.string.app_name)))
            .setContentText(message)
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "기록 끄기", stop).build())
            .build()
    }

    private companion object {
        const val CHANNEL = "driving-recording"
        const val NOTIFICATION = 4101
        const val STOP = "io.issac.pleosandroid.STOP_DRIVING_RECORDING"
    }
}
