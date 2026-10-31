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
    /**
     * Opt-in alarm mode: the due alarm drives a repeating vibration instead of
     * the ordinary Stage 1 notification. Vibration only — this flag never
     * enables audio. Defaults to `false` so every existing row stays Stage 1.
     */
    val esAlarma: Boolean = false,
    /** `DAYTESK` for local rows, `LIMEKATZE` for mirrored ones. */
    val origin: String = "DAYTESK",
    /** Stable handle in LimeKatze (`task:42`), null when local. */
    val remoteKey: String? = null,
    /** Local mute: the row stays visible but no alarm is scheduled. */
    val silenciado: Boolean = false,
    /** Epoch millis of the at-due notification, null when not surfaced yet. */
    val notificadaEn: Long? = null,
)
