package com.nuitcode.daytesk.utilities.video

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeWorker
import com.nuitcode.daytesk.utilities.common.VoskTranscriber

class VideoTranscribeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(INPUT_URI)?.takeIf { it.isNotBlank() }
            ?: return invalidInputResult()
        setProgress(workDataOf(PROGRESS_KEY to 25))
        return try {
            setProgress(workDataOf(PROGRESS_KEY to 55))
            val text = VoskTranscriber.transcribe(
                applicationContext,
                Uri.parse(uri),
                com.nuitcode.daytesk.utilities.common.MediaKind.VIDEO,
            )
            setProgress(workDataOf(PROGRESS_KEY to 100))
            Result.success(successData(text))
        } catch (failure: Throwable) {
            Result.failure(
                failureData(failure.message ?: "No se pudo transcribir el video."),
            )
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
            Data.Builder().putString(AudioTranscribeWorker.OUTPUT_TEXT, text).build()

        fun failureData(message: String): Data =
            Data.Builder().putString(OUTPUT_ERROR, message).build()

        fun invalidInputResult(): ListenableWorker.Result =
            ListenableWorker.Result.failure(failureData("Falta el video."))

        fun cancelledResult(): ListenableWorker.Result =
            ListenableWorker.Result.failure(failureData("Transcripción de video cancelada."))
    }
}
