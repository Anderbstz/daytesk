package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.auth.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Strict TDD — RED tests for the two-phase snooze, written before
 * [SnoozeReceiver] and [ReminderScheduler.scheduleSnooze] exist.
 *
 * Contract (see sdd/alarm-reminders/spec, "Snooze reschedules one alarm"):
 *   - tapping "Snooze" schedules exactly ONE alarm 10 minutes later;
 *   - the toggle gates the snooze path;
 *   - the fired snooze re-posts from the Intent extras and never reads Room
 *     (the at-due roll deletes the original row, so a DB lookup would be mute).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SnoozeReceiverTest {

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

    private fun alarmManager() =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun notificationManager() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun alarms() = shadowOf(alarmManager()).scheduledAlarms

    private fun postedNotifications() = shadowOf(notificationManager()).allNotifications

    private fun notificationById(id: Long) =
        shadowOf(notificationManager())
            .getNotification(ReminderScheduler.recordatorioRequestCode(id, early = false))

    private fun actionIntent(id: Long, texto: String = "Call mom") =
        Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_SNOOZE
            putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, id)
            putExtra(ReminderScheduler.EXTRA_TITLE, texto)
        }

    private fun fireIntent(id: Long, texto: String = "Call mom") =
        Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_SNOOZE_FIRE
            putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, id)
            putExtra(ReminderScheduler.EXTRA_TITLE, texto)
        }

    private fun dataOf(pendingIntent: PendingIntent) =
        shadowOf(pendingIntent).savedIntent.data.toString()

    @Test
    fun snoozeAction_schedulesExactlyOneAlarmTenMinutesLater() {
        val before = System.currentTimeMillis()

        SnoozeReceiver().onReceive(context, actionIntent(7L))

        val alarm = alarms().single()
        assertEquals("daytesk://recordatorio/7/snooze", dataOf(alarm.operation))
        assertEquals(
            ReminderScheduler.recordatorioSnoozeRequestCode(7L),
            shadowOf(alarm.operation).requestCode,
        )
        val expected = before + ReminderScheduler.SNOOZE_DELAY_MS
        assertTrue(
            "the snooze must fire ~10 minutes later, got ${alarm.triggerAtMs}",
            kotlin.math.abs(alarm.triggerAtMs - expected) <= 5_000,
        )
        assertEquals("the snooze must not be a repeating alarm", 0L, alarm.intervalMs)
    }

    @Test
    fun snoozeAction_isSuppressedWhileNotificationsAreDisabled() {
        SessionStore(context).notificationsEnabled = false

        SnoozeReceiver().onReceive(context, actionIntent(7L))

        assertTrue("a disabled toggle must not schedule the snooze", alarms().isEmpty())
    }

    @Test
    fun snoozeFire_repostsFromIntentExtrasWithoutTouchingRoom() {
        // No row with id 4242 exists (and no database is created at all): the
        // repost must come from the extras, because the at-due roll deleted the
        // original row before the snooze fired.
        //
        // Deliberately start from NO posted notification: a no-op receiver
        // would leave the list empty and fail here, which is what the previous
        // version of this test could never detect.
        val id = 4242L
        val texto = "Distinctive snooze text 4242"
        assertTrue(
            "the test must start with no posted notification",
            postedNotifications().isEmpty(),
        )

        SnoozeReceiver().onReceive(context, fireIntent(id, texto))

        val posted = postedNotifications().single()
        assertEquals(
            "the repost must render the text carried by the Intent",
            "Recordatorio: $texto",
            posted.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString(),
        )
        assertEquals(
            "the repost must be alarm-style again",
            NotificationHelper.CHANNEL_ID_ALARM,
            posted.channelId,
        )
        assertEquals(NotificationCompat.CATEGORY_ALARM, posted.category)
        assertNotNull(
            "the repost must reuse the recordatorio at-due notification id",
            notificationById(id),
        )
    }

    @Test
    fun snoozeFire_replacesAnExistingNotificationWithTheSameIdInsteadOfStacking() {
        val id = 4343L
        NotificationHelper.showRecordatorio(context, "stale text", id, early = false)
        assertEquals(1, postedNotifications().size)

        SnoozeReceiver().onReceive(context, fireIntent(id, "fresh text"))

        assertEquals(
            "the snoozed repost must replace the old notification, not stack",
            1,
            postedNotifications().size,
        )
        val posted = notificationById(id)
        assertNotNull("the replacement must keep the at-due notification id", posted)
        assertEquals(
            "the existing notification must be updated with the fired text",
            "Recordatorio: fresh text",
            posted!!.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun snoozeActionOnThePostedRecordatorio_schedulesTheSnoozeAlarm() {
        NotificationHelper.showRecordatorio(context, "Call mom", 7L)
        val notification = postedNotifications().single()

        val broadcast = shadowOf(notification.actions.single().actionIntent).savedIntent
        SnoozeReceiver().onReceive(context, broadcast)

        val alarm = alarms().single()
        assertEquals("daytesk://recordatorio/7/snooze", dataOf(alarm.operation))
        assertEquals(
            ReminderScheduler.recordatorioSnoozeRequestCode(7L),
            shadowOf(alarm.operation).requestCode,
        )
    }

    @Test
    fun snoozeFire_isSuppressedWhileNotificationsAreDisabled() {
        SessionStore(context).notificationsEnabled = false

        SnoozeReceiver().onReceive(context, fireIntent(4242L))

        assertTrue("a disabled toggle must suppress the repost", postedNotifications().isEmpty())
    }

    @Test
    fun intentWithoutRecordatorioId_isIgnored() {
        SnoozeReceiver().onReceive(
            context,
            Intent(context, SnoozeReceiver::class.java).apply {
                action = SnoozeReceiver.ACTION_SNOOZE
            },
        )

        assertTrue(alarms().isEmpty())
    }
}
