package com.nuitcode.daytesk.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.model.TareaEstado
import com.nuitcode.daytesk.theme.DayteskColors

@Composable
fun InicioScreen(
    data: DayteskData,
    onTaskClick: (Long) -> Unit = {},
    onTaskToggle: (Long, Boolean) -> Unit = { _, _ -> },
    onSeeAll: () -> Unit = {},
) {
    val pendingToday = data.tareasHoy.filter { it.estado == TareaEstado.PENDIENTE }
    val upcomingTasks = pendingToday.take(3)
    val dueTodayTasks = pendingToday

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { InicioTopBar() }
        item { QuickStatsRow(stats = data.stats) }

        // Próximas acciones
        item {
            SectionHeader(
                title = "Próximas acciones",
                actionLabel = "Ver todo",
                onAction = onSeeAll,
            )
        }
        items(upcomingTasks) { task ->
            CompactTaskCard(tarea = task, onClick = { onTaskClick(task.id) })
        }

        // Vencen hoy
        item { SectionHeader(title = "Vencen hoy") }
        items(dueTodayTasks) { task ->
            FullTaskCard(
                tarea = task,
                isChecked = false,
                onToggle = { checked -> onTaskToggle(task.id, checked) },
                onClick = { onTaskClick(task.id) },
            )
        }

        // Empty state (if no pending tasks)
        if (pendingToday.isEmpty()) {
            item { DashboardEmptyState() }
        }
    }
}
