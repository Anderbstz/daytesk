package com.nuitcode.daytesk.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.theme.DayteskColors

@Composable
fun RoundCheckbox(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val boxModifier = if (checked) {
        modifier
            .size(24.dp)
            .background(DayteskColors.Primary, shape)
            .clickable { onToggle(false) }
    } else {
        modifier
            .size(24.dp)
            .border(2.dp, DayteskColors.Border, shape)
            .clickable { onToggle(true) }
    }

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Completada",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
