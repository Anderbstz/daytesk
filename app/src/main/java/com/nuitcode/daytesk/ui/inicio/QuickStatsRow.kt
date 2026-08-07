package com.nuitcode.daytesk.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.data.DayteskStats
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskTypography

private data class StatConfig(
    val icon: ImageVector,
    val label: String,
    val count: Int,
    val subtitle: String,
    val bgColor: Color,
    val iconColor: Color,
)

@Composable
fun QuickStatsRow(stats: DayteskStats) {
    val statItems = listOf(
        StatConfig(
            icon = Icons.Default.DateRange,
            label = "Hoy",
            count = stats.tareasHoy,
            subtitle = "tareas",
            bgColor = DayteskColors.PrimaryLight,
            iconColor = DayteskColors.Primary,
        ),
        StatConfig(
            icon = Icons.Default.Email,
            label = "Inbox",
            count = stats.inboxPendientes,
            subtitle = "pendientes",
            bgColor = DayteskColors.WarningLight,
            iconColor = DayteskColors.Warning,
        ),
        StatConfig(
            icon = Icons.Default.CheckCircle,
            label = "Hechas",
            count = stats.totalCompletadasHistorico,
            subtitle = "completadas",
            bgColor = DayteskColors.SuccessLight,
            iconColor = DayteskColors.Success,
        ),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        statItems.forEach { stat ->
            StatCard(stat = stat, modifier = Modifier.fillMaxHeight().weight(1f))
        }
    }
}

@Composable
private fun StatCard(stat: StatConfig, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .testTag("StatCard")
            .background(stat.bgColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = stat.icon,
            contentDescription = stat.label,
            tint = stat.iconColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stat.label,
            style = DayteskTypography.caption,
            color = DayteskColors.TextSecondary,
        )
        Text(
            text = "${stat.count}",
            style = DayteskTypography.statsNumber,
            color = DayteskColors.TextPrimary,
        )
        Text(
            text = stat.subtitle,
            style = DayteskTypography.caption,
            color = DayteskColors.TextSecondary,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
