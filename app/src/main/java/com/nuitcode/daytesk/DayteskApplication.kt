package com.nuitcode.daytesk

import android.app.Application
import com.nuitcode.daytesk.data.ContextoRepository
import com.nuitcode.daytesk.data.DefaultContextoRepository
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.notification.NotificationHelper

class DayteskApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    /**
     * User-managed contexts CRUD. PR1 of `custom-contexts` exposes it via the
     * Application so PR2 can wire `ContextosScreen` and the modals without
     * changing the DI pattern.
     */
    val contextoRepository: ContextoRepository by lazy {
        DefaultContextoRepository(database.contextoDao())
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}