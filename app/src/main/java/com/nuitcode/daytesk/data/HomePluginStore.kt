package com.nuitcode.daytesk.data

import android.content.Context
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado

data class HomePlugins(
    val showRecientes: Boolean = true,
    val pinnedIds: List<Long> = emptyList(),
) {
    companion object {
        const val MAX_PINNED = 3
    }
}

class HomePluginStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): HomePlugins {
        val pinned = prefs.getString(KEY_PINNED, "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
        return HomePlugins(
            showRecientes = prefs.getBoolean(KEY_RECIENTES, true),
            pinnedIds = pinned,
        )
    }

    fun save(plugins: HomePlugins) {
        prefs.edit()
            .putBoolean(KEY_RECIENTES, plugins.showRecientes)
            .putString(KEY_PINNED, plugins.pinnedIds.joinToString(","))
            .apply()
    }

    fun toggleRecientes(): HomePlugins {
        val next = load().copy(showRecientes = !load().showRecientes)
        save(next)
        return next
    }

    fun pin(taskId: Long): HomePlugins {
        val current = load()
        if (current.pinnedIds.contains(taskId) || current.pinnedIds.size >= HomePlugins.MAX_PINNED) {
            return current
        }
        val next = current.copy(pinnedIds = current.pinnedIds + taskId)
        save(next)
        return next
    }

    fun unpin(taskId: Long): HomePlugins {
        val next = load().copy(pinnedIds = load().pinnedIds.filterNot { it == taskId })
        save(next)
        return next
    }

    companion object {
        private const val PREFS = "home_plugins"
        private const val KEY_RECIENTES = "show_recientes"
        private const val KEY_PINNED = "pinned_ids"
    }
}

fun recentPendingTasks(all: List<Tarea>, limit: Int = 3): List<Tarea> =
    all.filter { it.estado == TareaEstado.PENDIENTE }
        .sortedByDescending { it.fechaCreacion }
        .take(limit)
