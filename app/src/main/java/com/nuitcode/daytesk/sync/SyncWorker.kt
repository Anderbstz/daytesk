package com.nuitcode.daytesk.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.local.AppDatabase

/**
 * Periodic pull that keeps mirrored alarms fresh while the app is closed.
 *
 * WorkManager is not exact: Android may defer the run in Doze. The alarm itself
 * is still scheduled locally once the pull lands, so a delayed pull delays the
 * alarm, it does not lose it.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val sync = CloudSync(app, AppDatabase.getInstance(app), SessionStore(app))
        sync.onBackgroundSync()
        return Result.success()
    }
}
