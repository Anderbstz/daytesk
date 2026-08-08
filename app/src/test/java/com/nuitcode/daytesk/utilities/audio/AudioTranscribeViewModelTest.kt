package com.nuitcode.daytesk.utilities.audio

import android.net.Uri
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeWorkResult.Completed
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeWorkResult.Failed
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AudioTranscribeViewModelTest {

    @Test
    fun recognize_emitsLoadingThenSuccessWithTranscribedText() = runTest {
        val results = MutableSharedFlow<AudioTranscribeWorkResult>()
        val viewModel = AudioTranscribeViewModel(
            scheduler = FakeAudioTranscribeScheduler(
                preflight = AudioTranscribePreflight.Ready,
                results = results,
            ),
            coroutineScope = this,
        )

        viewModel.recognize(Uri.parse("content://audio/hello.m4a"))
        advanceUntilIdle()
        assertEquals(AudioTranscribeState.Loading, viewModel.state.value)

        results.emit(Completed("Hola desde el audio"))
        advanceUntilIdle()

        assertEquals(
            AudioTranscribeState.Success("Hola desde el audio"),
            viewModel.state.value,
        )
    }

    @Test
    fun recognize_emitsErrorWhenWorkerFails() = runTest {
        val results = MutableSharedFlow<AudioTranscribeWorkResult>()
        val viewModel = AudioTranscribeViewModel(
            scheduler = FakeAudioTranscribeScheduler(
                preflight = AudioTranscribePreflight.Ready,
                results = results,
            ),
            coroutineScope = this,
        )

        viewModel.recognize(Uri.parse("content://audio/broken.m4a"))
        advanceUntilIdle()
        results.emit(Failed("No se pudo transcribir el audio."))
        advanceUntilIdle()

        assertEquals(
            AudioTranscribeState.Error("No se pudo transcribir el audio."),
            viewModel.state.value,
        )
    }

    @Test
    fun recognize_emitsErrorWhenPreflightReportsUnavailable() = runTest {
        val viewModel = AudioTranscribeViewModel(
            scheduler = FakeAudioTranscribeScheduler(
                preflight = AudioTranscribePreflight.Unavailable(
                    "Reconocimiento de voz no disponible.",
                ),
                results = MutableSharedFlow(),
            ),
            coroutineScope = this,
        )

        viewModel.recognize(Uri.parse("content://audio/any.m4a"))
        advanceUntilIdle()

        assertEquals(
            AudioTranscribeState.Error("Reconocimiento de voz no disponible."),
            viewModel.state.value,
        )
    }

    @Test
    fun cancel_returnsToIdle() = runTest {
        val viewModel = AudioTranscribeViewModel(
            scheduler = FakeAudioTranscribeScheduler(
                preflight = AudioTranscribePreflight.Ready,
                results = MutableSharedFlow(),
            ),
            coroutineScope = this,
        )

        viewModel.recognize(Uri.parse("content://audio/x.m4a"))
        advanceUntilIdle()
        assertEquals(AudioTranscribeState.Loading, viewModel.state.value)

        viewModel.cancel()
        advanceUntilIdle()

        assertEquals(AudioTranscribeState.Idle, viewModel.state.value)
    }
}

private class FakeAudioTranscribeScheduler(
    private val preflight: AudioTranscribePreflight,
    private val results: Flow<AudioTranscribeWorkResult>,
) : AudioTranscribeWorkScheduler {
    override suspend fun preflight(): AudioTranscribePreflight = preflight
    override fun enqueue(uri: Uri): Flow<AudioTranscribeWorkResult> = results
}
