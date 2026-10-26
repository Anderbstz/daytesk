package com.nuitcode.daytesk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires 2 minutes after [AlarmVibrationService] starts and stops the ring.
 *
 * Kept as its own receiver (instead of reusing [SnoozeReceiver]) so the stop is
 * independent of every user-facing action and of the notifications toggle.
 */
class RingTimeoutReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RING_TIMEOUT = "com.nuitcode.daytesk.RING_TIMEOUT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RING_TIMEOUT) return
        AlarmVibrationService.stop(context)
    }
}
