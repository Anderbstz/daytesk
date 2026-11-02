package com.nuitcode.daytesk.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nuitcode.daytesk.MainActivity
import com.nuitcode.daytesk.R

object NotificationHelper {
    const val CHANNEL_ID = "daytesk_reminders_v2"
    const val CHANNEL_NAME = "Recordatorios"

    /**
     * Notification channels are IMMUTABLE after creation: a channel created by
     * an earlier build keeps its original sound/vibration settings forever, and
     * there is no API to mutate it. Every channel therefore lives under a
     * versioned id. Bump the suffix whenever its configuration must change —
     * reusing the old id would silently keep the broken configuration on every
     * device that already created it.
     */
    const val CHANNEL_ID_ALARM = "daytesk_alarm_reminders_v3"
    const val CHANNEL_NAME_ALARM = "Recordatorios (alarma)"

    /**
     * Channel ids created by earlier builds, all of them with sound or too low
     * an importance. They are kept only so they can be deleted; deleting is the
     * one legal way to retire an immutable channel. Never post to them again.
     */
    val LEGACY_CHANNEL_IDS = listOf(
        "daytesk_reminders",
        "daytesk_alarm_reminders",
        "daytesk_alarm_reminders_v2",
        "daytesk_alarm_ring_v1",
    )
    const val REMINDER_REQUEST_CODE = 1001

    /**
     * Foreground-service channel for a ringing alarm. The vibration is driven
     * directly by the service, so this channel makes no sound and no vibration of
     * its own. High importance is what lets its full-screen intent take over.
     */
    const val CHANNEL_ID_RING = "daytesk_alarm_ring_v2"
    const val CHANNEL_NAME_RING = "Recordatorio vibrando"
    const val RING_NOTIFICATION_ID = 9001

    /**
     * DayTesk never plays sound: reminders are felt, not heard. Bypassing Do Not
     * Disturb on creation is only honoured for some apps, which is why the posts
     * also carry the alarm category that the mode lets through by default.
     */
    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminders = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Recordatorios de tareas pendientes"
            setSound(null, null)
            enableVibration(true)
        }
        val alarmReminders = NotificationChannel(
            CHANNEL_ID_ALARM,
            CHANNEL_NAME_ALARM,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Recordatorios: solo vibración"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = ALARM_VIBRATION_PATTERN
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val ring = NotificationChannel(
            CHANNEL_ID_RING,
            CHANNEL_NAME_RING,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Recordatorio en modo alarma: solo vibración"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(reminders)
        manager.createNotificationChannel(alarmReminders)
        manager.createNotificationChannel(ring)
        LEGACY_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
    }

    private val ALARM_VIBRATION_PATTERN = longArrayOf(0, 600, 400, 600)

    private fun alarmScreenPendingIntent(context: Context, id: Long, texto: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            RING_NOTIFICATION_ID,
            AlarmActivity.intent(context, id, texto),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun showTaskReminder(context: Context, taskTitle: String, taskId: Long, early: Boolean = false) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("task_id", taskId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (early) "En 1 hora: $taskTitle" else "Tarea pendiente: $taskTitle"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\uD83D\uDCCB Daytesk")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifyId = if (early) taskId.toInt() xor 0x40000000 else taskId.toInt()
        manager.notify(notifyId, notification)
    }

    /**
     * Recordatorio notification on the alarm channel. The notify id shares the
     * recordatorio request-code namespace so it can never overwrite a task
     * notification with the same numeric id. In alarm mode this is the fallback
     * for a ring the platform rejected, so it still claims the whole screen.
     */
    fun showRecordatorio(
        context: Context,
        texto: String,
        recordatorioId: Long,
        early: Boolean = false,
        esAlarma: Boolean = false,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, recordatorioId)
        }
        val notifyId = ReminderScheduler.recordatorioRequestCode(recordatorioId, early)
        val pendingIntent = PendingIntent.getActivity(
            context, notifyId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (early) "En 1 hora: $texto" else "Recordatorio: $texto"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\u23F0 Daytesk")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                0,
                "Posponer 10 min",
                ReminderScheduler.snoozeActionPendingIntent(
                    context,
                    recordatorioId,
                    texto,
                    notifyId,
                    esAlarma,
                ),
            )
            .apply {
                if (esAlarma) {
                    setFullScreenIntent(alarmScreenPendingIntent(context, recordatorioId, texto), true)
                }
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifyId, notification)
    }

    /**
     * Foreground notification for [AlarmVibrationService]. It keeps the process
     * alive while the vibrator runs and carries the full-screen intent that puts
     * [AlarmActivity] on the display. The channel supplies no sound or vibration.
     */
    fun buildRingNotification(context: Context, recordatorioId: Long, texto: String) =
        NotificationCompat.Builder(context, CHANNEL_ID_RING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\u23F0 Daytesk")
            .setContentText("Recordatorio: $texto")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(alarmScreenPendingIntent(context, recordatorioId, texto), true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    RING_NOTIFICATION_ID,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, recordatorioId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(0, "Descartar", AlarmVibrationService.dismissPendingIntent(context, recordatorioId))
            .addAction(
                0,
                "Posponer 10 min",
                ReminderScheduler.snoozeActionPendingIntent(
                    context,
                    recordatorioId,
                    texto,
                    RING_NOTIFICATION_ID,
                    esAlarma = true,
                ),
            )
            .build()
}
