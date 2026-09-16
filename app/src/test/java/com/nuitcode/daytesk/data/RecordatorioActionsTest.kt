package com.nuitcode.daytesk.data

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.RecordatorioDao
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.model.Recordatorio
import com.nuitcode.daytesk.model.Repeticion
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Strict TDD — RED test written before `RecordatorioActions` exists.
 *
 * Contract (see sdd/quick-reminders/spec, "Repetition auto-next on expiry"):
 *   - DAILY / WEEKLY / MONTHLY roll to `Repeticion.nextDue(previousFecha)`
 *   - the next row gets a fresh `cloudKey` and its notifications are scheduled
 *   - NINGUNA does not recur -> no next row
 *   - the expired original is always deleted
 *   - persist schedules, delete cancels
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RecordatorioActionsTest {

    private val hour = 60 * 60 * 1000L
    private val past = 1_700_000_000_000L

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: RecordatorioDao

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
        dao = db.recordatorioDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertExpired(repeticion: Repeticion, fecha: Long = past): Long = dao.insert(
        RecordatorioEntity(
            texto = "expired",
            fecha = fecha,
            repeticion = repeticion.name,
            cloudKey = "old-cloud-key",
            updatedAt = 1L,
            fechaCreacion = 1L,
        ),
    )

    private fun scheduledAlarms() =
        shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms

    @Test
    fun dailyExpiry_rollsTheNextOccurrenceOneDayLater() = runTest {
        val originalId = insertExpired(Repeticion.DIARIA)

        rollExpiredRecordatorios(context, dao, now = past + 1)

        val next = dao.getAllOnce().single()
        assertEquals(Repeticion.DIARIA.nextDue(past), next.fecha)
        assertEquals(Repeticion.DIARIA.name, next.repeticion)
        assertEquals("expired", next.texto)
        assertNotEquals("the rolled row must be a new row", originalId, next.id)
    }

    @Test
    fun weeklyExpiry_rollsTheNextOccurrenceOneWeekLater() = runTest {
        insertExpired(Repeticion.SEMANAL)

        rollExpiredRecordatorios(context, dao, now = past + 1)

        assertEquals(Repeticion.SEMANAL.nextDue(past), dao.getAllOnce().single().fecha)
    }

    @Test
    fun monthlyExpiry_rollsTheNextOccurrenceOneMonthLater() = runTest {
        insertExpired(Repeticion.MENSUAL)

        rollExpiredRecordatorios(context, dao, now = past + 1)

        assertEquals(Repeticion.MENSUAL.nextDue(past), dao.getAllOnce().single().fecha)
    }

    @Test
    fun roll_givesTheNextRowAFreshCloudKeyAndSchedulesIt() = runTest {
        // Anchored to the real clock: the next occurrence must be genuinely in
        // the future for the scheduler to register its alarms.
        val now = System.currentTimeMillis()
        insertExpired(Repeticion.DIARIA, fecha = now - hour)

        rollExpiredRecordatorios(context, dao, now = now)

        val next = dao.getAllOnce().single()
        assertNotEquals("a new occurrence needs its own sync identity", "old-cloud-key", next.cloudKey)
        assertTrue("cloudKey must not be blank", next.cloudKey.isNotBlank())
        assertEquals(now, next.fechaCreacion)
        assertEquals(now, next.updatedAt)
        assertEquals(
            "the rolled occurrence must be scheduled",
            2,
            scheduledAlarms().size,
        )
    }

    @Test
    fun noRepetition_doesNotRecur() = runTest {
        insertExpired(Repeticion.NINGUNA)

        rollExpiredRecordatorios(context, dao, now = past + 1)

        assertTrue("NINGUNA must not create a next row", dao.getAllOnce().isEmpty())
        assertTrue("no alarm must survive a non-recurring expiry", scheduledAlarms().isEmpty())
    }

    @Test
    fun roll_deletesTheExpiredOriginal() = runTest {
        val originalId = insertExpired(Repeticion.DIARIA)

        rollExpiredRecordatorios(context, dao, now = past + 1)

        assertNull("the expired row must be deleted, not kept", dao.getById(originalId))
        assertEquals("only the next occurrence must remain", 1, dao.getAllOnce().size)
    }

    @Test
    fun roll_leavesFutureRecordatoriosUntouched() = runTest {
        val futureId = dao.insert(
            RecordatorioEntity(
                texto = "future",
                fecha = past + 10 * hour,
                repeticion = Repeticion.DIARIA.name,
                cloudKey = "keep-me",
                updatedAt = 1L,
                fechaCreacion = 1L,
            ),
        )

        rollExpiredRecordatorios(context, dao, now = past + 1)

        val stored = dao.getById(futureId)
        assertEquals("a future reminder must not be rolled early", "keep-me", stored?.cloudKey)
        assertTrue("no alarm must be scheduled for a future reminder", scheduledAlarms().isEmpty())
    }

    @Test
    fun persistRecordatorio_insertsAndSchedulesIt() = runTest {
        val id = persistRecordatorio(
            context,
            dao,
            Recordatorio(id = 0, texto = "Buy milk", fecha = System.currentTimeMillis() + 3 * hour),
        )

        assertTrue("persist must return the new row id", id > 0)
        assertEquals("Buy milk", dao.getById(id)?.texto)
        assertEquals(2, scheduledAlarms().size)
    }

    @Test
    fun deleteRecordatorio_removesTheRowAndCancelsItsAlarms() = runTest {
        val id = persistRecordatorio(
            context,
            dao,
            Recordatorio(id = 0, texto = "Buy milk", fecha = System.currentTimeMillis() + 3 * hour),
        )
        assertEquals(2, scheduledAlarms().size)

        deleteRecordatorio(context, dao, dao.getById(id)!!.toDomain())

        assertNull(dao.getById(id))
        assertTrue("delete must cancel every scheduled alarm", scheduledAlarms().isEmpty())
    }
}
