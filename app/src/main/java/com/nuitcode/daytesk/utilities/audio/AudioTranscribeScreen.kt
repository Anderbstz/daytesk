package com.nuitcode.daytesk.utilities.audio

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuitcode.daytesk.utilities.common.PermissionGate
import com.nuitcode.daytesk.utilities.common.PermissionRationale
import com.nuitcode.daytesk.utilities.common.ResultCard
import com.nuitcode.daytesk.utilities.common.TranscriptionProgress
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTranscribeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AudioTranscribeViewModel = viewModel {
        AudioTranscribeViewModel(context)
    }
    AudioTranscribeScreen(onBack = onBack, viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTranscribeScreen(
    onBack: () -> Unit,
    viewModel: AudioTranscribeViewModel,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isListening by remember { mutableStateOf(false) }
    var liveError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            isListening = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcribir audio") },
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
            Text("Elegí un archivo de audio (mp3, m4a, wav) o grabá con el micrófono.")

            val audioPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                uri?.let { viewModel.recognize(it) }
            }
            Button(
                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is AudioTranscribeState.Loading,
            ) {
                Text("Seleccionar audio")
            }

            PermissionGate(
                permissions = listOf("android.permission.RECORD_AUDIO"),
                rationale = PermissionRationale(
                    title = "Acceso al micrófono",
                    message = "Necesitamos acceso al micrófono para transcribir tu voz en vivo.",
                ),
            ) { requestPermission ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = {
                            requestPermission {
                                if (!isListening) {
                                    startLiveRecognition(
                                        context = context,
                                        onStart = { isListening = true; liveError = null },
                                        onResult = { text ->
                                            isListening = false
                                            viewModel.recognizeFromText(text)
                                        },
                                        onError = { message ->
                                            isListening = false
                                            liveError = message
                                        },
                                    )
                                } else {
                                    isListening = false
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = "Grabar audio",
                        )
                    }
                    Text(
                        text = "Grabar audio",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (isListening) {
                Text("Escuchando… tocá el micrófono para detener.")
            }

            liveError?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }

            when (val currentState = state) {
                AudioTranscribeState.Idle -> Unit
                AudioTranscribeState.Loading -> {
                    TranscriptionProgress(
                        visible = true,
                        label = "Transcribiendo audio…",
                        onCancel = viewModel::cancel,
                    )
                }
                is AudioTranscribeState.Success -> {
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
                is AudioTranscribeState.Error -> {
                    Text(text = currentState.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Kicks off the built-in [SpeechRecognizer] for live microphone transcription.
 * No file picker, no WorkManager — the system speech service streams partial +
 * final results via [RecognitionListener] and we surface them straight to the
 * caller. The recognizer is created on demand, lives for the duration of one
 * listen session, and is released when [onResult] / [onError] fires.
 */
private fun startLiveRecognition(
    context: android.content.Context,
    onStart: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onError(
            "Reconocimiento de voz no disponible. Verificá que esté habilitado en Ajustes.",
        )
        return
    }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    var settled = false
    val settle: (() -> Unit) -> Unit = { action ->
        if (!settled) {
            settled = true
            runCatching { recognizer.destroy() }
            action()
        }
    }
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(results: android.os.Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            settle { onResult(text) }
        }

        override fun onError(error: Int) {
            settle {
                onError("Error de reconocimiento: $error")
            }
        }

        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: android.os.Bundle?) {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    })
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "AR").toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    try {
        recognizer.startListening(intent)
        onStart()
    } catch (failure: Throwable) {
        settle { onError(failure.message ?: "No se pudo iniciar el reconocimiento.") }
    }
}
