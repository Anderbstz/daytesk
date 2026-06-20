package com.nuitcode.daytesk.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DataRepository {
    val data: Flow<DayteskData>
}

class DefaultDataRepository : DataRepository {
    override val data: Flow<DayteskData> = flow {
        // Simulate a brief loading delay
        delay(100)
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
