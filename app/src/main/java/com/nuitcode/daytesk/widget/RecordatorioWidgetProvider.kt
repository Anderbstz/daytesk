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
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.toDomain
import com.nuitcode.daytesk.ui.recordatorios.UpcomingRecordatorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1x2 home-screen widget that surfaces the next upcoming recordatorio.
 *
 * Because `RemoteViews` can neither host an editable field nor open an inline
 * popup, the widget exposes two distinct hit zones: the body opens the app on
 * the Recordatorios screen, and the trailing "+" zone launches the
 * dialog-themed [RecordatorioCaptureActivity].
 *
 * The data binding reads the local store on every update through the pure
 * [UpcomingRecordatorio.pick] seam and falls back to the empty state when no
 * reminder is upcoming. Every recordatorio write path calls [refresh] so the
 * widget never shows stale text.
 */
class RecordatorioWidgetProvider : AppWidgetProvider() {
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
        /**
         * Re-renders every placed instance.
         *
         * Called after any recordatorio create, edit, delete or repetition roll,
         * and alongside the task widget's own refresh on sync/boot.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RecordatorioWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val views = buildViews(context)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            }
        }

        private suspend fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_recordatorio)
            views.setOnClickPendingIntent(R.id.recordatorio_widget_root, openAppIntent(context))
            views.setOnClickPendingIntent(R.id.recordatorio_widget_add, openCaptureIntent(context))

            val recordatorios = runCatching {
                AppDatabase.getInstance(context).recordatorioDao().getAllOnce().map { it.toDomain() }
            }.getOrDefault(emptyList())
            val next = UpcomingRecordatorio.pick(recordatorios, System.currentTimeMillis())

            if (next == null) {
                views.setTextViewText(
                    R.id.recordatorio_widget_title,
                    context.getString(R.string.widget_recordatorio_empty),
                )
                views.setTextViewText(R.id.recordatorio_widget_when, "")
            } else {
                views.setTextViewText(R.id.recordatorio_widget_title, next.texto)
                views.setTextViewText(R.id.recordatorio_widget_when, formatWhen(next.fecha))
            }
            return views
        }

        // Request codes 10 / 11 are deliberate: PendingIntent equality ignores
        // extras, so code 0 for this MainActivity intent would collide with
        // NextTaskWidgetProvider's own code-0 MainActivity PendingIntent.
        private const val REQUEST_OPEN_APP = 10
        private const val REQUEST_OPEN_CAPTURE = 11

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_RECORDATORIOS)
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openCaptureIntent(context: Context): PendingIntent {
            val intent = Intent(context, RecordatorioCaptureActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_CAPTURE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun formatWhen(millis: Long): String {
            val formatter = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())
            return formatter.format(Date(millis))
        }
    }
}
