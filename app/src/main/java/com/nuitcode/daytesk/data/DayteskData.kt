package com.nuitcode.daytesk.data

import com.nuitcode.daytesk.model.Alerta
import com.nuitcode.daytesk.model.InboxItem
import com.nuitcode.daytesk.model.Tarea

data class DayteskStats(
    val tareasHoy: Int,
    val inboxPendientes: Int,
    val tareasCompletadas: Int,
    val rachaActual: Int = 0,
    val totalCompletadasHistorico: Int = 0,
    val promedioEfectividad: Float = 0f,
)

data class DayteskData(
    val stats: DayteskStats,
    val tareasHoy: List<Tarea>,
    val tareasSemana: List<Tarea>,
    val completadas: List<Tarea>,
    val inbox: List<InboxItem>,
    val alertas: List<Alerta>,
    val weeklyReview: List<String> = emptyList(),
)
