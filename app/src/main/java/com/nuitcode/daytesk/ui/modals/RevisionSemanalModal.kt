package com.nuitcode.daytesk.ui.modals

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.ui.inicio.RoundCheckbox

private data class ChecklistItem(
    val label: String,
    val subtitle: String? = null,
)

private val checklistItems = listOf(
    ChecklistItem("Procesar inbox", "5 items pendientes"),
    ChecklistItem("Revisar vencidas", "2 tareas vencidas"),
    ChecklistItem("Actualizar próximas acciones"),
    ChecklistItem("Revisar proyectos"),
    ChecklistItem("Limpiar completadas", "8 tareas para archivar"),
)

@Composable
fun RevisionSemanalModal(
    onDismiss: () -> Unit,
    onCompletar: () -> Unit,
    onProgramar: () -> Unit = {},
) {
    var checkedItems by remember { mutableStateOf(setOf<Int>()) }
    val totalItems = checklistItems.size
    val checkedCount = checkedItems.size
    val progressFraction = if (totalItems > 0) checkedCount.toFloat() / totalItems.toFloat() else 0f

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
                .clickable(enabled = false) { } // consume clicks
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Top: Title + Programar ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Revisión semanal",
                    style = DayteskTypography.display,
                    color = DayteskColors.TextPrimary,
                )
                Text(
                    text = "Programar",
                    style = DayteskTypography.label,
                    color = DayteskColors.Primary,
                    modifier = Modifier.clickable { onProgramar() },
                )
            }

            // ── Progress bar ────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DayteskSpacing.xl),
            ) {
                // Background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DayteskColors.PrimaryLight),
                ) {
                    // Fill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(DayteskColors.Primary),
                    )
                }

                // Caption
                Text(
                    text = "$checkedCount de $totalItems completado",
                    style = DayteskTypography.bodySm,
                    color = DayteskColors.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DayteskSpacing.sm),
                    textAlign = TextAlign.Center,
                )
            }

            // ── Checklist items ─────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                checklistItems.forEachIndexed { index, item ->
                    val isChecked = index in checkedItems
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checkedItems = if (isChecked) checkedItems - index
                                else checkedItems + index
                            }
                            .padding(vertical = DayteskSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundCheckbox(
                            checked = isChecked,
                            onToggle = { nowChecked ->
                                checkedItems = if (nowChecked) checkedItems + index
                                else checkedItems - index
                            },
                        )
                        Spacer(modifier = Modifier.width(DayteskSpacing.md))
                        Column {
                            Text(
                                text = item.label,
                                style = DayteskTypography.bodyMd,
                                color = if (isChecked) DayteskColors.TextDisabled
                                else DayteskColors.TextPrimary,
                            )
                            if (item.subtitle != null) {
                                Text(
                                    text = item.subtitle,
                                    style = DayteskTypography.caption,
                                    color = DayteskColors.TextDisabled,
                                )
                            }
                        }
                    }
                }
            }

            // ── Bottom button ───────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(DayteskShapes.pill)
                        .background(DayteskColors.Primary)
                        .clickable { onCompletar() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Completar revisión",
                        style = DayteskTypography.bodySm.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(DayteskSpacing.xxl))
        }
    }
}
