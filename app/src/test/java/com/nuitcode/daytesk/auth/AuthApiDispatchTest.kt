package com.nuitcode.daytesk.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression test for RESIL-006.
 *
 * `AuthApi` is built on blocking `HttpURLConnection`. Its callers are Compose
 * scopes on the main dispatcher (`Navigation.kt` `LaunchedEffect`,
 * `rememberCoroutineScope`), so an undispached call throws
 * `NetworkOnMainThreadException`, which the caller's `runCatching` swallowed —
 * sync silently never worked.
 *
 * Contract: every network entry point routes through [AuthApi.dispatchNetwork]
 * and that boundary never runs the blocking body on the caller's thread.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AuthApiDispatchTest {

    @Test
    fun dispatchNetwork_runsTheBlockingBodyOffTheCallerThread() = runBlocking {
        val callerThread = Thread.currentThread()

        val workerThread = AuthApi.dispatchNetwork { Thread.currentThread() }

        assertNotSame(
            "blocking network work must never run on the caller (main) thread",
            callerThread,
            workerThread,
        )
    }

    @Test
    fun dispatchNetwork_defaultsToTheIoDispatcher() {
        assertSame(
            "the network boundary must default to Dispatchers.IO",
            Dispatchers.IO,
            AuthApi.networkDispatcher,
        )
    }
}
