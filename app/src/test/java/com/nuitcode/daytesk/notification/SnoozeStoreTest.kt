package com.nuitcode.daytesk.notification

import android.app.AlarmManager
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
 * RESIL-001 — a pending snooze must survive a reboot.
 *
 * The at-due roll deletes the source row, so the alarm and its persisted
 * registry entry are the only copy of a pending snooze. These tests pin the
 * registry lifecycle: written when the alarm is scheduled, re-armed on boot
 * when still in the future, dropped when stale, and removed on fire/cancel.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SnoozeStoreTest {

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

    private fun alarms() =
        shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms

    private fun snoozeAlarms() = alarms().filter {
        shadowOf(it.operation).savedIntent.data.toString().endsWith("/snooze")
    }

    private fun fireIntent(id: Long, texto: String) =
        Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_SNOOZE_FIRE
            putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, id)
            putExtra(ReminderScheduler.EXTRA_TITLE, texto)
        }

    @Test
    fun scheduleSnooze_persistsThePendingSnooze() {
        val before = System.currentTimeMillis()

        ReminderScheduler.scheduleSnooze(context, id = 21L, texto = "Call mom")

        val stored = SnoozeStore.all(context).single()
        assertEquals(21L, stored.id)
        assertEquals("Call mom", stored.texto)
        val expected = before + ReminderScheduler.SNOOZE_DELAY_MS
        assertTrue(
            "the stored trigger must match the scheduled alarm, got ${stored.triggerAt}",
            kotlin.math.abs(stored.triggerAt - expected) <= 5_000,
        )
    }

    @Test
    fun scheduleSnooze_doesNotPersistWhileNotificationsAreDisabled() {
        SessionStore(context).notificationsEnabled = false

        ReminderScheduler.scheduleSnooze(context, id = 22L, texto = "Call mom")

        assertTrue("the toggle gate must also gate the registry", SnoozeStore.all(context).isEmpty())
        assertTrue("no alarm may be scheduled while the toggle is off", snoozeAlarms().isEmpty())
    }

    @Test
    fun rearmSnoozes_reschedulesAFutureSnoozeAtItsStoredTime() {
        val triggerAt = System.currentTimeMillis() + 7 * 60 * 1000L
        SnoozeStore.save(context, id = 31L, texto = "Call mom", triggerAt = triggerAt)

        ReminderScheduler.rearmSnoozes(context)

        val alarm = snoozeAlarms().single()
        assertEquals("daytesk://recordatorio/31/snooze", shadowOf(alarm.operation).savedIntent.data.toString())
        assertEquals(
            ReminderScheduler.recordatorioSnoozeRequestCode(31L),
            shadowOf(alarm.operation).requestCode,
        )
        assertEquals("the snooze must keep its original trigger time", triggerAt, alarm.triggerAtMs)
        assertEquals("the registry entry must survive a successful re-arm", 1, SnoozeStore.all(context).size)
    }

    @Test
    fun rearmSnoozes_dropsASnoozeThatExpiredWhileTheDeviceWasOff() {
        val past = System.currentTimeMillis() - 60_000L
        SnoozeStore.save(context, id = 32L, texto = "Stale", triggerAt = past)

        ReminderScheduler.rearmSnoozes(context)

        assertTrue("a stale snooze must not be re-armed", snoozeAlarms().isEmpty())
        assertTrue("a stale snooze must be dropped from the registry", SnoozeStore.all(context).isEmpty())
    }

    @Test
    fun snoozeFire_removesThePersistedEntry() {
        SnoozeStore.save(
            context,
            id = 33L,
            texto = "Call mom",
            triggerAt = System.currentTimeMillis() + 60_000L,
        )

        SnoozeReceiver().onReceive(context, fireIntent(33L, "Call mom"))

        assertTrue("a fired snooze is no longer pending", SnoozeStore.all(context).isEmpty())
    }

    @Test
    fun cancelSnooze_removesThePersistedEntry() {
        ReminderScheduler.scheduleSnooze(context, id = 34L, texto = "Call mom")
        assertEquals(1, SnoozeStore.all(context).size)

        ReminderScheduler.cancelSnooze(context, 34L)

        assertTrue("an explicitly cancelled snooze is no longer pending", SnoozeStore.all(context).isEmpty())
    }
}
