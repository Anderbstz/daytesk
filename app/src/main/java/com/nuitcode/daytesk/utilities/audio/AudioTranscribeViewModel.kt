package com.nuitcode.daytesk.utilities.audio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuitcode.daytesk.utilities.common.MediaKind
import com.nuitcode.daytesk.utilities.common.VoskTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        InProcessAudioTranscribeScheduler(context.applicationContext),
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

/**
 * Transcribes in the app process. The picker's read grant does not survive into
 * a background worker, so recognition happens while the screen is open.
 */
private class InProcessAudioTranscribeScheduler(
    private val context: Context,
) : AudioTranscribeWorkScheduler {

    override suspend fun preflight(): AudioTranscribePreflight = AudioTranscribePreflight.Ready

    override fun enqueue(uri: Uri): Flow<AudioTranscribeWorkResult> = flow {
        val result = try {
            AudioTranscribeWorkResult.Completed(
                VoskTranscriber.transcribe(context, uri, MediaKind.AUDIO),
            )
        } catch (failure: Throwable) {
            AudioTranscribeWorkResult.Failed(
                failure.message?.takeIf { it.isNotBlank() } ?: "No se pudo transcribir el audio.",
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)
}
