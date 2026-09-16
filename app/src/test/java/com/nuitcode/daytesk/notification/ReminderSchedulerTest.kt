package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
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
