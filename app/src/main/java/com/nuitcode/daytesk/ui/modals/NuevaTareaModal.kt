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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.graphics.SolidColor


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NuevaTareaModal(
    onDismiss: () -> Unit,
    onSave: (Tarea) -> Unit,
    contextos: List<Contexto> = Contexto.DEFAULTS,
    canAddContexto: Boolean = contextos.size < Contexto.MAX_COUNT,
    onAddContexto: (() -> Unit)? = null,
) {
    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var selectedContextoId by remember(contextos) {
        mutableStateOf((contextos.firstOrNull() ?: Contexto.FALLBACK).id)
    }
    var selectedPrioridad by remember { mutableStateOf(Prioridad.MEDIA) }
    var fechaVencimiento by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val selectedContexto = contextos.firstOrNull { it.id == selectedContextoId }
        ?: contextos.firstOrNull()
        ?: Contexto.FALLBACK

    fun buildTarea(): Tarea = Tarea(
        id = 0,
        titulo = titulo,
        descripcion = descripcion,
        contextoId = selectedContextoId,
        contexto = selectedContexto,
        prioridad = selectedPrioridad,
        estado = TareaEstado.PENDIENTE,
        fechaVencimiento = fechaVencimiento,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background)
            .systemBarsPadding()
    ) {
            // ── Top bar ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = DayteskColors.TextDisabled,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(DayteskSpacing.xxl)
                        .clickable { onDismiss() },
                )
                Text(
                    text = "Nueva tarea",
                    style = DayteskTypography.h1,
                    color = DayteskColors.TextPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = DayteskSpacing.xxxxl),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Guardar",
                    style = DayteskTypography.label,
                    color = DayteskColors.Primary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable {
                            if (titulo.isNotBlank()) {
                                onSave(buildTarea())
                            }
                        },
                )
            }

            // ── Scrollable form ─────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DayteskSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(DayteskSpacing.lg),
            ) {
                // Title input
                TextFieldThemed(
                    value = titulo,
                    onValueChange = { titulo = it },
                    placeholder = "Título de la tarea",
                    singleLine = true,
                    maxLength = Tarea.TITULO_MAX_LENGTH,
                    supportingText = {
                        Text(
                            text = "${titulo.length}/${Tarea.TITULO_MAX_LENGTH}",
                            style = DayteskTypography.tiny,
                            color = DayteskColors.TextDisabled,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                )

                // Description textarea
                TextFieldThemed(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    placeholder = "Descripción (opcional)",
                    singleLine = false,
                    minHeight = 80,
                    maxLength = Tarea.DESCRIPCION_MAX_LENGTH,
                    supportingText = {
                        Text(
                            text = "${descripcion.length}/${Tarea.DESCRIPCION_MAX_LENGTH}",
                            style = DayteskTypography.tiny,
                            color = DayteskColors.TextDisabled,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                )

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DayteskColors.Divider),
                )

                // Context selector
                Column {
                    Text(
                        text = "Contexto",
                        style = DayteskTypography.bodySm,
                        color = DayteskColors.TextSecondary,
                        modifier = Modifier.padding(bottom = DayteskSpacing.sm),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
                    ) {
                        contextos.forEach { ctx ->
                            val isSelected = ctx.id == selectedContextoId
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
                                    .clickable { selectedContextoId = ctx.id }
                                    .padding(horizontal = DayteskSpacing.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ctx.label(),
                                    style = DayteskTypography.bodyMd,
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
                                    .padding(horizontal = DayteskSpacing.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+",
                                    style = DayteskTypography.h3,
                                    color = DayteskColors.Primary,
                                )
                            }
                        }
                    }
                }

                // Date row
                FormRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Fecha",
                            tint = DayteskColors.TextDisabled,
                            modifier = Modifier.size(DayteskSpacing.xl),
                        )
                    },
                    label = "Fecha",
                    value = formatTimestamp(fechaVencimiento),
                    showChevron = true,
                    onClick = { showDatePicker = true },
                )

                // Priority row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "⚑", fontSize = 20.sp, color = DayteskColors.TextDisabled)
                    Spacer(modifier = Modifier.width(DayteskSpacing.sm))
                    Text(
                        text = "Prioridad",
                        style = DayteskTypography.bodySm,
                        color = DayteskColors.TextSecondary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DayteskSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DayteskSpacing.sm),
                    ) {
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

                Spacer(modifier = Modifier.height(DayteskSpacing.sm))
            }

            // ── Bottom button ───────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
            ) {
                PillButton(
                    text = "Crear tarea",
                    onClick = {
                        if (titulo.isNotBlank()) {
                            onSave(buildTarea())
                        }
                    },
                    enabled = titulo.isNotBlank(),
                )
        }
    }

    // ── Date picker dialog ─────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis
                    showDatePicker = false
                    if (selected != null) {
                        pendingDateMillis = selected
                        showTimePicker = true
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Time picker dialog (M3 has no TimePickerDialog; wrap manually) ─
    if (showTimePicker && pendingDateMillis != null) {
        val now = remember { Calendar.getInstance() }
        val timeState = rememberTimePickerState(
            initialHour = now.get(Calendar.HOUR_OF_DAY),
            initialMinute = now.get(Calendar.MINUTE),
        )
        Dialog(
            onDismissRequest = {
                showTimePicker = false
                pendingDateMillis = null
            },
        ) {
            Surface(
                shape = DayteskShapes.small,
                color = DayteskColors.Surface,
            ) {
                Column(Modifier.padding(DayteskSpacing.lg)) {
                    TimePicker(state = timeState)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            showTimePicker = false
                            pendingDateMillis = null
                        }) {
                            Text("Cancelar")
                        }
                        TextButton(onClick = {
                            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pendingDateMillis!!
                            }
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, utc.get(Calendar.YEAR))
                                set(Calendar.MONTH, utc.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
                                set(Calendar.HOUR_OF_DAY, timeState.hour)
                                set(Calendar.MINUTE, timeState.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            fechaVencimiento = cal.timeInMillis
                            showTimePicker = false
                            pendingDateMillis = null
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────

@Composable
private fun TextFieldThemed(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minHeight: Int = 0,
    maxLength: Int? = null,
    supportingText: (@Composable () -> Unit)? = null,
) {
    val shape = DayteskShapes.small
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (minHeight > 0) Modifier.height(minHeight.dp) else Modifier)
                .clip(shape)
                .background(DayteskColors.Surface)
                .border(1.dp, DayteskColors.Border, shape)
                .padding(horizontal = DayteskSpacing.lg, vertical = DayteskSpacing.md),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { newValue ->
                    val clamped = if (maxLength != null) newValue.take(maxLength) else newValue
                    onValueChange(clamped)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                textStyle = DayteskTypography.bodyMd.copy(color = DayteskColors.TextPrimary),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                cursorBrush = SolidColor(DayteskColors.Primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = DayteskTypography.bodyMd,
                                color = DayteskColors.TextDisabled,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (supportingText != null) {
            supportingText()
        }
    }
}

@Composable
private fun FormRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = Modifier.fillMaxWidth().let { base ->
        if (onClick != null) base.clickable { onClick() } else base
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(DayteskSpacing.sm))
        Text(
            text = label,
            style = DayteskTypography.bodySm,
            color = DayteskColors.TextSecondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = DayteskTypography.bodySm,
            color = DayteskColors.TextDisabled,
        )
        if (showChevron) {
            Spacer(modifier = Modifier.width(DayteskSpacing.xxs))
            Text(
                text = "›",
                fontSize = 18.sp,
                color = DayteskColors.TextDisabled,
            )
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DayteskSpacing.xxxxl)
            .clip(DayteskShapes.pill)
            .background(if (enabled) DayteskColors.Primary else DayteskColors.PrimaryLight)
            .clickable(enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
            ),
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────

private fun formatTimestamp(millis: Long?): String {
    if (millis == null) return "Sin fecha"
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
