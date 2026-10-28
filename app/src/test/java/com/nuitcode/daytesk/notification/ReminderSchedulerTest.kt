package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.auth.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Strict TDD — RED test written before `ReminderScheduler.scheduleRecordatorio`
 * / `cancelRecordatorio` and the disabled-toggle gate exist.
 *
 * Contract (see sdd/quick-reminders/spec + design, "Notification design"):
 *   - Delete cancels notifications -> both alarms are cancelled
 *   - Both reminders              -> `fecha > now + 1h` schedules 2 alarms
 *   - Only the due reminder       -> `fecha < now + 1h` schedules 1 alarm
 *   - Disabled skips scheduling   -> toggle off schedules 0 alarms
 *   - Request-code isolation      -> a recordatorio and a task with the same
 *                                    numeric id never share an alarm identity
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ReminderSchedulerTest {

    private val hour = 60 * 60 * 1000L
    private val minute = 60 * 1000L

    private lateinit var context: Context
    private lateinit var shadowAlarm: ShadowAlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daytesk_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarm = shadowOf(alarmManager)
    }

    private fun alarms() = shadowAlarm.scheduledAlarms
    private fun requestCode(pendingIntent: PendingIntent) = shadowOf(pendingIntent).requestCode
    private fun dataOf(pendingIntent: PendingIntent) = shadowOf(pendingIntent).savedIntent.data.toString()

    @Test
    fun scheduleRecordatorio_moreThanOneHourAhead_schedulesBothReminders() {
        val now = System.currentTimeMillis()

        ReminderScheduler.scheduleRecordatorio(
            context,
            id = 5L,
            texto = "Call mom",
            fecha = now + 3 * hour,
        )

        assertEquals("a reminder more than 1h ahead needs both alarms", 2, alarms().size)
        assertEquals(
            setOf("daytesk://recordatorio/5/due", "daytesk://recordatorio/5/early"),
            alarms().map { dataOf(it.operation) }.toSet(),
        )
    }

    @Test
    fun scheduleRecordatorio_lessThanOneHourAhead_schedulesOnlyTheDueReminder() {
        val now = System.currentTimeMillis()

        ReminderScheduler.scheduleRecordatorio(
            context,
            id = 7L,
            texto = "Water plants",
            fecha = now + 10 * minute,
        )

        assertEquals("the 1h-before alarm is already in the past", 1, alarms().size)
        assertEquals("daytesk://recordatorio/7/due", dataOf(alarms().single().operation))
    }

    @Test
    fun alarmMode_armsBothTheEarlyAndDueAlarmsAsAlarmClocks() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val now = System.currentTimeMillis()

        ReminderScheduler.scheduleRecordatorio(
            context,
            id = 13L,
            texto = "Wake up",
            fecha = now + 3 * hour,
            esAlarma = true,
        )

        val due = alarms().single { dataOf(it.operation) == "daytesk://recordatorio/13/due" }
        val early = alarms().single { dataOf(it.operation) == "daytesk://recordatorio/13/early" }
        assertNotNull("the alarm-mode due alarm must be an alarm clock", due.getAlarmClockInfo())
        assertNotNull(
            "the alarm-mode T-1h alarm must be an alarm clock too, or MIUI blocks its service start",
            early.getAlarmClockInfo(),
        )
        assertEquals(
            "the alarm clock must publish an action to open the reminder",
            ReminderScheduler.ALARM_SHOW_REQUEST_CODE,
            requestCode(due.showIntent!!),
        )
        assertEquals(
            "the early alarm clock must publish the same open action",
            ReminderScheduler.ALARM_SHOW_REQUEST_CODE,
            requestCode(early.showIntent!!),
        )
    }

    @Test
    fun nonAlarmMode_keepsBothAlarmsAsPlainAlarms() {
        val now = System.currentTimeMillis()

        ReminderScheduler.scheduleRecordatorio(
            context,
            id = 14L,
            texto = "Water plants",
            fecha = now + 3 * hour,
            esAlarma = false,
        )

        val due = alarms().single { dataOf(it.operation) == "daytesk://recordatorio/14/due" }
        val early = alarms().single { dataOf(it.operation) == "daytesk://recordatorio/14/early" }
        assertNull("only alarm-mode reminders may take over the system alarm clock", due.getAlarmClockInfo())
        assertNull("a non-alarm early reminder must stay a plain alarm", early.getAlarmClockInfo())
    }

    @Test
    fun setRingTimeout_armsAStopAlarmEvenWhileNotificationsAreDisabled() {
        SessionStore(context).notificationsEnabled = false
        val before = System.currentTimeMillis()

        ReminderScheduler.setRingTimeout(context, id = 15L)

        val timeout = alarms().single()
        assertEquals("daytesk://recordatorio/15/timeout", dataOf(timeout.operation))
        assertEquals(
            ReminderScheduler.RING_TIMEOUT_REQUEST_CODE,
            requestCode(timeout.operation),
        )
        val expected = before + ReminderScheduler.RING_TIMEOUT_MS
        assertTrue(
            "the safety stop must fire ~2 minutes later, got ${timeout.triggerAtMs}",
            kotlin.math.abs(timeout.triggerAtMs - expected) <= 5_000,
        )
    }

    @Test
    fun cancelRingTimeout_cancelsOnlyTheTimeoutAlarm() {
        val now = System.currentTimeMillis()
        ReminderScheduler.scheduleRecordatorio(context, 16L, "Wake up", now + 3 * hour, esAlarma = true)
        ReminderScheduler.setRingTimeout(context, 16L)
        assertEquals(3, alarms().size)

        ReminderScheduler.cancelRingTimeout(context, 16L)

        assertTrue(
            "no timeout alarm may remain",
            alarms().none { dataOf(it.operation).endsWith("/timeout") },
        )
        assertEquals("the reminder alarms must survive", 2, alarms().size)
    }

    @Test
    fun cancelRecordatorio_cancelsBothAlarms() {
        val now = System.currentTimeMillis()
        ReminderScheduler.scheduleRecordatorio(
            context,
            id = 9L,
            texto = "Dentist",
            fecha = now + 3 * hour,
        )
        assertEquals(2, alarms().size)

        ReminderScheduler.cancelRecordatorio(context, 9L)

        assertTrue("both recordatorio alarms must be cancelled", alarms().isEmpty())
    }

    @Test
    fun notificationsDisabled_schedulesNothing() {
        SessionStore(context).notificationsEnabled = false

        ReminderScheduler.scheduleRecordatorio(
            context,
            id = 4L,
            texto = "Gym",
            fecha = System.currentTimeMillis() + 3 * hour,
        )

        assertTrue("a disabled toggle must not schedule any alarm", alarms().isEmpty())
    }

    @Test
    fun snoozeRequestCode_isDistinctFromDueEarlyAndTaskCodes() {
        val id = 3L
        val snooze = ReminderScheduler.recordatorioSnoozeRequestCode(id)

        assertNotEquals(ReminderScheduler.recordatorioRequestCode(id, early = false), snooze)
        assertNotEquals(ReminderScheduler.recordatorioRequestCode(id, early = true), snooze)
        assertNotEquals("snooze must not reuse the task due slot", id.toInt(), snooze)
        assertNotEquals(
            "snooze must not reuse the task early slot",
            id.toInt() xor ReminderScheduler.EARLY_FLIP,
            snooze,
        )
    }

    @Test
    fun scheduleSnooze_registersOneAlarmInTheSnoozeSlot() {
        val before = System.currentTimeMillis()

        ReminderScheduler.scheduleSnooze(context, id = 6L, texto = "Call mom")

        val alarm = alarms().single()
        assertEquals(
            "a snooze is exactly one alarm",
            1,
            alarms().size,
        )
        assertEquals("daytesk://recordatorio/6/snooze", dataOf(alarm.operation))
        assertEquals(
            ReminderScheduler.recordatorioSnoozeRequestCode(6L),
            requestCode(alarm.operation),
        )
        val expected = before + ReminderScheduler.SNOOZE_DELAY_MS
        assertTrue(
            "the snooze must fire about 10 minutes later, got ${alarm.triggerAtMs}",
            kotlin.math.abs(alarm.triggerAtMs - expected) <= 5_000,
        )
        assertEquals("a snooze must never repeat", 0L, alarm.intervalMs)
    }

    @Test
    fun scheduleRecordatorio_doesNotCancelAPendingSnooze() {
        val now = System.currentTimeMillis()
        ReminderScheduler.scheduleSnooze(context, id = 8L, texto = "Call mom")

        ReminderScheduler.scheduleRecordatorio(context, 8L, "Call mom", now + 3 * hour)

        val snoozes = alarms().filter { dataOf(it.operation).endsWith("/snooze") }
        assertEquals(
            "re-arming a recordatorio must not silently drop a pending snooze",
            1,
            snoozes.size,
        )
        assertEquals(3, alarms().size)
    }

    @Test
    fun cancelRecordatorio_leavesAPendingSnoozeAlone() {
        val now = System.currentTimeMillis()
        ReminderScheduler.scheduleSnooze(context, id = 10L, texto = "Call mom")
        ReminderScheduler.scheduleRecordatorio(context, 10L, "Call mom", now + 3 * hour)

        ReminderScheduler.cancelRecordatorio(context, 10L)

        val remaining = alarms().single()
        assertEquals("daytesk://recordatorio/10/snooze", dataOf(remaining.operation))
    }

    @Test
    fun cancelSnooze_cancelsOnlyTheSnoozeAlarm() {
        val now = System.currentTimeMillis()
        ReminderScheduler.scheduleSnooze(context, id = 11L, texto = "Call mom")
        ReminderScheduler.scheduleRecordatorio(context, 11L, "Call mom", now + 3 * hour)
        assertEquals(3, alarms().size)

        ReminderScheduler.cancelSnooze(context, 11L)

        assertEquals("the due and early alarms must survive", 2, alarms().size)
        assertTrue(
            "no snooze alarm may remain",
            alarms().none { dataOf(it.operation).endsWith("/snooze") },
        )
    }

    @Test
    fun notificationsDisabled_suppressesSnooze() {
        SessionStore(context).notificationsEnabled = false

        ReminderScheduler.scheduleSnooze(context, id = 12L, texto = "Call mom")

        assertTrue("a disabled toggle must suppress the snooze", alarms().isEmpty())
    }

    @Test
    fun recordatorioAlarms_doNotCollideWithTaskAlarmsForTheSameId() {
        val now = System.currentTimeMillis()

        ReminderScheduler.scheduleIfDue(context, 1L, "Task", now + 3 * hour)
        val taskCodes = alarms().map { requestCode(it.operation) }.toSet()
        assertEquals("task scheduling sets both alarms", 2, taskCodes.size)

        ReminderScheduler.scheduleRecordatorio(context, 1L, "Reminder", now + 3 * hour)

        assertEquals(
            "task and recordatorio alarms must coexist as distinct entries",
            4,
            alarms().size,
        )
        val recordatorioAlarms = alarms().filter {
            dataOf(it.operation).startsWith("daytesk://recordatorio/")
        }
        assertEquals(2, recordatorioAlarms.size)
        recordatorioAlarms.forEach { alarm ->
            assertTrue(
                "request code ${requestCode(alarm.operation)} leaks into the task namespace",
                requestCode(alarm.operation) !in taskCodes,
            )
        }
    }
}
