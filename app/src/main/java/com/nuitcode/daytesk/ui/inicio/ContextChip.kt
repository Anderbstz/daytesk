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
import com.nuitcode.daytesk.theme.DayteskTypography

/**
 * Renders a `@nombre` chip with the context's background color and a
 * luminance-derived text color (black on light, white on dark). Works for
 * any `Contexto` — defaults and custom alike — because the text-color rule
 * reads from the context's own `color` rather than a hardcoded palette
 * mapping (REQ design-system).
 */
@Composable
fun ContextChip(contexto: Contexto) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .background(
                color = contexto.colorLight(),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = contexto.label(),
            style = DayteskTypography.label,
            color = contexto.color(),
        )
    }
}
