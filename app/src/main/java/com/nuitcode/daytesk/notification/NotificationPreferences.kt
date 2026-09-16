package com.nuitcode.daytesk.notification

import android.content.Context
import com.nuitcode.daytesk.auth.SessionStore

/**
 * Single read point for the "notifications enabled" user toggle.
 *
 * The value lives in [SessionStore] (device-local `SharedPreferences`); this
 * accessor keeps the notification layer from depending on the auth layer's
 * storage details and gives scheduling code one obvious call site.
 */
object NotificationPreferences {
    fun isEnabled(context: Context): Boolean = SessionStore(context).notificationsEnabled
}
