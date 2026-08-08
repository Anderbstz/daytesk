package com.nuitcode.daytesk.utilities.audio

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface AudioTranscribeState {
    data object Idle : AudioTranscribeState
    data object Loading : AudioTranscribeState
    data class Success(val text: String) : AudioTranscribeState
    data class Error(val message: String) : AudioTranscribeState
}

sealed interface AudioTranscribeWorkResult {
    data class Completed(val text: String) : AudioTranscribeWorkResult
    data class Failed(val message: String) : AudioTranscribeWorkResult
}

sealed interface AudioTranscribePreflight {
    data object Ready : AudioTranscribePreflight
    data class Unavailable(val message: String) : AudioTranscribePreflight
}

interface AudioTranscribeWorkScheduler {
    suspend fun preflight(): AudioTranscribePreflight
    fun enqueue(uri: Uri): Flow<AudioTranscribeWorkResult>
}

class AudioTranscribeViewModel(
    private val scheduler: AudioTranscribeWorkScheduler,
    private val coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    constructor(context: Context) : this(
        WorkManagerAudioTranscribeScheduler(WorkManager.getInstance(context)),
    )

    val state = MutableStateFlow<AudioTranscribeState>(AudioTranscribeState.Idle)
    private var recognitionJob: Job? = null

    fun recognize(uri: Uri) {
        recognitionJob?.cancel()
        recognitionJob = (coroutineScope ?: viewModelScope).launch {
            state.value = AudioTranscribeState.Loading
            when (val preflight = scheduler.preflight()) {
                is AudioTranscribePreflight.Unavailable -> {
                    state.value = AudioTranscribeState.Error(preflight.message)
                }
                is AudioTranscribePreflight.Ready -> {
                    scheduler.enqueue(uri).collectLatest { result ->
                        state.value = when (result) {
                            is AudioTranscribeWorkResult.Completed ->
                                AudioTranscribeState.Success(result.text)
                            is AudioTranscribeWorkResult.Failed ->
                                AudioTranscribeState.Error(result.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Emits a pre-transcribed text as a Success state. Used by the screen when
     * the built-in [android.speech.SpeechRecognizer] finishes live mic
     * recognition — the system API hands the text back directly, so we don't
     * need to round-trip through the WorkManager-based file pipeline.
     */
    fun recognizeFromText(text: String) {
        recognitionJob?.cancel()
        state.value = AudioTranscribeState.Success(text)
    }

    fun cancel() {
        recognitionJob?.cancel()
        recognitionJob = null
        state.value = AudioTranscribeState.Idle
    }
}

private class WorkManagerAudioTranscribeScheduler(
    private val workManager: WorkManager,
) : AudioTranscribeWorkScheduler {

    override suspend fun preflight(): AudioTranscribePreflight {
        // The pre-flight probe used to construct an ML Kit on-device recognizer
        // and catch ERROR_CANNOT_CHECK_MODEL / ERROR_MISSING_DATA. With the
        // switch to the built-in android.speech.SpeechRecognizer the
        // availability check happens at the screen level where the recognizer
        // is constructed. The screen surfaces a clear "Reconocimiento de voz no
        // disponible" message via RecognitionListener#onError if the device has
        // no speech service installed.
        return AudioTranscribePreflight.Ready
    }

    override fun enqueue(uri: Uri): Flow<AudioTranscribeWorkResult> = callbackFlow {
        val request = OneTimeWorkRequestBuilder<AudioTranscribeWorker>()
            .setInputData(Data.Builder().putString(AudioTranscribeWorker.INPUT_URI, uri.toString()).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        val observer = launch {
            workManager.getWorkInfoByIdFlow(request.id).collectLatest { info ->
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        trySend(
                            AudioTranscribeWorkResult.Completed(
                                info.outputData.getString(AudioTranscribeWorker.OUTPUT_TEXT).orEmpty(),
                            ),
                        )
                        close()
                    }

                    WorkInfo.State.FAILED -> {
                        trySend(
                            AudioTranscribeWorkResult.Failed(
                                info.outputData.getString(AudioTranscribeWorker.OUTPUT_ERROR)
                                    ?: "No se pudo transcribir el audio.",
                            ),
                        )
                        close()
                    }

                    WorkInfo.State.CANCELLED -> {
                        trySend(AudioTranscribeWorkResult.Failed("Transcripción cancelada."))
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
