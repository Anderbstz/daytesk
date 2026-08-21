package com.nuitcode.daytesk

import android.app.Application
import com.nuitcode.daytesk.data.ContextoRepository
import com.nuitcode.daytesk.data.DefaultContextoRepository
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.notification.NotificationHelper
import com.nuitcode.daytesk.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DayteskApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val contextoRepository: ContextoRepository by lazy {
        DefaultContextoRepository(database.contextoDao())
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val tareas = database.tareaDao().getAllTareas().first().map { it.toDomain() }
            ReminderScheduler.reschedulePending(this@DayteskApplication, tareas)
        }
    }
}