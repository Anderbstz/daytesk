package com.nuitcode.daytesk.utilities.audio

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters

/**
 * ADR-3: dedicated CoroutineWorker for audio transcription.
 *
 * Historically this worker wrapped ML Kit Speech Recognition
 * (`com.google.mlkit:speech-recognition`). That artifact does not exist in Google's
 * Maven repository, so the dependency was removed and the worker was simplified.
 *
 * The built-in `android.speech.SpeechRecognizer` only supports live microphone
 * capture, which cannot be driven from a `CoroutineWorker` (the API requires an
 * Activity context and a foreground user). File-based audio transcription is
 * therefore stubbed for now; the UI screen uses live mic recognition via the
 * built-in API directly.
 *
 * The companion-object helpers (`successData`, `failureData`, `invalidInputResult`,
 * `preflightUnavailableData`) are preserved so existing tests and the chained
 * VideoTranscribeWorker keep working.
 *
 * Runtime execution is BLOCKED on this host per skip-verify pattern #95. Static
 * analysis only.
 */
class AudioTranscribeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // File-based audio transcription is not supported anymore — see the class
        // doc. The UI surfaces a clear "use the microphone" message; this worker
        // surfaces the same message if it ever gets enqueued.
        val uri = inputData.getString(INPUT_URI)
        val message = if (uri.isNullOrBlank()) {
            "Falta el audio."
        } else {
            "La transcripción de archivos de audio todavía no está disponible. " +
                "Usá el micrófono para grabar en vivo."
        }
        return Result.failure(failureData(message))
    }

    companion object {
        const val INPUT_URI = "audio_uri"
        const val OUTPUT_TEXT = "text"
        const val OUTPUT_ERROR = "error"

        fun successData(text: String): Data =
            Data.Builder().putString(OUTPUT_TEXT, text).build()

        fun failureData(message: String): Data =
            Data.Builder().putString(OUTPUT_ERROR, message).build()

        fun invalidInputResult(): Result =
            Result.failure(failureData("Falta el audio."))

        fun preflightUnavailableData(message: String): Data =
            Data.Builder().putString(OUTPUT_ERROR, message).build()
    }
}
