package com.nuitcode.daytesk.ui.inicio

import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import java.util.Calendar

object ImportantNow {
    const val LIMIT = 3

    fun pick(
        candidates: List<Tarea>,
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = LIMIT,
    ): List<Tarea> {
        val startOfToday = startOfDay(nowMillis)
        return candidates
            .distinctBy { it.id }
            .filter { it.estado == TareaEstado.PENDIENTE && it.fechaVencimiento != null }
            .sortedWith(
                compareBy<Tarea> { tarea ->
                    val due = tarea.fechaVencimiento!!
                    if (due < startOfToday) 0 else 1
                }.thenBy { tarea ->
                    val due = tarea.fechaVencimiento!!
                    if (due < startOfToday) -due else due
                }.thenByDescending { it.prioridad.ordinal },
            )
            .take(limit)
    }

    private fun startOfDay(millis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
