package com.nuitcode.daytesk.utilities.ocr

import android.net.Uri
import com.nuitcode.daytesk.utilities.ocr.ImageOcrWorkResult.Completed
import com.nuitcode.daytesk.utilities.ocr.ImageOcrWorkResult.Failed
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ImageOcrViewModelTest {

    @Test
    fun recognize_emitsLoadingThenSuccessWithExtractedText() = runTest {
        val results = MutableSharedFlow<ImageOcrWorkResult>()
        val scheduler = FakeImageOcrScheduler(results)
        val viewModel = ImageOcrViewModel(scheduler, this)

        viewModel.recognize(Uri.parse("content://images/hello.png"))
        advanceUntilIdle()
        assertEquals(ImageOcrState.Loading, viewModel.state.value)

        results.emit(Completed("Hello from image"))
        advanceUntilIdle()

        assertEquals(ImageOcrState.Success("Hello from image"), viewModel.state.value)
    }

    @Test
    fun recognize_emitsErrorWhenWorkerFails() = runTest {
        val results = MutableSharedFlow<ImageOcrWorkResult>()
        val viewModel = ImageOcrViewModel(FakeImageOcrScheduler(results), this)

        viewModel.recognize(Uri.parse("content://images/broken.png"))
        advanceUntilIdle()
        results.emit(Failed("The image could not be read"))
        advanceUntilIdle()

        assertEquals(
            ImageOcrState.Error("The image could not be read"),
            viewModel.state.value,
        )
    }
}

private class FakeImageOcrScheduler(
    private val results: Flow<ImageOcrWorkResult>,
) : ImageOcrWorkScheduler {
    override fun enqueue(uri: Uri): Flow<ImageOcrWorkResult> = results
}
