package com.nuitcode.daytesk.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [ContextoEntity].
 *
 * The default-protection triple-gate (see REQ-05 in the spec) is layered:
 *   1. [deleteIfUnreferenced] refuses default contexts (returns -1)
 *   2. [deleteIfUnreferenced] refuses custom contexts in use (returns count)
 *   3. The UI hides the long-press affordance for defaults
 *
 * The repository layer (com.nuitcode.daytesk.data.DefaultContextoRepository)
 * wraps [deleteIfUnreferenced] in a `Result<Unit>` and translates the sentinel
 * values into typed exceptions.
 */
@Dao
interface ContextoDao {

    @Query("SELECT * FROM contextos ORDER BY orden ASC")
    fun getAllFlow(): Flow<List<ContextoEntity>>

    @Query("SELECT * FROM contextos WHERE id = :id")
    suspend fun getById(id: Long): ContextoEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ContextoEntity): Long

    @Update
    suspend fun update(entity: ContextoEntity)

    @Delete
    suspend fun delete(entity: ContextoEntity)

    @Query("SELECT COUNT(*) FROM tareas WHERE contextoId = :id")
    suspend fun countTareasForContext(id: Long): Int

    /**
     * Atomically deletes a context iff it is non-default AND no `Tarea`
     * references it. Runs inside a single Room transaction so the count and
     * the delete share the same DB connection — closes the race window from
     * spec risk #3.
     *
     * Return value (sentinel):
     *   - `-1` when the context is a default (refused; row untouched)
     *   - `>0` (count of referencing tareas) when in use (refused; row untouched)
     *   - `0` on success (row removed)
     */
    @Transaction
    suspend fun deleteIfUnreferenced(entity: ContextoEntity): Int {
        if (entity.esDefault) return -1
        val count = countTareasForContext(entity.id)
        if (count > 0) return count
        delete(entity)
        return 0
    }
}