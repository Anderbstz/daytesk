package com.nuitcode.daytesk.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuitcode.daytesk.data.DataRepository
import com.nuitcode.daytesk.data.DayteskData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface DayteskUiState {
    data object Loading : DayteskUiState
    data class Success(val data: DayteskData) : DayteskUiState
    data class Error(val throwable: Throwable) : DayteskUiState
}

class MainScreenViewModel(dataRepository: DataRepository) : ViewModel() {
    val uiState: StateFlow<DayteskUiState> =
        dataRepository.data
            .map<DayteskData, DayteskUiState> { DayteskUiState.Success(it) }
            .catch { emit(DayteskUiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayteskUiState.Loading)
}
