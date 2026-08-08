package com.nuitcode.daytesk.utilities.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.getWorkInfoByIdFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose

sealed interface ImageOcrState {
    data object Idle : ImageOcrState
    data object Loading : ImageOcrState
    data class Success(val text: String) : ImageOcrState
    data class Error(val message: String) : ImageOcrState
}

sealed interface ImageOcrWorkResult {
    data class Completed(val text: String) : ImageOcrWorkResult
    data class Failed(val message: String) : ImageOcrWorkResult
}

fun interface ImageOcrWorkScheduler {
    fun enqueue(uri: Uri): Flow<ImageOcrWorkResult>
}

class ImageOcrViewModel(
    private val scheduler: ImageOcrWorkScheduler,
    private val coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    constructor(context: Context) : this(
        WorkManagerImageOcrScheduler(WorkManager.getInstance(context)),
    )

    val state = kotlinx.coroutines.flow.MutableStateFlow<ImageOcrState>(ImageOcrState.Idle)
    private var recognitionJob: Job? = null

    fun recognize(uri: Uri) {
        recognitionJob?.cancel()
        recognitionJob = (coroutineScope ?: viewModelScope).launch {
            state.value = ImageOcrState.Loading
            scheduler.enqueue(uri).collectLatest { result ->
                state.value = when (result) {
                    is ImageOcrWorkResult.Completed -> ImageOcrState.Success(result.text)
                    is ImageOcrWorkResult.Failed -> ImageOcrState.Error(result.message)
                }
            }
        }
    }

    fun cancel() {
        recognitionJob?.cancel()
        recognitionJob = null
        state.value = ImageOcrState.Idle
    }

    }
}

private class WorkManagerImageOcrScheduler(
    private val workManager: WorkManager,
) : ImageOcrWorkScheduler {
    override fun enqueue(uri: Uri): Flow<ImageOcrWorkResult> = callbackFlow {
        val request = OneTimeWorkRequestBuilder<ImageOcrWorker>()
            .setInputData(Data.Builder().putString(ImageOcrWorker.INPUT_URI, uri.toString()).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()

        val observer = launch {
            workManager.getWorkInfoByIdFlow(request.id).collectLatest { info ->
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        trySend(
                            ImageOcrWorkResult.Completed(
                                info.outputData.getString(ImageOcrWorker.OUTPUT_TEXT).orEmpty(),
                            ),
                        )
                        close()
                    }

                    WorkInfo.State.FAILED -> {
                        trySend(
                            ImageOcrWorkResult.Failed(
                                info.outputData.getString(ImageOcrWorker.OUTPUT_ERROR)
                                    ?: "No se pudo leer la imagen.",
                            ),
                        )
                        close()
                    }

                    WorkInfo.State.CANCELLED -> {
                        trySend(ImageOcrWorkResult.Failed("OCR cancelado."))
                        close()
                    }

                    else -> Unit
                }
            }
        }
        workManager.enqueue(request)
        awaitClose {
            observer.cancel()
            workManager.cancelWorkById(request.id)
        }
    }
}
