package com.nuitcode.daytesk.ui.recordatorios

import com.nuitcode.daytesk.model.Recordatorio

/**
 * Pure selection seam for the home-screen recordatorio widget.
 *
 * The widget reads the local store on every update and needs exactly one row to
 * bind: the soonest recordatorio whose `fecha` is still in the future. Kept
 * pure (no Android, no database) so the widget's data behavior is testable on
 * the JVM — the surrounding [com.nuitcode.daytesk.widget.RecordatorioWidgetProvider]
 * only wires this result into `RemoteViews`.
 *
 * Past-dated rows are ignored rather than shown: a roll is scheduled but not
 * guaranteed to have run before the widget renders, and surfacing an already-due
 * reminder as "upcoming" would be stale.
 */
object UpcomingRecordatorio {
    /** @return the soonest future candidate, or `null` when none is upcoming. */
    fun pick(candidates: List<Recordatorio>, now: Long): Recordatorio? =
        candidates.filter { it.fecha > now }.minByOrNull { it.fecha }
}
