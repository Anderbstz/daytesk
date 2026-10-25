package com.nuitcode.daytesk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Two-phase snooze for recordatorio notifications.
 *
 * - [ACTION_SNOOZE] (notification action tap): dismisses the tapped
 *   notification — the only visible feedback a tap gets — and then schedules
 *   exactly one alarm 10 minutes later through
 *   [ReminderScheduler.scheduleSnooze].
 * - [ACTION_SNOOZE_FIRE] (the alarm firing): re-posts the notification from the
 *   Intent extras. It deliberately does NOT read Room: the at-due roll deletes
 *   the original row, so a database lookup here would find nothing and the
 *   snoozed reminder would be silently mute.
 *
 * Repost invariant: the source notification is always cancelled when the user
 * taps snooze, and the fired snooze posts on the stable at-due notify id. The
 * early/due notifications share that single visible slot after a snooze, so no
 * duplicate can remain in the shade.
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
            ACTION_SNOOZE -> {
                // Cancel first: if scheduling fails, the user still sees the
                // tap acknowledged instead of a notification that looks dead.
                dismissTappedNotification(context, intent)
                ReminderScheduler.scheduleSnooze(context, id, texto)
            }
            ACTION_SNOOZE_FIRE -> {
                // The alarm fired, so it is no longer pending in either the
                // alarm manager or the persisted registry.
                SnoozeStore.remove(context, id)
                if (NotificationPreferences.isEnabled(context)) {
                    // Repost on the stable at-due notification id: the tapped
                    // source was already dismissed, so nothing can stack.
                    NotificationHelper.showRecordatorio(context, texto, id, early = false)
                }
            }
        }
    }

    /**
     * Cancels exactly the notification whose action was tapped. The action
     * carries the source notify id because the 1h-early notification uses a
     * different id than the at-due one.
     */
    private fun dismissTappedNotification(context: Context, intent: Intent) {
        val notifyId = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIFY_ID, Int.MIN_VALUE)
        if (notifyId == Int.MIN_VALUE) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        manager.cancel(notifyId)
    }
}
