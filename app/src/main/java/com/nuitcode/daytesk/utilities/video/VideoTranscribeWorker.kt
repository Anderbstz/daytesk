package com.nuitcode.daytesk.utilities.video

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * PR3's CoroutineWorker that drives the video transcription pipeline.
 *
 * Historically this worker:
 *  1. Read the source video URI from `inputData`.
 *  2. Called `AudioExtractor.extractAudioTrackToWav` to write a 16 kHz mono PCM
 *     WAV file to `cacheDir/transcription/${UUID}.wav`.
 *  3. Emitted progress via `setProgress(workDataOf("progress" to pct))`.
 *  4. Enqueued an `AudioTranscribeWorker` for the extracted WAV URI.
 *  5. Awaited the chained transcription worker and returned the recognized text.
 *
 * That pipeline depended on ML Kit Speech Recognition
 * (`com.google.mlkit:speech-recognition`) for step 5. That artifact does not
 * exist in Google's Maven repository, and the built-in
 * `android.speech.SpeechRecognizer` only supports live microphone capture — it
 * cannot transcribe an extracted WAV offline.
 *
 * For the first iteration video transcription via file picker is therefore
 * stubbed with a friendly failure. The UI surfaces the same message. A
 * follow-up can re-enable file-based transcription using a TFLite Whisper
 * model or Vosk — see the commit message.
 *
 * The companion-object helpers (`inputData`, `successData`, `failureData`,
 * `invalidInputResult`, `cancelledResult`) are preserved so existing tests and
 * downstream callers keep working.
 */
class VideoTranscribeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(INPUT_URI)?.takeIf { it.isNotBlank() }
            ?: return invalidInputResult()

        // Emit a 50% progress hint so any UI still bound to the worker sees a
        // consistent state transition before the failure surfaces.
        setProgress(workDataOf(PROGRESS_KEY to 50))
        // `uri` is intentionally referenced so the API stays stable for the
        // follow-up that re-enables the extract→transcribe pipeline.
        @Suppress("UNUSED_VARIABLE") val pendingUri = uri
        return Result.failure(
            failureData(
                "La transcripción de archivos de video todavía no está disponible. " +
                    "Próximamente: modelo on-device.",
            ),
        )
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
    }
}
