package com.nuitcode.daytesk.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.persistRecordatorio
import com.nuitcode.daytesk.model.Recordatorio
import com.nuitcode.daytesk.model.Repeticion
import com.nuitcode.daytesk.sync.CloudSync
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTheme
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.ui.modals.DayteskDatePickerDialog
import com.nuitcode.daytesk.ui.modals.DayteskTimePickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Dialog-themed capture entry point launched by the home-screen widget.
 *
 * A widget's `RemoteViews` cannot host an editable field or an inline popup, so
 * tapping the widget opens this Activity instead. It collects the same three
 * inputs as [com.nuitcode.daytesk.ui.recordatorios.RecordatorioModal] — text,
 * a mandatory date and a repetition — reusing the existing date/time picker
 * dialogs.
 *
 * Validation mirrors the modal: "Guardar" stays disabled until the text is not
 * blank AND a date is set. Without a date nothing is written. On save the row is
 * persisted and its notifications scheduled on [Dispatchers.IO], then the
 * Activity finishes back to the launcher; cancel/back finishes with no write.
 */
class RecordatorioCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DayteskTheme {
                RecordatorioCaptureScreen(
                    onCancel = { finish() },
                    onSave = { recordatorio -> persistAndFinish(recordatorio) },
                )
            }
        }
    }

    /**
     * Writes the recordatorio and schedules its alarms off the main thread, then
     * dismisses the dialog. [persistRecordatorio] schedules notifications itself,
     * so the capture path never touches [com.nuitcode.daytesk.notification.ReminderScheduler]
     * directly.
     *
     * Sync: the capture must enqueue a push, otherwise the row exists only
     * locally until the next app start — where a pull used to delete it
     * (RESIL-004). The pending-push flag is set *before* the write so the row is
     * protected even if the process dies before the push runs, and the push is
     * scheduled on [CloudSync]'s app-lifetime scope because this Activity
     * finishes immediately.
     */
    private fun persistAndFinish(recordatorio: Recordatorio) {
        val appContext = applicationContext
        val sessionStore = SessionStore(appContext)
        val sync = CloudSync(appContext, AppDatabase.getInstance(appContext), sessionStore)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(appContext).recordatorioDao()
                sessionStore.hasPendingPush = true
                persistRecordatorio(appContext, dao, recordatorio)
            }
            CloudSync.schedulePush(sync)
            finish()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordatorioCaptureScreen(
    onCancel: () -> Unit,
    onSave: (Recordatorio) -> Unit,
) {
    var texto by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf<Long?>(null) }
    var repeticion by remember { mutableStateOf(Repeticion.NINGUNA) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }
    val focusRequester = remember { FocusRequester() }

    val canSave = texto.isNotBlank() && fecha != null

    fun build(): Recordatorio = Recordatorio(
        id = 0L,
        texto = texto.trim(),
        fecha = fecha ?: 0L,
        repeticion = repeticion,
        cloudKey = UUID.randomUUID().toString(),
        fechaCreacion = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(DayteskSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = DayteskShapes.large,
            color = DayteskColors.Surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(DayteskSpacing.xl),
            ) {
                Text(
                    text = "Nuevo recordatorio",
                    style = DayteskTypography.h3,
                    color = DayteskColors.TextPrimary,
                    modifier = Modifier.padding(bottom = DayteskSpacing.md),
                )

                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { testTag = "capture_recordatorio_text_field" },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "¿Qué querés recordar?",
                            style = DayteskTypography.bodyMd,
                            color = DayteskColors.TextDisabled,
                        )
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(vertical = DayteskSpacing.lg)
                        .semantics { testTag = "capture_recordatorio_date_row" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Fecha",
                        tint = if (fecha == null) DayteskColors.TextDisabled else DayteskColors.Primary,
                        modifier = Modifier.size(DayteskSpacing.xl),
                    )
                    Spacer(modifier = Modifier.width(DayteskSpacing.sm))
                    Text(
                        text = fecha?.let { formatFecha(it) } ?: "Elegir fecha (obligatoria)",
                        style = DayteskTypography.bodySm,
                        color = if (fecha == null) DayteskColors.TextDisabled else DayteskColors.TextSecondary,
                    )
                }

                Text(
                    text = "Repetición",
                    style = DayteskTypography.caption.copy(fontWeight = FontWeight.Medium),
                    color = DayteskColors.TextSecondary,
                    modifier = Modifier.padding(bottom = DayteskSpacing.sm),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DayteskSpacing.md),
                ) {
                    Repeticion.entries.forEach { option ->
                        val isSelected = option == repeticion
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) DayteskColors.PrimaryLight else DayteskColors.Background,
                                )
                                .then(
                                    if (!isSelected) {
                                        Modifier.border(1.dp, DayteskColors.Border, RoundedCornerShape(50))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { repeticion = option }
                                .padding(horizontal = DayteskSpacing.lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.label(),
                                style = DayteskTypography.bodySm,
                                color = if (isSelected) DayteskColors.Primary else DayteskColors.TextSecondary,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = DayteskSpacing.lg)
                        .height(1.dp)
                        .background(DayteskColors.Divider),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Cancelar",
                        style = DayteskTypography.bodySm,
                        color = DayteskColors.TextSecondary,
                        modifier = Modifier
                            .clickable { onCancel() }
                            .padding(vertical = DayteskSpacing.md)
                            .semantics { testTag = "capture_recordatorio_cancel" },
                    )

                    Box(
                        modifier = Modifier
                            .clip(DayteskShapes.pill)
                            .background(if (canSave) DayteskColors.Primary else DayteskColors.PrimaryLight)
                            .clickable(enabled = canSave) {
                                if (texto.isNotBlank() && fecha != null) onSave(build())
                            }
                            .padding(horizontal = DayteskSpacing.xxxl, vertical = DayteskSpacing.md)
                            .semantics { testTag = "capture_recordatorio_save" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Guardar",
                            style = DayteskTypography.bodySm.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (showDatePicker) {
        DayteskDatePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateSelected = { selected ->
                pendingDateMillis = selected
                showTimePicker = true
            },
        )
    }
    if (showTimePicker && pendingDateMillis != null) {
        DayteskTimePickerDialog(
            utcDateMillis = pendingDateMillis!!,
            onDismiss = {
                showTimePicker = false
                pendingDateMillis = null
            },
            onConfirm = { millis ->
                fecha = millis
                showTimePicker = false
                pendingDateMillis = null
            },
        )
    }
}

private fun formatFecha(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
