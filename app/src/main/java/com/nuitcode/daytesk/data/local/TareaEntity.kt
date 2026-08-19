package com.nuitcode.daytesk.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.model.Repeticion
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado

/**
 * Room entity for `tareas` table.
 *
 * v1 stored `contexto: String` (the enum name). v2 stores `contextoId: Long`
 * (FK into `contextos`). See [Migrations.MIGRATION_1_2] for the upgrade path.
 *
 * `contextoId` defaults to [Contexto.FALLBACK_ID] so newly inserted tareas without
 * an explicit context pick up the same default the enum used to provide.
 *
 * The `contexto: Contexto` field on the domain [Tarea] is no longer filled by
 * [toDomain] — resolution happens in
 * [com.nuitcode.daytesk.data.DefaultDataRepository.data] via the 3-way
 * `combine` over tareas + inbox + contextos.
 */
@Entity(tableName = "tareas")
data class TareaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val descripcion: String = "",
    val prioridad: String = "MEDIA",
    val contextoId: Long = Contexto.FALLBACK_ID,
    val estado: String = "PENDIENTE",
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaVencimiento: Long? = null,
    val fechaCompletada: Long? = null,
    val orden: Int = 0,
    val repeticion: String = "NINGUNA",
    val cloudKey: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Entity -> domain. The `contexto` field on [Tarea] gets a default of
 * [Contexto.FALLBACK] and is overwritten by
 * [com.nuitcode.daytesk.data.DefaultDataRepository] when the FK resolves
 * against the current `contextos` flow.
 */
fun TareaEntity.toDomain(): Tarea = Tarea(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    prioridad = Prioridad.valueOf(prioridad),
    contextoId = contextoId,
    contexto = Contexto.FALLBACK,
    estado = TareaEstado.valueOf(estado),
    fechaCreacion = fechaCreacion,
    fechaVencimiento = fechaVencimiento,
    fechaCompletada = fechaCompletada,
    orden = orden,
    repeticion = runCatching { Repeticion.valueOf(repeticion) }.getOrDefault(Repeticion.NINGUNA),
    cloudKey = cloudKey.ifBlank { "local-tarea-$id" },
    updatedAt = updatedAt,
)

/**
 * Domain -> entity. Maps the FK directly; the enum-string column is gone.
 */
fun Tarea.toEntity(): TareaEntity = TareaEntity(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    prioridad = prioridad.name,
    contextoId = contextoId,
    estado = estado.name,
    fechaCreacion = fechaCreacion,
    fechaVencimiento = fechaVencimiento,
    fechaCompletada = fechaCompletada,
    orden = orden,
    repeticion = repeticion.name,
    cloudKey = cloudKey.ifBlank {
        if (id != 0L) "local-tarea-$id" else java.util.UUID.randomUUID().toString()
    },
    updatedAt = updatedAt,
)