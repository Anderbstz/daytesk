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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevaTareaModal(
    onDismiss: () -> Unit,
    onSave: (Tarea) -> Unit,
    contextos: List<Contexto> = Contexto.DEFAULTS,
) {
    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var selectedContextoId by remember { mutableStateOf(3L) }
    var selectedPrioridad by remember { mutableStateOf(Prioridad.MEDIA) }
    var reminderOn by remember { mutableStateOf(false) }
    var fechaVencimiento by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val selectedContexto = contextos.firstOrNull { it.id == selectedContextoId }
        ?: Contexto.DEFAULTS.first { it.id == 3L }

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
    ) {
            // ── Top bar ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Close button
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = DayteskColors.TextDisabled,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onDismiss() },
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Nueva tarea",
                    style = DayteskTypography.h1,
                    color = DayteskColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Guardar",
                    style = DayteskTypography.label,
                    color = DayteskColors.Primary,
                    modifier = Modifier.clickable {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(DayteskSpacing.sm),
                    ) {
                        contextos.forEach { ctx ->
                            val isSelected = ctx.id == selectedContextoId
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
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
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ctx.label(),
                                    style = DayteskTypography.bodySm,
                                    color = if (isSelected) ctx.color() else DayteskColors.TextSecondary,
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
                            modifier = Modifier.size(20.dp),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Prioridad.entries.forEach { p ->
                            val isSelected = p == selectedPrioridad
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
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
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = p.label(),
                                    style = DayteskTypography.tiny,
                                    color = if (isSelected) p.color() else DayteskColors.TextSecondary,
                                )
                            }
                        }
                    }
                }

                // Reminder toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Recordatorio",
                        tint = DayteskColors.TextDisabled,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(DayteskSpacing.sm))
                    Text(
                        text = "Recordatorio",
                        style = DayteskTypography.bodySm,
                        color = DayteskColors.TextSecondary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = reminderOn,
                        onCheckedChange = { reminderOn = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DayteskColors.Primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = DayteskColors.Border,
                        ),
                    )
                }

                // Repeat row
                FormRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Repetir",
                            tint = DayteskColors.TextDisabled,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = "Repetir",
                    value = "No repetir",
                    showChevron = true,
                )

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
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
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
        val timeState = rememberTimePickerState(initialHour = 12, initialMinute = 0)
        Dialog(
            onDismissRequest = {
                showTimePicker = false
                pendingDateMillis = null
            },
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DayteskColors.Surface,
            ) {
                Column(Modifier.padding(16.dp)) {
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
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = pendingDateMillis!!
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
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (minHeight > 0) Modifier.height(minHeight.dp) else Modifier)
                .clip(shape)
                .background(DayteskColors.Surface)
                .border(1.dp, DayteskColors.Border, shape)
                .padding(horizontal = 14.dp, vertical = if (singleLine) 14.dp else 10.dp),
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    val clamped = if (maxLength != null) newValue.take(maxLength) else newValue
                    onValueChange(clamped)
                },
                placeholder = {
                    Text(
                        text = placeholder,
                        style = DayteskTypography.bodyMd,
                        color = DayteskColors.TextDisabled,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                textStyle = DayteskTypography.bodyMd.copy(color = DayteskColors.TextPrimary),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = DayteskColors.Primary,
                ),
                shape = shape,
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
            Spacer(modifier = Modifier.width(4.dp))
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
            .height(48.dp)
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
