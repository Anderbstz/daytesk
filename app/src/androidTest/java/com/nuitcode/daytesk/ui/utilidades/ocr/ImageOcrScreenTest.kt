package com.nuitcode.daytesk.ui.utilidades.ocr

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nuitcode.daytesk.theme.DayteskTheme
import com.nuitcode.daytesk.utilities.ocr.ImageOcrState
import com.nuitcode.daytesk.utilities.ocr.ImageOcrViewModel
import com.nuitcode.daytesk.utilities.ocr.ImageOcrWorkResult
import com.nuitcode.daytesk.utilities.ocr.ImageOcrWorkScheduler
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test

class ImageOcrScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idleState_showsImagePickerAction() {
        val scheduler = MutableSharedFlow<ImageOcrWorkResult>()
        val viewModel = ImageOcrViewModel(FakeImageOcrScheduler(scheduler))

        composeTestRule.setContent {
            DayteskTheme {
                ImageOcrScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Seleccionar imagen").assertExists()
    }

    @Test
    fun successState_showsExtractedTextAndShareActions() {
        val viewModel = ImageOcrViewModel(FakeImageOcrScheduler(MutableSharedFlow()))
        viewModel.state.value = ImageOcrState.Success("Hello from image")

        composeTestRule.setContent {
            DayteskTheme {
                ImageOcrScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Hello from image").assertExists()
        composeTestRule.onNodeWithContentDescription("Copiar").assertExists()
        composeTestRule.onNodeWithContentDescription("Compartir").assertExists()
    }

    @Test
    fun errorState_showsErrorMessage() {
        val viewModel = ImageOcrViewModel(FakeImageOcrScheduler(MutableSharedFlow()))
        viewModel.state.value = ImageOcrState.Error("Image processing failed")

        composeTestRule.setContent {
            DayteskTheme {
                ImageOcrScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Image processing failed").assertExists()
    }
}

private class FakeImageOcrScheduler(
    private val results: Flow<ImageOcrWorkResult>,
) : ImageOcrWorkScheduler {
    override fun enqueue(uri: Uri): Flow<ImageOcrWorkResult> = results
}
