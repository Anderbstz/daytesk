package com.nuitcode.daytesk.utilities.video

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

/**
 * State machine for the VideoTranscribe screen.
 *
 * Idle / Queued / Extracting / Transcribing / Success / Error / Cancelled.
 * The `Extracting` state carries a determinate progress value in 0f..1f
 * (REQ-09) so the UI can render a LinearProgressIndicator bound to the
 * WorkManager `setProgress` callbacks emitted by VideoTranscribeWorker.
 */
sealed interface VideoTranscribeState {
    data object Idle : VideoTranscribeState
    data object Queued : VideoTranscribeState
    data class Extracting(val progress: Float) : VideoTranscribeState
    data object DownloadingModel : VideoTranscribeState
    data object Transcribing : VideoTranscribeState
    data class Success(val text: String) : VideoTranscribeState
    data class Error(val message: String) : VideoTranscribeState
    data object Cancelled : VideoTranscribeState
}

/**
 * Internal phases emitted by the worker to the ViewModel via a callbackFlow.
 * Decoupled from `VideoTranscribeState` so the scheduler can be tested with
 * plain flows (no WorkManager dependency).
 */
sealed interface VideoTranscribePhase {
    data object Queued : VideoTranscribePhase
    data class Extracting(val progress: Float) : VideoTranscribePhase
    data object DownloadingModel : VideoTranscribePhase
    data object Transcribing : VideoTranscribePhase
    data class Completed(val text: String) : VideoTranscribePhase
    data class Failed(val message: String) : VideoTranscribePhase
}

/**
 * Abstraction over the WorkManager enqueue + cancel surface so the ViewModel
 * can be exercised in JVM tests with a fake scheduler.
 */
interface VideoTranscribeWorkScheduler {
    fun enqueue(uri: Uri): Flow<VideoTranscribePhase>
    fun cancel()
}

class VideoTranscribeViewModel(
    private val scheduler: VideoTranscribeWorkScheduler,
    private val coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    constructor(context: Context) : this(
        InProcessVideoTranscribeScheduler(context.applicationContext),
    )

    val state = MutableStateFlow<VideoTranscribeState>(VideoTranscribeState.Idle)
    private var extractionJob: Job? = null

    fun enqueue(uri: Uri) {
        extractionJob?.cancel()
        extractionJob = (coroutineScope ?: viewModelScope).launch {
            state.value = VideoTranscribeState.Queued
            scheduler.enqueue(uri).collectLatest { phase ->
                state.value = when (phase) {
                    VideoTranscribePhase.Queued -> VideoTranscribeState.Queued
                    is VideoTranscribePhase.Extracting -> VideoTranscribeState.Extracting(
                        phase.progress.coerceIn(0f, 1f),
                    )
                    VideoTranscribePhase.DownloadingModel -> VideoTranscribeState.DownloadingModel
                    VideoTranscribePhase.Transcribing -> VideoTranscribeState.Transcribing
                    is VideoTranscribePhase.Completed -> VideoTranscribeState.Success(phase.text)
                    is VideoTranscribePhase.Failed -> VideoTranscribeState.Error(phase.message)
                }
            }
        }
    }

    fun cancel() {
        extractionJob?.cancel()
        scheduler.cancel()
        state.value = VideoTranscribeState.Cancelled
    }
}

/**
 * Runs extraction and recognition in the app process while the screen is open.
 * Background workers lose the temporary read grant that the file picker hands
 * out, so the whole pipeline stays in the foreground instead.
 */
private class InProcessVideoTranscribeScheduler(
    private val context: Context,
) : VideoTranscribeWorkScheduler {

    override fun enqueue(uri: Uri): Flow<VideoTranscribePhase> = flow {
        emit(VideoTranscribePhase.Extracting(0.15f))
        val wav = try {
            VoskTranscriber.extractWav(context, uri, MediaKind.VIDEO)
        } catch (failure: Throwable) {
            emit(VideoTranscribePhase.Failed(failure.userMessage("No se pudo extraer el audio del video.")))
            return@flow
        }
        emit(VideoTranscribePhase.Extracting(1f))
        if (!VoskTranscriber.isModelReady(context)) {
            emit(VideoTranscribePhase.DownloadingModel)
        }
        emit(VideoTranscribePhase.Transcribing)
        try {
            emit(VideoTranscribePhase.Completed(VoskTranscriber.transcribeWav(context, wav)))
        } catch (failure: Throwable) {
            emit(VideoTranscribePhase.Failed(failure.userMessage("No se pudo transcribir el video.")))
        } finally {
            wav.delete()
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() = Unit
}

private fun Throwable.userMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback
