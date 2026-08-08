package com.nuitcode.daytesk.utilities.video

import android.net.Uri
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * RED tests for VideoTranscribeViewModel (PR3 tasks 3.4 + 3.12).
 *
 * Contract under test:
 * - `enqueue(uri)` transitions Idle → Queued → Extracting(progress) → Transcribing
 *   → Success(text) as the scheduler emits those phases.
 * - Cancelling an in-flight extraction returns the state to Cancelled and
 *   instructs the scheduler to cancel the underlying WorkRequest.
 * - The error path from the scheduler maps to a `VideoTranscribeState.Error(message)`.
 *
 * Runtime BLOCKED on this host (no Android SDK). Static analysis only per skip-verify
 * pattern #95.
 */
class VideoTranscribeViewModelTest {

    @Test
    fun enqueue_progressesThroughExtractingToTranscribingToSuccess() = runTest {
        val phases = MutableSharedFlow<VideoTranscribePhase>(replay = 0, extraBufferCapacity = 8)
        val viewModel = VideoTranscribeViewModel(
            scheduler = FakeVideoTranscribeScheduler(phases = phases),
            coroutineScope = this,
        )

        viewModel.enqueue(Uri.parse("content://video/tiny.mp4"))
        advanceUntilIdle()
        assertEquals(VideoTranscribeState.Queued, viewModel.state.value)

        phases.emit(VideoTranscribePhase.Extracting(0.2f))
        advanceUntilIdle()
        assertEquals(VideoTranscribeState.Extracting(0.2f), viewModel.state.value)

        phases.emit(VideoTranscribePhase.Extracting(0.5f))
        advanceUntilIdle()
        assertEquals(VideoTranscribeState.Extracting(0.5f), viewModel.state.value)

        phases.emit(VideoTranscribePhase.Transcribing)
        advanceUntilIdle()
        assertEquals(VideoTranscribeState.Transcribing, viewModel.state.value)

        phases.emit(VideoTranscribePhase.Completed("Hola desde el video"))
        advanceUntilIdle()
        assertEquals(
            VideoTranscribeState.Success("Hola desde el video"),
            viewModel.state.value,
        )
    }

    @Test
    fun enqueue_mapsSchedulerErrorToErrorState() = runTest {
        val phases = MutableSharedFlow<VideoTranscribePhase>(extraBufferCapacity = 4)
        val viewModel = VideoTranscribeViewModel(
            scheduler = FakeVideoTranscribeScheduler(phases = phases),
            coroutineScope = this,
        )

        viewModel.enqueue(Uri.parse("content://video/broken.mp4"))
        advanceUntilIdle()
        phases.emit(VideoTranscribePhase.Failed("No se pudo extraer el audio."))
        advanceUntilIdle()

        assertEquals(
            VideoTranscribeState.Error("No se pudo extraer el audio."),
            viewModel.state.value,
        )
    }

    @Test
    fun cancel_transitionsToCancelled_andCallsSchedulerCancel() = runTest {
        val phases = MutableSharedFlow<VideoTranscribePhase>(extraBufferCapacity = 4)
        val scheduler = FakeVideoTranscribeScheduler(phases = phases)
        val viewModel = VideoTranscribeViewModel(
            scheduler = scheduler,
            coroutineScope = this,
        )

        viewModel.enqueue(Uri.parse("content://video/cancel.mp4"))
        advanceUntilIdle()
        phases.emit(VideoTranscribePhase.Extracting(0.1f))
        advanceUntilIdle()
        assertEquals(VideoTranscribeState.Extracting(0.1f), viewModel.state.value)

        viewModel.cancel()
        advanceUntilIdle()

        assertEquals(VideoTranscribeState.Cancelled, viewModel.state.value)
        assertEquals("cancel() must be invoked on the scheduler", 1, scheduler.cancelCalls)
    }
}

private class FakeVideoTranscribeScheduler(
    private val phases: Flow<VideoTranscribePhase>,
) : VideoTranscribeWorkScheduler {
    var cancelCalls: Int = 0

    override fun enqueue(uri: Uri): Flow<VideoTranscribePhase> = phases

    override fun cancel() {
        cancelCalls += 1
    }
}
