package com.nuitcode.daytesk.ui.perfil

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nuitcode.daytesk.model.DayteskUser
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography

@Composable
fun AyudaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DayteskSpacing.sm, vertical = DayteskSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Ayuda y soporte", style = DayteskTypography.display, color = DayteskColors.TextPrimary)
        }
        Column(modifier = Modifier.padding(horizontal = DayteskSpacing.xl)) {
            Text(
                "Captura ideas en Inbox, conviértelas en tareas y márcalas al terminar. La racha cuenta días seguidos con al menos una tarea completada.",
                style = DayteskTypography.bodyMd,
                color = DayteskColors.TextSecondary,
            )
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:${DayteskUser.email}")
                        putExtra(Intent.EXTRA_SUBJECT, "Soporte Daytesk")
                    }
                    context.startActivity(Intent.createChooser(intent, "Enviar correo"))
                },
            ) {
                Text("Escribir a ${DayteskUser.email}", color = DayteskColors.Primary)
            }
        }
    }
}
