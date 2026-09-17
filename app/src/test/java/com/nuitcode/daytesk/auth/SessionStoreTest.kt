package com.nuitcode.daytesk.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Strict TDD — RED test written before [SessionStore.notificationsEnabled] exists.
 *
 * Contract (see sdd/quick-reminders/spec, "Toggle persists"):
 *   - the notification toggle defaults to enabled
 *   - writing it survives a new [SessionStore] instance (i.e. it is persisted)
 *   - [SessionStore.clear] (logout) preserves it, so signing out does not
 *     silently re-enable notifications
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SessionStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daytesk_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun notificationsEnabled_defaultsToTrue() {
        assertTrue(
            "notifications must be enabled by default",
            SessionStore(context).notificationsEnabled,
        )
    }

    @Test
    fun notificationsEnabled_survivesANewInstance() {
        SessionStore(context).notificationsEnabled = false

        assertFalse(
            "a fresh SessionStore must read the persisted value",
            SessionStore(context).notificationsEnabled,
        )
    }

    @Test
    fun clear_preservesNotificationsEnabled() {
        val store = SessionStore(context)
        store.notificationsEnabled = false

        store.clear()

        assertFalse(
            "clear() must preserve the notifications toggle",
            SessionStore(context).notificationsEnabled,
        )
        assertFalse("clear() must still log the user out", SessionStore(context).isLoggedIn)
    }
}
