package com.nuitcode.daytesk.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.nuitcode.daytesk.theme.DayteskElevation
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
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DayteskColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = DayteskElevation.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(tarea.prioridad.color(), CircleShape),
            )
            ContextChip(contexto = tarea.contexto)
            Spacer(modifier = Modifier.weight(1f))
            if (tarea.fechaVencimiento != null) {
                val dateFormat = SimpleDateFormat("d MMM", Locale.forLanguageTag("es-ES"))
                Text(
                    text = dateFormat.format(Date(tarea.fechaVencimiento)),
                    style = DayteskTypography.caption,
                    color = DayteskColors.TextSecondary,
                )
            }
        }

        Text(
            text = tarea.titulo,
            style = DayteskTypography.bodyMd,
            color = DayteskColors.TextPrimary,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        )
    }
}
