package com.nuitcode.daytesk.model

data class Tarea(
    val id: Long,
    val titulo: String,
    val descripcion: String = "",
    val prioridad: Prioridad = Prioridad.MEDIA,
    val contexto: Contexto = Contexto.PERSONAL,
    val estado: TareaEstado = TareaEstado.PENDIENTE,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaVencimiento: Long? = null,
    val orden: Int = 0,
)
