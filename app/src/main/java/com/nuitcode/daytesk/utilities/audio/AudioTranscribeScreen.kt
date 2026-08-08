package com.nuitcode.daytesk.utilities.audio

import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
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
import java.io.File
import java.util.UUID

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
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var pendingRecordingUri by remember { mutableStateOf<Uri?>(null) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let(viewModel::recognize)
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.runCatching { release() }
            mediaRecorder = null
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
            Text("Elegí un archivo de audio o grabá uno con el micrófono.")

            PermissionGate(
                permissions = listOf(
                    "android.permission.READ_MEDIA_AUDIO",
                    "android.permission.RECORD_AUDIO",
                ),
                rationale = PermissionRationale(
                    title = "Acceso a tu audio",
                    message = "Necesitamos acceso al audio que elijas o que grabes para transcribirlo.",
                ),
            ) { requestPermission ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            requestPermission { audioPicker.launch("audio/*") }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = state !is AudioTranscribeState.Loading && !isRecording,
                    ) {
                        Text("Seleccionar audio")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = {
                                requestPermission {
                                    if (!isRecording) {
                                        startRecording(
                                            context = context,
                                            onStart = { recorder, uri ->
                                                mediaRecorder = recorder
                                                pendingRecordingUri = uri
                                                isRecording = true
                                            },
                                        )
                                    } else {
                                        val uri = pendingRecordingUri
                                        mediaRecorder?.runCatching { stop() }
                                        mediaRecorder?.runCatching { release() }
                                        mediaRecorder = null
                                        isRecording = false
                                        if (uri != null) {
                                            viewModel.recognize(uri)
                                        }
                                        pendingRecordingUri = null
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = "Grabar audio",
                            )
                        }
                        Text(
                            text = "Grabar audio",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (isRecording) {
                    Text("Grabando… tocá el micrófono para detener.")
                }
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
                    Text(text = currentState.message)
                }
            }
        }
    }
}

private fun startRecording(
    context: android.content.Context,
    onStart: (MediaRecorder, Uri) -> Unit,
) {
    val outputDir = File(context.cacheDir, "audio").apply { mkdirs() }
    val output = File(outputDir, "${UUID.randomUUID()}.m4a")
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }
    recorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setOutputFile(output.absolutePath)
        prepare()
        start()
    }
    onStart(recorder, Uri.fromFile(output))
}
