package com.nuitcode.daytesk.utilities.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.mlkit.speech.recognition.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ADR-3: dedicated CoroutineWorker wrapping ML Kit Speech Recognition. Chained by PR3's
 * VideoTranscribeWorker to transcribe the extracted WAV. The worker's input is the audio URI;
 * the output data carries the recognized text or an error message.
 *
 * Runtime execution is BLOCKED on this host per skip-verify pattern #95. Static analysis
 * only. The actual `recognize()` helper bridges the ML Kit callback-based API into a
 * suspend function and is not exercised by JVM tests.
 */
class AudioTranscribeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(INPUT_URI)?.takeIf { it.isNotBlank() }
            ?: return invalidInputResult()

        return try {
            val text = recognize(applicationContext, Uri.parse(uri))
            Result.success(successData(text))
        } catch (exception: Exception) {
            Result.failure(failureData(exception.message ?: "No se pudo transcribir el audio."))
        }
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

        /**
         * Bridges the ML Kit Speech Recognition callback API into a suspend function.
         * The recognizer is created on-device with the es-AR locale and the audio URI
         * is passed via the RecognizerIntent. Returns the first hypothesis on
         * `onResults`; throws on `onError`.
         */
        internal suspend fun recognize(context: Context, uri: Uri): String =
            suspendCancellableCoroutine { cont ->
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(Locale("es", "AR"))
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (cont.isActive) cont.resume(text)
                    }

                    override fun onError(error: Int) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("Error de reconocimiento: $error"),
                            )
                        }
                    }

                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO, uri)
                }
                recognizer.startListening(intent)
                cont.invokeOnCancellation { recognizer.cancel() }
            }
    }
}
