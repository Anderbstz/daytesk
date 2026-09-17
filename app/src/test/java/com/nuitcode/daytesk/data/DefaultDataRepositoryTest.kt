package com.nuitcode.daytesk.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Strict TDD — RED test written before [DefaultDataRepository] switched its
 * third data source from the removed Inbox DAO to [RecordatorioDao] and before
 * `DayteskData.recordatorios` / `DayteskStats.recordatoriosPendientes` existed.
 *
 * Contract (see sdd/quick-reminders/spec, domain `recordatorios`):
 *   - Soonest first  -> `DayteskData.recordatorios` is ordered by `fecha` ASC
 *   - List ordering  -> every persisted recordatorio reaches the UI data
 *   - Home stats     -> `recordatoriosPendientes` counts every recordatorio
 *
 * Exercises the real Room DAO + the real flow `combine` over an in-memory DB,
 * so the assertion fails if the repository stops surfacing the new list.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DefaultDataRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DefaultDataRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultDataRepository(
            db.tareaDao(),
            db.recordatorioDao(),
            db.contextoDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun data_surfacesRecordatoriosSoonestFirst() = runTest {
        val dao = db.recordatorioDao()
        dao.insert(RecordatorioEntity(texto = "later", fecha = 3_000L, updatedAt = 1L, fechaCreacion = 1L))
        dao.insert(RecordatorioEntity(texto = "soonest", fecha = 1_000L, updatedAt = 1L, fechaCreacion = 1L))
        dao.insert(RecordatorioEntity(texto = "middle", fecha = 2_000L, updatedAt = 1L, fechaCreacion = 1L))

        val data = repository.data.first()

        assertEquals(listOf("soonest", "middle", "later"), data.recordatorios.map { it.texto })
        assertEquals(listOf(1_000L, 2_000L, 3_000L), data.recordatorios.map { it.fecha })
    }

    @Test
    fun data_countsEveryRecordatorioAsPending() = runTest {
        val dao = db.recordatorioDao()
        dao.insert(RecordatorioEntity(texto = "a", fecha = 1_000L, updatedAt = 1L, fechaCreacion = 1L))
        dao.insert(RecordatorioEntity(texto = "b", fecha = 2_000L, updatedAt = 1L, fechaCreacion = 1L))

        val data = repository.data.first()

        assertEquals(2, data.stats.recordatoriosPendientes)
        assertEquals(2, data.recordatorios.size)
    }
}
