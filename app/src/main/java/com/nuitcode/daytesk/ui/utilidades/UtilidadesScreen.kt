package com.nuitcode.daytesk.ui.utilidades

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskElevation
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography

// ── Data model ───────────────────────────────────────────────────

private data class ToolItem(
    val title: String,
    val description: String,
    val containerColor: Color,
    val iconColor: Color,
)

private val tools = listOf(
    ToolItem(
        title = "Convertir imágenes",
        description = "PNG ↔ ICO, quitar fondo",
        containerColor = DayteskColors.PrimaryLight,
        iconColor = DayteskColors.Primary,
    ),
    ToolItem(
        title = "Transcribir audio",
        description = "Audio a texto",
        containerColor = Color(0xFFF3EEFF),
        iconColor = Color(0xFFD4B8FD),
    ),
    ToolItem(
        title = "Video a texto/audio",
        description = "Extraer texto o audio",
        containerColor = DayteskColors.UrgentLight,
        iconColor = DayteskColors.Urgent,
    ),
)

// ── Main screen ──────────────────────────────────────────────────

@Composable
fun UtilidadesScreen(data: DayteskData) {
    Column(modifier = Modifier.fillMaxSize()) {
        // TopBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
        ) {
            Text(
                text = "Utilidades",
                style = DayteskTypography.display,
                color = DayteskColors.TextPrimary,
            )
        }

        // Tools grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
            contentPadding = PaddingValues(horizontal = DayteskSpacing.xl, vertical = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(tools) { tool ->
                ToolCard(
                    tool = tool,
                    onClick = { /* TODO: navigate to tool screen */ },
                )
            }
            item {
                PlaceholderCard()
            }
        }
    }
}

// ── Tool card ────────────────────────────────────────────────────

@Composable
private fun ToolCard(
    tool: ToolItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = DayteskShapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = DayteskElevation.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
                .padding(DayteskSpacing.xl),
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tool.containerColor),
                contentAlignment = Alignment.Center,
            ) {
                ToolIcon(tool = tool, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(DayteskSpacing.md))

            // Title
            Text(
                text = tool.title,
                style = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                color = DayteskColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = tool.description,
                style = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                ),
                color = DayteskColors.TextSecondary,
            )

            // Push arrow to bottom
            Spacer(modifier = Modifier.weight(1f))

            // Arrow right at bottom-right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = DayteskColors.TextDisabled,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── Placeholder card ─────────────────────────────────────────────

@Composable
private fun PlaceholderCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clip(DayteskShapes.medium)
            .dashedBorder(
                color = DayteskColors.Border,
                strokeWidth = 2.dp,
                dashLength = 8.dp,
                gapLength = 4.dp,
                shape = DayteskShapes.medium,
            )
            .padding(DayteskSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PlusIcon(
                modifier = Modifier.size(32.dp),
                color = DayteskColors.TextDisabled,
            )
            Spacer(modifier = Modifier.height(DayteskSpacing.sm))
            Text(
                text = "Próximamente",
                style = DayteskTypography.caption,
                color = DayteskColors.TextDisabled,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Icon dispatcher ──────────────────────────────────────────────

@Composable
private fun ToolIcon(
    tool: ToolItem,
    modifier: Modifier,
) {
    when (tool) {
        tools[0] -> ImageIcon(modifier = modifier, color = tool.iconColor)
        tools[1] -> MicIcon(modifier = modifier, color = tool.iconColor)
        tools[2] -> PlayIcon(modifier = modifier, color = tool.iconColor)
        else -> ImageIcon(modifier = modifier, color = tool.iconColor)
    }
}

// ── Canvas-drawn icons ───────────────────────────────────────────

@Composable
private fun ImageIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.07f

        // Frame
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.1f, h * 0.15f),
            size = Size(w * 0.8f, h * 0.7f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = Stroke(sw),
        )

        // Sun circle
        drawCircle(
            color = color,
            radius = w * 0.12f,
            center = Offset(w * 0.7f, h * 0.32f),
        )

        // Mountain
        val mountain = Path().apply {
            moveTo(w * 0.18f, h * 0.75f)
            lineTo(w * 0.42f, h * 0.38f)
            lineTo(w * 0.66f, h * 0.75f)
            close()
        }
        drawPath(mountain, color, style = Stroke(sw))
    }
}

@Composable
private fun MicIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.07f
        val bw = w * 0.36f  // body width
        val bh = h * 0.5f   // body height
        val bx = (w - bw) / 2f
        val by = h * 0.12f

        // Mic body — rounded pill
        drawRoundRect(
            color = color,
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bw / 2f),
            style = Stroke(sw),
        )

        // Stand — arc below
        val arc = Path().apply {
            moveTo(w * 0.18f, h * 0.66f)
            quadraticTo(w * 0.5f, h * 0.95f, w * 0.82f, h * 0.66f)
        }
        drawPath(arc, color, style = Stroke(sw))
    }
}

@Composable
private fun PlayIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.07f

        val triangle = Path().apply {
            moveTo(w * 0.28f, h * 0.12f)
            lineTo(w * 0.85f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.88f)
            close()
        }
        drawPath(triangle, color, style = Stroke(sw))
    }
}

@Composable
private fun PlusIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.08f
        val cx = w / 2f
        val cy = h / 2f
        val inset = w * 0.2f

        drawLine(color, Offset(cx, inset), Offset(cx, w - inset), sw)
        drawLine(color, Offset(inset, cy), Offset(w - inset, cy), sw)
    }
}

// ── Dashed border modifier ───────────────────────────────────────

@Composable
private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp = 2.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 4.dp,
    shape: Shape = DayteskShapes.medium,
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val swPx = with(density) { strokeWidth.toPx() }
    val dlPx = with(density) { dashLength.toPx() }
    val glPx = with(density) { gapLength.toPx() }

    return this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(Rect(Offset.Zero, size)) }
        }
        drawPath(
            path,
            color = color,
            style = Stroke(
                width = swPx,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(dlPx, glPx),
                    phase = 0f,
                ),
            ),
        )
    }
}
