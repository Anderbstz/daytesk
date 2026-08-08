package com.nuitcode.daytesk.ui.contextos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskTypography

/**
 * Add/Edit context modal. Used both for new custom contexts (`initial = null`)
 * and for editing existing ones (`initial = non-null`). The color picker is
 * disabled for defaults (REQ-04) — the UI hides it by collapsing the grid to
 * a single static swatch when `lockedColor = true`.
 *
 * Validation:
 *  - `nombre` is required; `onSave` is gated by `nombre.isNotBlank()`.
 *  - `onSave(nombre, color)` is invoked on Guardar with the latest state.
 *  - Uniqueness against `Contexto.DEFAULTS` is enforced by the repository
 *    (REQ-03); the modal does NOT pre-validate here.
 *
 * Test tags:
 *  - `context_modal_name` — the name `OutlinedTextField`
 *  - `context_modal_color_<index>` — each swatch in the 8-color grid
 *  - `context_modal_save` — the Guardar button
 *  - `context_modal_cancel` — the Cancelar button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextEditModal(
    initial: Contexto?,
    onDismiss: () -> Unit,
    onSave: (nombre: String, color: Int) -> Unit,
) {
    val isEdit = initial != null
    val lockedColor = isEdit && initial.isDefault()

    var nombre by remember { mutableStateOf(initial?.nombre ?: "") }
    var selectedColor by remember { mutableStateOf(initial?.color ?: Contexto.PALETTE.first()) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = DayteskShapes.large,
        containerColor = DayteskColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (isEdit) "Editar contexto" else "Nuevo contexto",
                style = DayteskTypography.h2,
                color = DayteskColors.TextPrimary,
            )

            // ── Name input ───────────────────────────────────
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                placeholder = {
                    Text(
                        text = "Nombre del contexto",
                        style = DayteskTypography.bodyMd,
                        color = DayteskColors.TextDisabled,
                    )
                },
                singleLine = true,
                textStyle = DayteskTypography.bodyMd.copy(color = DayteskColors.TextPrimary),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = "context_modal_name" },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DayteskColors.Primary,
                    unfocusedBorderColor = DayteskColors.Border,
                    focusedContainerColor = DayteskColors.Background,
                    unfocusedContainerColor = DayteskColors.Background,
                    cursorColor = DayteskColors.Primary,
                ),
            )

            // ── Color picker ────────────────────────────────
            Column {
                Text(
                    text = "Color",
                    style = DayteskTypography.bodySm,
                    color = DayteskColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (lockedColor) {
                    // Single static swatch for defaults — no grid.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(selectedColor))
                            .border(2.dp, DayteskColors.Border, CircleShape)
                            .semantics { testTag = "context_modal_color_locked" },
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                    ) {
                        items(Contexto.PALETTE) { color ->
                            val isSelected = color == selectedColor
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .then(
                                        if (isSelected) Modifier.border(
                                            3.dp,
                                            DayteskColors.TextPrimary,
                                            CircleShape,
                                        ) else Modifier.border(
                                            1.dp,
                                            DayteskColors.Border,
                                            CircleShape,
                                        )
                                    )
                                    .clickable { selectedColor = color }
                                    .semantics {
                                        testTag = "context_modal_color_$color"
                                    },
                            )
                        }
                    }
                }
            }

            // ── Bottom action row ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { testTag = "context_modal_cancel" },
                ) {
                    Text(
                        text = "Cancelar",
                        style = DayteskTypography.label.copy(color = DayteskColors.TextSecondary),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(DayteskShapes.pill)
                        .background(
                            if (nombre.isNotBlank()) DayteskColors.Primary
                            else DayteskColors.PrimaryLight,
                        )
                        .clickable(enabled = nombre.isNotBlank()) {
                            onSave(nombre.trim(), selectedColor)
                        }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .semantics { testTag = "context_modal_save" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Guardar",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
