package com.nuitcode.daytesk.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nuitcode.daytesk.MainActivity
import com.nuitcode.daytesk.R
import com.nuitcode.daytesk.data.HomePluginStore
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.model.TareaEstado
import com.nuitcode.daytesk.ui.inicio.ImportantNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NextTaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
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
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NextTaskWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, NextTaskWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
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
            val pinnedIds = HomePluginStore(context).load().pinnedIds
            val pinned = pinnedIds.firstNotNullOfOrNull { id ->
                tareas.firstOrNull { it.id == id && it.estado == TareaEstado.PENDIENTE }
            }
            val next = pinned ?: ImportantNow.pick(tareas).firstOrNull()
                ?: tareas.firstOrNull { it.estado == TareaEstado.PENDIENTE }

            if (next == null) {
                views.setTextViewText(R.id.widget_label, "Daytesk")
                views.setTextViewText(R.id.widget_title, "Sin tareas pendientes")
                views.setTextViewText(R.id.widget_when, "Tocá para abrir la app")
            } else {
                views.setTextViewText(R.id.widget_label, if (pinned != null) "Fijada" else "Importante ahora")
                views.setTextViewText(R.id.widget_title, next.titulo)
                views.setTextViewText(R.id.widget_when, formatDue(next.fechaVencimiento))
            }
            return views
        }

        private fun formatDue(millis: Long?): String {
            if (millis == null) return "Sin fecha"
            val fmt = SimpleDateFormat("d MMM · HH:mm", Locale.forLanguageTag("es-ES"))
            return fmt.format(Date(millis))
        }
    }
}
