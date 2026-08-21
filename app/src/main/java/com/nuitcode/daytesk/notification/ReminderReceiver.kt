package com.nuitcode.daytesk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nuitcode.daytesk.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
}
