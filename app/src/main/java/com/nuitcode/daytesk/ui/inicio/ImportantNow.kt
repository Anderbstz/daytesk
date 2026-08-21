package com.nuitcode.daytesk.ui.inicio

import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado

object ImportantNow {
    const val LIMIT = 3

    /**
     * Tareas pendientes que todavía no llegaron al 100% del plazo
     * (fecha/hora de vencimiento), más las que no tienen fecha.
     * Orden: más cercanas al vencimiento, luego más prioridad.
     */
    fun pick(
        candidates: List<Tarea>,
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = LIMIT,
    ): List<Tarea> {
        val pending = candidates
            .distinctBy { it.id }
            .filter { it.estado == TareaEstado.PENDIENTE }

        val upcoming = pending
            .filter { due ->
                val until = due.fechaVencimiento ?: return@filter false
                until > nowMillis
            }
            .sortedWith(
                compareBy<Tarea> { it.fechaVencimiento!! }
                    .thenByDescending { it.prioridad.ordinal },
            )

        val undated = pending
            .filter { it.fechaVencimiento == null }
            .sortedWith(
                compareByDescending<Tarea> { it.prioridad.ordinal }
                    .thenByDescending { it.fechaCreacion },
            )

        return (upcoming + undated).take(limit)
    }

    fun pickCurrent(
        candidates: List<Tarea>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Tarea? = pick(candidates, nowMillis, limit = 1).firstOrNull()

    /**
     * 0% al crear la tarea, 100% en la fecha/hora de vencimiento.
     * Se calcula en minutos enteros: transcurridos / total * 100.
     */
    fun timeProgress(
        fechaCreacion: Long,
        fechaVencimiento: Long?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Int {
        if (fechaVencimiento == null) return 0
        val totalMin = (fechaVencimiento - fechaCreacion) / 60_000L
        if (totalMin <= 0L) return if (nowMillis >= fechaVencimiento) 100 else 0
        val elapsedMin = ((nowMillis - fechaCreacion) / 60_000L).coerceAtLeast(0L)
        return ((elapsedMin * 100L) / totalMin).toInt().coerceIn(0, 100)
    }
}
