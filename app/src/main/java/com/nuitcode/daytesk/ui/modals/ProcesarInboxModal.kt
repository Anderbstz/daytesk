package com.nuitcode.daytesk.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.InboxItem
import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProcesarInboxModal(
    item: InboxItem,
    onDismiss: () -> Unit,
    onSave: (Contexto, Prioridad, Long?) -> Unit,
    onDelete: () -> Unit,
    contextos: List<Contexto> = Contexto.DEFAULTS,
    canAddContexto: Boolean = contextos.size < Contexto.MAX_COUNT,
    onAddContexto: (() -> Unit)? = null,
) {
    var selectedContexto by remember(contextos) {
        mutableStateOf(contextos.firstOrNull() ?: Contexto.FALLBACK)
    }
    var selectedPrioridad by remember { mutableStateOf(Prioridad.MEDIA) }
    var fechaVencimiento by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        )

        // Bottom sheet card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clip(DayteskShapes.large)
                .background(DayteskColors.Surface)
                .clickable(enabled = false) { }
                .padding(horizontal = DayteskSpacing.xl)
                .padding(bottom = DayteskSpacing.xl),
        ) {
            // Sheet handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DayteskSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DayteskColors.Border),
                )
            }

            // Title
            Text(
                text = "Procesar tarea",
                style = DayteskTypography.h3,
                color = DayteskColors.TextPrimary,
                modifier = Modifier.padding(top = DayteskSpacing.md, bottom = DayteskSpacing.md),
            )

            // Inbox item text
            Text(
                text = item.texto,
                style = DayteskTypography.bodyMd,
                color = DayteskColors.TextSecondary,
                modifier = Modifier.padding(bottom = DayteskSpacing.lg),
            )

            // Context selector
            Column(modifier = Modifier.padding(bottom = DayteskSpacing.lg)) {
                Text(
                    text = "Contexto",
                    style = DayteskTypography.caption.copy(fontWeight = FontWeight.Medium),
                    color = DayteskColors.TextSecondary,
                    modifier = Modifier.padding(bottom = DayteskSpacing.sm),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
                ) {
                    contextos.forEach { ctx ->
                        val isSelected = ctx == selectedContexto
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) ctx.colorLight()
                                    else DayteskColors.Surface
                                )
                                .then(
                                    if (!isSelected) Modifier.border(1.dp, DayteskColors.Border, RoundedCornerShape(50))
                                    else Modifier
                                )
                                .clickable { selectedContexto = ctx }
                                .padding(horizontal = DayteskSpacing.lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = ctx.label(),
                                style = DayteskTypography.bodySm,
                                color = if (isSelected) ctx.color() else DayteskColors.TextSecondary,
                            )
                        }
                    }
                    if (canAddContexto && onAddContexto != null) {
                            Box(
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(DayteskColors.PrimaryLight)
                                .clickable { onAddContexto() }
                                .padding(horizontal = DayteskSpacing.lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+",
                                style = DayteskTypography.bodySm,
                                color = DayteskColors.Primary,
                            )
                        }
                    }
                }
            }

            // Date row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .padding(vertical = DayteskSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Asignar fecha",
                    tint = DayteskColors.TextDisabled,
                    modifier = Modifier.size(DayteskSpacing.xl),
                )
                Spacer(modifier = Modifier.width(DayteskSpacing.sm))
                Text(
                    text = fechaVencimiento?.let { formatInboxFecha(it) } ?: "Asignar fecha",
                    style = DayteskTypography.bodySm,
                    color = DayteskColors.TextSecondary,
                )
            }

            // Priority selector
            Column(modifier = Modifier.padding(vertical = DayteskSpacing.md)) {
                Text(
                    text = "Prioridad",
                    style = DayteskTypography.caption.copy(fontWeight = FontWeight.Medium),
                    color = DayteskColors.TextSecondary,
                    modifier = Modifier.padding(bottom = DayteskSpacing.sm),
                )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(DayteskSpacing.md)) {
                    Prioridad.entries.forEach { p ->
                        val isSelected = p == selectedPrioridad
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) p.colorLight()
                                    else DayteskColors.Surface
                                )
                                .then(
                                    if (!isSelected) Modifier.border(1.dp, DayteskColors.Border, RoundedCornerShape(50))
                                    else Modifier
                                )
                                .clickable { selectedPrioridad = p }
                                .padding(horizontal = DayteskSpacing.lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = p.label(),
                                    style = DayteskTypography.bodySm,
                                color = if (isSelected) p.color() else DayteskColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = DayteskSpacing.sm)
                    .height(1.dp)
                    .background(DayteskColors.Divider),
            )

            // Bottom row: Eliminar + Guardar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DayteskSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Eliminar",
                    style = DayteskTypography.bodySm,
                    color = DayteskColors.Urgent,
                    modifier = Modifier
                        .clickable { onDelete() }
                        .padding(vertical = DayteskSpacing.md),
                )

                Box(
                    modifier = Modifier
                        .clip(DayteskShapes.pill)
                        .background(DayteskColors.Primary)
                        .clickable { onSave(selectedContexto, selectedPrioridad, fechaVencimiento) }
                        .padding(horizontal = DayteskSpacing.xxxl, vertical = DayteskSpacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Guardar",
                        style = DayteskTypography.bodySm.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DayteskDatePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateSelected = { selected ->
                pendingDateMillis = selected
                showTimePicker = true
            },
        )
    }
    if (showTimePicker && pendingDateMillis != null) {
        DayteskTimePickerDialog(
            utcDateMillis = pendingDateMillis!!,
            onDismiss = {
                showTimePicker = false
                pendingDateMillis = null
            },
            onConfirm = { millis ->
                fechaVencimiento = millis
                showTimePicker = false
                pendingDateMillis = null
            },
        )
    }
}

private fun formatInboxFecha(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
