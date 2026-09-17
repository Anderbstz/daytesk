package com.nuitcode.daytesk.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.auth.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * RESIL-007 / RELI-002: the notifications toggle governs the **show** path for
 * task reminders too.
 *
 * The weekly-review alarm is the synchronous task branch of [ReminderReceiver],
 * which makes it the one task-notification path observable without waiting on
 * `goAsync`. Before the fix it posted a notification regardless of the toggle.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ReminderReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daytesk_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun postedNotifications() =
        shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .allNotifications

    private fun weeklyReviewIntent() = Intent(context, ReminderReceiver::class.java).apply {
        putExtra(ReminderScheduler.EXTRA_TASK_ID, ReminderScheduler.WEEKLY_REVIEW_ID)
        putExtra(ReminderScheduler.EXTRA_TITLE, "Revisión semanal")
    }

    @Test
    fun taskAlarm_isSilentWhileNotificationsAreDisabled() {
        SessionStore(context).notificationsEnabled = false

        ReminderReceiver().onReceive(context, weeklyReviewIntent())

        assertTrue(
            "a task alarm must not post a notification while the toggle is off",
            postedNotifications().isEmpty(),
        )
    }

    @Test
    fun taskAlarm_postsWhileNotificationsAreEnabled() {
        SessionStore(context).notificationsEnabled = true

        ReminderReceiver().onReceive(context, weeklyReviewIntent())

        assertEquals(
            "a task alarm must still post while the toggle is on",
            1,
            postedNotifications().size,
        )
    }
}
