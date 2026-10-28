package com.nuitcode.daytesk.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
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
    fun bothOccurrencesOfAnEnabledAlarmModeReminderStartTheRing() {
        assertTrue(
            "the at-due alarm of an alarm-mode reminder must ring",
            shouldStartAlarmVibration(early = false, esAlarma = true, notificationsEnabled = true),
        )
        assertTrue(
            "the T-1h alarm of an alarm-mode reminder must ring too",
            shouldStartAlarmVibration(early = true, esAlarma = true, notificationsEnabled = true),
        )
        assertTrue(
            "a Stage 1 reminder must never ring",
            !shouldStartAlarmVibration(early = false, esAlarma = false, notificationsEnabled = true),
        )
        assertTrue(
            "a Stage 1 reminder must never ring early either",
            !shouldStartAlarmVibration(early = true, esAlarma = false, notificationsEnabled = true),
        )
        assertTrue(
            "a disabled toggle must suppress the ring",
            !shouldStartAlarmVibration(early = false, esAlarma = true, notificationsEnabled = false),
        )
        assertTrue(
            "a disabled toggle must suppress the early ring too",
            !shouldStartAlarmVibration(early = true, esAlarma = true, notificationsEnabled = false),
        )
    }

    @Test
    fun earlyAlarmModeHeadsUpIsSkippedOnceTheRingHasSurfaced() {
        assertTrue(
            "an alarm-mode early fire whose ring started must not also post the heads-up",
            !shouldPostEarlyRecordatorioNotification(
                esAlarma = true,
                alreadySurfaced = true,
                notificationsEnabled = true,
            ),
        )
        assertTrue(
            "the fallback notification already covers an alarm-mode early fire",
            !shouldPostEarlyRecordatorioNotification(
                esAlarma = true,
                alreadySurfaced = true,
                notificationsEnabled = true,
            ),
        )
        assertTrue(
            "a non-alarm reminder keeps its early heads-up",
            shouldPostEarlyRecordatorioNotification(
                esAlarma = false,
                alreadySurfaced = false,
                notificationsEnabled = true,
            ),
        )
        assertTrue(
            "a disabled toggle suppresses the early heads-up",
            !shouldPostEarlyRecordatorioNotification(
                esAlarma = false,
                alreadySurfaced = false,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    @Config(sdk = [30])
    fun alarmMode_startStartsTheVibrationService() {
        val launched = mutableListOf<Intent>()
        val previous = AlarmVibrationService.startService
        AlarmVibrationService.startService = { _, intent -> launched += intent }
        try {
            val started = startAlarmVibrationOrFallback(context, 77L, "Wake up")
            assertTrue("the alarm-mode due reminder must ring", started)
        } finally {
            AlarmVibrationService.startService = previous
        }

        assertEquals("the service must be started exactly once", 1, launched.size)
        assertEquals(
            "the service must receive the reminder id",
            77L,
            launched.single().getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L),
        )
        assertTrue(
            "the ringing reminder must not also post the Stage 1 notification",
            postedNotifications().isEmpty(),
        )
    }

    @Test
    @Config(sdk = [30])
    fun serviceStartFailure_fallsBackToTheStage1Notification() {
        val previous = AlarmVibrationService.startService
        AlarmVibrationService.startService = { _, _ -> throw SecurityException("blocked") }
        try {
            val started = startAlarmVibrationOrFallback(context, 77L, "Wake up")
            assertTrue("the caller must observe that the service did not start", !started)
        } finally {
            AlarmVibrationService.startService = previous
        }

        val posted = postedNotifications().single()
        assertEquals(NotificationHelper.CHANNEL_ID_ALARM, posted.channelId)
        assertEquals(NotificationCompat.CATEGORY_ALARM, posted.category)
        assertEquals(
            "the fallback must still surface the reminder",
            "Recordatorio: Wake up",
            posted.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString(),
        )
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
