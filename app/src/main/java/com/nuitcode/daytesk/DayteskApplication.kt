package com.nuitcode.daytesk

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nuitcode.daytesk.data.ContextoRepository
import com.nuitcode.daytesk.data.DefaultContextoRepository
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.notification.NotificationHelper
import com.nuitcode.daytesk.notification.ReminderScheduler
import com.nuitcode.daytesk.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DayteskApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val contextoRepository: ContextoRepository by lazy {
        DefaultContextoRepository(database.contextoDao())
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        enqueueBackgroundSync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val tareas = database.tareaDao().getAllTareas().first().map { it.toDomain() }
            ReminderScheduler.reschedulePending(this@DayteskApplication, tareas)
            val recordatorios = database.recordatorioDao().getAllRecordatorios().first().map { it.toDomain() }
            ReminderScheduler.rescheduleRecordatorios(this@DayteskApplication, recordatorios)
        }
    }

    /**
     * One unique periodic pull, kept across restarts. 15 minutes is WorkManager's
     * floor: fresher mirrored alarms at the cost of more radio wake-ups. On
     * Render's free tier this also keeps the service awake 24/7, which burns the
     * workspace's monthly instance hours; use 1 hour (or always-on infra) if that
     * matters.
     */
    private fun enqueueBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daytesk-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}