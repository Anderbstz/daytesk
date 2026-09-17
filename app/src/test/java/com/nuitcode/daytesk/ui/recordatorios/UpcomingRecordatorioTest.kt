package com.nuitcode.daytesk.ui.recordatorios

import com.nuitcode.daytesk.model.Recordatorio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Strict TDD — RED test written before `UpcomingRecordatorio` exists.
 *
 * Contract (see sdd/recordatorios-ux/spec, domain `home-widget`):
 *   - Shows next upcoming -> the soonest future `fecha` wins
 *   - Empty state        -> `null` when nothing is upcoming
 *
 * The store may still hold past-dated rows (a roll is scheduled but not
 * guaranteed to have run), so the pick must ignore them rather than surface a
 * stale reminder on the widget.
 */
class UpcomingRecordatorioTest {

    @Test
    fun pick_returnsNullWhenNoCandidates() {
        val picked = UpcomingRecordatorio.pick(emptyList(), now = 1_000L)

        assertNull(picked)
    }

    @Test
    fun pick_returnsNullWhenEveryCandidateIsPastDated() {
        val now = 1_000L
        val candidates = listOf(
            recordatorio(id = 1, fecha = 100L),
            recordatorio(id = 2, fecha = 999L),
        )

        val picked = UpcomingRecordatorio.pick(candidates, now = now)

        assertNull("past-dated rows must never surface on the widget", picked)
    }

    @Test
    fun pick_returnsSoonestFutureCandidate() {
        val now = 1_000L
        val candidates = listOf(
            recordatorio(id = 1, fecha = 5_000L),
            recordatorio(id = 2, fecha = 2_000L),
            recordatorio(id = 3, fecha = 9_000L),
        )

        val picked = UpcomingRecordatorio.pick(candidates, now = now)

        assertEquals(2L, picked?.id)
        assertEquals(2_000L, picked?.fecha)
    }

    @Test
    fun pick_ignoresPastDatedRowsWhenFutureOnesExist() {
        val now = 1_000L
        val candidates = listOf(
            recordatorio(id = 1, fecha = 500L),
            recordatorio(id = 2, fecha = 4_000L),
            recordatorio(id = 3, fecha = 3_000L),
        )

        val picked = UpcomingRecordatorio.pick(candidates, now = now)

        assertEquals("soonest list order must not override the date filter", 3L, picked?.id)
    }

    @Test
    fun pick_treatsExactNowAsNotUpcoming() {
        val now = 1_000L
        val candidates = listOf(
            recordatorio(id = 1, fecha = now),
            recordatorio(id = 2, fecha = now + 1L),
        )

        val picked = UpcomingRecordatorio.pick(candidates, now = now)

        assertEquals(2L, picked?.id)
    }

    private fun recordatorio(id: Long, fecha: Long): Recordatorio = Recordatorio(
        id = id,
        texto = "r$id",
        fecha = fecha,
    )
}
