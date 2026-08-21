package com.nuitcode.daytesk.sync

import android.content.Context
import com.nuitcode.daytesk.auth.AuthApi
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.local.ContextoSeed
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.ContextoEntity
import com.nuitcode.daytesk.data.local.InboxItemEntity
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
        private val SAMPLE_INBOX = setOf(
            "Revisar correo del banco",
            "Buscar vuelos para vacaciones",
            "Comprar nuevo monitor",
            "Llamar al seguro médico",
            "Actualizar CV",
        )
    }

    private suspend fun pullOrPushLocked(token: String) {
        val wipe = sessionStore.consumeWipeLocal()
        val keepLocal = sessionStore.consumeKeepLocal()
        sessionStore.consumeNeedsPull()
        val remote = stripLegacySamples(AuthApi.pullSync(token).getOrThrow())
        val remoteHasUserContent = remote.tareas.isNotEmpty() || remote.inbox.isNotEmpty()

        if (keepLocal && !remoteHasUserContent) {
            ensureDefaultContextos()
            pushLocked(token)
            return
        }

        if (wipe || !remoteHasUserContent) {
            database.tareaDao().deleteAll()
            database.inboxItemDao().deleteAll()
            if (remote.contextos.isNotEmpty()) {
                applyLocked(remote.copy(tareas = emptyList(), inbox = emptyList()))
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
        val inbox = snapshot.inbox.filterNot { it.texto in SAMPLE_INBOX }
        return snapshot.copy(tareas = tareas, inbox = inbox)
    }

    private suspend fun ensureDefaultContextos() {
        val dao = database.contextoDao()
        if (dao.getAllOnce().isNotEmpty()) return
        ContextoSeed.entries.forEach { seed -> dao.insert(seed.toEntity()) }
    }

    private suspend fun applyLocked(snapshot: AuthApi.SyncSnapshot) {
        val contextoDao = database.contextoDao()
        val tareaDao = database.tareaDao()
        val inboxDao = database.inboxItemDao()
        tareaDao.deleteAll()
        inboxDao.deleteAll()
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
        snapshot.inbox.forEach { item ->
            inboxDao.insertItem(
                InboxItemEntity(
                    id = 0,
                    texto = item.texto,
                    timestamp = item.timestamp,
                    procesado = item.procesado,
                    cloudKey = item.cloudKey.ifBlank { UUID.randomUUID().toString() },
                    updatedAt = item.updatedAt,
                ),
            )
        }
    }

    private suspend fun pushLocked(token: String) {
        val contextos = database.contextoDao().getAllOnce()
        val tareas = database.tareaDao().getAllOnce()
        val inbox = database.inboxItemDao().getAllOnce()
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
            inbox = inbox.map { entity ->
                AuthApi.SyncInboxDto(
                    cloudKey = entity.cloudKey.ifBlank { UUID.randomUUID().toString() },
                    texto = entity.texto,
                    timestamp = entity.timestamp,
                    procesado = entity.procesado,
                    updatedAt = entity.updatedAt,
                )
            },
        )
        AuthApi.pushSync(token, snapshot).getOrThrow()
    }
}
