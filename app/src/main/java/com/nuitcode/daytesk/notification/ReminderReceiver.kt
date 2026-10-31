package com.nuitcode.daytesk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.notifyExpiredRecordatorio
import com.nuitcode.daytesk.data.rollExpiredRecordatorios
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recordatorioId = intent.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L)
        if (recordatorioId >= 0L) {
            onRecordatorioAlarm(context, intent, recordatorioId)
            return
        }
        onTaskAlarm(context, intent)
    }

    private fun onTaskAlarm(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        val early = intent.getBooleanExtra(ReminderScheduler.EXTRA_EARLY, false)
        // The toggle governs the show path, not only the schedule path: an alarm
        // that fires while notifications are off (because it could not be
        // cancelled, or was registered before the toggle changed) stays silent.
        if (!NotificationPreferences.isEnabled(context)) return
        if (taskId == ReminderScheduler.WEEKLY_REVIEW_ID) {
            NotificationHelper.showTaskReminder(context, title, taskId)
            ReminderScheduler.scheduleWeeklyReview(context)
            return
        }
        if (taskId < 0L) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tarea = AppDatabase.getInstance(context).tareaDao().getTareaById(taskId)
                if (tarea != null && tarea.estado != "COMPLETADA") {
                    NotificationHelper.showTaskReminder(context, title, taskId, early)
                }
            } finally {
                pending?.finish()
            }
        }
    }

    /**
     * Recordatorio alarm.
     *
     * **1h-before (`early = true`)**: notify and leave the row alone so the
     * at-due alarm still fires. In alarm mode the ring itself is the heads-up,
     * so the separate early notification is skipped once the alarm surfaced
     * (see [shouldPostEarlyRecordatorioNotification]).
     *
     * **At-due (`early = false`)**: rolling the series is what notifies — the
     * roll surfaces every just-due occurrence before removing it. This makes
     * the in-app sweep and this alarm interchangeable: whichever runs first
     * notifies the user, the other finds nothing to roll. A due reminder can
     * never be deleted without a notification being requested (RESIL-005). If
     * the alarm fires a few milliseconds before `fecha`, the row survives the
     * roll and is notified directly here.
     *
     * Both paths are gated by the notifications toggle, so a disabled toggle
     * governs showing as well as scheduling.
     */
    private fun onRecordatorioAlarm(context: Context, intent: Intent, recordatorioId: Long) {
        val early = intent.getBooleanExtra(ReminderScheduler.EXTRA_EARLY, false)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val esAlarma = intent.getBooleanExtra(ReminderScheduler.EXTRA_ES_ALARMA, false)

        // Alarm mode must be started synchronously in `onReceive`: the
        // foreground-service window closes as soon as the receiver returns.
        // The flag travels in the Intent precisely so this needs no Room read.
        var ringingStarted = false
        var fallbackPosted = false
        if (shouldStartAlarmVibration(early, esAlarma, NotificationPreferences.isEnabled(context))) {
            ringingStarted = startAlarmVibrationOrFallback(context, recordatorioId, title)
            fallbackPosted = !ringingStarted
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val dao = database.recordatorioDao()
                val recordatorio = dao.getById(recordatorioId) ?: return@launch
                if (early) {
                    if (shouldPostEarlyRecordatorioNotification(
                            esAlarma = esAlarma || recordatorio.esAlarma,
                            alreadySurfaced = ringingStarted || fallbackPosted,
                            notificationsEnabled = NotificationPreferences.isEnabled(context),
                        )
                    ) {
                        NotificationHelper.showRecordatorio(
                            context,
                            title.ifBlank { recordatorio.texto },
                            recordatorioId,
                            early = true,
                            esAlarma = recordatorio.esAlarma,
                        )
                    }
                    return@launch
                }
                if (ringingStarted || fallbackPosted) {
                    // Stamp the row before the sweep runs so the same due event
                    // cannot notify twice (alarm + in-app roll).
                    dao.update(recordatorio.copy(notificadaEn = System.currentTimeMillis()))
                }
                rollExpiredRecordatorios(
                    context,
                    database,
                    notifyDue = { entity ->
                        val alreadySurfaced = entity.notificadaEn != null ||
                            (entity.id == recordatorioId && (ringingStarted || fallbackPosted))
                        if (!alreadySurfaced) {
                            notifyExpiredRecordatorio(context, entity)
                        }
                    },
                )
                if (dao.getById(recordatorioId) != null &&
                    NotificationPreferences.isEnabled(context) &&
                    !(esAlarma && (ringingStarted || fallbackPosted))
                ) {
                    NotificationHelper.showRecordatorio(
                        context,
                        title.ifBlank { recordatorio.texto },
                        recordatorioId,
                        early = false,
                        esAlarma = esAlarma || recordatorio.esAlarma,
                    )
                }
            } finally {
                pending?.finish()
            }
        }
    }
}

/**
 * Alarm mode applies to both the T-1h and the at-due occurrence, and only while
 * the user has notifications enabled. Both fires ring the full vibration alarm.
 */
internal fun shouldStartAlarmVibration(
    early: Boolean,
    esAlarma: Boolean,
    notificationsEnabled: Boolean,
): Boolean = esAlarma && notificationsEnabled

/**
 * In alarm mode the ring (or its fallback notification) already surfaces the
 * reminder, so the early fire must not also post the separate "En 1 hora"
 * notification. Non-alarm reminders keep their early heads-up unchanged, and a
 * disabled toggle still suppresses it.
 */
internal fun shouldPostEarlyRecordatorioNotification(
    esAlarma: Boolean,
    alreadySurfaced: Boolean,
    notificationsEnabled: Boolean,
): Boolean = notificationsEnabled && !(esAlarma && alreadySurfaced)

/**
 * Starts the vibration ring; when the platform rejects it (or the device has no
 * vibrator) the Stage 1 alarm notification posts instead. Returns whether the
 * service started. Never leaves the user with nothing.
 */
internal fun startAlarmVibrationOrFallback(context: Context, id: Long, texto: String): Boolean {
    if (AlarmVibrationService.start(context, id, texto)) return true
    NotificationHelper.showRecordatorio(context, texto, id, early = false, esAlarma = true)
    return false
}
