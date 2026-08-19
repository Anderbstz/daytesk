package com.nuitcode.daytesk.ui.perfil

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuitcode.daytesk.model.DayteskUser
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PerfilScreen(
    data: DayteskData,
    onNavigateContextos: () -> Unit = {},
    onNavigateConfiguracion: () -> Unit = {},
    onNavigateAyuda: () -> Unit = {},
    onNavigateHistorial: () -> Unit = {},
    onClearLocalData: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 1. Profile Header ──────────────────────────────────
        ProfileHeader()

        Spacer(modifier = Modifier.height(DayteskSpacing.xxl))

        // ── 2. Stats Row ───────────────────────────────────────
        StatsRow(data)

        Spacer(modifier = Modifier.height(DayteskSpacing.xxxl))

        // ── 3. Settings List ───────────────────────────────────
        SettingsList(
            onNavigateContextos = onNavigateContextos,
            onNavigateConfiguracion = onNavigateConfiguracion,
            onNavigateAyuda = onNavigateAyuda,
            onNavigateHistorial = onNavigateHistorial,
            onClearLocalData = onClearLocalData,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  PROFILE HEADER
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ProfileHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar circle with gradient
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            DayteskColors.Primary,
                            DayteskColors.ContextPersonal,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = DayteskUser.initials,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Color.White,
                ),
            )
        }

        Spacer(modifier = Modifier.height(DayteskSpacing.md))

        Text(
            text = DayteskUser.displayName,
            style = DayteskTypography.h1,
            color = DayteskColors.TextPrimary,
        )

        Text(
            text = DayteskUser.email,
            style = DayteskTypography.bodySm,
            color = DayteskColors.TextSecondary,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  STATS ROW
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StatsRow(data: DayteskData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            value = data.stats.rachaActual.toString(),
            label = "Racha",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = data.stats.totalCompletadasHistorico.toString(),
            label = "Completadas",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = data.stats.tareasHoy.toString(),
            label = "Pendientes",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = DayteskColors.TextPrimary,
                ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = DayteskColors.TextSecondary,
                ),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SETTINGS LIST
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SettingsList(
    onNavigateContextos: () -> Unit,
    onNavigateConfiguracion: () -> Unit,
    onNavigateAyuda: () -> Unit,
    onNavigateHistorial: () -> Unit,
    onClearLocalData: () -> Unit,
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsRow(
            icon = { GearIcon() },
            text = "Configuración",
            onClick = onNavigateConfiguracion,
        )
        SettingsDivider()
        SettingsRow(
            icon = { FolderIcon() },
            text = "Contextos",
            onClick = onNavigateContextos,
        )
        SettingsDivider()
        SettingsRow(
            icon = { FolderIcon() },
            text = "Historial",
            onClick = onNavigateHistorial,
        )
        SettingsDivider()
        SettingsRow(
            icon = { InfoIcon() },
            text = "Ayuda y soporte",
            onClick = onNavigateAyuda,
        )
        SettingsDivider()
        SettingsRow(
            icon = { LogoutIcon() },
            text = "Cerrar sesión",
            textColor = DayteskColors.Urgent,
            onClick = { showLogoutConfirm = true },
        )
    }
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Cerrar sesión") },
            text = {
                Text("Se cierra tu sesión. Las tareas de este teléfono se quedan guardadas.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearLocalData()
                    showLogoutConfirm = false
                }) {
                    Text("Cerrar sesión", color = DayteskColors.Urgent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 20.dp)
            .background(DayteskColors.Divider),
    )
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    text: String,
    textColor: Color = DayteskColors.TextPrimary,
    onClick: (() -> Unit)? = null,
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .padding(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = 14.dp,
        )
    val modifier = if (onClick != null) {
        baseModifier.clickable { onClick() }
    } else {
        baseModifier
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(22.dp)) {
            icon()
        }
        Spacer(modifier = Modifier.width(DayteskSpacing.lg))
        Text(
            text = text,
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = textColor,
            ),
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  CANVAS-DRAWN ICONS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GearIcon() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val outerR = size.minDimension * 0.36f
        val innerR = size.minDimension * 0.16f
        val strokeW = size.minDimension * 0.08f
        val color = DayteskColors.TextDisabled

        // Outer ring
        drawCircle(color, outerR, c, style = Stroke(strokeW))

        // Spokes (6 lines from inner to outer)
        for (i in 0 until 6) {
            val angle = i * (Math.PI.toFloat() / 3f)
            drawLine(
                color = color,
                start = Offset(c.x + innerR * cos(angle), c.y + innerR * sin(angle)),
                end = Offset(c.x + outerR * cos(angle), c.y + outerR * sin(angle)),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        }

        // Inner solid hub
        drawCircle(color, innerR, c)
    }
}

@Composable
private fun InfoIcon() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.4f
            val strokeW = size.minDimension * 0.08f
            drawCircle(
                color = DayteskColors.TextDisabled,
                radius = r,
                center = c,
                style = Stroke(strokeW),
            )
        }
        // "i" letter
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "i",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = DayteskColors.TextDisabled,
                ),
            )
        }
    }
}

@Composable
private fun LogoutIcon() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeW = size.minDimension * 0.09f
        val cY = size.height / 2f
        val margin = size.minDimension * 0.15f
        val barX = size.width - margin
        val color = DayteskColors.Urgent

        // Vertical bar (door edge) at right side
        drawLine(
            color = color,
            start = Offset(barX, margin),
            end = Offset(barX, size.height - margin),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )

        // Arrow shaft pointing left
        val arrowEndX = margin
        drawLine(
            color = color,
            start = Offset(barX - margin * 0.5f, cY),
            end = Offset(arrowEndX, cY),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )

        // Arrowhead
        val hs = size.minDimension * 0.12f
        val path = Path().apply {
            moveTo(arrowEndX + hs, cY - hs)
            lineTo(arrowEndX, cY)
            lineTo(arrowEndX + hs, cY + hs)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun FolderIcon() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeW = size.minDimension * 0.08f
        val color = DayteskColors.TextDisabled
        val w = size.width
        val h = size.height
        val inset = size.minDimension * 0.12f

        // Folder body — rounded rectangle covering most of the canvas.
        val bodyTop = inset + size.minDimension * 0.15f
        val bodyLeft = inset
        val bodyRight = w - inset
        val bodyBottom = h - inset

        val body = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = bodyLeft,
                    top = bodyTop,
                    right = bodyRight,
                    bottom = bodyBottom,
                    radiusX = strokeW,
                    radiusY = strokeW,
                ),
            )
        }
        drawPath(body, color, style = Stroke(strokeW))

        // Folder tab — small rectangle at top-left.
        val tabWidth = w * 0.45f
        val tabTop = inset
        val tabHeight = bodyTop - inset
        val tab = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = bodyLeft,
                    top = tabTop,
                    right = bodyLeft + tabWidth,
                    bottom = tabTop + tabHeight,
                    radiusX = strokeW * 0.6f,
                    radiusY = strokeW * 0.6f,
                ),
            )
        }
        drawPath(tab, color, style = Stroke(strokeW))
    }
}
