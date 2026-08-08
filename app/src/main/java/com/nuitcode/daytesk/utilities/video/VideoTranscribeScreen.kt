package com.nuitcode.daytesk.utilities.video

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuitcode.daytesk.utilities.audio.NetworkDisclosure
import com.nuitcode.daytesk.utilities.common.ResultCard
import com.nuitcode.daytesk.utilities.common.TranscriptionProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTranscribeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: VideoTranscribeViewModel = viewModel {
        VideoTranscribeViewModel(context)
    }
    VideoTranscribeScreen(onBack = onBack, viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTranscribeScreen(
    onBack: () -> Unit,
    viewModel: VideoTranscribeViewModel,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video a texto/audio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NetworkDisclosure()
            Text("La transcripción de archivos de video llegará en una próxima versión.")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = "Próximamente: extracción de audio on-device y transcripción " +
                        "con un modelo liviano (Whisper o Vosk). Por ahora, la herramienta " +
                        "de audio graba directamente desde el micrófono.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            when (val currentState = state) {
                VideoTranscribeState.Idle,
                VideoTranscribeState.Queued,
                -> Unit

                is VideoTranscribeState.Extracting -> {
                    TranscriptionProgress(
                        visible = true,
                        label = "Extrayendo audio…",
                        progress = currentState.progress,
                        onCancel = viewModel::cancel,
                    )
                }

                VideoTranscribeState.Transcribing -> {
                    TranscriptionProgress(
                        visible = true,
                        label = "Transcribiendo audio…",
                        onCancel = viewModel::cancel,
                    )
                }

                is VideoTranscribeState.Success -> {
                    ResultCard(
                        text = currentState.text,
                        onCopy = { clipboard.setText(AnnotatedString(currentState.text)) },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, currentState.text)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir texto"))
                        },
                    )
                }

                is VideoTranscribeState.Error -> {
                    Text(
                        text = currentState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                VideoTranscribeState.Cancelled -> {
                    Text("Transcripción cancelada.")
                }
            }
        }
    }
}
