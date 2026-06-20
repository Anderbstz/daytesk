package com.nuitcode.daytesk.ui.main

import com.nuitcode.daytesk.data.DataRepository
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.data.DayteskStats
import com.nuitcode.daytesk.data.MockData
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

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

private class FailingRepository : DataRepository {
    override val data: Flow<DayteskData> = flow {
        throw RuntimeException("Repository failure")
    }
}
