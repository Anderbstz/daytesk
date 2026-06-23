package com.nuitcode.daytesk.data

import com.nuitcode.daytesk.data.local.InboxItemDao
import com.nuitcode.daytesk.data.local.TareaDao
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.util.Calendar

interface DataRepository {
    val data: Flow<DayteskData>
}

class DefaultDataRepository(
    private val tareaDao: TareaDao,
    private val inboxItemDao: InboxItemDao,
) : DataRepository {
    override val data: Flow<DayteskData> = combine(
        tareaDao.getAllTareas(),
        inboxItemDao.getAllItems(),
    ) { tareaEntities, inboxEntities ->
        val tareas = tareaEntities.map { it.toDomain() }
        val inboxItems = inboxEntities.map { it.toDomain() }

        val completadas = tareas.filter { it.estado == TareaEstado.COMPLETADA }
        val hoy = tareas.filter { it.estado != TareaEstado.COMPLETADA && esHoy(it) }
        val semana = tareas.filter {
            it.estado != TareaEstado.COMPLETADA && !esHoy(it) && esEstaSemana(it)
        }

        DayteskData(
            stats = DayteskStats(
                tareasHoy = hoy.size,
                inboxPendientes = inboxItems.size,
                tareasCompletadas = completadas.size,
                totalCompletadasHistorico = completadas.size,
            ),
            tareasHoy = hoy,
            tareasSemana = semana,
            completadas = completadas,
            inbox = inboxItems,
            alertas = emptyList(),
            weeklyReview = emptyList(),
        )
    }.catch { _ ->
        emit(
            DayteskData(
                stats = DayteskStats(0, 0, 0),
                tareasHoy = emptyList(),
                tareasSemana = emptyList(),
                completadas = emptyList(),
                inbox = emptyList(),
                alertas = emptyList(),
            ),
        )
    }

    private fun esHoy(tarea: Tarea): Boolean {
        val venc = tarea.fechaVencimiento ?: return false
        val cal = Calendar.getInstance().apply { timeInMillis = venc }
        val today = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    private fun esEstaSemana(tarea: Tarea): Boolean {
        val venc = tarea.fechaVencimiento ?: return false
        val cal = Calendar.getInstance().apply { timeInMillis = venc }
        val weekStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val weekEnd = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis >= weekStart.timeInMillis &&
            cal.timeInMillis <= weekEnd.timeInMillis
    }
}
