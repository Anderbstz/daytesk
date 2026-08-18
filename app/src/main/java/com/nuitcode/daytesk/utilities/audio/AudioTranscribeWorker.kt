package com.nuitcode.daytesk.utilities.audio

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.nuitcode.daytesk.utilities.common.VoskTranscriber

class AudioTranscribeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uriValue = inputData.getString(INPUT_URI)?.takeIf { it.isNotBlank() }
            ?: return invalidInputResult()
        return try {
            val text = VoskTranscriber.transcribe(
                applicationContext,
                Uri.parse(uriValue),
                com.nuitcode.daytesk.utilities.common.MediaKind.AUDIO,
            )
            Result.success(successData(text))
        } catch (failure: Throwable) {
            Result.failure(
                failureData(failure.message ?: "No se pudo transcribir el audio."),
            )
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
    }
}
