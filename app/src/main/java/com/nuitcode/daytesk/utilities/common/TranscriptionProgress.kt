package com.nuitcode.daytesk.utilities.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class TranscriptionProgressMode {
    DETERMINATE,
    INDETERMINATE,
}

internal fun transcriptionProgressMode(progress: Float?): TranscriptionProgressMode =
    if (progress == null) TranscriptionProgressMode.INDETERMINATE else TranscriptionProgressMode.DETERMINATE

@Composable
fun TranscriptionProgress(
    visible: Boolean,
    label: String,
    progress: Float? = null,
    onCancel: () -> Unit,
) {
    if (!visible) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label)
        when (transcriptionProgressMode(progress)) {
            TranscriptionProgressMode.DETERMINATE -> {
                LinearProgressIndicator(
                    progress = { progress!!.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TranscriptionProgressMode.INDETERMINATE -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        TextButton(onClick = onCancel) {
            Text("Cancelar")
        }
    }
}
