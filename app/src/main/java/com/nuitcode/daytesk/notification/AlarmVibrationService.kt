package com.nuitcode.daytesk.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat

/**
 * Vibration-only alarm. **No audio is emitted anywhere in this path.**
 *
 * The service exists solely to keep the process alive while the vibrator runs:
 * Android cancels an app's vibration when its process dies, so a plain
 * `Vibrator.vibrate()` from a receiver would stop after a few seconds. It drives
 * the vibrator directly (not notification vibration) so Do Not Disturb cannot
 * suppress it.
 *
 * Lifecycle:
 *   - starts immediately with a silent low-importance notification;
 *   - repeats [ALARM_PATTERN] forever (`repeat = 0`);
 *   - arms a 2-minute safety stop that survives the notifications toggle;
 *   - cancels the vibration, the timeout and the notification on dismiss,
 *     snooze, timeout and `onDestroy`.
 *
 * `START_NOT_STICKY` and no `onTaskRemoved` override: swiping the app away must
 * not silence a ringing alarm.
 */
class AlarmVibrationService : Service() {

    private var vibrator: Vibrator? = null
    private var currentId: Long = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRinging()
            ACTION_START -> startRinging(intent)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L)
        if (id < 0L) {
            stopSelf()
            return
        }
        val texto = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()

        // Must happen first: the platform gives a foreground service a short
        // window to post this notification.
        startForegroundNow(id, texto)

        if (currentId == id) {
            // Same reminder re-fired: refresh the notification and leave the
            // already-looping vibration alone.
            return
        }

        ReminderScheduler.cancelRingTimeout(this, currentId)
        currentId = id
        startVibration()
        ReminderScheduler.setRingTimeout(this, id)
    }

    private fun startForegroundNow(id: Long, texto: String) {
        val notification = NotificationHelper.buildRingNotification(this, id, texto)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NotificationHelper.RING_NOTIFICATION_ID, notification, type)
    }

    private fun startVibration() {
        val target = vibratorFor(this) ?: run {
            // No vibrator after all: the caller already saw `start` succeed, so
            // fall back to the silent notification instead of staying mute.
            return
        }
        vibrator = target
        target.cancel()
        target.vibrate(VibrationEffect.createWaveform(ALARM_PATTERN, ALARM_PATTERN_REPEAT))
    }

    private fun stopRinging() {
        vibrator?.cancel()
        vibrator = null
        if (currentId >= 0L) {
            ReminderScheduler.cancelRingTimeout(this, currentId)
        }
        currentId = -1L
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NotificationHelper.RING_NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.nuitcode.daytesk.ALARM_VIBRATION_START"
        const val ACTION_STOP = "com.nuitcode.daytesk.ALARM_VIBRATION_STOP"
        private const val DISMISS_REQUEST_CODE = 8001

        /** Strong, loopable waveform: silence, triple pulse. */
        internal val ALARM_PATTERN = longArrayOf(0, 700, 300, 700, 300, 1200)
        private const val ALARM_PATTERN_REPEAT = 0

        /**
         * Seam over `startForegroundService` so tests can prove the degradation
         * path without a real service.
         */
        internal var startService: (Context, Intent) -> Unit = { context, intent ->
            context.startForegroundService(intent)
        }

        /**
         * Starts the ring. Returns `false` when the device has no vibrator or
         * the platform rejects the foreground-service start, so the caller can
         * fall back to the Stage 1 notification. It never starts silently.
         */
        fun start(context: Context, id: Long, texto: String): Boolean {
            val target = vibratorFor(context) ?: return false
            if (!target.hasVibrator()) return false
            val intent = Intent(context, AlarmVibrationService::class.java).apply {
                action = ACTION_START
                data = Uri.parse("daytesk://recordatorio/$id/alarm")
                putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, id)
                putExtra(ReminderScheduler.EXTRA_TITLE, texto)
            }
            return try {
                startService(context, intent)
                true
            } catch (rejected: RuntimeException) {
                // ForegroundServiceStartNotAllowedException (API 31+) and
                // SecurityException both extend RuntimeException.
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmVibrationService::class.java))
        }

        /** "Descartar" action on the ringing notification. */
        fun dismissPendingIntent(context: Context, id: Long): PendingIntent {
            val intent = Intent(context, AlarmVibrationService::class.java).apply {
                action = ACTION_STOP
                data = Uri.parse("daytesk://recordatorio/$id/dismiss")
                putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, id)
            }
            return PendingIntent.getService(
                context,
                DISMISS_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        @Suppress("DEPRECATION")
        internal fun vibratorFor(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}
