package com.nuitcode.daytesk.model

/**
 * A scheduled reminder.
 *
 * Replaces the old `InboxItem`: `fecha` is a **non-null** `Long`, so a
 * reminder cannot exist without a due date — the "date is mandatory" rule is
 * enforced by the type system end-to-end (widget, capture Activity, sync),
 * not by runtime validation.
 *
 * `repeticion` defaults to [Repeticion.NINGUNA]; when it is not `NINGUNA` the
 * expiry flow inserts the next occurrence at `Repeticion.nextDue(fecha)` with
 * a fresh `cloudKey`.
 */
data class Recordatorio(
    val id: Long,
    val texto: String,
    /** Due date as epoch millis. Mandatory: never nullable. */
    val fecha: Long,
    val repeticion: Repeticion = Repeticion.NINGUNA,
    val cloudKey: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val fechaCreacion: Long = System.currentTimeMillis(),
)
