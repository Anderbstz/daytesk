package com.nuitcode.daytesk.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Consumes every pointer event that the decorated node's own children did not
 * consume, so a full-screen overlay painted on top of other content also
 * captures input in the gaps between its children.
 *
 * Without this, a `Column` with only a `background` is transparent to pointer
 * input: taps and drags land on whatever composable is drawn underneath it.
 *
 * Ordering is safe for the overlay's own controls: descendants are dispatched
 * before their ancestors on the main pass, so text fields, pickers, chips and
 * buttons react first; this handler only consumes what bubbles up unclaimed.
 */
fun Modifier.blockTouchPassthrough(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}
