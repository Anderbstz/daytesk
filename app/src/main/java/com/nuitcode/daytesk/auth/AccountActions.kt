package com.nuitcode.daytesk.auth

import android.content.Context
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.notification.AlarmVibrationService
import com.nuitcode.daytesk.notification.ReminderScheduler
import com.nuitcode.daytesk.sync.CloudSync

/**
 * Account-lifecycle operations that invalidate the local identity.
 *
 * A pending snooze is posted from Intent extras and never reads Room, so an
 * alarm armed under one account would still show that account's reminder text
 * after a logout, an account switch or a local wipe. Both entry points below
 * cancel every pending snooze and clear its registry (RISK-001).
 *
 * The same invalidation must also silence an alarm that is ringing **right now**:
 * the ring carries the outgoing account's text, so it cannot survive the
 * identity that started it.
 */
object AccountActions {
    /** Ends the session and cancels anything tied to the outgoing identity. */
    fun logout(context: Context) {
        CloudSync.cancelScheduled()
        ReminderScheduler.cancelAllSnoozes(context)
        AlarmVibrationService.stopAnyRinging(context)
        SessionStore(context).clear()
    }

    /**
     * Discards the local user content for an account switch.
     *
     * The rows are deleted and any snooze scheduled against them is cancelled,
     * so the incoming account cannot inherit the outgoing account's alarms.
     */
    suspend fun discardLocalContent(context: Context, database: AppDatabase) {
        database.tareaDao().deleteAll()
        database.recordatorioDao().deleteAll()
        ReminderScheduler.cancelAllSnoozes(context)
        AlarmVibrationService.stopAnyRinging(context)
    }
}
