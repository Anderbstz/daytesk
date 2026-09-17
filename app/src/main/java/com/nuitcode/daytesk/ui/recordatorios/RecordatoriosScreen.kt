package com.nuitcode.daytesk.ui.recordatorios

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.model.Recordatorio
import com.nuitcode.daytesk.model.Repeticion
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * List of upcoming reminders ordered by `fecha` ascending (soonest first) —
 * the ordering is owned by `RecordatorioDao`.
 *
 * Tap a row to edit it, long-press to delete it. When `POST_NOTIFICATIONS` is
 * denied on API 33+ an in-screen banner offers a jump to the system settings.
 */
@Composable
fun RecordatoriosScreen(
    data: DayteskData,
    onEdit: (Recordatorio) -> Unit = {},
    onDelete: (Recordatorio) -> Unit = {},
    onNew: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background)
            .semantics { testTag = "recordatorios_screen" },
    ) {
        RecordatoriosTopBar(onNew = onNew)
        NotificationPermissionBanner()
        if (data.recordatorios.isEmpty()) {
            RecordatoriosEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data.recordatorios, key = { it.id }) { recordatorio ->
                    RecordatorioRow(
                        recordatorio = recordatorio,
                        onClick = { onEdit(recordatorio) },
                        onLongPress = { onDelete(recordatorio) },
                    )
                }
            }
        }
    }
}

/**
 * Top bar whose ENTIRE row is the create touch target, not just the "+ Nuevo"
 * glyph. The modifier order mirrors the Profile row (`PerfilScreen.kt`):
 * `clickable` BEFORE `padding`, then a minimum interactive height, so the whole
 * row — title included — is tappable and its hit area is at least 48dp.
 */
@Composable
private fun RecordatoriosTopBar(onNew: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNew() }
            .padding(20.dp)
            .heightIn(min = 48.dp)
            .semantics { testTag = "recordatorios_new" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Recordatorios",
            style = DayteskTypography.display,
            color = DayteskColors.TextPrimary,
            modifier = Modifier.semantics { testTag = "recordatorios_title" },
        )
        Text(
            text = "+ Nuevo",
            style = DayteskTypography.label.copy(color = DayteskColors.Primary),
        )
    }
}

/**
 * Renders an "Abrir ajustes" banner only when the runtime notification
 * permission is denied on Android 13+. On older versions notifications are
 * always allowed, so nothing is shown.
 */
@Composable
private fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    if (granted) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(DayteskShapes.medium)
            .background(DayteskColors.WarningLight)
            .padding(14.dp)
            .semantics { testTag = "recordatorios_permission_banner" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Las notificaciones están desactivadas",
            style = DayteskTypography.bodySm,
            color = DayteskColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Abrir ajustes",
            style = DayteskTypography.label.copy(color = DayteskColors.Primary),
            modifier = Modifier.clickable {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordatorioRow(
    recordatorio: Recordatorio,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DayteskShapes.medium)
            .background(DayteskColors.Surface)
            .border(1.dp, DayteskColors.Border, DayteskShapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(14.dp)
            .semantics { testTag = "recordatorio_row_${recordatorio.id}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(DayteskColors.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = DayteskColors.Primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(DayteskSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recordatorio.texto,
                style = DayteskTypography.bodyMd.copy(fontWeight = FontWeight.Medium),
                color = DayteskColors.TextPrimary,
            )
            Text(
                text = formatFecha(recordatorio.fecha),
                style = DayteskTypography.caption,
                color = DayteskColors.TextDisabled,
            )
        }
        RepeticionBadge(recordatorio.repeticion)
    }
}

@Composable
private fun RepeticionBadge(repeticion: Repeticion) {
    if (repeticion == Repeticion.NINGUNA) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DayteskColors.PrimaryLight)
            .padding(horizontal = DayteskSpacing.md, vertical = 4.dp),
    ) {
        Text(
            text = repeticion.label(),
            style = DayteskTypography.caption.copy(color = DayteskColors.Primary),
        )
    }
}

@Composable
private fun RecordatoriosEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            tint = DayteskColors.TextDisabled,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tenés recordatorios próximos",
            style = DayteskTypography.bodyMd,
            color = DayteskColors.TextSecondary,
            modifier = Modifier.semantics { testTag = "recordatorios_empty" },
        )
    }
}

private fun formatFecha(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
