package com.nuitcode.daytesk.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CompactTaskCard(
    tarea: Tarea,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DayteskColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = DayteskElevation.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Priority dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tarea.prioridad.color(), CircleShape),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Context chip
            ContextChip(contexto = tarea.contexto)

            // Time
            if (tarea.fechaVencimiento != null) {
                Spacer(modifier = Modifier.weight(1f))
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                Text(
                    text = timeFormat.format(Date(tarea.fechaVencimiento)),
                    style = DayteskTypography.caption,
                    color = DayteskColors.TextSecondary,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Text(
            text = tarea.titulo,
            style = DayteskTypography.bodyMd,
            color = DayteskColors.TextPrimary,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        )
    }
}

private object DayteskElevation {
    val card = 2.dp
}
