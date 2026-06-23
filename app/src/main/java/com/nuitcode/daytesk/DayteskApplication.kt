package com.nuitcode.daytesk

import android.app.Application
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.notification.NotificationHelper

class DayteskApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}
