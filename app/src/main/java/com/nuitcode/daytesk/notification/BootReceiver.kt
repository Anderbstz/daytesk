package com.nuitcode.daytesk.notification

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

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
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
