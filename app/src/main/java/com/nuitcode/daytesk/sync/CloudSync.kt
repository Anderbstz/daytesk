package com.nuitcode.daytesk.sync

import android.content.Context
import androidx.room.withTransaction
import com.nuitcode.daytesk.auth.AuthApi
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.local.ContextoSeed
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.ContextoEntity
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import com.nuitcode.daytesk.data.local.TareaEntity
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.data.rollExpiredRecordatorios
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.Repeticion
import com.nuitcode.daytesk.notification.AlarmVibrationService
import com.nuitcode.daytesk.notification.ReminderScheduler
import com.nuitcode.daytesk.widget.NextTaskWidgetProvider
import com.nuitcode.daytesk.widget.RecordatorioWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class CloudSync(
    private val context: Context,
    private val database: AppDatabase,
    private val sessionStore: SessionStore,
    private val transport: SyncTransport = HttpSyncTransport,
) {
    suspend fun onStart() {
        val token = sessionStore.token ?: return
        mutex.withLock {
            runSync("pull") { pullOrPushLocked(token) }
        }
        NextTaskWidgetProvider.refresh(context)
        RecordatorioWidgetProvider.refresh(context)
    }

    suspend fun pushNow() {
        val token = sessionStore.token ?: return
        mutex.withLock {
            runSync("push") { pushLocked(token) }
        }
        NextTaskWidgetProvider.refresh(context)
        RecordatorioWidgetProvider.refresh(context)
    }

    /**
     * Pull from a background worker and re-arm the alarms.
     *
     * The UI path schedules alarms from the state emission, which does not exist
     * outside the app: without this, a task created in Discord would land in
     * Room but never ring until the user opened DayTesk. The roll surfaces any
     * occurrence whose alarm was late, the same way the in-app sweep does.
     */
    suspend fun onBackgroundSync() {
        onStart()
        rollExpiredRecordatorios(context, database)
        // Without this the server keeps the expired rows, the next pull returns
        // them and they ring again. A failure stays flagged for the next run.
        if (sessionStore.hasPendingPush) pushNow()
        val tareas = database.tareaDao().getAllOnce().map { it.toDomain() }
        ReminderScheduler.reschedulePending(context, tareas)
        val reminders = database.recordatorioDao().getAllOnce().map { it.toDomain() }
        ReminderScheduler.rescheduleRecordatorios(context, reminders)
    }

    /**
     * Runs a sync step and makes its outcome observable.
     *
     * A failure used to be swallowed by `runCatching` with no outbox, retry or
     * user feedback, so a change made while a push failed was silently lost on
     * the next pull (RESIL-003). The failure is now logged through [SyncLog] and
     * persisted as [SessionStore.lastSyncError], and the pending-push flag stays
     * set so the next `onStart` pushes before it pulls.
     *
     * Accepted limitation (deliberately not a full sync engine): there is no
     * retry scheduler and no durable outbox. A failed push is only retried on
     * the next `onStart`/`pushNow`, and there is no user-facing banner yet — the
     * persisted error is the observable signal for now.
     */
    private suspend fun runSync(stage: String, block: suspend () -> Unit) {
        try {
            block()
            sessionStore.lastSyncError = null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            val message = "${error::class.java.simpleName}: ${error.message.orEmpty()}"
            sessionStore.lastSyncError = message
            SyncLog.failure(stage, error)
        }
    }

    companion object {
        /**
         * Shared by every instance: the UI and the background worker each build
         * their own [CloudSync], and two concurrent applies must not interleave.
         */
        private val mutex = Mutex()

        private var debounceJob: Job? = null

        /**
         * App-lifetime scope for pushes that must outlive their caller.
         *
         * A widget capture finishes its Activity immediately; a push tied to
         * that Activity's `lifecycleScope` would be cancelled before it ran.
         */
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun schedulePush(scope: CoroutineScope, sync: CloudSync) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(900)
                sync.pushNow()
            }
        }

        /**
         * Enqueues the same debounced push on [appScope].
         *
         * Use this from short-lived callers (Activities, receivers) that finish
         * before the debounce elapses.
         */
        fun schedulePush(sync: CloudSync) {
            schedulePush(appScope, sync)
        }

        fun cancelScheduled() {
            debounceJob?.cancel()
            debounceJob = null
        }

        private val SAMPLE_TASK_TITLES = setOf(
            "Llamar al dentista",
            "Informe trimestral",
            "Sacar la basura",
            "Limpiar el garaje",
            "Leer un libro",
            "Comprar regalo cumpleaños",
        )
    }

    private suspend fun pullOrPushLocked(token: String) {
        val wipe = sessionStore.consumeWipeLocal()
        // Consumed for its side effect only: the union merge in [applyLocked]
        // now keeps every local-only row, so "keep local" is inherent. The flag
        // still has to be cleared so it cannot affect a later login.
        sessionStore.consumeKeepLocal()
        sessionStore.consumeNeedsPull()

        if (wipe) {
            // Explicit account switch: the user chose the new account, so local
            // user content is intentionally discarded, along with its alarms.
            database.tareaDao().getAllOnce().forEach {
                ReminderScheduler.cancelTaskReminder(context, it.id)
            }
            database.recordatorioDao().getAllOnce().forEach {
                ReminderScheduler.cancelRecordatorio(context, it.id)
            }
            database.tareaDao().deleteAll()
            database.recordatorioDao().deleteAll()
            // The local rows just disappeared, so any snooze armed against
            // them would re-post the previous account's text (RISK-001).
            ReminderScheduler.cancelAllSnoozes(context)
            val remote = stripLegacySamples(transport.pull(token).getOrThrow())
            if (remote.contextos.isNotEmpty()) {
                applyLocked(remote.copy(tareas = emptyList(), recordatorios = emptyList()))
            }
            ensureDefaultContextos()
            pushLocked(token)
            return
        }

        // A local write may not have reached the server yet. Push before
        // pulling so an incoming snapshot cannot overwrite it. If this throws,
        // the pull below never runs and the local data is preserved (RESIL-004).
        if (sessionStore.hasPendingPush) {
            pushLocked(token)
        }

        val remote = stripLegacySamples(transport.pull(token).getOrThrow())
        // Tareas and recordatorios are the only synced lists that carry user
        // content; either one makes the remote account non-empty.
        val remoteHasUserContent = remote.tareas.isNotEmpty() || remote.recordatorios.isNotEmpty()

        if (!remoteHasUserContent) {
            // The remote holds no user content, so any local rows are the only
            // copy that exists. Wiping them here is exactly how a widget-captured
            // recordatorio used to be destroyed; push local instead (RESIL-004).
            // Mirrors still reconcile: a healthy, empty feed means they are gone.
            val hasMirrors = database.recordatorioDao().getAllOnce().any { it.origin == "LIMEKATZE" }
            if (remote.remoteError == null && hasMirrors) {
                applyLocked(remote)
            }
            ensureDefaultContextos()
            pushLocked(token)
            return
        }

        applyLocked(remote)
        ensureDefaultContextos()
    }

    private fun stripLegacySamples(snapshot: AuthApi.SyncSnapshot): AuthApi.SyncSnapshot {
        val tareas = snapshot.tareas.filterNot { it.titulo in SAMPLE_TASK_TITLES }
        // Only task titles need the legacy-sample filter: recordatorios are a
        // new list with no historical seed data to strip.
        return snapshot.copy(tareas = tareas)
    }

    private suspend fun ensureDefaultContextos() {
        val dao = database.contextoDao()
        if (dao.getAllOnce().isNotEmpty()) return
        ContextoSeed.entries.forEach { seed -> dao.insert(seed.toEntity()) }
    }

    /** Rows a pull removed, so their alarms can be dropped after the commit. */
    private class RemovedRows(val tareaIds: List<Long>, val recordatorioIds: List<Long>)

    /**
     * Reconciles the local store with the remote snapshot by `cloudKey`, keeping
     * any row that exists only locally (see [mergePreservingLocal]).
     *
     * ## Why rows are updated instead of replaced
     *
     * Alarms and snoozes are keyed by row id. Wiping and re-inserting gave every
     * row a new id on each pull, which left armed alarms pointing at rows that
     * no longer existed: they fired, found nothing and stayed mute. An unchanged
     * row now keeps its id, so its alarm stays valid across any number of pulls.
     *
     * Everything runs inside one [withSyncTransaction], so a crash or DB error
     * mid-pull can never leave the local store half-applied (RESIL-002). The
     * local snapshot is read inside the same transaction, so the merge cannot
     * observe a concurrent write either.
     */
    private suspend fun applyLocked(snapshot: AuthApi.SyncSnapshot) {
        val removed = database.withSyncTransaction {
            // Keys are the identity below, so none may be blank from here on.
            persistMissingCloudKeys()
            val contextoDao = database.contextoDao()
            val tareaDao = database.tareaDao()
            val recordatorioDao = database.recordatorioDao()
            val localTareas = tareaDao.getAllOnce().associateBy { it.cloudKey }
            val localRecordatorios = recordatorioDao.getAllOnce().associateBy { it.cloudKey }
            val reconciled = snapshot.mergePreservingLocal(localSnapshot())
                .withoutMirrorsArrivingExpired(localRecordatorios.keys, System.currentTimeMillis())
            val merged = reconciled.withoutRecordatorioCopies(localRecordatorios.keys)
            contextoDao.deleteAll()
            val keyToId = mutableMapOf<String, Long>()
            merged.contextos.sortedBy { it.orden }.forEach { item ->
                val id = contextoDao.insert(
                    ContextoEntity(
                        id = 0,
                        nombre = item.nombre,
                        color = item.color,
                        iconId = item.iconId,
                        orden = item.orden,
                        esDefault = item.esDefault,
                        cloudKey = item.cloudKey.ifBlank { UUID.randomUUID().toString() },
                        updatedAt = item.updatedAt,
                    ),
                )
                keyToId[item.cloudKey] = id
            }
            val fallbackId = keyToId.values.firstOrNull() ?: Contexto.FALLBACK_ID
            merged.tareas.forEach { item ->
                val existing = localTareas[item.cloudKey]
                val entity = TareaEntity(
                    id = existing?.id ?: 0,
                    titulo = item.titulo,
                    descripcion = item.descripcion,
                    prioridad = item.prioridad,
                    contextoId = keyToId[item.contextoKey] ?: fallbackId,
                    estado = item.estado,
                    fechaCreacion = item.fechaCreacion,
                    fechaVencimiento = item.fechaVencimiento,
                    fechaCompletada = item.fechaCompletada,
                    orden = item.orden,
                    repeticion = runCatching { Repeticion.valueOf(item.repeticion) }
                        .getOrDefault(Repeticion.NINGUNA).name,
                    cloudKey = item.cloudKey.ifBlank { UUID.randomUUID().toString() },
                    updatedAt = item.updatedAt,
                )
                if (existing == null) tareaDao.insertTarea(entity) else tareaDao.updateTarea(entity)
            }
            merged.recordatorios.forEach { item ->
                val existing = localRecordatorios[item.cloudKey]
                val isRemote = item.origin == "LIMEKATZE"
                val entity = RecordatorioEntity(
                    id = existing?.id ?: 0,
                    texto = item.texto,
                    fecha = item.fecha,
                    repeticion = runCatching { Repeticion.valueOf(item.repeticion) }
                        .getOrDefault(Repeticion.NINGUNA).name,
                    cloudKey = item.cloudKey.ifBlank { UUID.randomUUID().toString() },
                    updatedAt = item.updatedAt,
                    fechaCreacion = item.fechaCreacion,
                    // The server contract has no `esAlarma` field, so the local
                    // flag is the only copy. Mirrored items always alarm: that
                    // is the whole point of bringing them into DayTesk.
                    esAlarma = isRemote || existing?.esAlarma == true,
                    origin = item.origin,
                    remoteKey = item.remoteKey,
                    silenciado = item.silenciado,
                    // A mirror moved at the source is a new occurrence to notify.
                    notificadaEn = existing?.notificadaEn
                        ?.takeIf { !isRemote || existing?.fecha == item.fecha },
                )
                if (existing == null) recordatorioDao.insert(entity) else recordatorioDao.update(entity)
            }

            val mergedTareaKeys = merged.tareas.map { it.cloudKey }.toSet()
            val mergedRecordatorioKeys = merged.recordatorios.map { it.cloudKey }.toSet()
            val removedTareas = localTareas.filterKeys { it !in mergedTareaKeys }.values
            val removedRecordatorios =
                localRecordatorios.filterKeys { it !in mergedRecordatorioKeys }.values
            removedTareas.forEach { tareaDao.deleteTarea(it) }
            removedRecordatorios.forEach { recordatorioDao.delete(it) }
            // The server may still hold what was just collapsed.
            if (reconciled.recordatorios.size > merged.recordatorios.size) {
                sessionStore.hasPendingPush = true
            }
            RemovedRows(removedTareas.map { it.id }, removedRecordatorios.map { it.id })
        }
        // After the commit, so a rolled-back apply leaves alarms untouched. Only
        // rows that really disappeared lose theirs: pending snoozes of surviving
        // rows, and of rows already rolled by their at-due alarm, keep working.
        removed.tareaIds.forEach { ReminderScheduler.cancelTaskReminder(context, it) }
        removed.recordatorioIds.forEach { id ->
            ReminderScheduler.cancelRecordatorio(context, id)
            ReminderScheduler.cancelSnooze(context, id)
            AlarmVibrationService.stopIfRinging(context, id)
        }
    }

    /** The local store as a sync snapshot, with `cloudKey`s normalized. */
    private suspend fun localSnapshot(): AuthApi.SyncSnapshot {
        val contextos = database.contextoDao().getAllOnce()
        val tareas = database.tareaDao().getAllOnce()
        val recordatorios = database.recordatorioDao().getAllOnce()
        val contextoKeyById = contextos.associate { entity ->
            entity.id to entity.cloudKey.ifBlank {
                if (entity.id in 1L..4L) "default-${entity.id}" else UUID.randomUUID().toString()
            }
        }
        return AuthApi.SyncSnapshot(
            contextos = contextos.map { entity ->
                AuthApi.SyncContextoDto(
                    cloudKey = contextoKeyById.getValue(entity.id),
                    nombre = entity.nombre,
                    color = entity.color,
                    iconId = entity.iconId,
                    orden = entity.orden,
                    esDefault = entity.esDefault,
                    updatedAt = entity.updatedAt,
                )
            },
            tareas = tareas.map { entity ->
                AuthApi.SyncTareaDto(
                    cloudKey = entity.cloudKey.ifBlank { UUID.randomUUID().toString() },
                    titulo = entity.titulo,
                    descripcion = entity.descripcion,
                    prioridad = entity.prioridad,
                    contextoKey = contextoKeyById[entity.contextoId]
                        ?: contextoKeyById.values.firstOrNull()
                        ?: "default-3",
                    estado = entity.estado,
                    fechaCreacion = entity.fechaCreacion,
                    fechaVencimiento = entity.fechaVencimiento,
                    fechaCompletada = entity.fechaCompletada,
                    orden = entity.orden,
                    repeticion = entity.repeticion,
                    updatedAt = entity.updatedAt,
                )
            },
            recordatorios = recordatorios.map { entity ->
                AuthApi.SyncRecordatorioDto(
                    cloudKey = entity.cloudKey.ifBlank { UUID.randomUUID().toString() },
                    texto = entity.texto,
                    fecha = entity.fecha,
                    repeticion = entity.repeticion,
                    fechaCreacion = entity.fechaCreacion,
                    updatedAt = entity.updatedAt,
                    origin = entity.origin,
                    remoteKey = entity.remoteKey,
                    silenciado = entity.silenciado,
                ).withMirrorOriginRestored()
            },
        )
    }

    /**
     * Pushes the whole local store.
     *
     * The pending flag is set *before* the network call and cleared only after
     * it succeeds, so a failed or interrupted push leaves the local change
     * protected from the next pull.
     */
    private suspend fun pushLocked(token: String) {
        sessionStore.hasPendingPush = true
        persistMissingCloudKeys()
        transport.push(token, localSnapshot()).getOrThrow()
        sessionStore.hasPendingPush = false
    }

    /**
     * Assigns a stable `cloudKey` to every legacy row that still has a blank
     * one, and persists it before the snapshot is built.
     *
     * ## Why this exists
     *
     * `localSnapshot()` used to mint a fresh random UUID for each blank key on
     * every push, without writing it back to Room. The server replaces rows by
     * `(user_id, cloud_key)`, so every sync invented a new identity and the
     * account accumulated byte-identical duplicates with different keys.
     *
     * ## Why runtime and not a SQL migration
     *
     * SQLite has no UUID generator, so a migration would need
     * `hex(randomblob(16))` plus per-table UPDATEs. Doing it in Kotlin keeps one
     * canonical UUID source ([UUID]) and the existing `default-<id>` convention
     * for the four seeded contextos (ids 1..4) so already-synced cloud data
     * keeps matching.
     *
     * Runs in one [withSyncTransaction], so it is atomic, and only touches
     * blank keys, so it is idempotent and never rewrites an identity that
     * already exists.
     */
    private suspend fun persistMissingCloudKeys() {
        database.withSyncTransaction {
            val tareaDao = database.tareaDao()
            tareaDao.getAllOnce().forEach { entity ->
                if (entity.cloudKey.isBlank()) {
                    tareaDao.updateTarea(entity.copy(cloudKey = UUID.randomUUID().toString()))
                }
            }

            val recordatorioDao = database.recordatorioDao()
            recordatorioDao.getAllOnce().forEach { entity ->
                if (entity.cloudKey.isBlank()) {
                    recordatorioDao.update(entity.copy(cloudKey = UUID.randomUUID().toString()))
                }
            }

            val contextoDao = database.contextoDao()
            contextoDao.getAllOnce().forEach { entity ->
                if (entity.cloudKey.isBlank()) {
                    val key = if (entity.id in 1L..4L) {
                        "default-${entity.id}"
                    } else {
                        UUID.randomUUID().toString()
                    }
                    contextoDao.update(entity.copy(cloudKey = key))
                }
            }
        }
    }
}
