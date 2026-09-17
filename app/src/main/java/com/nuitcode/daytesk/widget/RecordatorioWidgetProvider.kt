package com.nuitcode.daytesk.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
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
 *
 * The write-path refresh alone is not enough to keep the row honest: the
 * "next upcoming" set changes on its own the moment the soonest reminder's
 * `fecha` slips into the past, and that transition happens with no write at
 * all — most visibly while the notifications toggle is off, because
 * [com.nuitcode.daytesk.notification.ReminderScheduler] then registers no alarm
 * and nothing else re-renders the widget. A repeating [ACTION_TICK] alarm
 * (see [scheduleTicks]) bounds that stale window the same way
 * [NextTaskWidgetProvider] keeps its own row live.
 */
class RecordatorioWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        scheduleTicks(context)
    }

    override fun onDisabled(context: Context) {
        cancelTicks(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> {
                // Hold the broadcast's work slot until the render lands; a
                // detached refresh could be killed mid-flight, leaving the row
                // stale until the next tick.
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        refreshNow(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
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
        const val ACTION_TICK = "com.nuitcode.daytesk.widget.RECORDATORIO_TICK"

        /**
         * Tick cadence.
         *
         * The row only prints minute precision ("d MMM · HH:mm"), and its one
         * autonomous state change is "the soonest reminder became past due" —
         * a transition that always lands on a minute boundary. A 60s tick
         * therefore bounds the stale window to under one displayed minute.
         * Matching [NextTaskWidgetProvider.TICK_MS] keeps both home-screen rows
         * on a single cadence the OS can batch into one wake-up.
         */
        private const val TICK_MS = 60_000L

        // Distinct from the task widget's tick code (71) and from this
        // provider's click PendingIntents (10/11): this broadcast is explicit
        // to the class, but unique codes keep the intent shapes independently
        // addressable if they ever change.
        private const val REQUEST_TICK = 72

        /**
         * Fire-and-forget re-render of every placed instance.
         *
         * Called after any recordatorio create, edit, delete or repetition roll,
         * and alongside the task widget's own refresh on sync/boot. The render
         * runs on a detached scope, so a caller that owns a `goAsync()` work
         * slot must call [refreshNow] instead — otherwise the process can be
         * killed right after `finish()` and the render is silently dropped.
         */
        fun refresh(context: Context) {
            CoroutineScope(Dispatchers.IO).launch { refreshNow(context) }
        }

        /**
         * Awaitable re-render: returns only once every placed instance has been
         * updated, so a `goAsync()` caller can keep its work slot open until the
         * render is on screen. Used by the [ACTION_TICK] path and by
         * [com.nuitcode.daytesk.notification.BootReceiver].
         */
        suspend fun refreshNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RecordatorioWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
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

        /**
         * The repeating alarm that keeps the row honest between writes.
         *
         * Deliberately independent of
         * [com.nuitcode.daytesk.notification.NotificationPreferences]: the widget
         * is a read-only surface, not a reminder, so a user who silenced
         * notifications still expects the row to stop advertising a reminder
         * that already fired.
         */
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

        private fun tickIntent(context: Context): PendingIntent {
            val intent = Intent(context, RecordatorioWidgetProvider::class.java).apply {
                action = ACTION_TICK
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_TICK,
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
