package com.nuitcode.daytesk.notification

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.auth.AccountActions
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.deleteRecordatorio
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.data.persistRecordatorio
import com.nuitcode.daytesk.model.Recordatorio
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Deleting or invalidating a reminder must silence it **immediately**.
 *
 * [AlarmVibrationService] used to ring until dismissed, snoozed, or its
 * 2-minute timeout. The identity-invalidating paths (delete, edit,
 * notifications-off, logout, account switch) cancelled the scheduled alarms but
 * never the active ring, so deleting a ringing reminder left the vibrator
 * running for up to two minutes.
 *
 * The stop is **id-aware**: one reminder's invalidation must never silence a
 * different reminder that happens to be ringing. Only a whole-identity
 * invalidation (logout, account switch) may stop unconditionally.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AlarmVibrationStopTest {

    private val hour = 60 * 60 * 1000L

    private lateinit var app: Application
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        context.getSharedPreferences("daytesk_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        SnoozeStore.clear(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------------------------------------------------------- predicate

    @Test
    fun shouldStop_matchingId_stopsTheRing() {
        assertTrue(
            "the ringing reminder's own id must stop it",
            AlarmVibrationService.shouldStop(currentId = 71L, incomingId = 71L),
        )
    }

    @Test
    fun shouldStop_differentId_keepsTheOtherRingAlive() {
        assertFalse(
            "deleting reminder 72 must not silence the ring of reminder 71",
            AlarmVibrationService.shouldStop(currentId = 71L, incomingId = 72L),
        )
    }

    @Test
    fun shouldStop_missingIncomingId_stopsAnyRing() {
        assertTrue(
            "a whole-identity invalidation has no id to compare, so it must stop anyway",
            AlarmVibrationService.shouldStop(
                currentId = 71L,
                incomingId = AlarmVibrationService.NO_RECORDATORIO_ID,
            ),
        )
    }

    @Test
    fun shouldStop_withNothingRinging_letsTheServiceCleanUp() {
        assertTrue(
            "a stop delivered while nothing rings must still be allowed to finish the service",
            AlarmVibrationService.shouldStop(
                currentId = AlarmVibrationService.NO_RECORDATORIO_ID,
                incomingId = 72L,
            ),
        )
    }

    // ------------------------------------------------------------ stop senders

    @Test
    fun stopIfRinging_sendsAStopIntentCarryingThatId() {
        AlarmVibrationService.stopIfRinging(context, 71L)

        val sent = shadowOf(app).nextStartedService
        assertEquals(AlarmVibrationService.ACTION_STOP, sent?.action)
        assertEquals(
            "the stop must name the reminder it belongs to",
            71L,
            sent?.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L),
        )
        assertTrue(
            "the stop must target the vibration service",
            sent?.component?.className.orEmpty().endsWith("AlarmVibrationService"),
        )
    }

    @Test
    fun stopIfRinging_targetsEachReminderSeparately() {
        AlarmVibrationService.stopIfRinging(context, 71L)
        AlarmVibrationService.stopIfRinging(context, 72L)

        val shadow = shadowOf(app)
        val first = shadow.nextStartedService
        val second = shadow.nextStartedService
        assertEquals(71L, first?.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L))
        assertEquals(72L, second?.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L))
    }

    @Test
    fun stopAnyRinging_sendsAStopIntentWithoutAnId() {
        AlarmVibrationService.stopAnyRinging(context)

        val sent = shadowOf(app).nextStartedService
        assertEquals(AlarmVibrationService.ACTION_STOP, sent?.action)
        assertEquals(
            "the unconditional stop must not pretend to belong to one reminder",
            AlarmVibrationService.NO_RECORDATORIO_ID,
            sent?.getLongExtra(
                ReminderScheduler.EXTRA_RECORDATORIO_ID,
                AlarmVibrationService.NO_RECORDATORIO_ID,
            ),
        )
    }

    // --------------------------------------------------------- service wiring

    @Test
    fun stopForADifferentReminder_leavesTheActiveRingTimeoutArmed() {
        val service = Robolectric.buildService(AlarmVibrationService::class.java).create().get()
        service.onStartCommand(startIntent(71L), 0, 0)
        assertEquals("precondition: the 2-minute timeout is armed", 1, timeoutAlarms().size)

        service.onStartCommand(stopIntent(72L), 0, 1)

        assertEquals(
            "a stop for another reminder must not cancel this reminder's timeout",
            1,
            timeoutAlarms().size,
        )
        assertEquals(
            "the surviving timeout must still belong to the ringing reminder",
            71L,
            shadowOf(timeoutAlarms().single().operation).savedIntent
                .getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L),
        )
    }

    @Test
    fun stopForTheRingingReminder_silencesItAndCancelsTheTimeout() {
        val service = Robolectric.buildService(AlarmVibrationService::class.java).create().get()
        service.onStartCommand(startIntent(71L), 0, 0)
        assertEquals("precondition: the 2-minute timeout is armed", 1, timeoutAlarms().size)

        service.onStartCommand(stopIntent(71L), 0, 1)

        assertTrue(
            "the matching stop must cancel the 2-minute timeout with the ring",
            timeoutAlarms().isEmpty(),
        )
    }

    // ------------------------------------------------- identity-invalidating paths

    @Test
    fun deletingARecordatorio_stopsItsOwnRing() = runTest {
        val id = persistRecordatorio(
            context,
            db.recordatorioDao(),
            Recordatorio(id = 0, texto = "Wake up", fecha = System.currentTimeMillis() + 3 * hour),
        )

        deleteRecordatorio(context, db.recordatorioDao(), db.recordatorioDao().getById(id)!!.toDomain())

        val sent = shadowOf(app).nextStartedService
        assertEquals(AlarmVibrationService.ACTION_STOP, sent?.action)
        assertEquals(id, sent?.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L))
    }

    @Test
    fun editingARecordatorio_stopsTheRingOfTheOldIdentity() = runTest {
        val now = System.currentTimeMillis()
        val id = persistRecordatorio(
            context,
            db.recordatorioDao(),
            Recordatorio(id = 0, texto = "Wake up", fecha = now + 3 * hour),
        )
        assertNull(
            "creating a reminder invalidates nothing, so it must not send a stop",
            shadowOf(app).nextStartedService,
        )

        persistRecordatorio(
            context,
            db.recordatorioDao(),
            Recordatorio(id = id, texto = "Wake up later", fecha = now + 4 * hour),
        )

        val sent = shadowOf(app).nextStartedService
        assertEquals(AlarmVibrationService.ACTION_STOP, sent?.action)
        assertEquals(id, sent?.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L))
    }

    @Test
    fun disablingNotifications_stopsTheRingOfEveryReminderItCancels() = runTest {
        val dao = db.recordatorioDao()
        val due = System.currentTimeMillis() + 3 * hour
        val first = dao.insert(
            RecordatorioEntity(
                texto = "First",
                fecha = due,
                repeticion = "NINGUNA",
                cloudKey = "rec-first",
                updatedAt = 1L,
                fechaCreacion = 1L,
            ),
        )
        val second = dao.insert(
            RecordatorioEntity(
                texto = "Second",
                fecha = due,
                repeticion = "NINGUNA",
                cloudKey = "rec-second",
                updatedAt = 1L,
                fechaCreacion = 1L,
            ),
        )

        applyNotificationsEnabled(context, false, db.tareaDao(), dao)

        val shadow = shadowOf(app)
        val stopped = generateSequence { shadow.nextStartedService }
            .filter { it.action == AlarmVibrationService.ACTION_STOP }
            .map { it.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, -1L) }
            .toSet()
        assertEquals(
            "turning notifications off must silence every reminder whose alarms it cancels",
            setOf(first, second),
            stopped,
        )
    }

    @Test
    fun logout_stopsAnyActiveRing() {
        SessionStore(context).save("token", "Ander", "ander@example.com")

        AccountActions.logout(context)

        val sent = shadowOf(app).nextStartedService
        assertEquals(AlarmVibrationService.ACTION_STOP, sent?.action)
        assertEquals(
            "logout invalidates the whole identity, so it cannot name a single reminder",
            AlarmVibrationService.NO_RECORDATORIO_ID,
            sent?.getLongExtra(
                ReminderScheduler.EXTRA_RECORDATORIO_ID,
                AlarmVibrationService.NO_RECORDATORIO_ID,
            ),
        )
    }

    @Test
    fun accountSwitch_stopsAnyActiveRing() = runTest {
        AccountActions.discardLocalContent(context, db)

        val sent = shadowOf(app).nextStartedService
        assertEquals(AlarmVibrationService.ACTION_STOP, sent?.action)
        assertEquals(
            "the incoming account must never inherit a ringing reminder",
            AlarmVibrationService.NO_RECORDATORIO_ID,
            sent?.getLongExtra(
                ReminderScheduler.EXTRA_RECORDATORIO_ID,
                AlarmVibrationService.NO_RECORDATORIO_ID,
            ),
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun startIntent(id: Long, texto: String = "Wake up") =
        Intent(app, AlarmVibrationService::class.java).apply {
            action = AlarmVibrationService.ACTION_START
            data = Uri.parse("daytesk://recordatorio/$id/alarm")
            putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, id)
            putExtra(ReminderScheduler.EXTRA_TITLE, texto)
        }

    private fun stopIntent(id: Long) =
        Intent(app, AlarmVibrationService::class.java).apply {
            action = AlarmVibrationService.ACTION_STOP
            data = Uri.parse("daytesk://recordatorio/$id/stop")
            putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, id)
        }

    private fun timeoutAlarms() =
        shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms
            .filter { shadowOf(it.operation).savedIntent.data.toString().endsWith("/timeout") }
}
