package com.nuitcode.daytesk.utilities.video

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
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
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
        WorkManagerVideoTranscribeScheduler(WorkManager.getInstance(context)),
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
 * Production scheduler that drives the ViewModel from WorkManager's
 * `getWorkInfoByIdFlow`. The worker's `setProgress(workDataOf("progress" to pct))`
 * calls map to `Extracting(progress)`; the chained AudioTranscribeWorker
 * progress maps to `Transcribing` until SUCCEEDED → Completed.
 *
 * The scheduler tracks the most recent enqueued request ID so `cancel()` can
 * synchronously cancel it on the WorkManager instance.
 */
private class WorkManagerVideoTranscribeScheduler(
    private val workManager: WorkManager,
) : VideoTranscribeWorkScheduler {
    @Volatile private var activeRequestId: java.util.UUID? = null

    override fun enqueue(uri: Uri): Flow<VideoTranscribePhase> = callbackFlow {
        val request = OneTimeWorkRequestBuilder<VideoTranscribeWorker>()
            .setInputData(VideoTranscribeWorker.inputData(uri.toString()))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        activeRequestId = request.id

        val observer = launch {
            workManager.getWorkInfoByIdFlow(request.id).collectLatest { info ->
                when (info?.state) {
                    WorkInfo.State.ENQUEUED -> trySend(VideoTranscribePhase.Queued)
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress
                            .getInt(VideoTranscribeWorker.PROGRESS_KEY, -1)
                        when {
                            progress in 0..50 ->
                                trySend(VideoTranscribePhase.Extracting(progress / 50f))
                            progress in 51..99 ->
                                trySend(VideoTranscribePhase.Transcribing)
                            else -> trySend(VideoTranscribePhase.Transcribing)
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        trySend(
                            VideoTranscribePhase.Completed(
                                info.outputData.getString(AudioTranscribeWorker.OUTPUT_TEXT).orEmpty(),
                            ),
                        )
                        close()
                    }
                    WorkInfo.State.FAILED -> {
                        trySend(
                            VideoTranscribePhase.Failed(
                                info.outputData.getString(VideoTranscribeWorker.OUTPUT_ERROR)
                                    ?: "No se pudo transcribir el video.",
                            ),
                        )
                        close()
                    }
                    WorkInfo.State.CANCELLED -> {
                        trySend(VideoTranscribePhase.Failed("Transcripción de video cancelada."))
                        close()
                    }
                    else -> Unit
                }
            }
        }
        workManager.enqueue(request)
        awaitClose {
            observer.cancel()
            // Defensive: cancel the request from the flow teardown path too.
            activeRequestId?.let { workManager.cancelWorkById(it) }
            activeRequestId = null
        }
    }

    override fun cancel() {
        activeRequestId?.let { workManager.cancelWorkById(it) }
        activeRequestId = null
    }
}
