package com.nuitcode.daytesk.utilities.ocr

import androidx.test.core.app.ApplicationProvider
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageOcrWorkerTest {

    @Test
    fun successData_containsRecognizedText() {
        val data = ImageOcrWorker.successData("Hello from OCR")

        assertEquals("Hello from OCR", data.getString(ImageOcrWorker.OUTPUT_TEXT))
    }

    @Test
    fun invalidInput_returnsFailureResult() {
        val result = ImageOcrWorker.invalidInputResult()

        assertEquals(
            "Falta la imagen.",
            result.outputData.getString(ImageOcrWorker.OUTPUT_ERROR),
        )
    }

    @Test
    fun fakeRecognizer_canProduceWorkerText() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val recognized = ImageOcrWorker.recognizeWith { "${context.packageName}: extracted" }

        assertEquals("com.nuitcode.daytesk: extracted", recognized)
    }
}
