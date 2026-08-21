package com.nuitcode.daytesk.ui.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayteskDatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selected = datePickerState.selectedDateMillis
                onDismiss()
                if (selected != null) onDateSelected(selected)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayteskTimePickerDialog(
    utcDateMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val now = remember { Calendar.getInstance() }
    val timeState = rememberTimePickerState(
        initialHour = now.get(Calendar.HOUR_OF_DAY),
        initialMinute = now.get(Calendar.MINUTE),
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = DayteskShapes.small,
            color = DayteskColors.Surface,
        ) {
            Column(Modifier.padding(DayteskSpacing.lg)) {
                TimePicker(
                    state = timeState,
                    colors = dayteskTimePickerColors(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    TextButton(onClick = {
                        onConfirm(combineUtcDateWithLocalTime(utcDateMillis, timeState.hour, timeState.minute))
                    }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun dayteskTimePickerColors() = TimePickerDefaults.colors(
    clockDialColor = DayteskColors.PrimaryLight,
    selectorColor = DayteskColors.Primary,
    containerColor = DayteskColors.Surface,
    clockDialSelectedContentColor = Color.White,
    clockDialUnselectedContentColor = DayteskColors.TextPrimary,
    periodSelectorBorderColor = DayteskColors.Border,
    periodSelectorSelectedContainerColor = DayteskColors.Primary,
    periodSelectorUnselectedContainerColor = DayteskColors.PrimaryLight,
    periodSelectorSelectedContentColor = Color.White,
    periodSelectorUnselectedContentColor = DayteskColors.TextPrimary,
    timeSelectorSelectedContainerColor = DayteskColors.Primary,
    timeSelectorUnselectedContainerColor = DayteskColors.Background,
    timeSelectorSelectedContentColor = Color.White,
    timeSelectorUnselectedContentColor = DayteskColors.TextPrimary,
)

fun combineUtcDateWithLocalTime(utcDateMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcDateMillis
    }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
