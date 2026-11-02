package com.nuitcode.daytesk.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Vibration-only alarm. **No audio is emitted anywhere in this path.**
 *
 * The service exists solely to keep the process alive while the vibrator runs:
 * Android cancels an app's vibration when its process dies, so a plain
 * `Vibrator.vibrate()` from a receiver would stop after a few seconds. It drives
 * the vibrator directly (not notification vibration) with alarm usage, which is
 * what lets it through silent mode, Do Not Disturb and battery saver.
 *
 * The ring surfaces like a clock alarm: [AlarmActivity] takes the screen, through
 * the notification's full-screen intent when the platform decides so, and through
 * a direct start otherwise.
 *
 * Lifecycle:
 *   - starts immediately with a silent high-importance notification;
 *   - repeats [ALARM_PATTERN] forever (`repeat = 0`);
 *   - arms a 2-minute safety stop that survives the notifications toggle;
 *   - cancels the vibration, the timeout and the notification on dismiss,
 *     snooze, timeout and `onDestroy`;
 *   - stops early when the reminder is invalidated (deleted, edited,
 *     notifications turned off, logout or account switch). Those stops are
 *     id-scoped through [stopIfRinging], so one reminder's invalidation cannot
 *     silence a different reminder's ring.
 *
 * `START_NOT_STICKY` and no `onTaskRemoved` override: swiping the app away must
 * not silence a ringing alarm.
 */
class AlarmVibrationService : Service() {

    private var vibrator: Vibrator? = null
    private var timeoutId: Long = NO_RECORDATORIO_ID

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> applyStop(intent.recordatorioId())
            ACTION_START -> startRinging(intent)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent) {
        val id = intent.recordatorioId()
        if (id < 0L) {
            stopSelf()
            return
        }
        val texto = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()

        // Must happen first: the platform gives a foreground service a short
        // window to post this notification.
        startForegroundNow(id, texto)

        val current = ringing.value
        if (id in current) {
            // Same reminder re-fired: refresh the notification and leave the
            // already-looping vibration alone.
            return
        }

        // Several reminders can come due together. They share one vibrator and
        // one screen, so a later one joins the ring instead of replacing it:
        // otherwise answering the first left the last one vibrating.
        ringing.value = current + (id to texto)
        if (current.isEmpty()) startVibration()
        if (timeoutId != NO_RECORDATORIO_ID) ReminderScheduler.cancelRingTimeout(this, timeoutId)
        timeoutId = id
        ReminderScheduler.setRingTimeout(this, id)
        // Android may block this start in the background; the full-screen intent
        // of the notification above is the path the platform always honours.
        runCatching { startActivity(AlarmActivity.intent(this, id, texto)) }
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
        target.vibrate(
            VibrationEffect.createWaveform(ALARM_PATTERN, ALARM_PATTERN_REPEAT),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
    }

    private fun applyStop(incomingId: Long) {
        if (incomingId == NO_RECORDATORIO_ID) markAnswered(ringing.value.values)
        val remaining = ringAfterStop(ringing.value, incomingId)
        ringing.value = remaining
        if (remaining.isEmpty()) stopRinging()
    }

    private fun stopRinging() {
        vibrator?.cancel()
        vibrator = null
        if (timeoutId != NO_RECORDATORIO_ID) {
            ReminderScheduler.cancelRingTimeout(this, timeoutId)
        }
        timeoutId = NO_RECORDATORIO_ID
        ringing.value = emptyMap()
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

        /** Reminders ringing now, id to text. [AlarmActivity] shows and answers them all. */
        internal val ringing = MutableStateFlow<Map<Long, String>>(emptyMap())

        private const val ANSWERED_WINDOW_MS = 60_000L
        private val answeredAt = ConcurrentHashMap<String, Long>()

        /** Records that the user just answered these reminders. */
        internal fun markAnswered(textos: Collection<String>) {
            val now = System.currentTimeMillis()
            textos.forEach { answeredAt[it] = now }
        }

        /**
         * True for a reminder the user answered moments ago. The system can
         * deliver alarms of the same minute seconds apart, so a late copy must not
         * start vibrating again right after "Descartar".
         */
        internal fun wasJustAnswered(texto: String): Boolean {
            val at = answeredAt[texto] ?: return false
            return System.currentTimeMillis() - at < ANSWERED_WINDOW_MS
        }

        /**
         * Sentinel for "no reminder id". Also the marker of an unconditional
         * stop: a whole-identity invalidation has no single reminder to name.
         */
        internal const val NO_RECORDATORIO_ID = -1L

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

        /**
         * Silences the ring **only if it belongs to [id]**.
         *
         * The identity-invalidating paths (delete, edit, notifications-off) use
         * this instead of [stop], so invalidating one reminder can never silence
         * a different reminder that happens to be ringing at the same time. The
         * service is already running, so [Context.startService] is enough — no
         * foreground-service start is required.
         *
         * A failed start is swallowed: if the service cannot be started there is
         * no ring to stop, and a delete must never crash for it.
         */
        fun stopIfRinging(context: Context, id: Long) {
            sendStop(context, id)
        }

        /**
         * Silences whatever is ringing, without naming a reminder.
         *
         * Reserved for paths that discard the whole local identity (logout,
         * account switch): every reminder on screen belongs to the outgoing
         * identity, so there is no id to compare against.
         */
        fun stopAnyRinging(context: Context) {
            sendStop(context, NO_RECORDATORIO_ID)
        }

        private fun sendStop(context: Context, id: Long) {
            val intent = Intent(context, AlarmVibrationService::class.java).apply {
                action = ACTION_STOP
                data = Uri.parse("daytesk://recordatorio/$id/stop")
                if (id != NO_RECORDATORIO_ID) {
                    putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, id)
                }
            }
            runCatching { context.startService(intent) }
        }

        /**
         * What keeps ringing after an [ACTION_STOP] for [incomingId].
         *
         * - a named id only removes that reminder, so one reminder's invalidation
         *   cannot cut off another that is ringing;
         * - a missing id empties the ring: "Descartar" answers everything on
         *   screen, and a whole-identity invalidation can never leave one behind.
         *
         * The vibration stops once nothing is left.
         */
        internal fun ringAfterStop(current: Map<Long, String>, incomingId: Long): Map<Long, String> =
            if (incomingId == NO_RECORDATORIO_ID) emptyMap() else current - incomingId

        /** "Descartar" action on the ringing notification: answers the whole ring. */
        fun dismissPendingIntent(context: Context, id: Long): PendingIntent {
            val intent = Intent(context, AlarmVibrationService::class.java).apply {
                action = ACTION_STOP
                data = Uri.parse("daytesk://recordatorio/$id/dismiss")
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

/** The reminder an incoming command belongs to, or [NO_RECORDATORIO_ID]. */
private fun Intent.recordatorioId(): Long =
    getLongExtra(
        ReminderScheduler.EXTRA_RECORDATORIO_ID,
        AlarmVibrationService.NO_RECORDATORIO_ID,
    )
