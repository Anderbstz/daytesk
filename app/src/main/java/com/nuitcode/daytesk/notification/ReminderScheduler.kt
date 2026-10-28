package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.nuitcode.daytesk.MainActivity
import com.nuitcode.daytesk.model.Recordatorio
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado

object ReminderScheduler {
    private const val ACTION_REMINDER = "com.nuitcode.daytesk.REMINDER"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_RECORDATORIO_ID = "recordatorio_id"
    const val EXTRA_TITLE = "task_title"
    const val EXTRA_EARLY = "early"
    /** Carries the opt-in vibration-only alarm mode through every reminder path. */
    const val EXTRA_ES_ALARMA = "es_alarma"

    /**
     * The notify id of the notification whose snooze action was tapped. The
     * action carries it so the receiver cancels exactly that notification: the
     * 1h-early and at-due notifications use different notify ids.
     */
    const val EXTRA_NOTIFY_ID = "notify_id"
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

    /**
     * A snooze is a third, independent alarm slot for the same recordatorio.
     * Bit 31 is the only free flag after [RECORDATORIO_OFFSET] and
     * [EARLY_FLIP], so a snooze can never collide with a task, due or early
     * alarm. The resulting request code is negative by design.
     */
    internal const val SNOOZE_FLIP = Int.MIN_VALUE
    const val SNOOZE_DELAY_MS = 10 * 60 * 1000L
    private const val SNOOZE_ACTION_REQUEST_CODE = 5001

    /** Request code of the activity shown by the system alarm-clock indicator. */
    internal const val ALARM_SHOW_REQUEST_CODE = 6001

    /** Safety stop for a ringing alarm: the service dies after this delay. */
    const val RING_TIMEOUT_MS = 2 * 60 * 1000L
    internal const val RING_TIMEOUT_REQUEST_CODE = 7001

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
    fun scheduleRecordatorio(
        context: Context,
        id: Long,
        texto: String,
        fecha: Long,
        esAlarma: Boolean = false,
    ) {
        cancelRecordatorio(context, id)
        val now = System.currentTimeMillis()
        if (fecha > now) {
            val due = recordatorioPendingIntent(context, id, texto, early = false, esAlarma = esAlarma)
            if (isAlarmClockSlot(esAlarma, early = false)) {
                setAlarmClock(context, fecha, due, id)
            } else {
                setAlarm(context, fecha, due)
            }
        }
        val earlyAt = fecha - HOUR_MS
        if (earlyAt > now) {
            // An alarm-mode reminder rings the full alarm at T-1h too, so its
            // early fire needs the same alarm-clock arm as the at-due one: the
            // foreground-service start exemption comes from that arm.
            val early =
                recordatorioPendingIntent(context, id, texto, early = true, esAlarma = esAlarma)
            if (isAlarmClockSlot(esAlarma, early = true)) {
                setAlarmClock(context, earlyAt, early, id)
            } else {
                setAlarm(context, earlyAt, early)
            }
        }
    }

    /**
     * In alarm mode BOTH the T-1h and the at-due occurrence are armed as system
     * alarm clocks; non-alarm reminders keep plain alarms for both slots.
     */
    internal fun isAlarmClockSlot(esAlarma: Boolean, early: Boolean): Boolean = esAlarma

    fun cancelRecordatorio(context: Context, id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(recordatorioPendingIntent(context, id, "", early = false))
        alarmManager.cancel(recordatorioPendingIntent(context, id, "", early = true))
    }

    /**
     * Arms the 2-minute safety stop for a ringing alarm.
     *
     * Deliberately bypasses the notifications toggle: a stop must never depend
     * on a user preference, or the vibration could run forever.
     */
    fun setRingTimeout(context: Context, id: Long) {
        val triggerAt = System.currentTimeMillis() + RING_TIMEOUT_MS
        scheduleExactAt(context, triggerAt, ringTimeoutPendingIntent(context, id, ""))
    }

    fun cancelRingTimeout(context: Context, id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(ringTimeoutPendingIntent(context, id, ""))
    }

    /**
     * Schedules the single alarm behind a 10-minute snooze. It goes through
     * [setAlarm], so the notifications toggle gate and the exact-alarm fallback
     * apply exactly as they do to regular reminder alarms.
     *
     * A successfully scheduled snooze is mirrored in [SnoozeStore]; the alarm
     * is the only copy left once the at-due roll deletes the row, so the
     * registry is what lets [BootReceiver] re-arm it after a reboot.
     */
    fun scheduleSnooze(
        context: Context,
        id: Long,
        texto: String,
        delayMillis: Long = SNOOZE_DELAY_MS,
        esAlarma: Boolean = false,
    ) {
        val triggerAt = System.currentTimeMillis() + delayMillis.coerceAtLeast(0L)
        if (setAlarm(context, triggerAt, snoozeAlarmPendingIntent(context, id, texto, esAlarma))) {
            SnoozeStore.save(context, id, texto, triggerAt, esAlarma)
        }
    }

    /**
     * Cancels only the snooze slot and forgets its persisted entry. This is
     * intentionally separate from [cancelRecordatorio]: [scheduleRecordatorio]
     * re-arms reminders on every app start, so folding the snooze in would
     * silently drop a pending snooze. It belongs only on identity-invalidating
     * paths (edit, delete, roll).
     */
    fun cancelSnooze(context: Context, id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(snoozeAlarmPendingIntent(context, id, ""))
        SnoozeStore.remove(context, id)
    }

    /**
     * Cancels every pending snooze and clears the persisted registry.
     *
     * Snooze notifications re-post from Intent extras only, so an alarm armed
     * under account A would still show A's reminder text after switching to
     * account B or logging out. Every identity-invalidating path calls this.
     */
    fun cancelAllSnoozes(context: Context) {
        SnoozeStore.all(context).forEach { snooze ->
            cancelSnooze(context, snooze.id)
        }
        SnoozeStore.clear(context)
    }

    /**
     * Re-arms every persisted snooze that is still in the future and drops the
     * ones whose trigger time passed while the device was off.
     *
     * Called by [BootReceiver]: unlike tareas and recordatorios, a snooze has
     * no database row to reschedule from.
     */
    fun rearmSnoozes(context: Context, now: Long = System.currentTimeMillis()) {
        SnoozeStore.all(context).forEach { snooze ->
            if (snooze.triggerAt <= now) {
                SnoozeStore.remove(context, snooze.id)
            } else {
                setAlarm(
                    context,
                    snooze.triggerAt,
                    snoozeAlarmPendingIntent(context, snooze.id, snooze.texto, snooze.esAlarma),
                )
            }
        }
    }

    /** Re-registers every recordatorio's alarms, e.g. after a reboot or login. */
    fun rescheduleRecordatorios(context: Context, recordatorios: List<Recordatorio>) {
        NotificationHelper.createNotificationChannel(context)
        recordatorios.forEach { recordatorio ->
            scheduleRecordatorio(
                context,
                recordatorio.id,
                recordatorio.texto,
                recordatorio.fecha,
                recordatorio.esAlarma,
            )
        }
    }

    /** Request code for a recordatorio alarm; kept in its own namespace. */
    internal fun recordatorioRequestCode(id: Long, early: Boolean): Int {
        val base = (id and RECORDATORIO_ID_MASK).toInt() or RECORDATORIO_OFFSET
        return if (early) base xor EARLY_FLIP else base
    }

    /** Request code for the recordatorio snooze slot (bit 31 flag). */
    internal fun recordatorioSnoozeRequestCode(id: Long): Int =
        recordatorioRequestCode(id, early = false) xor SNOOZE_FLIP

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
     * recordatorios — while the toggle is disabled. Returns whether the alarm
     * was actually registered, so callers can keep their persisted bookkeeping
     * in sync with the real schedule.
     */
    private fun setAlarm(context: Context, triggerAtMillis: Long, pending: PendingIntent): Boolean {
        if (!NotificationPreferences.isEnabled(context)) return false
        NotificationHelper.createNotificationChannel(context)
        scheduleExactAt(context, triggerAtMillis, pending)
        return true
    }

    /**
     * Registers an exact alarm when the OS allows it and degrades to an
     * inexact one otherwise. Shared by reminders and the ungated ring timeout.
     */
    private fun scheduleExactAt(context: Context, triggerAtMillis: Long, pending: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    /**
     * Arms a reminder alarm as an alarm clock, which is what makes MIUI/Doze
     * treat it like the system clock and grants the foreground-service start
     * exemption. Reuses the caller's broadcast PendingIntent so the request-code
     * namespace, [cancelRecordatorio] and boot re-arming keep working unchanged.
     * Without the exact-alarm grant it degrades to a plain alarm instead of
     * throwing.
     */
    private fun setAlarmClock(
        context: Context,
        triggerAtMillis: Long,
        pending: PendingIntent,
        id: Long,
    ) {
        if (!NotificationPreferences.isEnabled(context)) return
        NotificationHelper.createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            scheduleExactAt(context, triggerAtMillis, pending)
            return
        }
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, alarmShowIntent(context, id)),
            pending,
        )
    }

    /** Activity surface behind the system alarm-clock indicator tap. */
    private fun alarmShowIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data = Uri.parse("daytesk://recordatorio/$id/show")
            putExtra(EXTRA_RECORDATORIO_ID, id)
        }
        return PendingIntent.getActivity(
            context,
            ALARM_SHOW_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ringTimeoutPendingIntent(
        context: Context,
        id: Long,
        texto: String,
    ): PendingIntent {
        val intent = Intent(context, RingTimeoutReceiver::class.java).apply {
            action = RingTimeoutReceiver.ACTION_RING_TIMEOUT
            data = Uri.parse("daytesk://recordatorio/$id/timeout")
            putExtra(EXTRA_RECORDATORIO_ID, id)
            putExtra(EXTRA_TITLE, texto)
        }
        return PendingIntent.getBroadcast(
            context,
            RING_TIMEOUT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
        esAlarma: Boolean = false,
    ): PendingIntent {
        val kind = if (early) "early" else "due"
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("daytesk://recordatorio/$id/$kind")
            putExtra(EXTRA_RECORDATORIO_ID, id)
            putExtra(EXTRA_TITLE, texto)
            putExtra(EXTRA_EARLY, early)
            putExtra(EXTRA_ES_ALARMA, esAlarma)
        }
        return PendingIntent.getBroadcast(
            context,
            recordatorioRequestCode(id, early),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * PendingIntent behind the snooze alarm. It carries the text so the fired
     * snooze can re-post without reading Room (the row may already be gone).
     */
    private fun snoozeAlarmPendingIntent(
        context: Context,
        id: Long,
        texto: String,
        esAlarma: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_SNOOZE_FIRE
            data = Uri.parse("daytesk://recordatorio/$id/snooze")
            putExtra(EXTRA_RECORDATORIO_ID, id)
            putExtra(EXTRA_TITLE, texto)
            putExtra(EXTRA_ES_ALARMA, esAlarma)
        }
        return PendingIntent.getBroadcast(
            context,
            recordatorioSnoozeRequestCode(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * PendingIntent behind the notification's "Snooze" action. All snooze
     * actions share one request code; their distinct data Uris keep the
     * PendingIntent identities apart.
     *
     * [notifyId] travels as an extra so [SnoozeReceiver] can dismiss the exact
     * notification the user tapped (the early and at-due notifications have
     * different notify ids).
     */
    fun snoozeActionPendingIntent(
        context: Context,
        id: Long,
        texto: String,
        notifyId: Int = recordatorioRequestCode(id, early = false),
        esAlarma: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_SNOOZE
            data = Uri.parse("daytesk://recordatorio/$id/snooze-action")
            putExtra(EXTRA_RECORDATORIO_ID, id)
            putExtra(EXTRA_TITLE, texto)
            putExtra(EXTRA_NOTIFY_ID, notifyId)
            putExtra(EXTRA_ES_ALARMA, esAlarma)
        }
        return PendingIntent.getBroadcast(
            context,
            SNOOZE_ACTION_REQUEST_CODE,
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
