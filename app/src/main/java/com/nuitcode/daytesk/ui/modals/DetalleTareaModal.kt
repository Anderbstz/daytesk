package com.nuitcode.daytesk.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.ui.inicio.ContextChip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DetalleTareaModal(
    tarea: Tarea,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        )

        // Modal content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(DayteskColors.Background, DayteskShapes.large)
                .clickable(enabled = false) { } // consume clicks
        ) {
            // ── Top bar ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = DayteskColors.TextDisabled,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(DayteskSpacing.xxl)
                        .clickable { onDismiss() },
                )
                Text(
                    text = "Detalle",
                    style = DayteskTypography.h1,
                    color = DayteskColors.TextPrimary,
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Info card ────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DayteskSpacing.xl)
                        .clip(DayteskShapes.medium)
                        .background(DayteskColors.Surface)
                        .border(1.dp, DayteskColors.Border, DayteskShapes.medium)
                        .padding(DayteskSpacing.xl),
                ) {
                    // Top row: priority dot + ContextChip + status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(tarea.prioridad.color(), CircleShape),
                        )
                        Spacer(modifier = Modifier.width(DayteskSpacing.sm))
                        ContextChip(contexto = tarea.contexto)
                        Spacer(modifier = Modifier.weight(1f))
                        // Status badge
                        if (tarea.estado == TareaEstado.PENDIENTE) {
                            Box(
                                modifier = Modifier
                                    .background(DayteskColors.WarningLight, RoundedCornerShape(50))
                                    .padding(horizontal = DayteskSpacing.sm, vertical = 2.dp),
                            ) {
                                Text(
                                    text = tarea.estado.label(),
                                    style = DayteskTypography.badge,
                                    color = DayteskColors.Warning,
                                )
                            }
                        }
                    }

                    // Title
                    Text(
                        text = tarea.titulo,
                        style = DayteskTypography.h1,
                        color = DayteskColors.TextPrimary,
                        modifier = Modifier.padding(top = DayteskSpacing.md),
                    )

                    // Description
                    if (tarea.descripcion.isNotBlank()) {
                        Text(
                            text = tarea.descripcion,
                            style = DayteskTypography.bodyMd,
                            color = DayteskColors.TextSecondary,
                            modifier = Modifier.padding(top = DayteskSpacing.xxs),
                        )
                    }
                }

                // ── Details section ─────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(DayteskSpacing.xxs),
                ) {
                    // Due date
                    DetailRow(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Vence",
                                tint = DayteskColors.TextDisabled,
                                modifier = Modifier.size(DayteskSpacing.xl),
                            )
                        },
                        label = "Vence",
                        value = formatFechaVencimiento(tarea.fechaVencimiento),
                        valueColor = if (venceHoy(tarea.fechaVencimiento)) DayteskColors.Urgent else null,
                    )

                    // Context label
                    DetailRow(
                        icon = {
                            Text(text = "#", fontSize = 18.sp, color = DayteskColors.TextDisabled)
                        },
                        label = "Contexto",
                        customValue = {
                            ContextChip(contexto = tarea.contexto)
                        },
                    )

                    // Priority
                    DetailRow(
                        icon = {
                            Text(text = "⚑", fontSize = 20.sp, color = DayteskColors.TextDisabled)
                        },
                        label = "Prioridad",
                        customValue = {
                            Box(
                                modifier = Modifier
                                    .height(DayteskSpacing.xxl)
                                    .clip(RoundedCornerShape(50))
                                    .background(tarea.prioridad.colorLight())
                                    .padding(horizontal = DayteskSpacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = tarea.prioridad.label(),
                                    style = DayteskTypography.badge,
                                    color = tarea.prioridad.color(),
                                )
                            }
                        },
                    )

                    // Created date
                    DetailRow(
                        icon = {
                            Text(text = "🕐", fontSize = 16.sp, color = DayteskColors.TextDisabled)
                        },
                        label = "Creada",
                        value = formatTimestamp(tarea.fechaCreacion),
                    )

                }

                // ── Action buttons ──────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DayteskSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
                ) {
                    // Completar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DayteskSpacing.xxxxl)
                            .clip(DayteskShapes.pill)
                            .background(DayteskColors.Success)
                            .clickable { onComplete() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Marcar como completada",
                            style = DayteskTypography.bodySm.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }

                    // Eliminar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DayteskSpacing.xxxxl)
                            .clip(DayteskShapes.pill)
                            .background(DayteskColors.Surface)
                            .border(1.5.dp, DayteskColors.Urgent, DayteskShapes.pill)
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Eliminar tarea",
                            style = DayteskTypography.bodySm.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = DayteskColors.Urgent,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(DayteskSpacing.xl))
            }
        }
    }
}

// ── Detail row composable ─────────────────────────────────────

@Composable
private fun DetailRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String? = null,
    valueColor: Color? = null,
    customValue: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DayteskSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(DayteskSpacing.sm))
        Text(
            text = label,
            style = DayteskTypography.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = DayteskColors.TextSecondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (customValue != null) {
            customValue()
        } else if (value != null) {
            Text(
                text = value,
                style = DayteskTypography.bodySm,
                color = valueColor ?: DayteskColors.TextSecondary,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────

private fun venceHoy(millis: Long?): Boolean {
    if (millis == null) return false
    val cal = Calendar.getInstance()
    val taskCal = Calendar.getInstance().apply { timeInMillis = millis }
    return cal.get(Calendar.YEAR) == taskCal.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == taskCal.get(Calendar.DAY_OF_YEAR)
}

private fun formatFechaVencimiento(millis: Long?): String {
    if (millis == null) return "Sin fecha"
    val locale = Locale.forLanguageTag("es-ES")
    val date = Date(millis)
    if (venceHoy(millis)) {
        val timeFmt = SimpleDateFormat("h:mm a", locale)
        return "hoy · ${timeFmt.format(date)}"
    }
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", locale)
    return fmt.format(date)
}

private fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("es-ES"))
    return fmt.format(Date(millis))
}
