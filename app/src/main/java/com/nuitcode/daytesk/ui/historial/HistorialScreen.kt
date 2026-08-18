package com.nuitcode.daytesk.ui.historial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.data.StreakCalculator
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.ui.inicio.ContextChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistorialScreen(
    data: DayteskData,
    onBack: () -> Unit,
    onTaskClick: (Long) -> Unit = {},
) {
    val grouped = data.completadas.groupBy { tarea ->
        StreakCalculator.dayKey(tarea.fechaCompletada ?: tarea.fechaCreacion)
    }.toList().sortedByDescending { it.first }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DayteskSpacing.sm, vertical = DayteskSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = DayteskColors.TextDisabled,
                )
            }
            Text(
                text = "Historial",
                style = DayteskTypography.display,
                color = DayteskColors.TextPrimary,
            )
        }

        if (grouped.isEmpty()) {
            Text(
                text = "Aún no hay tareas completadas.",
                style = DayteskTypography.bodyMd,
                color = DayteskColors.TextSecondary,
                modifier = Modifier.padding(DayteskSpacing.xl),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
            ) {
                grouped.forEach { (_, tareas) ->
                    val label = formatDayLabel(tareas.first().fechaCompletada ?: tareas.first().fechaCreacion)
                    item(key = "h-$label") {
                        Text(
                            text = label,
                            style = DayteskTypography.h3,
                            color = DayteskColors.TextPrimary,
                            modifier = Modifier.padding(bottom = DayteskSpacing.sm),
                        )
                    }
                    items(tareas, key = { it.id }) { tarea ->
                        HistorialRow(tarea = tarea)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorialRow(tarea: Tarea) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DayteskColors.Surface)
            .padding(DayteskSpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tarea.titulo,
                style = DayteskTypography.bodyMd,
                color = DayteskColors.TextPrimary,
            )
            Text(
                text = formatTime(tarea.fechaCompletada ?: tarea.fechaCreacion),
                style = DayteskTypography.caption,
                color = DayteskColors.TextDisabled,
            )
        }
        ContextChip(contexto = tarea.contexto)
    }
}

private fun formatDayLabel(millis: Long): String {
    val fmt = SimpleDateFormat("EEEE d MMM yyyy", Locale.forLanguageTag("es-ES"))
    return fmt.format(Date(millis)).replaceFirstChar { it.uppercase() }
}

private fun formatTime(millis: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}
