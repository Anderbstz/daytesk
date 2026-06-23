package com.nuitcode.daytesk.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    fun scheduleTaskReminder(context: Context, taskId: Long, taskTitle: String, delayMillis: Long) {
        val inputData = Data.Builder()
            .putLong("task_id", taskId)
            .putString("task_title", taskTitle)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag("task_reminder_$taskId")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "reminder_$taskId",
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$taskId")
    }
}
