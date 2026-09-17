package com.nuitcode.daytesk.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Strict TDD — RED test written before the sync payload switched from
 * `inbox` to `recordatorios` (see sdd/quick-reminders/spec, domain `sync`).
 *
 * Contract:
 *   - a payload missing `recordatorios` parses to an empty list;
 *   - an unknown legacy `inbox` key is ignored (never parsed);
 *   - `toJson` emits `recordatorios` and no longer emits `inbox`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AuthApiSyncTest {

    @Test
    fun parseSnapshot_missingRecordatorios_yieldsEmptyList() {
        val snapshot = AuthApi.parseSnapshot(JSONObject("""{"contextos":[],"tareas":[]}"""))

        assertTrue(
            "a payload without recordatorios must parse to an empty list",
            snapshot.recordatorios.isEmpty(),
        )
    }

    @Test
    fun parseSnapshot_ignoresLegacyInboxKey() {
        val legacyInbox = """
            {"contextos":[],"tareas":[],
             "inbox":[{"cloudKey":"inbox-1","texto":"old item","timestamp":1,"procesado":false,"updatedAt":1}]}
        """.trimIndent()

        val snapshot = AuthApi.parseSnapshot(JSONObject(legacyInbox))

        assertTrue(
            "the legacy inbox key must be ignored, not parsed into recordatorios",
            snapshot.recordatorios.isEmpty(),
        )
    }

    @Test
    fun parseSnapshot_readsRecordatorios() {
        val json = JSONObject(
            """
            {"contextos":[],"tareas":[],
             "recordatorios":[{"cloudKey":"rec-1","texto":"Llamar al dentista","fecha":1700000000000,
                               "repeticion":"SEMANAL","fechaCreacion":1600000000000,"updatedAt":1700000000000}]}
            """.trimIndent(),
        )

        val snapshot = AuthApi.parseSnapshot(json)

        assertEquals(1, snapshot.recordatorios.size)
        val item = snapshot.recordatorios.single()
        assertEquals("rec-1", item.cloudKey)
        assertEquals("Llamar al dentista", item.texto)
        assertEquals(1700000000000L, item.fecha)
        assertEquals("SEMANAL", item.repeticion)
        assertEquals(1600000000000L, item.fechaCreacion)
        assertEquals(1700000000000L, item.updatedAt)
    }

    @Test
    fun toJson_emitsRecordatoriosKey_andNoInboxKey() {
        val json = AuthApi.SyncSnapshot(
            contextos = emptyList(),
            tareas = emptyList(),
            recordatorios = emptyList(),
        ).toJson()

        assertFalse("the payload must no longer emit an inbox key", json.has("inbox"))
        assertTrue("the payload must emit a recordatorios key", json.has("recordatorios"))
    }

    @Test
    fun toJson_roundTripsRecordatorios() {
        val original = AuthApi.SyncSnapshot(
            contextos = emptyList(),
            tareas = emptyList(),
            recordatorios = listOf(
                AuthApi.SyncRecordatorioDto(
                    cloudKey = "rec-1",
                    texto = "Pagar la luz",
                    fecha = 1800000000000L,
                    repeticion = "MENSUAL",
                    fechaCreacion = 1700000000000L,
                    updatedAt = 1800000000000L,
                ),
            ),
        )

        val parsed = AuthApi.parseSnapshot(original.toJson())

        assertEquals(original.recordatorios, parsed.recordatorios)
    }
}
