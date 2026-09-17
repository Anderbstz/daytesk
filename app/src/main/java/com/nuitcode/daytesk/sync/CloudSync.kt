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
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.Repeticion
import com.nuitcode.daytesk.widget.NextTaskWidgetProvider
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
    private val mutex = Mutex()

    suspend fun onStart() {
        val token = sessionStore.token ?: return
        mutex.withLock {
            runCatching { pullOrPushLocked(token) }
        }
        NextTaskWidgetProvider.refresh(context)
    }

    suspend fun pushNow() {
        val token = sessionStore.token ?: return
        mutex.withLock {
            runCatching { pushLocked(token) }
        }
        NextTaskWidgetProvider.refresh(context)
    }

    companion object {
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
            // user content is intentionally discarded.
            database.tareaDao().deleteAll()
            database.recordatorioDao().deleteAll()
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

    /**
     * Replaces the local store with the remote snapshot, keeping any row that
     * exists only locally (see [mergePreservingLocal]).
     *
     * The whole wipe + re-insert runs inside one [withSyncTransaction], so a
     * crash or DB error mid-pull can never leave the local store empty or
     * half-applied (RESIL-002). The local snapshot is read inside the same
     * transaction, so the merge cannot observe a concurrent write either.
     */
    private suspend fun applyLocked(snapshot: AuthApi.SyncSnapshot) {
        database.withSyncTransaction {
            val merged = snapshot.mergePreservingLocal(localSnapshot())

            val contextoDao = database.contextoDao()
            val tareaDao = database.tareaDao()
            val recordatorioDao = database.recordatorioDao()
            tareaDao.deleteAll()
            recordatorioDao.deleteAll()
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
                tareaDao.insertTarea(
                    TareaEntity(
                        id = 0,
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
                    ),
                )
            }
            merged.recordatorios.forEach { item ->
                recordatorioDao.insert(
                    RecordatorioEntity(
                        id = 0,
                        texto = item.texto,
                        fecha = item.fecha,
                        repeticion = runCatching { Repeticion.valueOf(item.repeticion) }
                            .getOrDefault(Repeticion.NINGUNA).name,
                        cloudKey = item.cloudKey.ifBlank { UUID.randomUUID().toString() },
                        updatedAt = item.updatedAt,
                        fechaCreacion = item.fechaCreacion,
                    ),
                )
            }
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
                )
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
        transport.push(token, localSnapshot()).getOrThrow()
        sessionStore.hasPendingPush = false
    }
}
