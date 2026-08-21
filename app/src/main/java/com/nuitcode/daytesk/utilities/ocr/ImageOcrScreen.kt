package com.nuitcode.daytesk.utilities.ocr

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import com.nuitcode.daytesk.utilities.common.PermissionGate
import com.nuitcode.daytesk.utilities.common.PermissionRationale
import com.nuitcode.daytesk.utilities.common.ResultCard
import com.nuitcode.daytesk.utilities.common.TranscriptionProgress

@Composable
fun ImageOcrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val imageOcrViewModel: ImageOcrViewModel = viewModel {
        ImageOcrViewModel(context)
    }
    ImageOcrScreen(onBack = onBack, viewModel = imageOcrViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageOcrScreen(
    onBack: () -> Unit,
    viewModel: ImageOcrViewModel,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let(viewModel::recognize)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Convertir imagen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { paddingValues ->
        PermissionGate(
            permissions = listOf("android.permission.READ_MEDIA_IMAGES"),
            rationale = PermissionRationale(
                title = "Acceso a tus imágenes",
                message = "Necesitamos acceso a la imagen que elijas para extraer su texto.",
            ),
        ) { requestPermission ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Extraé texto de una imagen con reconocimiento en el dispositivo.")
                Button(
                    onClick = {
                        requestPermission { picker.launch("image/*") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state !is ImageOcrState.Loading,
                ) {
                    Text("Seleccionar imagen")
                }

                when (val currentState = state) {
                    ImageOcrState.Idle -> Unit
                    ImageOcrState.Loading -> {
                        TranscriptionProgress(
                            visible = true,
                            label = "Leyendo imagen…",
                            onCancel = viewModel::cancel,
                        )
                    }

                    is ImageOcrState.Success -> {
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

                    is ImageOcrState.Error -> {
                        Text(text = currentState.message)
                    }
                }
            }
        }
    }
}
