package com.nuitcode.daytesk.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nuitcode.daytesk.MainActivity
import com.nuitcode.daytesk.R

object NotificationHelper {
    const val CHANNEL_ID = "daytesk_reminders"
    const val CHANNEL_NAME = "Recordatorios"
    const val REMINDER_REQUEST_CODE = 1001

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Recordatorios de tareas pendientes"
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

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
     * Recordatorio notification, reusing the [CHANNEL_ID] channel. The notify
     * id shares the recordatorio request-code namespace so it can never
     * overwrite a task notification with the same numeric id.
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\u23F0 Daytesk")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifyId, notification)
    }
}
