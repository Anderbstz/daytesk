package com.nuitcode.daytesk.widget

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
 * Strict TDD — RED test written before `RecordatorioWidgetProvider` gained its
 * repeating refresh tick.
 *
 * Contract (see sdd/recordatorios-ux review, RELI-002 / RESIL-001):
 *   - A placed widget registers exactly one repeating tick on enable.
 *   - The tick repeats (interval > 0), so a past-dated reminder cannot stay on
 *     screen until the next write.
 *   - Removing the widget cancels the tick.
 *   - The tick is independent of the notifications toggle: the widget is a
 *     read-only surface and must self-heal even when notifications are off.
 *   - The tick identity does not collide with the task widget's own tick.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RecordatorioWidgetTickTest {

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
    private fun actionOf(pendingIntent: PendingIntent) =
        shadowOf(pendingIntent).savedIntent.action

    @Test
    fun onEnabled_registersARepeatingTick() {
        RecordatorioWidgetProvider().onEnabled(context)

        assertEquals("a placed widget needs exactly one refresh tick", 1, alarms().size)
        val tick = alarms().single()
        assertEquals("the tick is wall-clock independent", AlarmManager.ELAPSED_REALTIME, tick.type)
        assertEquals(
            "the tick must repeat, not fire once",
            60_000L,
            tick.intervalMs,
        )
        assertEquals(RecordatorioWidgetProvider.ACTION_TICK, actionOf(tick.operation))
    }

    @Test
    fun onDisabled_cancelsTheTick() {
        val provider = RecordatorioWidgetProvider()
        provider.onEnabled(context)
        assertEquals(1, alarms().size)

        provider.onDisabled(context)

        assertTrue("removing the widget must stop its tick", alarms().isEmpty())
    }

    @Test
    fun onEnabled_registersTheTickEvenWhenNotificationsAreDisabled() {
        SessionStore(context).notificationsEnabled = false

        RecordatorioWidgetProvider().onEnabled(context)

        assertEquals(
            "the widget tick must not be gated by the notifications toggle",
            1,
            alarms().size,
        )
    }

    @Test
    fun widgetTicks_doNotCollideWithTheTaskWidgetTick() {
        NextTaskWidgetProvider().onEnabled(context)
        RecordatorioWidgetProvider().onEnabled(context)

        assertEquals("both widgets keep their own tick", 2, alarms().size)
        assertEquals(
            setOf(NextTaskWidgetProvider.ACTION_TICK, RecordatorioWidgetProvider.ACTION_TICK),
            alarms().map { actionOf(it.operation) }.toSet(),
        )
    }
}
