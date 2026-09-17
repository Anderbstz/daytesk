package com.nuitcode.daytesk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Strict TDD — RED test written before [Recordatorio] exists.
 *
 * Covers the `Recordatorio model` spec requirement:
 *   - Model exposes all fields
 *   - Date is mandatory
 */
class RecordatorioTest {

    @Test
    fun exposesAllFields() {
        val recordatorio = Recordatorio(
            id = 42L,
            texto = "Buy milk",
            fecha = 1_700_000_000_000L,
            repeticion = Repeticion.SEMANAL,
            cloudKey = "cloud-abc",
            updatedAt = 111L,
            fechaCreacion = 222L,
        )

        assertEquals(42L, recordatorio.id)
        assertEquals("Buy milk", recordatorio.texto)
        assertEquals(1_700_000_000_000L, recordatorio.fecha)
        assertEquals(Repeticion.SEMANAL, recordatorio.repeticion)
        assertEquals("cloud-abc", recordatorio.cloudKey)
        assertEquals(111L, recordatorio.updatedAt)
        assertEquals(222L, recordatorio.fechaCreacion)
    }

    @Test
    fun fechaIsMandatoryByType() {
        // `fecha` is a non-null Long, so a recordatorio cannot even be
        // constructed without a date — the type system enforces the spec's
        // "Date is mandatory" scenario.
        val recordatorio = Recordatorio(id = 0L, texto = "Call mom", fecha = 123L)

        assertEquals(123L, recordatorio.fecha)
    }

    @Test
    fun defaultsToNoRepetition() {
        val recordatorio = Recordatorio(id = 0L, texto = "Call mom", fecha = 1L)

        assertEquals(Repeticion.NINGUNA, recordatorio.repeticion)
    }

    @Test
    fun generatesDistinctCloudKeysWhenAbsent() {
        val first = Recordatorio(id = 0L, texto = "Call mom", fecha = 1L)
        val second = Recordatorio(id = 0L, texto = "Call mom", fecha = 1L)

        assertTrue("cloudKey must not be blank", first.cloudKey.isNotBlank())
        assertNotEquals("each recordatorio needs its own cloudKey", first.cloudKey, second.cloudKey)
    }

    @Test
    fun derivesTimestampsByDefault() {
        val before = System.currentTimeMillis()

        val recordatorio = Recordatorio(id = 0L, texto = "Call mom", fecha = 1L)

        val after = System.currentTimeMillis()
        assertTrue("updatedAt must default to now", recordatorio.updatedAt in before..after)
        assertTrue("fechaCreacion must default to now", recordatorio.fechaCreacion in before..after)
    }
}
