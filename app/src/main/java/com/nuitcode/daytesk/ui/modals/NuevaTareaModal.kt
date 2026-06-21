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
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography

@Composable
fun NuevaTareaModal(
    onDismiss: () -> Unit,
    onGuardar: (titulo: String, descripcion: String, contexto: Contexto, prioridad: Prioridad, reminderOn: Boolean) -> Unit,
) {
    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var selectedContexto by remember { mutableStateOf(Contexto.PERSONAL) }
    var selectedPrioridad by remember { mutableStateOf(Prioridad.MEDIA) }
    var reminderOn by remember { mutableStateOf(false) }

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
                .background(DayteskColors.Background, DayteskShapes.large)
                .clickable(enabled = false) { } // consume clicks on content
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
                            onGuardar(titulo, descripcion, selectedContexto, selectedPrioridad, reminderOn)
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
                )

                // Description textarea
                TextFieldThemed(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    placeholder = "Descripción (opcional)",
                    singleLine = false,
                    minHeight = 80,
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
                        Contexto.entries.forEach { ctx ->
                            val isSelected = ctx == selectedContexto
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
                                    .clickable { selectedContexto = ctx }
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
                    value = "Sin fecha",
                    showChevron = true,
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
                            onGuardar(titulo, descripcion, selectedContexto, selectedPrioridad, reminderOn)
                        }
                    },
                    enabled = titulo.isNotBlank(),
                )
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
) {
    val shape = RoundedCornerShape(12.dp)
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
            onValueChange = onValueChange,
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
}

@Composable
private fun FormRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    showChevron: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
