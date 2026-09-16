package com.nuitcode.daytesk.sync

import android.content.Context
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class CloudSync(
    private val context: Context,
    private val database: AppDatabase,
    private val sessionStore: SessionStore,
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

        fun schedulePush(scope: CoroutineScope, sync: CloudSync) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(900)
                sync.pushNow()
            }
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
        val keepLocal = sessionStore.consumeKeepLocal()
        sessionStore.consumeNeedsPull()
        val remote = stripLegacySamples(AuthApi.pullSync(token).getOrThrow())
        // Tareas and recordatorios are the only synced lists that carry user
        // content; either one makes the remote account non-empty.
        val remoteHasUserContent = remote.tareas.isNotEmpty() || remote.recordatorios.isNotEmpty()

        if (keepLocal && !remoteHasUserContent) {
            ensureDefaultContextos()
            pushLocked(token)
            return
        }

        if (wipe || !remoteHasUserContent) {
            database.tareaDao().deleteAll()
            database.recordatorioDao().deleteAll()
            if (remote.contextos.isNotEmpty()) {
                applyLocked(remote.copy(tareas = emptyList(), recordatorios = emptyList()))
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

    private suspend fun applyLocked(snapshot: AuthApi.SyncSnapshot) {
        val contextoDao = database.contextoDao()
        val tareaDao = database.tareaDao()
        val recordatorioDao = database.recordatorioDao()
        tareaDao.deleteAll()
        recordatorioDao.deleteAll()
        contextoDao.deleteAll()
        val keyToId = mutableMapOf<String, Long>()
        snapshot.contextos.sortedBy { it.orden }.forEach { item ->
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
        snapshot.tareas.forEach { item ->
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
        snapshot.recordatorios.forEach { item ->
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

    private suspend fun pushLocked(token: String) {
        val contextos = database.contextoDao().getAllOnce()
        val tareas = database.tareaDao().getAllOnce()
        val recordatorios = database.recordatorioDao().getAllOnce()
        val contextoKeyById = contextos.associate { entity ->
            entity.id to entity.cloudKey.ifBlank {
                if (entity.id in 1L..4L) "default-${entity.id}" else UUID.randomUUID().toString()
            }
        }
        val snapshot = AuthApi.SyncSnapshot(
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
        AuthApi.pushSync(token, snapshot).getOrThrow()
    }
}
