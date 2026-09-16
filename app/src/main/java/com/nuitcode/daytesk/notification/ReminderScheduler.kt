package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.nuitcode.daytesk.model.Recordatorio
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado

object ReminderScheduler {
    private const val ACTION_REMINDER = "com.nuitcode.daytesk.REMINDER"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_RECORDATORIO_ID = "recordatorio_id"
    const val EXTRA_TITLE = "task_title"
    const val EXTRA_EARLY = "early"
    const val WEEKLY_REVIEW_ID = 0L
    private const val HOUR_MS = 60 * 60 * 1000L

    /**
     * Recordatorio alarms live in their own request-code namespace.
     *
     * Task and recordatorio ids come from independent Room autoincrements and
     * WILL collide, so a shared id must never share an alarm identity. The
     * offset keeps recordatorio codes disjoint from task codes; [EARLY_FLIP]
     * separates the 1h-before alarm from the at-due alarm in both namespaces.
     */
    private const val RECORDATORIO_ID_MASK = 0x1FFFFFFFL
    internal const val RECORDATORIO_OFFSET = 0x20000000
    internal const val EARLY_FLIP = 0x40000000

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

    /**
     * Schedules the two alarms of a recordatorio: one 1 hour before `fecha`
     * and one at `fecha`, each only when still in the future. Existing alarms
     * for the same id are cancelled first so an edit never leaves a stale one.
     */
    fun scheduleRecordatorio(context: Context, id: Long, texto: String, fecha: Long) {
        cancelRecordatorio(context, id)
        val now = System.currentTimeMillis()
        if (fecha > now) {
            setAlarm(context, fecha, recordatorioPendingIntent(context, id, texto, early = false))
        }
        val earlyAt = fecha - HOUR_MS
        if (earlyAt > now) {
            setAlarm(context, earlyAt, recordatorioPendingIntent(context, id, texto, early = true))
        }
    }

    fun cancelRecordatorio(context: Context, id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(recordatorioPendingIntent(context, id, "", early = false))
        alarmManager.cancel(recordatorioPendingIntent(context, id, "", early = true))
    }

    /** Re-registers every recordatorio's alarms, e.g. after a reboot or login. */
    fun rescheduleRecordatorios(context: Context, recordatorios: List<Recordatorio>) {
        NotificationHelper.createNotificationChannel(context)
        recordatorios.forEach { recordatorio ->
            scheduleRecordatorio(context, recordatorio.id, recordatorio.texto, recordatorio.fecha)
        }
    }

    /** Request code for a recordatorio alarm; kept in its own namespace. */
    internal fun recordatorioRequestCode(id: Long, early: Boolean): Int {
        val base = (id and RECORDATORIO_ID_MASK).toInt() or RECORDATORIO_OFFSET
        return if (early) base xor EARLY_FLIP else base
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
        setAlarm(context, triggerAtMillis, pendingIntent(context, taskId, title, early))
    }

    /**
     * Single alarm boundary for every reminder kind. Returns early when the
     * user turned notifications off, so no alarm is registered — for tasks or
     * recordatorios — while the toggle is disabled.
     */
    private fun setAlarm(context: Context, triggerAtMillis: Long, pending: PendingIntent) {
        if (!NotificationPreferences.isEnabled(context)) return
        NotificationHelper.createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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

    private fun recordatorioPendingIntent(
        context: Context,
        id: Long,
        texto: String,
        early: Boolean,
    ): PendingIntent {
        val kind = if (early) "early" else "due"
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("daytesk://recordatorio/$id/$kind")
            putExtra(EXTRA_RECORDATORIO_ID, id)
            putExtra(EXTRA_TITLE, texto)
            putExtra(EXTRA_EARLY, early)
        }
        return PendingIntent.getBroadcast(
            context,
            recordatorioRequestCode(id, early),
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
