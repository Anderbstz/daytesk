package com.nuitcode.daytesk.utilities.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptionProgressTest {

    @Test
    fun progressMode_withKnownProgress_isDeterminate() {
        assertEquals(
            TranscriptionProgressMode.DETERMINATE,
            transcriptionProgressMode(0.5f),
        )
    }

    @Test
    fun progressMode_withoutProgress_isIndeterminate() {
        assertEquals(
            TranscriptionProgressMode.INDETERMINATE,
            transcriptionProgressMode(null),
        )
    }
}
