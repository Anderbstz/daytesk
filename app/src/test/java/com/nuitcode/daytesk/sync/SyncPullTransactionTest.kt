package com.nuitcode.daytesk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * RESIL-002: the pull's wipe + re-insert must be all-or-nothing.
 *
 * A crash or DB error midway through [CloudSync]'s apply step used to leave the
 * local store empty or partial, because the three `deleteAll` calls committed
 * independently of the re-insert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SyncPullTransactionTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(): Long = db.recordatorioDao().insert(
        RecordatorioEntity(
            texto = "keep me",
            fecha = 1L,
            repeticion = "NINGUNA",
            cloudKey = "rec-1",
            updatedAt = 1L,
            fechaCreacion = 1L,
        ),
    )

    @Test
    fun aFailureMidApply_rollsBackTheWipeAndLeavesTheStoreIntact() = runTest {
        val id = seed()

        val thrown = try {
            db.withSyncTransaction {
                db.recordatorioDao().deleteAll()
                throw IllegalStateException("disk full")
            }
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertNotNull("the failure must propagate to the caller", thrown)
        assertNotNull(
            "a failed pull must not leave the local store empty",
            db.recordatorioDao().getById(id),
        )
        assertEquals(1, db.recordatorioDao().getAllOnce().size)
    }

    @Test
    fun aSuccessfulApply_commitsEveryWrite() = runTest {
        db.withSyncTransaction {
            seed()
        }

        assertEquals(1, db.recordatorioDao().getAllOnce().size)
    }
}
