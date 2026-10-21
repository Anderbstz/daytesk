package com.nuitcode.daytesk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Two-phase snooze for recordatorio notifications.
 *
 * - [ACTION_SNOOZE] (notification action tap): schedules exactly one alarm
 *   10 minutes later through [ReminderScheduler.scheduleSnooze].
 * - [ACTION_SNOOZE_FIRE] (the alarm firing): re-posts the notification from the
 *   Intent extras. It deliberately does NOT read Room: the at-due roll deletes
 *   the original row, so a database lookup here would find nothing and the
 *   snoozed reminder would be silently mute.
 */
class SnoozeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SNOOZE = "com.nuitcode.daytesk.SNOOZE"
        const val ACTION_SNOOZE_FIRE = "com.nuitcode.daytesk.SNOOZE_FIRE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L)
        if (id < 0L) return
        val texto = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        when (intent.action) {
            ACTION_SNOOZE -> ReminderScheduler.scheduleSnooze(context, id, texto)
            ACTION_SNOOZE_FIRE -> if (NotificationPreferences.isEnabled(context)) {
                // Reuse the at-due notification id so the repost replaces the
                // existing notification instead of stacking a second copy.
                NotificationHelper.showRecordatorio(context, texto, id, early = false)
            }
        }
    }
}
