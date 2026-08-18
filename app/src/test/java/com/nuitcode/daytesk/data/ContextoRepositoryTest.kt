package com.nuitcode.daytesk.data

import com.nuitcode.daytesk.data.local.ContextoDao
import com.nuitcode.daytesk.data.local.ContextoEntity
import com.nuitcode.daytesk.model.Contexto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Strict TDD — RED tests written before [DefaultContextoRepository] exists.
 *
 * Repository contract (see sdd/custom-contexts/spec REQ-03..REQ-05):
 *   - add(c) returns Result<Long> with the new row id; refuses blank/duplicate names
 *   - delete(id) returns Result<Unit>; refuses if esDefault (DefaultContextProtectedException)
 *     or if any Tarea references the id (ContextoInUseException)
 *   - update(c) persists changes
 *   - contextos flow emits the current list ordered by orden
 *
 * Test execution is BLOCKED on this Windows host (no Java/Android SDK per
 * skip-verify pattern #95).
 */
class ContextoRepositoryTest {

    @Test
    fun add_persistsAndReturnsId() = runTest {
        val dao = FakeContextoDao()
        val repo = DefaultContextoRepository(dao)

        val result = repo.add(
            Contexto(id = 0, nombre = "compras", color = 0xFF9CCC65.toInt()),
        )

        assertTrue("add() must succeed for a new name", result.isSuccess)
        val id = result.getOrNull()!!
        assertTrue("id must be > 0", id > 0)
        assertEquals(1, dao.all.size)
        assertEquals("compras", dao.all.first().nombre)
    }

    @Test
    fun add_refusesBlankName() = runTest {
        val dao = FakeContextoDao()
        val repo = DefaultContextoRepository(dao)

        val result = repo.add(
            Contexto(id = 0, nombre = "   ", color = 0xFF000000.toInt()),
        )

        assertTrue("add() must refuse blank name", result.isFailure)
        assertEquals(0, dao.all.size)
    }

    @Test
    fun add_refusesDuplicateName() = runTest {
        val dao = FakeContextoDao().apply {
            insert(
                ContextoEntity(
                    id = 1,
                    nombre = "compras",
                    color = 0xFF000000.toInt(),
                    orden = 5,
                    esDefault = false,
                ),
            )
        }
        val repo = DefaultContextoRepository(dao)

        val result = repo.add(
            Contexto(id = 0, nombre = "compras", color = 0xFF111111.toInt()),
        )

        assertTrue("add() must refuse duplicate name", result.isFailure)
        assertEquals(1, dao.all.size)
    }

    @Test
    fun delete_succeedsForCustomWithZeroTareas() = runTest {
        val dao = FakeContextoDao().apply {
            insert(
                ContextoEntity(
                    id = 4,
                    nombre = "otro",
                    color = 0xFF000000.toInt(),
                    orden = 1,
                    esDefault = false,
                ),
            )
            insert(
                ContextoEntity(
                    id = 5,
                    nombre = "limpieza",
                    color = 0xFFAB47BC.toInt(),
                    orden = 7,
                    esDefault = false,
                ),
            )
        }
        val repo = DefaultContextoRepository(dao)

        val result = repo.delete(5)

        assertTrue("delete() must succeed for custom with zero tareas", result.isSuccess)
        assertNull(dao.getById(5))
    }

    @Test
    fun delete_succeedsForDefaultWhenAnotherExists() = runTest {
        val dao = FakeContextoDao().apply {
            insert(
                ContextoEntity(
                    id = 1,
                    nombre = "casa",
                    color = 0xFFFBC4AB.toInt(),
                    orden = 1,
                    esDefault = true,
                ),
            )
            insert(
                ContextoEntity(
                    id = 2,
                    nombre = "trabajo",
                    color = 0xFF90CAF9.toInt(),
                    orden = 2,
                    esDefault = true,
                ),
            )
        }
        val repo = DefaultContextoRepository(dao)

        val result = repo.delete(1)

        assertTrue("delete() must succeed for a default when another context remains", result.isSuccess)
        assertNull(dao.getById(1))
        assertNotNull(dao.getById(2))
    }

    @Test
    fun delete_refusesLastContext() = runTest {
        val dao = FakeContextoDao().apply {
            insert(
                ContextoEntity(
                    id = 1,
                    nombre = "casa",
                    color = 0xFFFBC4AB.toInt(),
                    orden = 1,
                    esDefault = true,
                ),
            )
        }
        val repo = DefaultContextoRepository(dao)

        val result = repo.delete(1)

        assertTrue("delete() must refuse the last remaining context", result.isFailure)
        assertTrue(result.exceptionOrNull() is LastContextoException)
        assertNotNull(dao.getById(1))
    }

    @Test
    fun delete_reassignsTareasThenDeletes() = runTest {
        val dao = FakeContextoDao().apply {
            insert(
                ContextoEntity(
                    id = 7,
                    nombre = "compras",
                    color = 0xFF9CCC65.toInt(),
                    orden = 5,
                    esDefault = false,
                ),
            )
            insert(
                ContextoEntity(
                    id = 8,
                    nombre = "otro",
                    color = 0xFF000000.toInt(),
                    orden = 6,
                    esDefault = false,
                ),
            )
            countFor[7L] = 3
        }
        val repo = DefaultContextoRepository(dao)

        val result = repo.delete(7)

        assertTrue("delete() must reassign tareas and succeed", result.isSuccess)
        assertNull(dao.getById(7))
        assertEquals(3, dao.countFor[8L])
    }

    @Test
    fun add_refusesWhenAtMax() = runTest {
        val dao = FakeContextoDao().apply {
            repeat(Contexto.MAX_COUNT) { index ->
                insert(
                    ContextoEntity(
                        id = 0,
                        nombre = "ctx$index",
                        color = 0xFF000000.toInt(),
                        orden = index,
                        esDefault = false,
                    ),
                )
            }
        }
        val repo = DefaultContextoRepository(dao)

        val result = repo.add(Contexto(id = 0, nombre = "extra", color = 0xFF111111.toInt()))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ContextoLimitException)
    }

    @Test
    fun delete_returnsFailureWhenContextNotFound() = runTest {
        val dao = FakeContextoDao()
        val repo = DefaultContextoRepository(dao)

        val result = repo.delete(999)

        assertTrue("delete() must fail when context not found", result.isFailure)
    }

    @Test
    fun update_persistsChanges() = runTest {
        val dao = FakeContextoDao().apply {
            insert(
                ContextoEntity(
                    id = 5,
                    nombre = "compras",
                    color = 0xFF9CCC65.toInt(),
                    orden = 5,
                    esDefault = false,
                ),
            )
        }
        val repo = DefaultContextoRepository(dao)

        val result = repo.update(
            Contexto(id = 5, nombre = "shopping", color = 0xFFEF5350.toInt()),
        )

        assertTrue("update() must succeed", result.isSuccess)
        val updated = dao.getById(5)!!
        assertEquals("shopping", updated.nombre)
        assertEquals(0xFFEF5350.toInt(), updated.color)
    }

    @Test
    fun update_refusesBlankName() = runTest {
        val dao = FakeContextoDao().apply {
            insert(
                ContextoEntity(
                    id = 5,
                    nombre = "compras",
                    color = 0xFF9CCC65.toInt(),
                    orden = 5,
                    esDefault = false,
                ),
            )
        }
        val repo = DefaultContextoRepository(dao)

        val result = repo.update(
            Contexto(id = 5, nombre = "   ", color = 0xFFEF5350.toInt()),
        )

        assertTrue("update() must refuse blank name", result.isFailure)
        assertEquals("compras", dao.getById(5)!!.nombre)
    }

    @Test
    fun contextos_emitsEntitiesInOrdenOrder() = runTest {
        val dao = FakeContextoDao().apply {
            insert(ContextoEntity(id = 0, nombre = "zzz", color = 0, orden = 30))
            insert(ContextoEntity(id = 0, nombre = "aaa", color = 0, orden = 10))
            insert(ContextoEntity(id = 0, nombre = "mmm", color = 0, orden = 20))
        }
        val repo = DefaultContextoRepository(dao)

        val list = repo.contextos.first()

        assertEquals(listOf("aaa", "mmm", "zzz"), list.map { it.nombre })
    }
}

/**
 * In-memory fake of [ContextoDao] for pure-JVM repository tests.
 * Mimics the Room semantics for the operations exercised here:
 *   - insert assigns an auto-increment id if id == 0
 *   - getById returns null when not present
 *   - countTareasForContext reads from the configurable map
 *   - getAllFlow emits the current list ordered by orden ASC
 */
private class FakeContextoDao : ContextoDao {
    private val storage = mutableListOf<ContextoEntity>()
    private var nextId = 1L
    val all: List<ContextoEntity> get() = storage.toList()
    val countFor: MutableMap<Long, Int> = mutableMapOf()

    override suspend fun insert(entity: ContextoEntity): Long {
        val assignedId = if (entity.id == 0L) nextId++ else entity.id
        storage.add(entity.copy(id = assignedId))
        // unique name — emulate index violation
        if (storage.count { it.nombre == entity.nombre } > 1) {
            storage.removeAll { it.id == assignedId }
            throw android.database.sqlite.SQLiteConstraintException(
                "UNIQUE constraint failed: contextos.nombre",
            )
        }
        return assignedId
    }

    override suspend fun update(entity: ContextoEntity) {
        val idx = storage.indexOfFirst { it.id == entity.id }
        if (idx >= 0) storage[idx] = entity
    }

    override suspend fun delete(entity: ContextoEntity) {
        storage.removeAll { it.id == entity.id }
    }

    override fun getAllFlow(): Flow<List<ContextoEntity>> =
        MutableStateFlow(storage.sortedBy { it.orden })

    override suspend fun getById(id: Long): ContextoEntity? =
        storage.firstOrNull { it.id == id }

    override suspend fun countTareasForContext(id: Long): Int = countFor[id] ?: 0

    override suspend fun reassignTareas(oldId: Long, newId: Long) {
        val moving = countFor.remove(oldId) ?: 0
        countFor[newId] = (countFor[newId] ?: 0) + moving
    }

    @androidx.room.Transaction
    override suspend fun deleteIfUnreferenced(entity: ContextoEntity): Int {
        val count = countTareasForContext(entity.id)
        if (count > 0) return count
        storage.removeAll { it.id == entity.id }
        return 0
    }
}