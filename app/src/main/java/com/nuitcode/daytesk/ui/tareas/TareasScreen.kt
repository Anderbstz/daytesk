package com.nuitcode.daytesk.ui.tareas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.ui.inicio.ContextChip
import com.nuitcode.daytesk.ui.inicio.RoundCheckbox
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Helper functions ──────────────────────────────────────────

private fun isToday(millis: Long): Boolean {
    val cal = Calendar.getInstance()
    val taskCal = Calendar.getInstance().apply { timeInMillis = millis }
    return cal.get(Calendar.YEAR) == taskCal.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == taskCal.get(Calendar.DAY_OF_YEAR)
}

private fun formatDueDate(millis: Long): String {
    if (isToday(millis)) return "Vence hoy"
    val dateFormat = SimpleDateFormat("d 'de' MMM", Locale.forLanguageTag("es-ES"))
    return dateFormat.format(Date(millis))
}

// ── Main screen ───────────────────────────────────────────────

@Composable
fun TareasScreen(
    data: DayteskData,
    onTaskClick: (Long) -> Unit = {},
    onTaskToggle: (Long, Boolean) -> Unit = { _, _ -> },
) {
    var selectedFilter by remember { mutableStateOf<Long?>(null) }
    var checkedTasks by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val hoyFiltered = data.tareasHoy.filter {
        selectedFilter == null || it.contextoId == selectedFilter
    }
    val semanaFiltered = data.tareasSemana.filter {
        selectedFilter == null || it.contextoId == selectedFilter
    }
    val hasResults = hoyFiltered.isNotEmpty() || semanaFiltered.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        TareasTopBar()

        FilterChipsRow(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
            contextos = data.contextos,
        )

        if (!hasResults) {
            TareasEmptyState()
        } else {
            // ── Hoy section ──
            if (hoyFiltered.isNotEmpty()) {
                SectionLabel(title = "Hoy", count = hoyFiltered.size)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hoyFiltered.forEach { tarea ->
                        val isChecked =
                            tarea.id in checkedTasks || tarea.estado == TareaEstado.COMPLETADA
                        TareasTaskCard(
                            tarea = tarea,
                            isChecked = isChecked,
                            onToggle = { checked ->
                                onTaskToggle(tarea.id, checked)
                                checkedTasks = if (checked) checkedTasks + tarea.id
                                else checkedTasks - tarea.id
                            },
                            onClick = { onTaskClick(tarea.id) },
                        )
                    }
                }
            }

            // ── Esta semana section ──
            if (semanaFiltered.isNotEmpty()) {
                SectionLabel(
                    title = "Esta semana",
                    count = semanaFiltered.size,
                    topPadding = 8.dp,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    semanaFiltered.forEach { tarea ->
                        val isChecked =
                            tarea.id in checkedTasks || tarea.estado == TareaEstado.COMPLETADA
                        TareasTaskCard(
                            tarea = tarea,
                            isChecked = isChecked,
                            onToggle = { checked ->
                                onTaskToggle(tarea.id, checked)
                                checkedTasks = if (checked) checkedTasks + tarea.id
                                else checkedTasks - tarea.id
                            },
                            onClick = { onTaskClick(tarea.id) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────

@Composable
private fun TareasTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Tareas",
            style = DayteskTypography.display,
            color = DayteskColors.TextPrimary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar",
                tint = DayteskColors.TextDisabled,
                modifier = Modifier.size(22.dp),
            )
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Filtrar",
                tint = DayteskColors.TextDisabled,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ── Filter chips ──────────────────────────────────────────────

@Composable
private fun FilterChipsRow(
    selectedFilter: Long?,
    onFilterSelected: (Long?) -> Unit,
    contextos: List<Contexto>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChipPill(
            label = "Todas",
            isSelected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
        )
        contextos.forEach { contexto ->
            FilterChipPill(
                label = contexto.label(),
                isSelected = selectedFilter == contexto.id,
                onClick = { onFilterSelected(contexto.id) },
            )
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                color = if (isSelected) DayteskColors.Primary else DayteskColors.Surface,
                shape = RoundedCornerShape(50),
            )
            .then(
                if (!isSelected) {
                    Modifier.border(1.dp, DayteskColors.Border, RoundedCornerShape(50))
                } else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = DayteskTypography.bodySm,
            color = if (isSelected) Color.White else DayteskColors.TextSecondary,
        )
    }
}

// ── Section label ─────────────────────────────────────────────

@Composable
private fun SectionLabel(
    title: String,
    count: Int,
    topPadding: Dp = 20.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = DayteskTypography.h3,
            color = DayteskColors.TextPrimary,
        )
        Text(
            text = " ($count)",
            style = DayteskTypography.caption,
            color = DayteskColors.TextDisabled,
        )
    }
}

// ── Task card ─────────────────────────────────────────────────

@Composable
private fun TareasTaskCard(
    tarea: Tarea,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val isCompleted = tarea.estado == TareaEstado.COMPLETADA || isChecked

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCompleted) {
                    Modifier.border(1.dp, DayteskColors.Border, RoundedCornerShape(16.dp))
                } else Modifier
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) Color(0xFFF8F9FA) else DayteskColors.Surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCompleted) 0.dp else 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // ── Top row: priority dot + urgency chip + context chip ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(tarea.prioridad.color(), CircleShape),
                )

                if (tarea.prioridad == Prioridad.ALTA) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(DayteskColors.UrgentLight, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Urgente",
                            style = DayteskTypography.badge,
                            color = DayteskColors.Urgent,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                ContextChip(contexto = tarea.contexto)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Title ──
            Text(
                text = tarea.titulo,
                style = DayteskTypography.bodyMd,
                color = if (isCompleted) DayteskColors.TextDisabled else DayteskColors.TextPrimary,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            )

            // ── Description (hidden for completed tasks) ──
            if (tarea.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tarea.descripcion,
                    style = DayteskTypography.bodySm,
                    color = DayteskColors.TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Bottom row: date + checkbox ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tarea.fechaVencimiento != null) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Fecha",
                        tint = if (isCompleted) DayteskColors.TextDisabled
                        else if (isToday(tarea.fechaVencimiento)) DayteskColors.Urgent
                        else DayteskColors.TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val dueText = formatDueDate(tarea.fechaVencimiento)
                    val dueColor = if (isCompleted) DayteskColors.TextDisabled
                    else if (isToday(tarea.fechaVencimiento)) DayteskColors.Urgent
                    else DayteskColors.TextSecondary
                    Text(
                        text = dueText,
                        style = DayteskTypography.caption,
                        color = dueColor,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isCompleted) {
                    // Green filled checkbox for completed tasks
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(DayteskColors.Success, CircleShape)
                            .clickable { onToggle(false) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completada",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    RoundCheckbox(
                        checked = isChecked,
                        onToggle = onToggle,
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────

@Composable
private fun TareasEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = DayteskColors.TextDisabled,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hay tareas en esta categoría",
            style = DayteskTypography.bodyMd,
            color = DayteskColors.TextSecondary,
        )
    }
}
