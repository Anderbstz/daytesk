package com.nuitcode.daytesk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nuitcode.daytesk.data.local.AppDatabase
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
                pending.finish()
            }
        }
    }

    /**
     * Recordatorio alarm.
     *
     * **1h-before (`early = true`)**: notify and leave the row alone so the
     * at-due alarm still fires.
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

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val dao = database.recordatorioDao()
                val recordatorio = dao.getById(recordatorioId) ?: return@launch
                if (early) {
                    if (NotificationPreferences.isEnabled(context)) {
                        NotificationHelper.showRecordatorio(
                            context,
                            title.ifBlank { recordatorio.texto },
                            recordatorioId,
                            early = true,
                        )
                    }
                    return@launch
                }
                rollExpiredRecordatorios(context, database)
                if (dao.getById(recordatorioId) != null && NotificationPreferences.isEnabled(context)) {
                    NotificationHelper.showRecordatorio(
                        context,
                        title.ifBlank { recordatorio.texto },
                        recordatorioId,
                        early = false,
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
