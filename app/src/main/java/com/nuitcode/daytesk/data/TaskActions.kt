package com.nuitcode.daytesk.data

import android.content.Context
import com.nuitcode.daytesk.data.local.TareaDao
import com.nuitcode.daytesk.data.local.toEntity
import com.nuitcode.daytesk.model.Repeticion
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import com.nuitcode.daytesk.notification.ReminderScheduler
import com.nuitcode.daytesk.widget.NextTaskWidgetProvider
import java.util.UUID

suspend fun persistTaskCompletion(
    context: Context,
    tareaDao: TareaDao,
    task: Tarea,
    completed: Boolean,
) {
    val now = System.currentTimeMillis()
    val updated = task.withCompletion(completed).copy(updatedAt = now)
    tareaDao.updateTarea(updated.toEntity())
    if (completed) {
        ReminderScheduler.cancelTaskReminder(context, task.id)
        if (task.repeticion != Repeticion.NINGUNA) {
            val nextDue = task.repeticion.nextDue(task.fechaVencimiento ?: now)
            val next = task.copy(
                id = 0,
                estado = TareaEstado.PENDIENTE,
                fechaCompletada = null,
                fechaCreacion = now,
                fechaVencimiento = nextDue,
                cloudKey = UUID.randomUUID().toString(),
                updatedAt = now,
            )
            val id = tareaDao.insertTarea(next.toEntity())
            ReminderScheduler.scheduleIfDue(context, id, next.titulo, nextDue)
        }
    } else {
        ReminderScheduler.scheduleIfDue(context, task.id, task.titulo, task.fechaVencimiento)
    }
    NextTaskWidgetProvider.refresh(context)
}

fun Tarea.withCompletion(completed: Boolean): Tarea =
    if (completed) {
        copy(estado = TareaEstado.COMPLETADA, fechaCompletada = System.currentTimeMillis())
    } else {
        copy(estado = TareaEstado.PENDIENTE, fechaCompletada = null)
    }

fun Tarea.touched(): Tarea = copy(updatedAt = System.currentTimeMillis())

suspend fun archiveExpiredTasks(
    context: Context,
    tareaDao: TareaDao,
    now: Long = System.currentTimeMillis(),
) {
    var changed = false
    tareaDao.getAllOnce().forEach { entity ->
        val due = entity.fechaVencimiento ?: return@forEach
        if (due > now || entity.estado != TareaEstado.PENDIENTE.name) return@forEach
        val archived = entity.copy(
            estado = TareaEstado.VENCIDA.name,
            updatedAt = now,
        )
        tareaDao.updateTarea(archived)
        ReminderScheduler.cancelTaskReminder(context, entity.id)
        val repeticion = runCatching { Repeticion.valueOf(entity.repeticion) }
            .getOrDefault(Repeticion.NINGUNA)
        if (repeticion != Repeticion.NINGUNA) {
            val nextDue = repeticion.nextDue(due)
            val next = entity.copy(
                id = 0,
                estado = TareaEstado.PENDIENTE.name,
                fechaCompletada = null,
                fechaCreacion = now,
                fechaVencimiento = nextDue,
                cloudKey = UUID.randomUUID().toString(),
                updatedAt = now,
            )
            val id = tareaDao.insertTarea(next)
            ReminderScheduler.scheduleIfDue(context, id, next.titulo, nextDue)
        }
        changed = true
    }
    if (changed) NextTaskWidgetProvider.refresh(context)
}
