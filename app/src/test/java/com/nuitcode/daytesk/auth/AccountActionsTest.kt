package com.nuitcode.daytesk.auth

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.RecordatorioEntity
import com.nuitcode.daytesk.notification.ReminderScheduler
import com.nuitcode.daytesk.notification.SnoozeStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * RISK-001 — the identity-invalidating call sites cancel pending snoozes.
 *
 * [AccountActions] is the testable seam behind the logout button and the
 * registration/account-switch wipe in `LoginScreen`. These tests prove the
 * operations that those call sites invoke really drop the outgoing account's
 * alarms instead of only deleting Room rows.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AccountActionsTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daytesk_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        SnoozeStore.clear(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SessionStore(context).save("token", "Ander", "ander@example.com")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snoozeAlarms() =
        shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms
            .filter {
                shadowOf(it.operation).savedIntent.data.toString().endsWith("/snooze")
            }

    @Test
    fun logout_cancelsPendingSnoozesAndClearsTheSession() {
        ReminderScheduler.scheduleSnooze(context, id = 61L, texto = "Old account reminder")

        AccountActions.logout(context)

        assertTrue("logout must cancel pending snoozes", snoozeAlarms().isEmpty())
        assertTrue("logout must clear the snooze registry", SnoozeStore.all(context).isEmpty())
        assertFalse("logout must still end the session", SessionStore(context).isLoggedIn)
    }

    @Test
    fun discardLocalContent_wipesRowsAndCancelsPendingSnoozes() = runTest {
        db.recordatorioDao().insert(
            RecordatorioEntity(
                texto = "Old account reminder",
                fecha = System.currentTimeMillis() + 3_600_000L,
                repeticion = "NINGUNA",
                cloudKey = "old-1",
                updatedAt = 1L,
                fechaCreacion = 1L,
            ),
        )
        ReminderScheduler.scheduleSnooze(context, id = 62L, texto = "Old account reminder")

        AccountActions.discardLocalContent(context, db)

        assertEquals("the local rows must be wiped", 0, db.recordatorioDao().getAllOnce().size)
        assertTrue("the outgoing account's snooze must be cancelled", snoozeAlarms().isEmpty())
        assertTrue("the snooze registry must be cleared", SnoozeStore.all(context).isEmpty())
    }
}
