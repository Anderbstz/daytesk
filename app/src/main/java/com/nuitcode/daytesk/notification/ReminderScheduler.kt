package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado

object ReminderScheduler {
    private const val ACTION_REMINDER = "com.nuitcode.daytesk.REMINDER"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TITLE = "task_title"
    const val WEEKLY_REVIEW_ID = 0L

    fun scheduleIfDue(context: Context, taskId: Long, taskTitle: String, dueMillis: Long?) {
        if (dueMillis == null) {
            cancelTaskReminder(context, taskId)
            return
        }
        val delay = dueMillis - System.currentTimeMillis()
        if (delay <= 0L) {
            cancelTaskReminder(context, taskId)
            return
        }
        scheduleAt(context, taskId, taskTitle, dueMillis)
    }

    fun scheduleTaskReminder(context: Context, taskId: Long, taskTitle: String, delayMillis: Long) {
        val triggerAt = System.currentTimeMillis() + delayMillis.coerceAtLeast(0L)
        scheduleAt(context, taskId, taskTitle, triggerAt)
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, taskId, ""))
    }

    fun scheduleWeeklyReview(context: Context) {
        scheduleAt(
            context,
            WEEKLY_REVIEW_ID,
            "Revisión semanal",
            System.currentTimeMillis() + millisUntilNextSundayMorning(),
        )
    }

    fun reschedulePending(context: Context, tareas: List<Tarea>) {
        NotificationHelper.createNotificationChannel(context)
        tareas
            .filter { it.estado == TareaEstado.PENDIENTE }
            .forEach { tarea ->
                scheduleIfDue(context, tarea.id, tarea.titulo, tarea.fechaVencimiento)
            }
    }

    private fun scheduleAt(context: Context, taskId: Long, title: String, triggerAtMillis: Long) {
        NotificationHelper.createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, taskId, title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun pendingIntent(context: Context, taskId: Long, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("daytesk://reminder/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun millisUntilNextSundayMorning(): Long {
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 10)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(java.util.Calendar.WEEK_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }
}
