package com.nuitcode.daytesk.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nuitcode.daytesk.R

/**
 * 2x2 home-screen widget that captures a recordatorio in two taps.
 *
 * The widget is intentionally static: `RemoteViews` can neither host an
 * editable field nor open an inline popup, so the whole surface is a single
 * click target that launches [RecordatorioCaptureActivity] as a dialog. There
 * is no data to bind and nothing to refresh, hence no alarm/period work — the
 * layout is rendered from resources on every `onUpdate`.
 */
class RecordatorioWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = buildViews(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }

    companion object {
        /** Re-renders every placed instance; exposed for symmetry with the task widget. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RecordatorioWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_recordatorio)
            val openCapture = PendingIntent.getActivity(
                context,
                0,
                Intent(context, RecordatorioCaptureActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.recordatorio_widget_root, openCapture)
            return views
        }
    }
}
