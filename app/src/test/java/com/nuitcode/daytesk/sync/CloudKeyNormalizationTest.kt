package com.nuitcode.daytesk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.auth.AuthApi
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.ContextoEntity
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import com.nuitcode.daytesk.data.local.TareaEntity
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression coverage for the stable-identity bug.
 *
 * A row with a blank `cloudKey` used to get a fresh random UUID in memory on
 * every push, and the key was never written back to Room. The server replaces
 * by `(user_id, cloud_key)`, so each sync created another row: the same
 * recordatorio accumulated byte-identical duplicates with different keys.
 *
 * The push path must now assign and persist a stable key before building the
 * snapshot, idempotently and without touching existing keys.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CloudKeyNormalizationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var sessionStore: SessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daytesk_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionStore = SessionStore(context)
        sessionStore.save("token", "Ander", "ander@example.com")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private class RecordingTransport : SyncTransport {
        val pushed = mutableListOf<AuthApi.SyncSnapshot>()

        override suspend fun pull(token: String): Result<AuthApi.SyncSnapshot> =
            Result.success(AuthApi.SyncSnapshot(emptyList(), emptyList(), emptyList()))

        override suspend fun push(
            token: String,
            snapshot: AuthApi.SyncSnapshot,
        ): Result<Unit> {
            pushed += snapshot
            return Result.success(Unit)
        }
    }

    private suspend fun insertBlankTarea(): Long = db.tareaDao().insertTarea(
        TareaEntity(
            titulo = "legacy task",
            cloudKey = "",
            updatedAt = 1L,
        ),
    )

    private suspend fun insertBlankRecordatorio(): Long = db.recordatorioDao().insert(
        RecordatorioEntity(
            texto = "legacy reminder",
            fecha = 1L,
            repeticion = "NINGUNA",
            cloudKey = "",
            updatedAt = 1L,
            fechaCreacion = 1L,
        ),
    )

    private suspend fun insertBlankContexto(id: Long, nombre: String): Long =
        db.contextoDao().insert(
            ContextoEntity(
                id = id,
                nombre = nombre,
                color = 0,
                iconId = null,
                orden = id.toInt(),
                esDefault = id in 1L..4L,
                cloudKey = "",
                updatedAt = 1L,
            ),
        )

    private fun assertUuid(key: String) {
        assertFalse("a normalized key must not stay blank", key.isBlank())
        // Throws (and fails the test) when the value is not a real UUID.
        UUID.fromString(key)
    }

    @Test
    fun aBlankCloudKeyIsAssignedAndPersistedForEveryEntityType() = runTest {
        val tareaId = insertBlankTarea()
        val recordatorioId = insertBlankRecordatorio()
        // id 9 is a custom context, so it must get a UUID rather than default-<id>.
        val contextoId = insertBlankContexto(id = 9, nombre = "legacy ctx")
        val transport = RecordingTransport()

        CloudSync(context, db, sessionStore, transport).pushNow()

        val tarea = db.tareaDao().getTareaById(tareaId)
        val recordatorio = db.recordatorioDao().getById(recordatorioId)
        val contexto = db.contextoDao().getById(contextoId)
        assertNotNull(tarea)
        assertNotNull(recordatorio)
        assertNotNull(contexto)
        assertUuid(tarea!!.cloudKey)
        assertUuid(recordatorio!!.cloudKey)
        assertUuid(contexto!!.cloudKey)

        val pushed = transport.pushed.single()
        assertEquals("the pushed key must be the persisted one", tarea.cloudKey, pushed.tareas.single().cloudKey)
        assertEquals(
            "the pushed key must be the persisted one",
            recordatorio.cloudKey,
            pushed.recordatorios.single().cloudKey,
        )
        assertEquals(
            "the pushed key must be the persisted one",
            contexto.cloudKey,
            pushed.contextos.single().cloudKey,
        )
    }

    @Test
    fun aLegacyDefaultContextNormalizesToItsStableDefaultKey() = runTest {
        insertBlankContexto(id = 2, nombre = "trabajo")
        val transport = RecordingTransport()

        CloudSync(context, db, sessionStore, transport).pushNow()

        assertEquals("default-2", db.contextoDao().getById(2)?.cloudKey)
        assertEquals("default-2", transport.pushed.single().contextos.single().cloudKey)
    }

    @Test
    fun normalizationIsIdempotent() = runTest {
        val tareaId = insertBlankTarea()
        val recordatorioId = insertBlankRecordatorio()
        val contextoId = insertBlankContexto(id = 9, nombre = "legacy ctx")
        val sync = CloudSync(context, db, sessionStore, RecordingTransport())

        sync.pushNow()
        val firstTareaKey = db.tareaDao().getTareaById(tareaId)!!.cloudKey
        val firstRecordatorioKey = db.recordatorioDao().getById(recordatorioId)!!.cloudKey
        val firstContextoKey = db.contextoDao().getById(contextoId)!!.cloudKey

        sync.pushNow()

        assertEquals(firstTareaKey, db.tareaDao().getTareaById(tareaId)!!.cloudKey)
        assertEquals(firstRecordatorioKey, db.recordatorioDao().getById(recordatorioId)!!.cloudKey)
        assertEquals(firstContextoKey, db.contextoDao().getById(contextoId)!!.cloudKey)
    }

    @Test
    fun aNonBlankCloudKeyIsNeverModified() = runTest {
        val tareaId = db.tareaDao().insertTarea(
            TareaEntity(titulo = "synced task", cloudKey = "task-key", updatedAt = 1L),
        )
        val recordatorioId = db.recordatorioDao().insert(
            RecordatorioEntity(
                texto = "synced reminder",
                fecha = 1L,
                repeticion = "NINGUNA",
                cloudKey = "rec-key",
                updatedAt = 1L,
                fechaCreacion = 1L,
            ),
        )
        val contextoId = insertBlankContexto(id = 9, nombre = "synced ctx").let {
            db.contextoDao().update(
                db.contextoDao().getById(it)!!.copy(cloudKey = "ctx-key"),
            )
            it
        }
        val transport = RecordingTransport()
        val sync = CloudSync(context, db, sessionStore, transport)

        sync.pushNow()
        sync.pushNow()

        assertEquals("task-key", db.tareaDao().getTareaById(tareaId)?.cloudKey)
        assertEquals("rec-key", db.recordatorioDao().getById(recordatorioId)?.cloudKey)
        assertEquals("ctx-key", db.contextoDao().getById(contextoId)?.cloudKey)
        assertEquals("task-key", transport.pushed[0].tareas.single().cloudKey)
        assertEquals("task-key", transport.pushed[1].tareas.single().cloudKey)
        assertEquals("rec-key", transport.pushed[0].recordatorios.single().cloudKey)
        assertEquals("rec-key", transport.pushed[1].recordatorios.single().cloudKey)
        assertEquals("ctx-key", transport.pushed[0].contextos.single().cloudKey)
        assertEquals("ctx-key", transport.pushed[1].contextos.single().cloudKey)
    }

    @Test
    fun consecutivePushesOfTheSameBlankRowUseTheSameCloudKey() = runTest {
        insertBlankRecordatorio()
        val transport = RecordingTransport()
        val sync = CloudSync(context, db, sessionStore, transport)

        sync.pushNow()
        sync.pushNow()

        val first = transport.pushed[0].recordatorios.single()
        val second = transport.pushed[1].recordatorios.single()
        assertEquals("the same row must keep one identity across pushes", first.cloudKey, second.cloudKey)
        assertEquals(
            "a stable key must not duplicate the row",
            transport.pushed[0].recordatorios.size,
            transport.pushed[1].recordatorios.size,
        )
    }
}
