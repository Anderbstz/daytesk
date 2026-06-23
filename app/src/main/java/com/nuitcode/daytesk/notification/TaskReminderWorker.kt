package com.nuitcode.daytesk.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nuitcode.daytesk.data.local.AppDatabase

class TaskReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong("task_id", -1L)
        val taskTitle = inputData.getString("task_title") ?: return Result.failure()

        if (taskId == -1L) return Result.failure()

        val database = AppDatabase.getInstance(applicationContext)
        val tarea = database.tareaDao().getTareaById(taskId)

        if (tarea != null && tarea.estado != "COMPLETADA") {
            NotificationHelper.showTaskReminder(applicationContext, taskTitle, taskId)
        }

        return Result.success()
    }
}
