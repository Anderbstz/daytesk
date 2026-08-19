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
    const val EXTRA_EARLY = "early"
    const val WEEKLY_REVIEW_ID = 0L
    private const val HOUR_MS = 60 * 60 * 1000L

    fun scheduleIfDue(context: Context, taskId: Long, taskTitle: String, dueMillis: Long?) {
        cancelTaskReminder(context, taskId)
        if (dueMillis == null) return
        val now = System.currentTimeMillis()
        if (dueMillis > now) {
            scheduleAt(context, taskId, taskTitle, dueMillis, early = false)
        }
        val earlyAt = dueMillis - HOUR_MS
        if (earlyAt > now) {
            scheduleAt(context, taskId, taskTitle, earlyAt, early = true)
        }
    }

    fun scheduleTaskReminder(context: Context, taskId: Long, taskTitle: String, delayMillis: Long) {
        val triggerAt = System.currentTimeMillis() + delayMillis.coerceAtLeast(0L)
        scheduleAt(context, taskId, taskTitle, triggerAt, early = false)
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, taskId, "", early = false))
        alarmManager.cancel(pendingIntent(context, taskId, "", early = true))
    }

    fun scheduleWeeklyReview(context: Context) {
        scheduleAt(
            context,
            WEEKLY_REVIEW_ID,
            "Revisión semanal",
            System.currentTimeMillis() + millisUntilNextSundayMorning(),
            early = false,
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

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun scheduleAt(
        context: Context,
        taskId: Long,
        title: String,
        triggerAtMillis: Long,
        early: Boolean,
    ) {
        NotificationHelper.createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, taskId, title, early)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun pendingIntent(
        context: Context,
        taskId: Long,
        title: String,
        early: Boolean,
    ): PendingIntent {
        val kind = if (early) "early" else "due"
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("daytesk://reminder/$taskId/$kind")
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_EARLY, early)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(taskId, early),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(taskId: Long, early: Boolean): Int {
        val base = (taskId and 0x7FFFFFFF).toInt()
        return if (early) base xor 0x40000000 else base
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
