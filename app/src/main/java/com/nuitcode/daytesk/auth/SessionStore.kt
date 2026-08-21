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

    fun save(token: String, displayName: String, email: String, newAccount: Boolean = false) {
        val previous = prefs.getString(KEY_EMAIL, null)
        val switched = previous != null && !previous.equals(email, ignoreCase = true)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NAME, displayName)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_NEEDS_PULL, true)
            .putBoolean(KEY_WIPE_LOCAL, newAccount || switched)
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

    fun consumeWipeLocal(): Boolean {
        val wipe = prefs.getBoolean(KEY_WIPE_LOCAL, false)
        if (wipe) {
            prefs.edit().putBoolean(KEY_WIPE_LOCAL, false).apply()
        }
        return wipe
    }

    fun clear() {
        prefs.edit().clear().apply()
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
    }
}
