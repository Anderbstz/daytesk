package com.nuitcode.daytesk.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.auth.AuthApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * RESIL-004, pure merge contract.
 *
 * The pull must never destroy a row that exists only locally: a recordatorio
 * captured from the widget may still have a failed or pending push.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SnapshotMergeTest {

    private fun contexto(cloudKey: String) = AuthApi.SyncContextoDto(
        cloudKey = cloudKey,
        nombre = cloudKey,
        color = 0,
        iconId = null,
        orden = 0,
        esDefault = false,
        updatedAt = 1L,
    )

    private fun tarea(cloudKey: String, contextoKey: String = "ctx") = AuthApi.SyncTareaDto(
        cloudKey = cloudKey,
        titulo = cloudKey,
        descripcion = "",
        prioridad = "MEDIA",
        contextoKey = contextoKey,
        estado = "PENDIENTE",
        fechaCreacion = 1L,
        fechaVencimiento = null,
        fechaCompletada = null,
        orden = 0,
        repeticion = "NINGUNA",
        updatedAt = 1L,
    )

    private fun recordatorio(cloudKey: String, texto: String = cloudKey) = AuthApi.SyncRecordatorioDto(
        cloudKey = cloudKey,
        texto = texto,
        fecha = 1L,
        repeticion = "NINGUNA",
        fechaCreacion = 1L,
        updatedAt = 1L,
    )

    private fun snapshot(
        contextos: List<AuthApi.SyncContextoDto> = emptyList(),
        tareas: List<AuthApi.SyncTareaDto> = emptyList(),
        recordatorios: List<AuthApi.SyncRecordatorioDto> = emptyList(),
    ) = AuthApi.SyncSnapshot(contextos, tareas, recordatorios)

    @Test
    fun localOnlyRecordatorio_survivesTheMerge() {
        val remote = snapshot(recordatorios = listOf(recordatorio("remote-1")))
        val local = snapshot(recordatorios = listOf(recordatorio("local-1")))

        val merged = remote.mergePreservingLocal(local)

        assertEquals(
            "a row that only exists locally must be kept",
            setOf("remote-1", "local-1"),
            merged.recordatorios.map { it.cloudKey }.toSet(),
        )
    }

    @Test
    fun remoteRow_winsOverTheLocalCopyWithTheSameCloudKey() {
        val remote = snapshot(recordatorios = listOf(recordatorio("shared", texto = "remote")))
        val local = snapshot(recordatorios = listOf(recordatorio("shared", texto = "local")))

        val merged = remote.mergePreservingLocal(local)

        assertEquals("cloudKey must not be duplicated", 1, merged.recordatorios.size)
        assertEquals("the remote copy is authoritative", "remote", merged.recordatorios.single().texto)
    }

    @Test
    fun emptyRemote_preservesEveryLocalRow() {
        val local = snapshot(
            tareas = listOf(tarea("task-1")),
            recordatorios = listOf(recordatorio("rec-1")),
        )

        val merged = snapshot().mergePreservingLocal(local)

        assertEquals(listOf("task-1"), merged.tareas.map { it.cloudKey })
        assertEquals(listOf("rec-1"), merged.recordatorios.map { it.cloudKey })
    }

    @Test
    fun localOnlyContextoIsPreservedSoAPreservedTareaKeepsItsReference() {
        val remote = snapshot(contextos = listOf(contexto("default-1")))
        val local = snapshot(
            contextos = listOf(contexto("default-1"), contexto("local-ctx")),
            tareas = listOf(tarea("task-1", contextoKey = "local-ctx")),
        )

        val merged = remote.mergePreservingLocal(local)

        assertEquals(
            setOf("default-1", "local-ctx"),
            merged.contextos.map { it.cloudKey }.toSet(),
        )
        assertEquals(
            "the preserved tarea must still resolve its contextoKey",
            "local-ctx",
            merged.tareas.single().contextoKey,
        )
    }
}
