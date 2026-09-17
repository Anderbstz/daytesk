package com.nuitcode.daytesk.auth

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        private set(value) { prefs.edit().putString(KEY_TOKEN, value).apply() }

    var displayName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        private set(value) { prefs.edit().putString(KEY_NAME, value).apply() }

    var email: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        private set(value) { prefs.edit().putString(KEY_EMAIL, value).apply() }

    /**
     * Whether local reminders are allowed to schedule and show. Defaults to
     * `true` and is preserved by [clear], so logging out does not silently
     * re-enable notifications the user turned off.
     */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply() }

    /**
     * `true` while a local change has not been confirmed by the server.
     *
     * Set before a push starts and cleared only after it succeeds, so a change
     * made while the push fails (or while the process dies mid-push) is never
     * overwritten by the next pull.
     */
    var hasPendingPush: Boolean
        get() = prefs.getBoolean(KEY_PENDING_PUSH, false)
        set(value) { prefs.edit().putBoolean(KEY_PENDING_PUSH, value).apply() }

    /**
     * Message of the last failed sync, or `null` when the last attempt
     * succeeded.
     *
     * Sync failures used to be discarded by a bare `runCatching`, so a push that
     * failed looked identical to one that worked. Persisting the message makes
     * the failure observable (RESIL-003).
     */
    var lastSyncError: String?
        get() = prefs.getString(KEY_LAST_SYNC_ERROR, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_LAST_SYNC_ERROR)
            } else {
                editor.putString(KEY_LAST_SYNC_ERROR, value)
            }
            editor.apply()
        }

    val hadPreviousAccount: Boolean
        get() = prefs.getBoolean(KEY_HAD_ACCOUNT, false)

    fun save(
        token: String,
        displayName: String,
        email: String,
        newAccount: Boolean = false,
        keepLocal: Boolean = false,
    ) {
        val previous = prefs.getString(KEY_EMAIL, null)
            ?: prefs.getString(KEY_PREVIOUS_EMAIL, null)
        val switched = previous != null && !previous.equals(email, ignoreCase = true)
        val wipe = !keepLocal && (newAccount || switched)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NAME, displayName)
            .putString(KEY_EMAIL, email)
            .remove(KEY_PREVIOUS_EMAIL)
            .putBoolean(KEY_NEEDS_PULL, true)
            .putBoolean(KEY_WIPE_LOCAL, wipe)
            .putBoolean(KEY_KEEP_LOCAL, keepLocal)
            .putBoolean(KEY_HAD_ACCOUNT, true)
            .apply()
        applyToUser()
    }

    fun consumeNeedsPull(): Boolean {
        val needsPull = prefs.getBoolean(KEY_NEEDS_PULL, false)
        if (needsPull) {
            prefs.edit().putBoolean(KEY_NEEDS_PULL, false).apply()
        }
        return needsPull
    }

    fun consumeKeepLocal(): Boolean {
        val keep = prefs.getBoolean(KEY_KEEP_LOCAL, false)
        if (keep) {
            prefs.edit().putBoolean(KEY_KEEP_LOCAL, false).apply()
        }
        return keep
    }

    fun consumeWipeLocal(): Boolean {
        val wipe = prefs.getBoolean(KEY_WIPE_LOCAL, false)
        if (wipe) {
            prefs.edit().putBoolean(KEY_WIPE_LOCAL, false).apply()
        }
        return wipe
    }

    fun clear() {
        val hadAccount = hadPreviousAccount || isLoggedIn
        val previousEmail = prefs.getString(KEY_EMAIL, null)
        // Preserved across logout: the toggle is a device preference, not
        // account state (D6 in the quick-reminders design).
        val notificationsEnabled = notificationsEnabled
        prefs.edit()
            .clear()
            .putBoolean(KEY_HAD_ACCOUNT, hadAccount)
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, notificationsEnabled)
            .apply()
        if (!previousEmail.isNullOrBlank()) {
            prefs.edit().putString(KEY_PREVIOUS_EMAIL, previousEmail).apply()
        }
        applyToUser()
    }

    fun applyToUser() {
        com.nuitcode.daytesk.model.DayteskUser.displayName = if (isLoggedIn) displayName else ""
        com.nuitcode.daytesk.model.DayteskUser.email = if (isLoggedIn) email else ""
    }

    companion object {
        private const val PREFS = "daytesk_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_NEEDS_PULL = "needs_pull"
        private const val KEY_WIPE_LOCAL = "wipe_local"
        private const val KEY_KEEP_LOCAL = "keep_local"
        private const val KEY_HAD_ACCOUNT = "had_account"
        private const val KEY_PREVIOUS_EMAIL = "previous_email"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_PENDING_PUSH = "pending_push"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
    }
}
