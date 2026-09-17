package com.nuitcode.daytesk.data

import android.content.Context
import androidx.room.withTransaction
import com.nuitcode.daytesk.data.local.AppDatabase
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

/** A row inserted by [rollExpiredRecordatorios], paired with its new id. */
private data class RolledOccurrence(val id: Long, val entity: RecordatorioEntity)

/**
 * Rolls every expired recordatorio forward.
 *
 * For each row whose `fecha` is not in the future:
 *   - if `repeticion != NINGUNA`, the next occurrence is inserted with a fresh
 *     `cloudKey` and `fechaCreacion = updatedAt = now`, and its notifications
 *     are scheduled;
 *   - the expired original is deleted and its notifications cancelled.
 *
 * ## Atomicity and idempotency
 *
 * The read-modify-write runs inside [AppDatabase.withTransaction]. The in-app
 * sweep (`Navigation.kt`) and the at-due receiver (`ReminderReceiver`) can run
 * concurrently; without a transaction both would read the same expired row and
 * insert a next occurrence, producing a duplicate series. Because the read now
 * happens inside the transaction, the second writer observes the committed
 * state and finds nothing left to roll — a second pass is a no-op.
 *
 * ## Future advancement
 *
 * `Repeticion.nextDue(previousFecha)` can still land on or before `now` for a
 * long-dormant recordatorio. Inserting that past date would stall the series,
 * because [ReminderScheduler.scheduleRecordatorio] only registers alarms in the
 * future. The roll therefore keeps advancing until the next occurrence is
 * strictly in the future. `Repeticion.nextDue()` itself is left untouched.
 *
 * ## Side-effect ordering
 *
 * Notifications and alarm changes run only after the transaction commits, so a
 * rollback can never leave an alarm scheduled for a row that survived.
 *
 * @param nextCloudKey identity generator for the rolled occurrence. Injectable
 *   so tests can make the roll deterministic (and prove the transaction rolls
 *   back when an insertion fails midway).
 */
suspend fun rollExpiredRecordatorios(
    context: Context,
    database: AppDatabase,
    now: Long = System.currentTimeMillis(),
    nextCloudKey: () -> String = { UUID.randomUUID().toString() },
) {
    val dao = database.recordatorioDao()
    val rolled = mutableListOf<RolledOccurrence>()
    val expiredIds = mutableListOf<Long>()

    database.withTransaction {
        dao.getAllOnce().forEach { entity ->
            if (entity.fecha > now) return@forEach
            expiredIds += entity.id
            val repeticion = runCatching { Repeticion.valueOf(entity.repeticion) }
                .getOrDefault(Repeticion.NINGUNA)
            if (repeticion != Repeticion.NINGUNA) {
                var nextFecha = repeticion.nextDue(entity.fecha)
                while (nextFecha <= now) {
                    nextFecha = repeticion.nextDue(nextFecha)
                }
                val next = RecordatorioEntity(
                    id = 0,
                    texto = entity.texto,
                    fecha = nextFecha,
                    repeticion = entity.repeticion,
                    cloudKey = nextCloudKey(),
                    updatedAt = now,
                    fechaCreacion = now,
                )
                rolled += RolledOccurrence(dao.insert(next), next)
            }
            dao.delete(entity)
        }
    }

    // Post-commit side effects: the database is authoritative by now.
    rolled.forEach { occurrence ->
        ReminderScheduler.scheduleRecordatorio(
            context,
            occurrence.id,
            occurrence.entity.texto,
            occurrence.entity.fecha,
        )
    }
    expiredIds.forEach { ReminderScheduler.cancelRecordatorio(context, it) }
}
