package com.nuitcode.daytesk.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskTypography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

@Composable
fun FullTaskCard(
    tarea: Tarea,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val isCompleted = tarea.estado == com.nuitcode.daytesk.model.TareaEstado.COMPLETADA || isChecked

    val cardColors = if (isCompleted) {
        CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
    } else {
        CardDefaults.cardColors(containerColor = DayteskColors.Surface)
    }
    val cardElevation = if (isCompleted) {
        CardDefaults.cardElevation(defaultElevation = 0.dp)
    } else {
        CardDefaults.cardElevation(defaultElevation = 2.dp)
    }
    val cardBorder = if (isCompleted) {
        Modifier.border(1.dp, DayteskColors.Border, RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .then(cardBorder)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = cardColors,
        elevation = cardElevation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // First row: priority dot + urgency chip + context chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Priority dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(tarea.prioridad.color(), CircleShape),
                )

                if (tarea.prioridad == Prioridad.ALTA) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                DayteskColors.UrgentLight,
                                RoundedCornerShape(50),
                            )
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

            // Title
            if (isCompleted) {
                Text(
                    text = tarea.titulo,
                    style = DayteskTypography.bodyMd,
                    color = DayteskColors.TextDisabled,
                    textDecoration = TextDecoration.LineThrough,
                )
            } else {
                Text(
                    text = tarea.titulo,
                    style = DayteskTypography.bodyMd,
                    color = DayteskColors.TextPrimary,
                )
            }

            // Description (if present)
            if (tarea.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tarea.descripcion,
                    style = DayteskTypography.bodySm,
                    color = DayteskColors.TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom row: date + checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tarea.fechaVencimiento != null) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Fecha",
                        tint = if (isToday(tarea.fechaVencimiento))
                            DayteskColors.Urgent else DayteskColors.TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val dueText = formatDueDate(tarea.fechaVencimiento)
                    val dueColor = if (isToday(tarea.fechaVencimiento))
                        DayteskColors.Urgent else DayteskColors.TextSecondary
                    Text(
                        text = dueText,
                        style = DayteskTypography.caption,
                        color = dueColor,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                RoundCheckbox(
                    checked = isChecked,
                    onToggle = onToggle,
                )
            }
        }
    }
}
