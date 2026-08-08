package com.nuitcode.daytesk.ui.utilidades.audio

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.nuitcode.daytesk.theme.DayteskTheme
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeState
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeViewModel
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeWorkResult
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeWorkScheduler
import com.nuitcode.daytesk.utilities.audio.AudioTranscribePreflight
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeScreen
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test

class AudioTranscribeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idleState_showsFilePickerDisclosureAndMicButton() {
        val viewModel = AudioTranscribeViewModel(
            FakeAudioTranscribeScheduler(
                preflight = AudioTranscribePreflight.Ready,
                results = MutableSharedFlow(),
            ),
        )

        composeTestRule.setContent {
            DayteskTheme {
                AudioTranscribeScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Seleccionar audio").assertExists()
        composeTestRule.onNodeWithText("Grabar audio").assertExists()
        composeTestRule.onNodeWithText(
            "La transcripción usa el reconocimiento de voz en tu dispositivo. " +
                "Requiere conexión para descargar el modelo la primera vez y para mejores resultados. " +
                "Tu audio NO se envía a la nube.",
        ).assertExists()
    }

    @Test
    fun successState_showsExtractedTextAndShareActions() {
        val viewModel = AudioTranscribeViewModel(
            FakeAudioTranscribeScheduler(
                preflight = AudioTranscribePreflight.Ready,
                results = MutableSharedFlow(),
            ),
        )
        viewModel.state.value = AudioTranscribeState.Success("Hola desde el audio")

        composeTestRule.setContent {
            DayteskTheme {
                AudioTranscribeScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Hola desde el audio").assertExists()
        composeTestRule.onNodeWithContentDescription("Copiar").assertExists()
        composeTestRule.onNodeWithContentDescription("Compartir").assertExists()
    }

    @Test
    fun errorState_showsErrorMessage() {
        val viewModel = AudioTranscribeViewModel(
            FakeAudioTranscribeScheduler(
                preflight = AudioTranscribePreflight.Ready,
                results = MutableSharedFlow(),
            ),
        )
        viewModel.state.value = AudioTranscribeState.Error("Reconocimiento de voz no disponible.")

        composeTestRule.setContent {
            DayteskTheme {
                AudioTranscribeScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Reconocimiento de voz no disponible.").assertExists()
    }
}

private class FakeAudioTranscribeScheduler(
    private val preflight: AudioTranscribePreflight,
    private val results: Flow<AudioTranscribeWorkResult>,
) : AudioTranscribeWorkScheduler {
    override suspend fun preflight(): AudioTranscribePreflight = preflight
    override fun enqueue(uri: Uri): Flow<AudioTranscribeWorkResult> = results
}
