package com.nuitcode.daytesk.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.data.HomePlugins
import com.nuitcode.daytesk.data.recentPendingTasks
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskTypography

@Composable
fun InicioScreen(
    data: DayteskData,
    plugins: HomePlugins = HomePlugins(),
    onTaskClick: (Long) -> Unit = {},
    onTaskToggle: (Long, Boolean) -> Unit = { _, _ -> },
    onSeeAll: () -> Unit = {},
    onOpenWeeklyReview: () -> Unit = {},
    onToggleRecientes: () -> Unit = {},
    onPinTask: (Long) -> Unit = {},
    onUnpinTask: (Long) -> Unit = {},
) {
    val pendingToday = data.tareasHoy.filter { it.estado == TareaEstado.PENDIENTE }
    val dueTodayTasks = pendingToday
    val importantNow = ImportantNow.pick(
        data.tareasHoy + data.tareasSemana + data.otrasPendientes,
    )
    val allPending = (data.tareasHoy + data.tareasSemana + data.otrasPendientes + data.vencidas)
        .distinctBy { it.id }
        .filter { it.estado == TareaEstado.PENDIENTE }
    val recientes = recentPendingTasks(allPending)
    val pinned = plugins.pinnedIds.mapNotNull { id -> allPending.find { it.id == id } }
    var showPluginPicker by remember { mutableStateOf(false) }
    var showPinPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { InicioTopBar(points = data.stats.totalCompletadasHistorico) }
        item { QuickStatsRow(stats = data.stats) }
        item {
            SectionHeader(
                title = "Revisión semanal",
                actionLabel = "Abrir",
                onAction = onOpenWeeklyReview,
            )
        }

        item {
            SectionHeader(
                title = "Plugins",
                actionLabel = if (canAddPlugin(plugins)) "Agregar" else null,
                onAction = { showPluginPicker = true },
            )
        }
        if (plugins.showRecientes) {
            item {
                SectionHeader(
                    title = "Recientes",
                    actionLabel = "Quitar",
                    onAction = onToggleRecientes,
                )
            }
            if (recientes.isEmpty()) {
                item {
                    Text(
                        text = "Todavía no hay tareas recientes.",
                        style = DayteskTypography.bodySm,
                        color = DayteskColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            } else {
                items(recientes) { task ->
                    CompactTaskCard(tarea = task, onClick = { onTaskClick(task.id) })
                }
            }
        }
        if (pinned.isNotEmpty()) {
            item {
                SectionHeader(title = "Ancladas")
            }
            items(pinned) { task ->
                CompactTaskCard(tarea = task, onClick = { onTaskClick(task.id) })
            }
        }

        item {
            SectionHeader(
                title = "Importantes ahora",
                actionLabel = "Ver todo",
                onAction = onSeeAll,
            )
        }
        items(importantNow) { task ->
            CompactTaskCard(tarea = task, onClick = { onTaskClick(task.id) })
        }

        item { SectionHeader(title = "Vencen hoy") }
        items(dueTodayTasks) { task ->
            FullTaskCard(
                tarea = task,
                isChecked = false,
                onToggle = { checked -> onTaskToggle(task.id, checked) },
                onClick = { onTaskClick(task.id) },
            )
        }

        if (pendingToday.isEmpty() && importantNow.isEmpty() && recientes.isEmpty() && pinned.isEmpty()) {
            item { DashboardEmptyState() }
        }
    }

    if (showPluginPicker) {
        AlertDialog(
            onDismissRequest = { showPluginPicker = false },
            title = { Text("Agregar plugin") },
            text = {
                Column {
                    if (!plugins.showRecientes) {
                        Text(
                            text = "Tareas más recientes",
                            style = DayteskTypography.bodyMd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onToggleRecientes()
                                    showPluginPicker = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                    if (plugins.pinnedIds.size < HomePlugins.MAX_PINNED) {
                        Text(
                            text = "Elegir una tarea",
                            style = DayteskTypography.bodyMd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPluginPicker = false
                                    showPinPicker = true
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                    pinned.forEach { tarea ->
                        Text(
                            text = "Quitar anclada: ${tarea.titulo}",
                            style = DayteskTypography.bodySm,
                            color = DayteskColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUnpinTask(tarea.id)
                                    showPluginPicker = false
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPluginPicker = false }) { Text("Cerrar") }
            },
        )
    }

    if (showPinPicker) {
        PinTaskDialog(
            tareas = allPending.filterNot { plugins.pinnedIds.contains(it.id) },
            onPick = { id ->
                onPinTask(id)
                showPinPicker = false
            },
            onDismiss = { showPinPicker = false },
        )
    }
}

@Composable
private fun PinTaskDialog(
    tareas: List<Tarea>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegí una tarea") },
        text = {
            if (tareas.isEmpty()) {
                Text("No hay más tareas pendientes para anclar.")
            } else {
                Column {
                    tareas.take(12).forEach { tarea ->
                        Text(
                            text = tarea.titulo,
                            style = DayteskTypography.bodyMd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(tarea.id) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

private fun canAddPlugin(plugins: HomePlugins): Boolean =
    !plugins.showRecientes || plugins.pinnedIds.size < HomePlugins.MAX_PINNED
