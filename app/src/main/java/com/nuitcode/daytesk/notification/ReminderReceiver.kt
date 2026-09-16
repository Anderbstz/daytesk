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
     * On the at-due alarm the series is rolled, so a recurring reminder keeps
     * advancing even when the app is never opened. This is idempotent with the
     * in-app sweep: whichever path runs first deletes the expired row, the
     * other finds nothing to roll. The toggle is re-checked before showing, so
     * a reminder disabled after it was scheduled stays silent.
     */
    private fun onRecordatorioAlarm(context: Context, intent: Intent, recordatorioId: Long) {
        val early = intent.getBooleanExtra(ReminderScheduler.EXTRA_EARLY, false)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).recordatorioDao()
                val recordatorio = dao.getById(recordatorioId) ?: return@launch
                if (!early) {
                    rollExpiredRecordatorios(context, dao, System.currentTimeMillis())
                }
                if (NotificationPreferences.isEnabled(context)) {
                    NotificationHelper.showRecordatorio(
                        context,
                        title.ifBlank { recordatorio.texto },
                        recordatorioId,
                        early,
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
