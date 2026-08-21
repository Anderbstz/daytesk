package com.nuitcode.daytesk.utilities.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDisclosureTest {

    @Test
    fun networkDisclosureText_mentionsConnectionRequirement() {
        assertTrue(
            "Disclosure must warn that the tool needs a network connection.",
            NETWORK_DISCLOSURE_TEXT.contains("Requiere conexión"),
        )
    }

    @Test
    fun networkDisclosureText_disclaimsAudioUpload() {
        assertTrue(
            "Disclosure must state that audio is NOT uploaded to the cloud.",
            NETWORK_DISCLOSURE_TEXT.contains("Tu audio NO se envía a la nube"),
        )
    }

    @Test
    fun networkDisclosureHelper_recognisesFullDisclosureText() {
        assertTrue(
            networkDisclosureContainsRequiredPhrase(NETWORK_DISCLOSURE_TEXT),
        )
    }

    @Test
    fun networkDisclosureHelper_rejectsMissingConnectionHint() {
        assertTrue(
            !networkDisclosureContainsRequiredPhrase(
                "Esta herramienta funciona sin conexión y tu audio se envía a la nube.",
            ),
        )
    }
}
