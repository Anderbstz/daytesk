package com.nuitcode.daytesk.data

import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePluginStoreTest {
    @Test
    fun recentPendingTasks_returnsNewestFirst() {
        val tasks = listOf(
            Tarea(id = 1, titulo = "vieja", estado = TareaEstado.PENDIENTE, fechaCreacion = 10),
            Tarea(id = 2, titulo = "nueva", estado = TareaEstado.PENDIENTE, fechaCreacion = 30),
            Tarea(id = 3, titulo = "media", estado = TareaEstado.PENDIENTE, fechaCreacion = 20),
            Tarea(id = 4, titulo = "hecha", estado = TareaEstado.COMPLETADA, fechaCreacion = 40),
        )
        assertEquals(listOf(2L, 3L, 1L), recentPendingTasks(tasks).map { it.id })
    }
}
