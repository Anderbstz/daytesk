package com.nuitcode.daytesk.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.nuitcode.daytesk.MainActivity
import com.nuitcode.daytesk.R
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.data.persistTaskCompletion
import com.nuitcode.daytesk.ui.inicio.ImportantNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NextTaskWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        scheduleTicks(context)
    }

    override fun onDisabled(context: Context) {
        cancelTicks(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COMPLETE -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (taskId > 0) {
                            val dao = AppDatabase.getInstance(context).tareaDao()
                            val task = dao.getTareaById(taskId)?.toDomain()
                            if (task != null) {
                                persistTaskCompletion(context, dao, task, completed = true)
                            }
                        }
                        refreshNow(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_TICK -> refreshNow(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        scheduleTicks(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = buildViews(context)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.nuitcode.daytesk.widget.COMPLETE"
        const val ACTION_TICK = "com.nuitcode.daytesk.widget.TICK"
        const val EXTRA_TASK_ID = "task_id"
        private const val TICK_MS = 60_000L

        fun refresh(context: Context) {
            refreshNow(context)
        }

        private fun refreshNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NextTaskWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val views = buildViews(context)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            }
        }

        private fun tickIntent(context: Context): PendingIntent {
            val intent = Intent(context, NextTaskWidgetProvider::class.java).apply {
                action = ACTION_TICK
            }
            return PendingIntent.getBroadcast(
                context,
                71,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun scheduleTicks(context: Context) {
            val alarm = context.getSystemService(AlarmManager::class.java)
            alarm.setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + TICK_MS,
                TICK_MS,
                tickIntent(context),
            )
        }

        private fun cancelTicks(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
        }

        private suspend fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_task)
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            val tareas = runCatching {
                AppDatabase.getInstance(context).tareaDao().getAllOnce().map { it.toDomain() }
            }.getOrDefault(emptyList())
            val now = System.currentTimeMillis()
            val next = ImportantNow.pickCurrent(tareas, now)

            if (next == null) {
                views.setTextViewText(R.id.widget_label, "Importante ahora")
                views.setTextViewText(R.id.widget_title, "Sin tareas pendientes")
                views.setTextViewText(R.id.widget_when, "")
                views.setTextViewText(R.id.widget_percent, "—")
                views.setProgressBar(R.id.widget_bar, 100, 0, false)
                views.setImageViewBitmap(R.id.widget_arc, drawArc(0, densitySize(context)))
                views.setViewVisibility(R.id.widget_complete, View.GONE)
            } else {
                val percent = ImportantNow.timeProgress(
                    next.fechaCreacion,
                    next.fechaVencimiento,
                    now,
                )
                views.setTextViewText(R.id.widget_label, "Importante ahora")
                views.setTextViewText(R.id.widget_title, next.titulo)
                views.setTextViewText(R.id.widget_when, formatDue(next.fechaVencimiento))
                views.setTextViewText(R.id.widget_percent, "$percent%")
                views.setProgressBar(R.id.widget_bar, 100, percent, false)
                views.setImageViewBitmap(R.id.widget_arc, drawArc(percent, densitySize(context)))
                views.setViewVisibility(R.id.widget_complete, View.VISIBLE)
                val complete = Intent(context, NextTaskWidgetProvider::class.java).apply {
                    action = ACTION_COMPLETE
                    putExtra(EXTRA_TASK_ID, next.id)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_complete,
                    PendingIntent.getBroadcast(
                        context,
                        next.id.toInt(),
                        complete,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            return views
        }

        private fun densitySize(context: Context): Int {
            val density = context.resources.displayMetrics.density
            return (220 * density).toInt().coerceIn(200, 720)
        }

        private fun drawArc(percent: Int, size: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size / 2 + 8, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val stroke = size * 0.11f
            val pad = stroke
            val oval = RectF(pad, pad, size - pad, size - pad)
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                color = 0xFFE4D6C4.toInt()
            }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                color = 0xFFC46A3A.toInt()
            }
            canvas.drawArc(oval, 180f, 180f, false, track)
            val sweep = 180f * (percent.coerceIn(0, 100) / 100f)
            if (sweep > 0f) canvas.drawArc(oval, 180f, sweep, false, fill)
            return bitmap
        }

        private fun formatDue(millis: Long?): String {
            if (millis == null) return "Sin fecha"
            val fmt = SimpleDateFormat("d MMM · HH:mm", Locale.forLanguageTag("es-ES"))
            return fmt.format(Date(millis))
        }
    }
}
