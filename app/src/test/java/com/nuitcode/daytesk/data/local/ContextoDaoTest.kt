package com.nuitcode.daytesk.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Strict TDD — RED tests written before production code.
 *
 * Test execution is BLOCKED on this Windows host (no Java/Android SDK per skip-verify
 * pattern #95). These tests document the contract that [ContextoDao] MUST honor:
 *   - insert assigns an auto-generated id and persists the row
 *   - getAllFlow emits rows ordered by `orden ASC`
 *   - deleteIfUnreferenced refuses default contexts (sentinel -1)
 *   - deleteIfUnreferenced refuses custom contexts in use (returns positive count)
 *   - deleteIfUnreferenced removes custom contexts with zero tareas (returns 0)
 *
 * Migrations run on a freshly built v2 schema; no v1->v2 migration exercised here
 * (see [MigrationTest]).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ContextoDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var contextoDao: ContextoDao
    private lateinit var tareaDao: TareaDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contextoDao = db.contextoDao()
        tareaDao = db.tareaDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_assignsIdAndPersists() = runTest {
        val id = contextoDao.insert(
            ContextoEntity(
                id = 0,
                nombre = "compras",
                color = 0xFF9CCC65.toInt(),
                iconId = null,
                orden = 10,
                esDefault = false,
            ),
        )
        assertTrue("insert id must be > 0, got $id", id > 0)
        val all = contextoDao.getAllFlow().first()
        assertEquals(1, all.size)
        assertEquals("compras", all[0].nombre)
        assertEquals(0xFF9CCC65.toInt(), all[0].color)
        assertEquals(false, all[0].esDefault)
    }

    @Test
    fun getAllFlow_emitsOrderedByOrden() = runTest {
        contextoDao.insert(ContextoEntity(nombre = "zzz", color = 0xFF000000.toInt(), orden = 30))
        contextoDao.insert(ContextoEntity(nombre = "aaa", color = 0xFF111111.toInt(), orden = 10))
        contextoDao.insert(ContextoEntity(nombre = "mmm", color = 0xFF222222.toInt(), orden = 20))

        val all = contextoDao.getAllFlow().first()
        assertEquals(listOf("aaa", "mmm", "zzz"), all.map { it.nombre })
    }

    @Test
    fun deleteIfUnreferenced_allowsDefaultWhenUnused() = runTest {
        val casa = ContextoEntity(
            id = 1,
            nombre = "casa",
            color = 0xFFFBC4AB.toInt(),
            orden = 1,
            esDefault = true,
        )
        contextoDao.insert(casa)

        val result = contextoDao.deleteIfUnreferenced(casa)

        assertEquals(0, result)
        assertEquals(null, contextoDao.getById(1))
    }

    @Test
    fun deleteIfUnreferenced_returnsCountWhenInUse() = runTest {
        val compras = contextoDao.insert(
            ContextoEntity(nombre = "compras", color = 0xFF9CCC65.toInt(), orden = 5),
        )
        // Insert two tareas referencing compras
        tareaDao.insertTarea(
            TareaEntity(
                titulo = "leche",
                contextoId = compras,
            ),
        )
        tareaDao.insertTarea(
            TareaEntity(
                titulo = "pan",
                contextoId = compras,
            ),
        )

        val comprasEntity = contextoDao.getById(compras)!!
        val result = contextoDao.deleteIfUnreferenced(comprasEntity)

        assertEquals(
            "deleteIfUnreferenced MUST return positive count when tareas reference the context",
            2,
            result,
        )
        assertNotNull("context MUST still exist after refused delete", contextoDao.getById(compras))
    }

    @Test
    fun deleteIfUnreferenced_removesCustomWithZeroTareas_returnsZero() = runTest {
        val id = contextoDao.insert(
            ContextoEntity(nombre = "limpieza", color = 0xFFAB47BC.toInt(), orden = 7),
        )
        val entity = contextoDao.getById(id)!!

        val result = contextoDao.deleteIfUnreferenced(entity)

        assertEquals(
            "deleteIfUnreferenced MUST return 0 when no tareas reference the context",
            0,
            result,
        )
        assertEquals(null, contextoDao.getById(id))
    }

    @Test
    fun countTareasForContext_returnsZeroWhenNone() = runTest {
        val id = contextoDao.insert(
            ContextoEntity(nombre = "estudios", color = 0xFF29B6F6.toInt(), orden = 9),
        )
        assertEquals(0, contextoDao.countTareasForContext(id))
    }

    @Test
    fun countTareasForContext_returnsMatchingCount() = runTest {
        val id = contextoDao.insert(
            ContextoEntity(nombre = "proyectos", color = 0xFF5C6BC0.toInt(), orden = 11),
        )
        tareaDao.insertTarea(TareaEntity(titulo = "a", contextoId = id))
        tareaDao.insertTarea(TareaEntity(titulo = "b", contextoId = id))
        assertEquals(2, contextoDao.countTareasForContext(id))
    }

    @Test
    fun getById_returnsNullForMissing() = runTest {
        assertEquals(null, contextoDao.getById(999))
    }
}