package com.nuitcode.daytesk.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Strict TDD — RED test written before [RecordatorioEntity] / [RecordatorioDao] exist.
 *
 * Contract (see sdd/quick-reminders/spec):
 *   - Create persists     -> insert assigns an id and `fecha` round-trips
 *   - Soonest first       -> getAllRecordatorios() orders by `fecha` ASC
 *   - Edit updates in place -> update keeps the same id
 *   - Delete              -> delete removes the row
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RecordatorioDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RecordatorioDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recordatorioDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_assignsIdAndRoundTripsFecha() = runTest {
        val id = dao.insert(
            RecordatorioEntity(
                texto = "Buy milk",
                fecha = 1_700_000_000_000L,
                updatedAt = 1L,
                fechaCreacion = 1L,
            ),
        )

        assertTrue("insert id must be > 0, got $id", id > 0)
        val stored = dao.getById(id)
        assertEquals("Buy milk", stored?.texto)
        assertEquals("fecha must round-trip unchanged", 1_700_000_000_000L, stored?.fecha)
    }

    @Test
    fun getAllRecordatorios_emitsSoonestFirst() = runTest {
        dao.insert(RecordatorioEntity(texto = "later", fecha = 3_000L, updatedAt = 1L, fechaCreacion = 1L))
        dao.insert(RecordatorioEntity(texto = "soonest", fecha = 1_000L, updatedAt = 1L, fechaCreacion = 1L))
        dao.insert(RecordatorioEntity(texto = "middle", fecha = 2_000L, updatedAt = 1L, fechaCreacion = 1L))

        val all = dao.getAllRecordatorios().first()

        assertEquals(listOf(1_000L, 2_000L, 3_000L), all.map { it.fecha })
        assertEquals(listOf("soonest", "middle", "later"), all.map { it.texto })
    }

    @Test
    fun update_keepsSameId() = runTest {
        val id = dao.insert(
            RecordatorioEntity(texto = "Buy milk", fecha = 1_000L, updatedAt = 1L, fechaCreacion = 1L),
        )
        val stored = dao.getById(id)!!

        dao.update(stored.copy(texto = "Buy bread", fecha = 9_999L))

        val updated = dao.getById(id)!!
        assertEquals("update must keep the same id", id, updated.id)
        assertEquals("Buy bread", updated.texto)
        assertEquals(9_999L, updated.fecha)
        assertEquals("update must not insert a second row", 1, dao.getAllOnce().size)
    }

    @Test
    fun delete_removesRow() = runTest {
        val id = dao.insert(
            RecordatorioEntity(texto = "Buy milk", fecha = 1_000L, updatedAt = 1L, fechaCreacion = 1L),
        )
        val stored = dao.getById(id)!!

        dao.delete(stored)

        assertNull("row must be gone after delete", dao.getById(id))
        assertTrue("table must be empty after deleting the only row", dao.getAllOnce().isEmpty())
    }
}
