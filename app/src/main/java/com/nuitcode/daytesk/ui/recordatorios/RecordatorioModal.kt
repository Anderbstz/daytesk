package com.nuitcode.daytesk.ui.recordatorios

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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.model.Recordatorio
import com.nuitcode.daytesk.model.Repeticion
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.ui.modals.DayteskDatePickerDialog
import com.nuitcode.daytesk.ui.modals.DayteskTimePickerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Create/edit bottom sheet for a [Recordatorio].
 *
 * The date is mandatory: `fecha` starts `null` for a new reminder, the date row
 * is the only way to set it, and "Guardar" stays disabled until both the text
 * and the date are present. The date/time pickers are the same dialogs the task
 * modal uses ([DayteskDatePickerDialog] + [DayteskTimePickerDialog]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordatorioModal(
    onDismiss: () -> Unit,
    onSave: (Recordatorio) -> Unit,
    onDelete: ((Recordatorio) -> Unit)? = null,
    initial: Recordatorio? = null,
) {
    var texto by remember(initial?.id) { mutableStateOf(initial?.texto.orEmpty()) }
    var fecha by remember(initial?.id) { mutableStateOf(initial?.fecha) }
    var repeticion by remember(initial?.id) {
        mutableStateOf(initial?.repeticion ?: Repeticion.NINGUNA)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val editing = initial != null
    val canSave = texto.isNotBlank() && fecha != null

    fun build(): Recordatorio = Recordatorio(
        id = initial?.id ?: 0L,
        texto = texto.trim(),
        fecha = fecha ?: 0L,
        repeticion = repeticion,
        cloudKey = initial?.cloudKey ?: java.util.UUID.randomUUID().toString(),
        fechaCreacion = initial?.fechaCreacion ?: System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
        )

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

            Text(
                text = if (editing) "Editar recordatorio" else "Nuevo recordatorio",
                style = DayteskTypography.h3,
                color = DayteskColors.TextPrimary,
                modifier = Modifier.padding(top = DayteskSpacing.md, bottom = DayteskSpacing.md),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DayteskShapes.small)
                    .background(DayteskColors.Background)
                    .border(1.dp, DayteskColors.Border, DayteskShapes.small)
                    .padding(horizontal = DayteskSpacing.lg, vertical = DayteskSpacing.md)
                    .semantics { testTag = "recordatorio_text_field" },
            ) {
                BasicTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = DayteskTypography.bodyMd.copy(color = DayteskColors.TextPrimary),
                    cursorBrush = SolidColor(DayteskColors.Primary),
                    decorationBox = { inner ->
                        if (texto.isEmpty()) {
                            Text(
                                text = "¿Qué querés recordar?",
                                style = DayteskTypography.bodyMd,
                                color = DayteskColors.TextDisabled,
                            )
                        }
                        inner()
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .padding(vertical = DayteskSpacing.lg)
                    .semantics { testTag = "recordatorio_date_row" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Fecha",
                    tint = if (fecha == null) DayteskColors.TextDisabled else DayteskColors.Primary,
                    modifier = Modifier.size(DayteskSpacing.xl),
                )
                Spacer(modifier = Modifier.width(DayteskSpacing.sm))
                Text(
                    text = fecha?.let { formatFecha(it) } ?: "Elegir fecha (obligatoria)",
                    style = DayteskTypography.bodySm,
                    color = if (fecha == null) DayteskColors.TextDisabled else DayteskColors.TextSecondary,
                )
            }

            Text(
                text = "Repetición",
                style = DayteskTypography.caption.copy(fontWeight = FontWeight.Medium),
                color = DayteskColors.TextSecondary,
                modifier = Modifier.padding(bottom = DayteskSpacing.sm),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
                verticalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
            ) {
                Repeticion.entries.forEach { option ->
                    val isSelected = option == repeticion
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) DayteskColors.PrimaryLight else DayteskColors.Background,
                            )
                            .then(
                                if (!isSelected) {
                                    Modifier.border(1.dp, DayteskColors.Border, RoundedCornerShape(50))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { repeticion = option }
                            .padding(horizontal = DayteskSpacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label(),
                            style = DayteskTypography.bodySm,
                            color = if (isSelected) DayteskColors.Primary else DayteskColors.TextSecondary,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = DayteskSpacing.lg)
                    .height(1.dp)
                    .background(DayteskColors.Divider),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing && onDelete != null) {
                    Text(
                        text = "Eliminar",
                        style = DayteskTypography.bodySm,
                        color = DayteskColors.Urgent,
                        modifier = Modifier
                            .clickable { initial?.let(onDelete) }
                            .padding(vertical = DayteskSpacing.md)
                            .semantics { testTag = "recordatorio_delete" },
                    )
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(DayteskShapes.pill)
                        .background(if (canSave) DayteskColors.Primary else DayteskColors.PrimaryLight)
                        .clickable(enabled = canSave) { onSave(build()) }
                        .padding(horizontal = DayteskSpacing.xxxl, vertical = DayteskSpacing.md)
                        .semantics { testTag = "recordatorio_save" },
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
                fecha = millis
                showTimePicker = false
                pendingDateMillis = null
            },
        )
    }
}

private fun formatFecha(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
