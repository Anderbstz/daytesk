package com.nuitcode.daytesk.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.nuitcode.daytesk.MainActivity
import com.nuitcode.daytesk.R

object NotificationHelper {
    const val CHANNEL_ID = "daytesk_reminders"
    const val CHANNEL_NAME = "Recordatorios"
    const val CHANNEL_ID_ALARM = "daytesk_alarm_reminders"
    const val CHANNEL_NAME_ALARM = "Recordatorios (alarma)"
    const val REMINDER_REQUEST_CODE = 1001

    /**
     * Channels are immutable after creation, so the alarm behaviour needs a
     * brand-new id; the original [CHANNEL_ID] keeps serving task reminders.
     */
    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminders = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Recordatorios de tareas pendientes"
            enableVibration(true)
        }
        val alarmReminders = NotificationChannel(
            CHANNEL_ID_ALARM,
            CHANNEL_NAME_ALARM,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Recordatorios con tono de alarma"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600)
            setSound(
                alarmSoundUri(),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(reminders)
        manager.createNotificationChannel(alarmReminders)
    }

    /**
     * System alarm tone, with a guaranteed non-null fallback so an unavailable
     * alarm ringtone can never degrade the channel to silence.
     */
    internal fun alarmSoundUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

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
     * notification with the same numeric id.
     */
    fun showRecordatorio(context: Context, texto: String, recordatorioId: Long, early: Boolean = false) {
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
            .setSound(alarmSoundUri(), android.media.AudioManager.STREAM_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                0,
                "Posponer 10 min",
                ReminderScheduler.snoozeActionPendingIntent(context, recordatorioId, texto),
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifyId, notification)
    }
}
