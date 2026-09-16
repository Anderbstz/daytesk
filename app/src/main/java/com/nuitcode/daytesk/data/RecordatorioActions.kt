package com.nuitcode.daytesk.data

import android.content.Context
import com.nuitcode.daytesk.data.local.RecordatorioDao
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import com.nuitcode.daytesk.data.local.toEntity
import com.nuitcode.daytesk.model.Recordatorio
import com.nuitcode.daytesk.model.Repeticion
import com.nuitcode.daytesk.notification.ReminderScheduler
import java.util.UUID

/**
 * Creates or edits a recordatorio and (re)schedules its notifications.
 *
 * Scheduling always runs after the row is written so the alarms reference a
 * persisted id; [ReminderScheduler.scheduleRecordatorio] cancels any previous
 * alarms first, so an edit cannot leave a stale reminder behind.
 *
 * @return the persisted row id.
 */
suspend fun persistRecordatorio(
    context: Context,
    dao: RecordatorioDao,
    recordatorio: Recordatorio,
): Long {
    val now = System.currentTimeMillis()
    val entity = recordatorio.copy(updatedAt = now).toEntity()
    val id = if (recordatorio.id == 0L) {
        dao.insert(entity)
    } else {
        dao.update(entity)
        recordatorio.id
    }
    ReminderScheduler.scheduleRecordatorio(context, id, recordatorio.texto, recordatorio.fecha)
    return id
}

/** Cancels the recordatorio's alarms, then removes the row. */
suspend fun deleteRecordatorio(
    context: Context,
    dao: RecordatorioDao,
    recordatorio: Recordatorio,
) {
    ReminderScheduler.cancelRecordatorio(context, recordatorio.id)
    dao.delete(recordatorio.toEntity())
}

/**
 * Rolls every expired recordatorio forward.
 *
 * For each row whose `fecha` is not in the future:
 *   - if `repeticion != NINGUNA`, the next occurrence is inserted with
 *     `fecha = Repeticion.nextDue(previousFecha)`, a fresh `cloudKey` and
 *     `fechaCreacion = updatedAt = now`, and its notifications are scheduled;
 *   - the expired original is deleted and its notifications cancelled.
 *
 * `Repeticion.nextDue()` is reused unchanged, exactly like
 * [archiveExpiredTasks] does for recurring tasks. Because the expired row is
 * deleted in the same pass, running this twice (in-app sweep plus the
 * at-due alarm receiver) is idempotent: the second pass finds nothing.
 */
suspend fun rollExpiredRecordatorios(
    context: Context,
    dao: RecordatorioDao,
    now: Long = System.currentTimeMillis(),
) {
    dao.getAllOnce().forEach { entity ->
        if (entity.fecha > now) return@forEach
        val repeticion = runCatching { Repeticion.valueOf(entity.repeticion) }
            .getOrDefault(Repeticion.NINGUNA)
        if (repeticion != Repeticion.NINGUNA) {
            val nextFecha = repeticion.nextDue(entity.fecha)
            val next = RecordatorioEntity(
                id = 0,
                texto = entity.texto,
                fecha = nextFecha,
                repeticion = entity.repeticion,
                cloudKey = UUID.randomUUID().toString(),
                updatedAt = now,
                fechaCreacion = now,
            )
            val nextId = dao.insert(next)
            ReminderScheduler.scheduleRecordatorio(context, nextId, next.texto, nextFecha)
        }
        dao.delete(entity)
        ReminderScheduler.cancelRecordatorio(context, entity.id)
    }
}
