package com.nuitcode.daytesk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.auth.AuthApi
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * RESIL-004, end-to-end pull behaviour with a fake transport.
 *
 * A recordatorio captured from the widget is written locally and its push is
 * best-effort. The next `onStart` must not destroy it, whether the remote is
 * empty or already holds other rows.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CloudSyncTest {

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

    private class FakeTransport(
        private val pullResult: Result<AuthApi.SyncSnapshot>,
        private val pushResult: Result<Unit> = Result.success(Unit),
    ) : SyncTransport {
        val pushed = mutableListOf<AuthApi.SyncSnapshot>()
        var pullCount = 0
            private set

        override suspend fun pull(token: String): Result<AuthApi.SyncSnapshot> {
            pullCount += 1
            return pullResult
        }

        override suspend fun push(
            token: String,
            snapshot: AuthApi.SyncSnapshot,
        ): Result<Unit> {
            pushed += snapshot
            return pushResult
        }
    }

    private fun emptySnapshot() = AuthApi.SyncSnapshot(emptyList(), emptyList(), emptyList())

    private suspend fun insertLocalRecordatorio(cloudKey: String): Long =
        db.recordatorioDao().insert(
            RecordatorioEntity(
                texto = "captured from widget",
                fecha = System.currentTimeMillis() + 3_600_000L,
                repeticion = "NINGUNA",
                cloudKey = cloudKey,
                updatedAt = 1L,
                fechaCreacion = 1L,
            ),
        )

    @Test
    fun onStart_withEmptyRemote_doesNotDestroyALocallyCapturedRecordatorio() = runTest {
        val id = insertLocalRecordatorio("local-1")
        val transport = FakeTransport(Result.success(emptySnapshot()))

        CloudSync(context, db, sessionStore, transport).onStart()

        assertNotNull(
            "an unpushed local recordatorio must survive an empty-remote pull",
            db.recordatorioDao().getById(id),
        )
    }

    @Test
    fun onStart_withRemoteContent_keepsTheUnpushedLocalRowAlongsideTheRemoteOnes() = runTest {
        insertLocalRecordatorio("local-1")
        val remote = AuthApi.SyncSnapshot(
            contextos = emptyList(),
            tareas = emptyList(),
            recordatorios = listOf(
                AuthApi.SyncRecordatorioDto(
                    cloudKey = "remote-1",
                    texto = "from another device",
                    fecha = 1L,
                    repeticion = "NINGUNA",
                    fechaCreacion = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val transport = FakeTransport(Result.success(remote))

        CloudSync(context, db, sessionStore, transport).onStart()

        assertEquals(
            "the pull must merge, not replace",
            setOf("local-1", "remote-1"),
            db.recordatorioDao().getAllOnce().map { it.cloudKey }.toSet(),
        )
    }

    @Test
    fun onStart_withLocalContentAndEmptyRemote_pushesTheLocalRow() = runTest {
        insertLocalRecordatorio("local-1")
        val transport = FakeTransport(Result.success(emptySnapshot()))

        CloudSync(context, db, sessionStore, transport).onStart()

        assertEquals("local content must be pushed when the remote is empty", 1, transport.pushed.size)
        assertEquals("local-1", transport.pushed.single().recordatorios.single().cloudKey)
    }

    @Test
    fun aSuccessfulPush_clearsThePendingPushFlag() = runTest {
        sessionStore.hasPendingPush = true
        val transport = FakeTransport(Result.success(emptySnapshot()))

        CloudSync(context, db, sessionStore, transport).onStart()

        assertFalse(
            "a confirmed push must clear the pending flag",
            sessionStore.hasPendingPush,
        )
    }

    // ── RESIL-003: failures must be observable, not swallowed ──────────────

    @Test
    fun aFailedPull_isReportedInsteadOfSilentlyDiscarded() = runTest {
        val transport = FakeTransport(Result.failure(IllegalStateException("server down")))

        CloudSync(context, db, sessionStore, transport).onStart()

        val error = sessionStore.lastSyncError
        assertNotNull("a sync failure must be surfaced", error)
        assertTrue("the message must name the cause, got $error", error!!.contains("server down"))
        assertTrue(
            "the failure must also reach the sync log",
            ShadowLog.getLogsForTag(SyncLog.TAG).isNotEmpty(),
        )
    }

    @Test
    fun aFailedPush_keepsTheLocalChangeFlaggedAndIsReported() = runTest {
        insertLocalRecordatorio("local-1")
        sessionStore.hasPendingPush = true
        val transport = FakeTransport(
            pullResult = Result.success(emptySnapshot()),
            pushResult = Result.failure(IllegalStateException("offline")),
        )

        CloudSync(context, db, sessionStore, transport).onStart()

        assertNotNull("a failed push must be surfaced", sessionStore.lastSyncError)
        assertTrue(
            "an unpushed change must stay flagged for the next attempt",
            sessionStore.hasPendingPush,
        )
        assertEquals("the push must be attempted", 1, transport.pushed.size)
        assertEquals(
            "a failed push must defer the pull instead of overwriting local state",
            0,
            transport.pullCount,
        )
    }

    @Test
    fun aSuccessfulSync_clearsAPreviousError() = runTest {
        sessionStore.lastSyncError = "old failure"
        val transport = FakeTransport(Result.success(emptySnapshot()))

        CloudSync(context, db, sessionStore, transport).onStart()

        assertNull("a successful sync must clear the recorded error", sessionStore.lastSyncError)
    }
}
