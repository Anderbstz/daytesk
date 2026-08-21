package com.nuitcode.daytesk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.widget.NextTaskWidgetProvider
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
                val tareas = AppDatabase.getInstance(context)
                    .tareaDao()
                    .getAllTareas()
                    .first()
                    .map { it.toDomain() }
                ReminderScheduler.reschedulePending(context, tareas)
                NextTaskWidgetProvider.refresh(context)
                NextTaskWidgetProvider.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
