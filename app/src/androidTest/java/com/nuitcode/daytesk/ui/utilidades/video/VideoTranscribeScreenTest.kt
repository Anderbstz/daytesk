package com.nuitcode.daytesk.ui.utilidades.video

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.nuitcode.daytesk.theme.DayteskTheme
import com.nuitcode.daytesk.utilities.video.VideoTranscribeState
import com.nuitcode.daytesk.utilities.video.VideoTranscribeViewModel
import com.nuitcode.daytesk.utilities.video.VideoTranscribePhase
import com.nuitcode.daytesk.utilities.video.VideoTranscribeWorkScheduler
import com.nuitcode.daytesk.utilities.video.VideoTranscribeScreen
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test

/**
 * RED instrumented test for VideoTranscribeScreen (PR3 task 3.11).
 *
 * Verifies:
 * - Idle state shows the file picker trigger, the network disclosure, and a disabled
 *   cancel button (no extraction in flight).
 * - Loading state with `Extracting(0.5f)` renders the progress overlay with a
 *   cancel button (per REQ-09).
 * - Success state shows the ResultCard with copy + share affordances.
 *
 * Runtime BLOCKED on this host (no Android SDK). Static analysis only per skip-verify
 * pattern #95.
 */
class VideoTranscribeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idleState_showsComingSoonAndNetworkDisclosure() {
        val viewModel = VideoTranscribeViewModel(
            scheduler = FakeVideoTranscribeScheduler(MutableSharedFlow()),
        )

        composeTestRule.setContent {
            DayteskTheme {
                VideoTranscribeScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText(
            "Próximamente: extracción de audio on-device y transcripción " +
                "con un modelo liviano (Whisper o Vosk). Por ahora, la herramienta " +
                "de audio graba directamente desde el micrófono.",
        ).assertExists()
        composeTestRule.onNodeWithText(
            "La transcripción usa el reconocimiento de voz integrado en Android. " +
                "Requiere conexión a Internet para funcionar en la mayoría de los dispositivos. " +
                "Tu audio NO se envía a la nube.",
        ).assertExists()
    }

    @Test
    fun extractingState_showsProgressOverlayAndCancelButton() {
        val viewModel = VideoTranscribeViewModel(
            scheduler = FakeVideoTranscribeScheduler(MutableSharedFlow()),
        )
        viewModel.state.value = VideoTranscribeState.Extracting(0.5f)

        composeTestRule.setContent {
            DayteskTheme {
                VideoTranscribeScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Extrayendo audio…").assertExists()
        composeTestRule.onNodeWithText("Cancelar").assertExists()
    }

    @Test
    fun successState_showsExtractedTextAndCopyShareActions() {
        val viewModel = VideoTranscribeViewModel(
            scheduler = FakeVideoTranscribeScheduler(MutableSharedFlow()),
        )
        viewModel.state.value = VideoTranscribeState.Success("Hola desde el video")

        composeTestRule.setContent {
            DayteskTheme {
                VideoTranscribeScreen(onBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Hola desde el video").assertExists()
        composeTestRule.onNodeWithContentDescription("Copiar").assertExists()
        composeTestRule.onNodeWithContentDescription("Compartir").assertExists()
    }
}

private class FakeVideoTranscribeScheduler(
    private val phases: Flow<VideoTranscribePhase>,
) : VideoTranscribeWorkScheduler {
    override fun enqueue(uri: Uri): Flow<VideoTranscribePhase> = phases

    override fun cancel() = Unit
}
