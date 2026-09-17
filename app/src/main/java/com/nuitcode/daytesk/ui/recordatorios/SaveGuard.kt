package com.nuitcode.daytesk.ui.recordatorios

/**
 * Single-flight guard for a recordatorio save action.
 *
 * A fast double tap on "Guardar" used to enqueue two inserts — each built by
 * `build()` with a distinct `cloudKey` — producing a duplicate reminder and a
 * duplicate pair of notification alarms. Both save surfaces
 * ([RecordatorioModal] and
 * [com.nuitcode.daytesk.widget.RecordatorioCaptureActivity]) now gate the tap
 * on this guard, so only the first one runs while a save is in flight.
 *
 * It is a plain state holder (no Compose or Android types) so the behavior is
 * unit-testable; each surface owns one instance via `remember { SaveGuard() }`.
 */
internal class SaveGuard {
    private var inFlight = false

    /**
     * `true` only for the first caller. Every call made while a save is already
     * in flight returns `false` and must be treated as a no-op.
     */
    fun tryBegin(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }
}
