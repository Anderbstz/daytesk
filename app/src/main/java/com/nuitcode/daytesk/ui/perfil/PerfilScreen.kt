package com.nuitcode.daytesk.ui.perfil

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.theme.DayteskTypography

@Composable
fun PerfilScreen(data: DayteskData) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Perfil",
            style = DayteskTypography.h2,
        )
    }
}
