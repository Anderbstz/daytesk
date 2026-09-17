package com.nuitcode.daytesk.sync

import com.nuitcode.daytesk.auth.AuthApi

/**
 * Union-merges the local snapshot into this (remote) snapshot by `cloudKey`.
 *
 * ## Why
 *
 * The pull used to wipe every local row and re-insert the remote one. A row
 * that exists only locally — a recordatorio just captured from the home-screen
 * widget whose push has not landed yet — was destroyed by the next pull
 * (RESIL-004).
 *
 * ## Semantics
 *
 * Rows present remotely win (the server holds the shared state). Rows present
 * only locally are appended, so an unpushed local change always survives and is
 * pushed to the server on the next sync.
 *
 * ## Tradeoff
 *
 * A row deleted on another device but still present locally is resurrected and
 * re-pushed. Propagating deletes would require tombstones or an `updatedAt`
 * reconciliation this app does not have. Surviving data beats losing it, so the
 * merge always prefers keeping a local-only row.
 *
 * `cloudKey` is the identity here; blank keys must be normalized before the
 * merge (both [CloudSync.pushLocked] and the pull path do that).
 */
internal fun AuthApi.SyncSnapshot.mergePreservingLocal(
    local: AuthApi.SyncSnapshot,
): AuthApi.SyncSnapshot {
    val remoteContextoKeys = contextos.map { it.cloudKey }.toSet()
    val remoteTareaKeys = tareas.map { it.cloudKey }.toSet()
    val remoteRecordatorioKeys = recordatorios.map { it.cloudKey }.toSet()

    return copy(
        contextos = contextos + local.contextos.filterNot { it.cloudKey in remoteContextoKeys },
        tareas = tareas + local.tareas.filterNot { it.cloudKey in remoteTareaKeys },
        recordatorios = recordatorios + local.recordatorios.filterNot {
            it.cloudKey in remoteRecordatorioKeys
        },
    )
}
