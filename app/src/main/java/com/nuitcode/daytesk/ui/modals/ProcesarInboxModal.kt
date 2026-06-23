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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.InboxItem
import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography

@Composable
fun ProcesarInboxModal(
    item: InboxItem,
    onDismiss: () -> Unit,
    onSave: (Contexto, Prioridad) -> Unit,
    onDelete: () -> Unit,
) {
    var selectedContexto by remember { mutableStateOf(Contexto.PERSONAL) }
    var selectedPrioridad by remember { mutableStateOf(Prioridad.MEDIA) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(DayteskSpacing.sm)) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = DayteskSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Asignar fecha",
                    tint = DayteskColors.TextDisabled,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(DayteskSpacing.sm))
                Text(
                    text = "Asignar fecha",
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Prioridad.entries.forEach { p ->
                        val isSelected = p == selectedPrioridad
                        Box(
                            modifier = Modifier
                                .height(32.dp)
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
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = p.label(),
                                style = DayteskTypography.badge,
                                color = if (isSelected) p.color() else DayteskColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            // Divider (visual)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DayteskColors.Divider)
                    .padding(vertical = DayteskSpacing.sm),
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
                        .padding(vertical = 12.dp),
                )

                Box(
                    modifier = Modifier
                        .clip(DayteskShapes.pill)
                        .background(DayteskColors.Primary)
                        .clickable { onSave(selectedContexto, selectedPrioridad) }
                        .padding(horizontal = 32.dp, vertical = 12.dp),
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
}
