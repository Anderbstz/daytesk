package com.nuitcode.daytesk.ui.inbox

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.model.InboxItem
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InboxScreen(
    data: DayteskData,
    onItemClick: (Long) -> Unit = {},
    onProcessAll: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        InboxTopBar(
            count = data.inbox.size,
            onProcessAll = onProcessAll,
        )
        QuickCaptureBar()
        InboxList(
            items = data.inbox,
            onItemClick = onItemClick,
        )
    }
}

@Composable
private fun InboxTopBar(
    count: Int,
    onProcessAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Inbox",
            style = DayteskTypography.display,
            color = DayteskColors.TextPrimary,
        )
        Row(
            modifier = Modifier.clickable { onProcessAll() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Procesar todo",
                style = DayteskTypography.label,
                color = DayteskColors.Primary,
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(DayteskColors.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    style = DayteskTypography.badge,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun QuickCaptureBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, DayteskColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(1.5.dp, DayteskColors.TextDisabled, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Agregar",
                tint = DayteskColors.TextDisabled,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = "Agregar tarea rápida...",
            style = DayteskTypography.bodySm,
            color = DayteskColors.TextDisabled,
            modifier = Modifier.weight(1f),
        )
        MicIcon(
            modifier = Modifier.size(20.dp),
            tint = DayteskColors.Primary,
        )
    }
}

@Composable
private fun InboxList(
    items: List<InboxItem>,
    onItemClick: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            InboxItemCard(
                item = item,
                onClick = { onItemClick(item.id) },
            )
        }
    }
}

@Composable
private fun InboxItemCard(
    item: InboxItem,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(DayteskColors.TextDisabled),
                )
                Text(
                    text = item.texto,
                    style = DayteskTypography.bodyMd,
                    color = DayteskColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatRelativeTime(item.timestamp),
                    style = DayteskTypography.caption,
                    color = DayteskColors.TextDisabled,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DayteskColors.Divider),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Procesar",
                    tint = DayteskColors.TextDisabled,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Toca para procesar",
                    color = DayteskColors.TextDisabled,
                    style = DayteskTypography.caption.copy(fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun MicIcon(
    modifier: Modifier = Modifier,
    tint: Color = DayteskColors.Primary,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.5.dp.toPx()
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Mic body — rounded pill shape
        val bodyW = w * 0.25f
        val bodyH = h * 0.45f
        val bodyLeft = cx - bodyW / 2f
        val bodyTop = h * 0.15f

        val bodyPath = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = bodyLeft,
                    top = bodyTop,
                    right = bodyLeft + bodyW,
                    bottom = bodyTop + bodyH,
                    radiusX = bodyW / 2f,
                    radiusY = bodyW / 2f,
                ),
            )
        }
        drawPath(bodyPath, tint, style = Stroke(width = strokeWidth))

        // Connector arc
        val arcPath = Path().apply {
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = cx - w * 0.3f,
                    top = bodyTop + bodyH - 2.dp.toPx(),
                    right = cx + w * 0.3f,
                    bottom = bodyTop + bodyH + h * 0.25f,
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = true,
            )
        }
        drawPath(arcPath, tint, style = Stroke(width = strokeWidth))

        // Vertical line down
        val lineTop = bodyTop + bodyH + h * 0.04f
        val lineBottom = h * 0.82f
        drawLine(tint, Offset(cx, lineTop), Offset(cx, lineBottom), strokeWidth)

        // Base horizontal line
        val baseY = h * 0.82f
        val baseHalf = w * 0.15f
        drawLine(tint, Offset(cx - baseHalf, baseY), Offset(cx + baseHalf, baseY), strokeWidth)
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val hours = diff / (1000 * 60 * 60)
    val days = hours / 24

    return when {
        hours < 1 -> "hace 1h"
        hours < 24 -> "hace ${hours}h"
        days == 1L -> "ayer"
        days < 7 -> "hace ${days}d"
        else -> {
            val sdf = SimpleDateFormat("d MMM", Locale.forLanguageTag("es-ES"))
            sdf.format(Date(timestamp))
        }
    }
}
