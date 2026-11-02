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
/**
 * Collapses copies of one reminder: same text, time and repetition under different
 * `cloudKey`s, left behind by repeated rolls. Each copy would ring on its own. A
 * row already stored here wins, so its id and armed alarms survive. Mirrors are
 * exempt: their key is their identity at the source.
 */
internal fun AuthApi.SyncSnapshot.withoutRecordatorioCopies(
    localKeys: Set<String>,
): AuthApi.SyncSnapshot {
    val kept = mutableSetOf<Triple<String, Long, String>>()
    val survivors = recordatorios
        .sortedByDescending { it.cloudKey in localKeys }
        .filter { it.origin == "LIMEKATZE" || kept.add(Triple(it.texto, it.fecha, it.repeticion)) }
    return copy(recordatorios = survivors)
}

/**
 * Drops mirrors that arrive already past their time. An overdue task stays pending
 * at the source, so a feed that lists it would insert it on every pull, the expired
 * roll would ring it and delete it, and the next pull would start over. A mirror
 * already stored here is kept: its alarm may simply be late, and it rings only once.
 */
internal fun AuthApi.SyncSnapshot.withoutMirrorsArrivingExpired(
    localKeys: Set<String>,
    now: Long,
): AuthApi.SyncSnapshot = copy(
    recordatorios = recordatorios.filterNot {
        it.origin == "LIMEKATZE" && it.fecha <= now && it.cloudKey !in localKeys
    },
)

internal fun AuthApi.SyncSnapshot.mergePreservingLocal(
    local: AuthApi.SyncSnapshot,
    now: Long = System.currentTimeMillis(),
): AuthApi.SyncSnapshot {
    val remoteContextoKeys = contextos.map { it.cloudKey }.toSet()
    val remoteTareaKeys = tareas.map { it.cloudKey }.toSet()
    val remoteRecordatorioKeys = recordatorios.map { it.cloudKey }.toSet()

    return copy(
        contextos = contextos + local.contextos.filterNot { it.cloudKey in remoteContextoKeys },
        tareas = tareas + local.tareas.filterNot { it.cloudKey in remoteTareaKeys },
        // A mirrored row missing from a healthy feed was deleted or completed at
        // the source, so it must disappear here too. Two exceptions keep it:
        //   - the feed failed (`remoteError`), so its absence means nothing;
        //   - its time already passed, because the feed only lists future items.
        //     A late alarm must still surface it through the expired roll.
        recordatorios = recordatorios + local.recordatorios.filterNot {
            it.cloudKey in remoteRecordatorioKeys ||
                (it.origin == "LIMEKATZE" && remoteError == null && it.fecha > now)
        },
    )
}
