package com.nuitcode.daytesk.data

import com.nuitcode.daytesk.data.local.ContextoDao
import com.nuitcode.daytesk.data.local.InboxItemDao
import com.nuitcode.daytesk.data.local.InboxItemEntity
import com.nuitcode.daytesk.data.local.TareaDao
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.model.Contexto
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
    private val contextoDao: ContextoDao,
) : DataRepository {
    override val data: Flow<DayteskData> = combine(
        tareaDao.getAllTareas(),
        inboxItemDao.getAllItems(),
        contextoDao.getAllFlow(),
    ) { tareaEntities, inboxEntities, contextoEntities ->
        val contextos = contextoEntities.map { it.toDomain() }
        val contextosById = contextos.associateBy { it.id }
        val fallback = Contexto.FALLBACK

        val tareas = tareaEntities.map { entity ->
            val tarea = entity.toDomain()
            val resolved = contextosById[entity.contextoId] ?: fallback
            tarea.copy(contexto = resolved)
        }

        val now = System.currentTimeMillis()
        val enHistorial = { tarea: Tarea ->
            tarea.estado == TareaEstado.COMPLETADA ||
                tarea.estado == TareaEstado.VENCIDA ||
                (tarea.fechaVencimiento != null && tarea.fechaVencimiento <= now)
        }
        val historial = tareas
            .filter(enHistorial)
            .sortedByDescending { it.fechaVencimiento ?: it.fechaCompletada ?: it.fechaCreacion }
        val completadas = historial.filter { it.estado == TareaEstado.COMPLETADA }
        val vencidas = historial.filter { it.estado != TareaEstado.COMPLETADA }
        val pendientes = tareas.filterNot(enHistorial)
        val hoy = pendientes.filter { esHoy(it) }
        val semana = pendientes.filter { !esHoy(it) && esEstaSemana(it) }
        val otras = pendientes.filter { !esHoy(it) && !esEstaSemana(it) }
        val racha = StreakCalculator.currentStreak(
            completadas.mapNotNull { it.fechaCompletada ?: it.fechaCreacion },
        )

        DayteskData(
            stats = DayteskStats(
                tareasHoy = hoy.size,
                inboxPendientes = inboxItems(inboxEntities).size,
                tareasCompletadas = completadas.size,
                rachaActual = racha,
                totalCompletadasHistorico = completadas.size,
            ),
            tareasHoy = hoy,
            tareasSemana = semana,
            completadas = completadas,
            inbox = inboxItems(inboxEntities),
            alertas = emptyList(),
            contextos = contextos,
            weeklyReview = emptyList(),
            otrasPendientes = otras,
            vencidas = vencidas,
            historial = historial,
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
                contextos = Contexto.DEFAULTS,
            ),
        )
    }

    private fun inboxItems(entities: List<InboxItemEntity>) =
        entities.map { it.toDomain() }

    private fun esHoy(tarea: Tarea): Boolean {
        val venc = tarea.fechaVencimiento ?: return false
        if (venc <= System.currentTimeMillis()) return false
        val cal = Calendar.getInstance().apply { timeInMillis = venc }
        val today = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    private fun esEstaSemana(tarea: Tarea): Boolean {
        val venc = tarea.fechaVencimiento ?: return false
        if (venc <= System.currentTimeMillis()) return false
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