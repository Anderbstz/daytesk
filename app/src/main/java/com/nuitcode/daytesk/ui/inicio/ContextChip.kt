package com.nuitcode.daytesk.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskTypography

@Composable
fun ContextChip(contexto: Contexto) {
    val backgroundColor = contexto.color().copy(alpha = 0.2f)
    val textColor = when (contexto) {
        Contexto.CASA -> DayteskColors.ContextCasaText
        Contexto.TRABAJO -> DayteskColors.ContextTrabajoText
        Contexto.PERSONAL -> DayteskColors.ContextPersonalText
        Contexto.SALUD -> DayteskColors.ContextSaludText
    }

    Box(
        modifier = Modifier
            .height(26.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = contexto.label(),
            style = DayteskTypography.badge,
            color = textColor,
        )
    }
}
