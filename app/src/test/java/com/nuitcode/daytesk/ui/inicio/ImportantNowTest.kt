package com.nuitcode.daytesk.ui.inicio

import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ImportantNowTest {

    @Test
    fun pick_keepsThreeClosestUpcomingDays() {
        val now = date(2026, Calendar.AUGUST, 17)
        val tasks = listOf(
            task(1, date(2026, Calendar.AUGUST, 18)),
            task(2, date(2026, Calendar.AUGUST, 19)),
            task(3, date(2026, Calendar.AUGUST, 20)),
            task(4, date(2026, Calendar.AUGUST, 21)),
            task(5, date(2026, Calendar.AUGUST, 23)),
        )

        val picked = ImportantNow.pick(tasks, nowMillis = now)

        assertEquals(listOf(1L, 2L, 3L), picked.map { it.id })
    }

    @Test
    fun pick_prefersOverdueClosestToToday() {
        val now = date(2026, Calendar.AUGUST, 17)
        val tasks = listOf(
            task(1, date(2026, Calendar.AUGUST, 15)),
            task(2, date(2026, Calendar.AUGUST, 16)),
            task(3, date(2026, Calendar.AUGUST, 20)),
            task(4, date(2026, Calendar.AUGUST, 21)),
        )

        val picked = ImportantNow.pick(tasks, nowMillis = now)

        assertEquals(listOf(2L, 1L, 3L), picked.map { it.id })
    }

    private fun task(id: Long, due: Long) = Tarea(
        id = id,
        titulo = "t$id",
        prioridad = Prioridad.ALTA,
        estado = TareaEstado.PENDIENTE,
        fechaVencimiento = due,
    )

    private fun date(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, 12, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
