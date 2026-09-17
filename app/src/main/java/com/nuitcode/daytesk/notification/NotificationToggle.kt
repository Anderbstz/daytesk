package com.nuitcode.daytesk.notification

import android.content.Context
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.local.RecordatorioDao
import com.nuitcode.daytesk.data.local.TareaDao
import com.nuitcode.daytesk.data.local.toDomain

/**
 * Applies the user's notifications toggle **immediately**.
 *
 * The scheduler already refuses to register alarms while the toggle is off
 * ([ReminderScheduler.setAlarm]), but that only governs the future: turning the
 * toggle off left the alarms that were already registered untouched, and turning
 * it back on rescheduled nothing, so pending reminders stayed silent until a
 * reboot or login re-registered them (RELI-001/RELI-002).
 *
 * This function closes both gaps:
 *   - enabling reschedules every pending tarea and every recordatorio;
 *   - disabling cancels the alarms that are already registered.
 *
 * The receiver-side gate remains the final guarantee: an alarm that fires before
 * it can be cancelled still shows nothing while the toggle is off.
 */
suspend fun applyNotificationsEnabled(
    context: Context,
    enabled: Boolean,
    tareaDao: TareaDao,
    recordatorioDao: RecordatorioDao,
) {
    SessionStore(context).notificationsEnabled = enabled
    if (enabled) {
        ReminderScheduler.reschedulePending(context, tareaDao.getAllOnce().map { it.toDomain() })
        ReminderScheduler.rescheduleRecordatorios(
            context,
            recordatorioDao.getAllOnce().map { it.toDomain() },
        )
    } else {
        tareaDao.getAllOnce().forEach { ReminderScheduler.cancelTaskReminder(context, it.id) }
        recordatorioDao.getAllOnce().forEach { ReminderScheduler.cancelRecordatorio(context, it.id) }
        ReminderScheduler.cancelTaskReminder(context, ReminderScheduler.WEEKLY_REVIEW_ID)
    }
}
