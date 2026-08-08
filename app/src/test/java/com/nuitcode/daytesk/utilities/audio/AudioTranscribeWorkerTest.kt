package com.nuitcode.daytesk.utilities.audio

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioTranscribeWorkerTest {

    @Test
    fun successData_containsRecognizedText() {
        val data = AudioTranscribeWorker.successData("Hola desde el audio")

        assertEquals("Hola desde el audio", data.getString(AudioTranscribeWorker.OUTPUT_TEXT))
    }

    @Test
    fun failureData_containsErrorMessage() {
        val data = AudioTranscribeWorker.failureData("No se pudo transcribir el audio.")

        assertEquals(
            "No se pudo transcribir el audio.",
            data.getString(AudioTranscribeWorker.OUTPUT_ERROR),
        )
    }

    @Test
    fun invalidInput_returnsFailureResult() {
        val result = AudioTranscribeWorker.invalidInputResult()

        assertEquals(
            "Falta el audio.",
            result.outputData.getString(AudioTranscribeWorker.OUTPUT_ERROR),
        )
    }

    @Test
    fun preflightUnavailableData_containsFriendlyGuidance() {
        val data = AudioTranscribeWorker.preflightUnavailableData(
            "Reconocimiento de voz no disponible. Verificá que esté habilitado en Ajustes.",
        )

        assertEquals(
            "Reconocimiento de voz no disponible. Verificá que esté habilitado en Ajustes.",
            data.getString(AudioTranscribeWorker.OUTPUT_ERROR),
        )
    }
}
