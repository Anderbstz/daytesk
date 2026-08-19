package com.nuitcode.daytesk.ui.perfil

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.nuitcode.daytesk.notification.ReminderScheduler
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.utilities.common.PermissionGate
import com.nuitcode.daytesk.utilities.common.PermissionRationale

@Composable
fun ConfiguracionScreen(
    onBack: () -> Unit,
    onOpenWeeklyReview: () -> Unit,
) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(true) }

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
            Text("Configuración", style = DayteskTypography.display, color = DayteskColors.TextPrimary)
        }

        val notificationPermissions = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }

        PermissionGate(
            permissions = notificationPermissions,
            rationale = PermissionRationale(
                title = "Notificaciones",
                message = "Daytesk avisa cuando una tarea está por vencer.",
            ),
        ) { requestPermission ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recordatorios", style = DayteskTypography.bodyMd, color = DayteskColors.TextPrimary)
                    Text(
                        "Avisos locales 1 hora antes y al vencer. No usan un servidor ni tienen costo.",
                        style = DayteskTypography.caption,
                        color = DayteskColors.TextSecondary,
                    )
                }
                Switch(
                    checked = notificationsOn,
                    onCheckedChange = { enabled ->
                        if (enabled && notificationPermissions.isNotEmpty()) {
                            requestPermission { notificationsOn = true }
                        } else {
                            notificationsOn = enabled
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DayteskColors.Primary,
                    ),
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ReminderScheduler.canScheduleExactAlarms(context)
        ) {
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
                modifier = Modifier.padding(horizontal = DayteskSpacing.lg),
            ) {
                Text("Permitir alarmas exactas", color = DayteskColors.Primary)
            }
        }

        TextButton(
            onClick = {
                ReminderScheduler.scheduleWeeklyReview(context)
                onOpenWeeklyReview()
            },
            modifier = Modifier.padding(horizontal = DayteskSpacing.lg),
        ) {
            Text("Programar revisión semanal", color = DayteskColors.Primary)
        }
    }
}
