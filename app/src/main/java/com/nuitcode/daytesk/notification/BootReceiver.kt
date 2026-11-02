package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.widget.NextTaskWidgetProvider
import com.nuitcode.daytesk.widget.RecordatorioWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms every alarm whenever the system may have dropped or downgraded them:
 * a reboot, an app update, or the exact-alarm grant changing (an alarm armed
 * while it was denied stays inexact until it is registered again).
 */
class BootReceiver : BroadcastReceiver() {
    private companion object {
        val REARM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in REARM_ACTIONS) return
        NotificationHelper.createNotificationChannel(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val tareas = database
                    .tareaDao()
                    .getAllTareas()
                    .first()
                    .map { it.toDomain() }
                ReminderScheduler.reschedulePending(context, tareas)
                val recordatorios = database
                    .recordatorioDao()
                    .getAllRecordatorios()
                    .first()
                    .map { it.toDomain() }
                ReminderScheduler.rescheduleRecordatorios(context, recordatorios)
                // Snoozes have no database row to reschedule from: they are
                // re-armed from the persisted registry (RESIL-001).
                ReminderScheduler.rearmSnoozes(context)
                NextTaskWidgetProvider.refresh(context)
                // Awaited: the recordatorio render must finish before the
                // goAsync slot is released, or a kill right after finish()
                // would drop it and leave the widget stale until the next tick.
                RecordatorioWidgetProvider.refreshNow(context)
            } finally {
                pending.finish()
            }
        }
    }
}
