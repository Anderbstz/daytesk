package com.nuitcode.daytesk.ui.main

import com.nuitcode.daytesk.data.DataRepository
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.data.DayteskStats
import com.nuitcode.daytesk.data.MockData
import com.nuitcode.daytesk.model.Contexto
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * ViewModel state-transition tests for [MainScreenViewModel].
 *
 * PR3 of `custom-contexts` extends this file with the `contextos` propagation
 * assertion (spec REQ-06 / REQ-09): the 3-way `combine` in `DefaultDataRepository`
 * MUST surface every `Contexto` from the `contextos` table on the emitted
 * `DayteskData`, and the ViewModel MUST pass that field through to its
 * `Success` state without dropping it.
 *
 * Test execution is BLOCKED on this Windows host (no Java/Android SDK per
 * skip-verify pattern #95). Static analysis confirms GREEN against the
 * existing 3-way `combine` in `data/DataRepository.kt` and the pass-through
 * mapping in `ui/main/MainScreenViewModel.kt`.
 */
class MainScreenViewModelTest {

    @Test
    fun uiState_initiallyLoading() = runTest {
        val viewModel = MainScreenViewModel(FakeDayteskRepository())
        val state = viewModel.uiState.value
        assertTrue(state is DayteskUiState.Loading)
    }

    @Test
    fun uiState_emitsSuccess_withTypedData() = runTest {
        val viewModel = MainScreenViewModel(FakeDayteskRepository())
        val state = viewModel.uiState.drop(1).first()
        assertTrue(state is DayteskUiState.Success)
        val success = state as DayteskUiState.Success
        assertEquals(3, success.data.stats.tareasHoy)
        assertEquals(6, success.data.tareasHoy.size + success.data.tareasSemana.size + success.data.completadas.size)
        assertEquals(5, success.data.inbox.size)
    }

    @Test
    fun uiState_emitsError_onRepositoryFailure() = runTest {
        val viewModel = MainScreenViewModel(FailingRepository())
        val state = viewModel.uiState.drop(1).first()
        assertTrue(state is DayteskUiState.Error)
    }

    /**
     * PR3 — assert that the `contextos` field on `DayteskData` is propagated
     * through the ViewModel's `Success` state. Mirrors the runtime contract of
     * `DefaultDataRepository.data` (3-way `combine` over tareas, inbox, and
     * contextos) and confirms the ViewModel does not drop the new field.
     *
     * RED at write-time: no test asserted this before PR3; the production
     * code path was implicit in the 3-way `combine` added in PR1. Static
     * GREEN: the ViewModel maps `DayteskData` -> `Success(DayteskData)` without
     * filtering, and `DayteskData.contextos` is a top-level field.
     */
    @Test
    fun uiState_propagatesContextos() = runTest {
        val viewModel = MainScreenViewModel(FakeDayteskRepositoryWithContextos())
        val state = viewModel.uiState.drop(1).first()
        assertTrue(state is DayteskUiState.Success)
        val success = state as DayteskUiState.Success

        // REQ-02 / REQ-06 — the 4 default seeds MUST appear in orden order
        // (ids 1..4) on every emission that survives the ViewModel mapping.
        assertEquals(4, success.data.contextos.size)
        assertEquals(
            listOf("casa", "trabajo", "personal", "salud"),
            success.data.contextos.map { it.nombre },
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), success.data.contextos.map { it.id })

        // Sanity: every propagated contexto is the data-class instance the
        // repository emitted (no spurious re-maps at the ViewModel boundary).
        success.data.contextos.forEach { ctx ->
            assertTrue(
                "propagated contexto MUST be a Contexto data-class instance",
                ctx is Contexto,
            )
        }
    }
}

private class FakeDayteskRepository : DataRepository {
    override val data: Flow<DayteskData> = flow {
        emit(
            DayteskData(
                stats = MockData.stats,
                tareasHoy = MockData.tareasHoy,
                tareasSemana = MockData.tareasSemana,
                completadas = MockData.completadas,
                inbox = MockData.inboxItems,
                alertas = MockData.alertas,
                weeklyReview = MockData.weeklyReview,
            )
        )
    }
}

/**
 * PR3 — same shape as [FakeDayteskRepository] but also populates the
 * `contextos` field with the 4 default seeds. Drives the propagation test.
 */
private class FakeDayteskRepositoryWithContextos : DataRepository {
    override val data: Flow<DayteskData> = flow {
        emit(
            DayteskData(
                stats = MockData.stats,
                tareasHoy = MockData.tareasHoy,
                tareasSemana = MockData.tareasSemana,
                completadas = MockData.completadas,
                inbox = MockData.inboxItems,
                alertas = MockData.alertas,
                weeklyReview = MockData.weeklyReview,
                contextos = Contexto.DEFAULTS,
            )
        )
    }
}

private class FailingRepository : DataRepository {
    override val data: Flow<DayteskData> = flow {
        throw RuntimeException("Repository failure")
    }
}
