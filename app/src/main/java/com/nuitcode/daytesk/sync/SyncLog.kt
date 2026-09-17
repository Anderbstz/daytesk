package com.nuitcode.daytesk.sync

import android.util.Log

/**
 * Single structured log channel for sync outcomes.
 *
 * Failures used to be swallowed by `runCatching`, leaving nothing to inspect.
 * Everything sync-related now goes through this one tag, so a failing sync is
 * observable in logcat and in tests (`ShadowLog`).
 */
internal object SyncLog {
    const val TAG = "DayteskSync"

    fun failure(stage: String, error: Throwable) {
        Log.w(TAG, "sync $stage failed: ${error.message ?: error::class.java.simpleName}", error)
    }
}
