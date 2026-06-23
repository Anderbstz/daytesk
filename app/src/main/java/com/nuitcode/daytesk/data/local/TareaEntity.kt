package com.nuitcode.daytesk.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado

@Entity(tableName = "tareas")
data class TareaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val descripcion: String = "",
    val prioridad: String = "MEDIA",
    val contexto: String = "PERSONAL",
    val estado: String = "PENDIENTE",
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaVencimiento: Long? = null,
    val orden: Int = 0,
)

fun TareaEntity.toDomain(): Tarea = Tarea(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    prioridad = Prioridad.valueOf(prioridad),
    contexto = Contexto.valueOf(contexto),
    estado = TareaEstado.valueOf(estado),
    fechaCreacion = fechaCreacion,
    fechaVencimiento = fechaVencimiento,
    orden = orden,
)

fun Tarea.toEntity(): TareaEntity = TareaEntity(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    prioridad = prioridad.name,
    contexto = contexto.name,
    estado = estado.name,
    fechaCreacion = fechaCreacion,
    fechaVencimiento = fechaVencimiento,
    orden = orden,
)
