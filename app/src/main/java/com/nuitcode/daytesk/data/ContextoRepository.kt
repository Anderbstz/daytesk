package com.nuitcode.daytesk.data

import android.database.sqlite.SQLiteConstraintException
import com.nuitcode.daytesk.data.local.ContextoDao
import com.nuitcode.daytesk.data.local.ContextoEntity
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.model.Contexto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * CRUD contract for user-managed contexts.
 *
 * Returns `Result<T>` so the UI can render a friendly message instead of
 * crashing on raw [SQLiteConstraintException] (REQ-03 / REQ-05).
 */
interface ContextoRepository {
    val contextos: Flow<List<Contexto>>

    suspend fun add(contexto: Contexto): Result<Long>
    suspend fun update(contexto: Contexto): Result<Unit>
    suspend fun delete(id: Long): Result<Unit>
}

/** Thrown when a delete is refused because at least one `Tarea` references the context. */
class ContextoInUseException(val count: Int) :
    Exception("Contexto en uso por $count tarea(s)")

/** Thrown when adding would exceed [Contexto.MAX_COUNT]. */
class ContextoLimitException :
    Exception("Podés tener como máximo ${Contexto.MAX_COUNT} contextos")

/** Thrown when the last remaining context would be deleted. */
class LastContextoException :
    Exception("Tenés que dejar al menos un contexto")

/** Thrown when an insert/update collides with an existing `nombre` (case-insensitive). */
class DuplicateContextoNameException(val nombre: String) :
    Exception("Ya existe un contexto con el nombre \"$nombre\"")

/** Thrown when an insert/update receives a blank `nombre`. */
class BlankContextoNameException :
    Exception("El nombre del contexto no puede estar vacío")

/**
 * Default implementation. The repository is the second layer of the
 * triple-gate that protects default-context deletion (DAO + Repository + UI).
 */
class DefaultContextoRepository(
    private val contextoDao: ContextoDao,
) : ContextoRepository {

    override val contextos: Flow<List<Contexto>> =
        contextoDao.getAllFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun add(contexto: Contexto): Result<Long> {
        val nombre = contexto.nombre.trim()
        if (nombre.isBlank()) return Result.failure(BlankContextoNameException())
        val existing = contextoDao.getAllFlow().first()
        if (existing.size >= Contexto.MAX_COUNT) {
            return Result.failure(ContextoLimitException())
        }
        return try {
            // Assign id 0 — Room auto-generates. orden defaults to
            // (max existing orden) + 1 so the new row renders at the end.
            val nextOrden = (contextoDao.getAllFlow().first().maxOfOrNull { it.orden } ?: 0) + 1
            val id = contextoDao.insert(
                ContextoEntity(
                    id = 0,
                    nombre = nombre,
                    color = contexto.color,
                    iconId = contexto.iconId,
                    orden = nextOrden,
                    esDefault = false,
                    cloudKey = contexto.cloudKey.ifBlank { java.util.UUID.randomUUID().toString() },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            Result.success(id)
        } catch (e: SQLiteConstraintException) {
            Result.failure(DuplicateContextoNameException(nombre))
        }
    }

    override suspend fun update(contexto: Contexto): Result<Unit> {
        val nombre = contexto.nombre.trim()
        if (nombre.isBlank()) return Result.failure(BlankContextoNameException())
        val existing = contextoDao.getById(contexto.id)
            ?: return Result.failure(IllegalStateException("Contexto ${contexto.id} no existe"))
        // Defaults MAY be renamed (name editable per REQ-04) but color/icon locked.
        // For now we allow color updates only on non-default — UI enforces the same
        // affordance. Repository stays defensive: non-default rows can change freely.
        return try {
            contextoDao.update(
                ContextoEntity(
                    id = existing.id,
                    nombre = nombre,
                    color = contexto.color,
                    iconId = contexto.iconId,
                    orden = existing.orden,
                    esDefault = existing.esDefault,
                    cloudKey = existing.cloudKey.ifBlank { "default-${existing.id}" },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            Result.success(Unit)
        } catch (e: SQLiteConstraintException) {
            Result.failure(DuplicateContextoNameException(nombre))
        }
    }

    override suspend fun delete(id: Long): Result<Unit> {
        val entity = contextoDao.getById(id)
            ?: return Result.failure(IllegalStateException("Contexto $id no existe"))
        val all = contextoDao.getAllFlow().first()
        if (all.size <= 1) return Result.failure(LastContextoException())
        val replacement = all.first { it.id != id }
        val inUse = contextoDao.countTareasForContext(id)
        if (inUse > 0) {
            contextoDao.reassignTareas(id, replacement.id)
        }
        contextoDao.delete(entity)
        return Result.success(Unit)
    }
}