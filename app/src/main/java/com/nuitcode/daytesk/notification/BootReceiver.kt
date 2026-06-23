package com.nuitcode.daytesk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            NotificationHelper.createNotificationChannel(context)
            // Pending reminders from the database will be re-scheduled
            // when the user next opens the app and the task list is loaded.
        }
    }
}
