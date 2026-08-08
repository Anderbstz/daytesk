package com.nuitcode.daytesk.utilities.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val NETWORK_DISCLOSURE_TEXT =
    "La transcripción usa el reconocimiento de voz integrado en Android. " +
        "Requiere conexión a Internet para funcionar en la mayoría de los dispositivos. " +
        "Tu audio NO se envía a la nube."

internal fun networkDisclosureContainsRequiredPhrase(text: String): Boolean =
    text.contains("Requiere conexión") && text.contains("Tu audio NO se envía a la nube")

/**
 * REQ-06: A persistent banner that discloses the network requirement of the built-in
 * speech recognition service. Sits above the start button on audio and video tool
 * screens.
 */
@Composable
fun NetworkDisclosure(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Información de red",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = NETWORK_DISCLOSURE_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
