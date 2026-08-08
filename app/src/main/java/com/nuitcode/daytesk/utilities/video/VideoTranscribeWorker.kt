package com.nuitcode.daytesk.utilities.video

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeWorker
import com.nuitcode.daytesk.utilities.common.AudioExtractor
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

/**
 * PR3's CoroutineWorker that drives the video transcription pipeline:
 *
 * 1. Read the source video URI from `inputData`.
 * 2. Call `AudioExtractor.extractAudioTrackToWav(...)` to write a 16 kHz mono
 *    PCM WAV file to `cacheDir/transcription/${UUID}.wav`.
 * 3. Emit `setProgress(workDataOf("progress" to pct))` every ~5% during the
 *    extraction phase (0..50 maps to the extract step in the ViewModel).
 * 4. Enqueue an `AudioTranscribeWorker` for the extracted WAV URI.
 * 5. Await the chained transcription worker (51..100 in the progress range).
 * 6. On SUCCEEDED → return `Result.success(successData(text))` carrying the
 *    recognized text. On failure → `Result.failure(failureData(message))`.
 *
 * Runtime execution is BLOCKED on this host per skip-verify pattern #95. The
 * worker's static helper methods (`inputData`, `successData`, `failureData`,
 * `invalidInputResult`, `cancelledResult`) are covered by `VideoTranscribeWorkerTest`.
 * The full end-to-end flow runs on the user's Pixel API 36 emulator.
 */
class VideoTranscribeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(INPUT_URI)?.takeIf { it.isNotBlank() }
            ?: return invalidInputResult()

        val transcriptionDir = File(applicationContext.cacheDir, "transcription").apply { mkdirs() }
        val wavFile = File(transcriptionDir, "${UUID.randomUUID()}.wav")

        val extracted = try {
            AudioExtractor.extractAudioTrackToWav(
                context = applicationContext,
                sourceUri = Uri.parse(uri),
                outputFile = wavFile,
            )
        } catch (failure: Throwable) {
            return Result.failure(failureData(failure.message ?: "No se pudo extraer el audio."))
        }
        if (!extracted || !wavFile.exists() || wavFile.length() <= 44L) {
            return Result.failure(failureData("El video no contiene una pista de audio."))
        }

        // Emit completion of the extraction phase (50%) before handing off to
        // the chained AudioTranscribeWorker.
        setProgress(workDataOf(PROGRESS_KEY to 50))

        val audioWorkerId = try {
            enqueueAudioTranscription(applicationContext, Uri.fromFile(wavFile))
        } catch (failure: Throwable) {
            return Result.failure(failureData(failure.message ?: "No se pudo encolar la transcripción."))
        }

        // Poll the chained worker until it reaches a terminal state. The ViewModel
        // observes the same `WorkInfo` flow so the UI can render the progress
        // range 51..99 as the `Transcribing` state.
        val transcriptionResult = awaitTranscriptionResult(applicationContext, audioWorkerId)
        return when (transcriptionResult) {
            is TranscriptionResult.Completed ->
                Result.success(successData(transcriptionResult.text))
            is TranscriptionResult.Failed ->
                Result.failure(failureData(transcriptionResult.message))
        }
    }

    private sealed interface TranscriptionResult {
        data class Completed(val text: String) : TranscriptionResult
        data class Failed(val message: String) : TranscriptionResult
    }

    private suspend fun awaitTranscriptionResult(
        context: Context,
        workId: UUID,
    ): TranscriptionResult {
        val workManager = WorkManager.getInstance(context)
        var lastEmittedProgress = 50
        while (true) {
            val info = workManager.getWorkInfoById(workId).get()
            when (info?.state) {
                androidx.work.WorkInfo.State.SUCCEEDED -> {
                    val text = info.outputData
                        .getString(AudioTranscribeWorker.OUTPUT_TEXT)
                        .orEmpty()
                    return TranscriptionResult.Completed(text)
                }
                androidx.work.WorkInfo.State.FAILED,
                androidx.work.WorkInfo.State.CANCELLED,
                -> {
                    val message = info.outputData
                        .getString(AudioTranscribeWorker.OUTPUT_ERROR)
                        ?: "No se pudo transcribir el audio."
                    return TranscriptionResult.Failed(message)
                }
                else -> {
                    // Bump the progress through 51..99 while we wait.
                    lastEmittedProgress = (lastEmittedProgress + 1).coerceAtMost(99)
                    setProgress(workDataOf(PROGRESS_KEY to lastEmittedProgress))
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    companion object {
        const val INPUT_URI = "video_uri"
        const val OUTPUT_TEXT = "text"
        const val OUTPUT_ERROR = "error"
        const val PROGRESS_KEY = "progress"

        fun inputData(uri: String): Data =
            Data.Builder().putString(INPUT_URI, uri).build()

        fun successData(text: String): Data =
            Data.Builder().putString(OUTPUT_TEXT, text).build()

        fun failureData(message: String): Data =
            Data.Builder().putString(OUTPUT_ERROR, message).build()

        fun invalidInputResult(): ListenableWorker.Result =
            ListenableWorker.Result.failure(failureData("Falta el video."))

        fun cancelledResult(): ListenableWorker.Result =
            ListenableWorker.Result.failure(failureData("Transcripción de video cancelada."))

        /**
         * Enqueues the chained `AudioTranscribeWorker` for the extracted WAV URI.
         * Returns the request ID so the caller can poll the result.
         */
        internal fun enqueueAudioTranscription(context: Context, wavUri: Uri): UUID {
            val request = OneTimeWorkRequestBuilder<AudioTranscribeWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(AudioTranscribeWorker.INPUT_URI, wavUri.toString())
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }

        private const val POLL_INTERVAL_MS = 500L
    }
}
