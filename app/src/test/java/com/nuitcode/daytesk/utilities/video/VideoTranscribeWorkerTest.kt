package com.nuitcode.daytesk.utilities.video

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RED tests for VideoTranscribeWorker (PR3 task 3.6).
 *
 * The worker is a CoroutineWorker driven by WorkManager; we cannot run WorkManager
 * itself under Robolectric, so these tests cover the static helpers on the companion
 * object that the worker delegates to. The end-to-end happy path (extract → transcribe)
 * is covered by the instrumented VideoTranscribeScreenTest and the manual visual gate.
 *
 * Runtime BLOCKED on this host (no Android SDK). Static analysis only per skip-verify
 * pattern #95.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoTranscribeWorkerTest {

    @Test
    fun invalidInputResult_isFailure_andContainsFriendlyMessage() {
        val result = VideoTranscribeWorker.invalidInputResult()

        assertTrue(
            "invalidInputResult must be a Failure",
            result is androidx.work.ListenableWorker.Result.Failure,
        )
        assertEquals(
            "Falta el video.",
            (result as androidx.work.ListenableWorker.Result.Failure)
                .outputData.getString(VideoTranscribeWorker.OUTPUT_ERROR),
        )
    }

    @Test
    fun failureData_containsErrorMessage() {
        val data = VideoTranscribeWorker.failureData("No se pudo extraer el audio.")

        assertEquals(
            "No se pudo extraer el audio.",
            data.getString(VideoTranscribeWorker.OUTPUT_ERROR),
        )
    }

    @Test
    fun successData_containsRecognizedText() {
        val data = VideoTranscribeWorker.successData("Hola desde el video")

        assertEquals(
            "Hola desde el video",
            data.getString(VideoTranscribeWorker.OUTPUT_TEXT),
        )
    }

    @Test
    fun cancelledResult_isFailure_andContainsCancellationMessage() {
        val result = VideoTranscribeWorker.cancelledResult()

        assertTrue(
            "cancelledResult must be a Failure",
            result is androidx.work.ListenableWorker.Result.Failure,
        )
        assertEquals(
            "Transcripción de video cancelada.",
            (result as androidx.work.ListenableWorker.Result.Failure)
                .outputData.getString(VideoTranscribeWorker.OUTPUT_ERROR),
        )
    }

    @Test
    fun inputData_serializesUri_andProgressKeyIsExposed() {
        val data = VideoTranscribeWorker.inputData(uri = "content://video/tiny.mp4")

        assertEquals(
            "content://video/tiny.mp4",
            data.getString(VideoTranscribeWorker.INPUT_URI),
        )
        assertEquals("progress", VideoTranscribeWorker.PROGRESS_KEY)
        assertEquals("text", VideoTranscribeWorker.OUTPUT_TEXT)
        assertEquals("error", VideoTranscribeWorker.OUTPUT_ERROR)
    }
}
