package com.nuitcode.daytesk.ui.recordatorios

import com.nuitcode.daytesk.model.Recordatorio
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordatoriosSearchTest {
    private val rows = listOf(
        Recordatorio(id = 1L, texto = "Entregar SAP", fecha = 1L),
        Recordatorio(id = 2L, texto = "Llamar al dentista", fecha = 2L),
    )

    @Test
    fun aBlankQuery_keepsEveryRow() {
        assertEquals(rows, filterRecordatorios(rows, "   "))
    }

    @Test
    fun theQuery_matchesTheTextIgnoringCaseAndOuterSpaces() {
        assertEquals(listOf(rows[0]), filterRecordatorios(rows, " sap "))
    }

    @Test
    fun aQueryWithNoMatch_leavesNothing() {
        assertEquals(emptyList<Recordatorio>(), filterRecordatorios(rows, "canvas"))
    }
}
