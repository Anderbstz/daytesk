package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.ContextoEntity
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import com.nuitcode.daytesk.data.local.TareaEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * RELI-001 / RELI-002: the toggle must take effect immediately.
 *
 * Enabling reschedules every pending reminder (it used to reschedule nothing
 * until a reboot or login); disabling cancels the alarms already registered.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class NotificationToggleTest {

    private val hour = 60 * 60 * 1000L

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var sessionStore: SessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daytesk_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionStore = SessionStore(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun alarms() =
        shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms

    private suspend fun seedPendingReminders() {
        val due = System.currentTimeMillis() + 3 * hour
        db.contextoDao().insert(
            ContextoEntity(id = 1, nombre = "casa", color = 0, orden = 1, esDefault = true),
        )
        db.tareaDao().insertTarea(
            TareaEntity(
                titulo = "Pending task",
                contextoId = 1,
                estado = "PENDIENTE",
                fechaVencimiento = due,
                cloudKey = "task-1",
            ),
        )
        db.recordatorioDao().insert(
            RecordatorioEntity(
                texto = "Pending reminder",
                fecha = due,
                repeticion = "NINGUNA",
                cloudKey = "rec-1",
                updatedAt = 1L,
                fechaCreacion = 1L,
            ),
        )
    }

    @Test
    fun enablingTheToggle_reschedulesPendingTareasAndRecordatorios() = runTest {
        seedPendingReminders()
        sessionStore.notificationsEnabled = false

        applyNotificationsEnabled(context, true, db.tareaDao(), db.recordatorioDao())

        assertTrue("the toggle must be persisted", sessionStore.notificationsEnabled)
        assertEquals(
            "both task alarms, both recordatorio alarms and the weekly review must be registered",
            5,
            alarms().size,
        )
    }

    @Test
    fun disablingTheToggle_cancelsTheRegisteredAlarms() = runTest {
        seedPendingReminders()
        applyNotificationsEnabled(context, true, db.tareaDao(), db.recordatorioDao())
        assertEquals("precondition: everything is scheduled", 5, alarms().size)

        applyNotificationsEnabled(context, false, db.tareaDao(), db.recordatorioDao())

        assertTrue(
            "a disabled toggle must cancel the alarms already registered",
            alarms().isEmpty(),
        )
    }

    @Test
    fun disablingTheToggle_alsoCancelsTheWeeklyReviewAlarm() = runTest {
        ReminderScheduler.scheduleWeeklyReview(context)
        assertEquals(1, alarms().size)

        applyNotificationsEnabled(context, false, db.tareaDao(), db.recordatorioDao())

        assertTrue("the weekly review alarm must be cancelled too", alarms().isEmpty())
    }

    /**
     * The DISABLE path cancels the weekly review alarm, so ENABLE must re-arm it:
     * otherwise an OFF→ON round trip silently drops the weekly review until the
     * user presses the manual button again.
     */
    @Test
    fun enablingTheToggle_armsTheWeeklyReviewAlarm() = runTest {
        sessionStore.notificationsEnabled = false

        applyNotificationsEnabled(context, true, db.tareaDao(), db.recordatorioDao())

        assertEquals(
            "enabling must arm the weekly review alarm even with no pending reminders",
            1,
            alarms().size,
        )
    }

    @Test
    fun disablingThenEnablingTheToggle_rearmsTheWeeklyReviewAlarm() = runTest {
        ReminderScheduler.scheduleWeeklyReview(context)
        assertEquals("precondition: the weekly review is armed", 1, alarms().size)

        applyNotificationsEnabled(context, false, db.tareaDao(), db.recordatorioDao())
        assertTrue("disabling must cancel the weekly review alarm", alarms().isEmpty())

        applyNotificationsEnabled(context, true, db.tareaDao(), db.recordatorioDao())

        assertEquals(
            "OFF→ON must re-arm the weekly review alarm that DISABLE cancelled",
            1,
            alarms().size,
        )
    }
}
