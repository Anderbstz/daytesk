package com.nuitcode.daytesk.utilities.ocr

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class ImageOcrWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(INPUT_URI)?.takeIf { it.isNotBlank() }
            ?: return invalidInputResult()

        return try {
            val image = InputImage.fromFilePath(applicationContext, android.net.Uri.parse(uri))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val text = recognizer.process(image).await().text
                Result.success(successData(text))
            } finally {
                recognizer.close()
            }
        } catch (exception: Exception) {
            Result.failure(
                Data.Builder()
                    .putString(OUTPUT_ERROR, exception.message ?: "No se pudo leer la imagen.")
                    .build(),
            )
        }
    }

    companion object {
        const val INPUT_URI = "image_uri"
        const val OUTPUT_TEXT = "text"
        const val OUTPUT_ERROR = "error"

        fun successData(text: String): Data =
            Data.Builder().putString(OUTPUT_TEXT, text).build()

        fun invalidInputResult(): Result =
            Result.failure(Data.Builder().putString(OUTPUT_ERROR, "Falta la imagen.").build())

        internal suspend fun recognizeWith(provider: suspend () -> String): String = provider()
    }
}
