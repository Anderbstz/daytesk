package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * RISK-001 — pending snoozes must die with the identity that armed them.
 *
 * A snooze re-posts from Intent extras and never reads Room, so an alarm left
 * behind by a logout or account switch still shows the previous account's
 * reminder text. [ReminderScheduler.cancelAllSnoozes] must both cancel the
 * registered alarms and clear the persisted registry.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SnoozeCancellationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daytesk_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        SnoozeStore.clear(context)
    }

    private fun snoozeAlarms() =
        shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms
            .filter {
                shadowOf(it.operation).savedIntent.data.toString().endsWith("/snooze")
            }

    @Test
    fun cancelAllSnoozes_cancelsEveryRegisteredAlarmAndClearsTheRegistry() {
        ReminderScheduler.scheduleSnooze(context, id = 51L, texto = "Account A reminder")
        ReminderScheduler.scheduleSnooze(context, id = 52L, texto = "Account A reminder 2")
        assertEquals(2, snoozeAlarms().size)
        assertEquals(2, SnoozeStore.all(context).size)

        ReminderScheduler.cancelAllSnoozes(context)

        assertTrue("every snooze alarm must be cancelled", snoozeAlarms().isEmpty())
        assertTrue("the registry must be empty", SnoozeStore.all(context).isEmpty())
    }

    @Test
    fun cancelAllSnoozes_isSafeWithNothingPending() {
        ReminderScheduler.cancelAllSnoozes(context)

        assertTrue(snoozeAlarms().isEmpty())
        assertTrue(SnoozeStore.all(context).isEmpty())
    }
}
