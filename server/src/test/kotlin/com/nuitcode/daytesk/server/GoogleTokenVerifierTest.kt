package com.nuitcode.daytesk.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleTokenVerifierTest {
    private val clientId = "client.apps.googleusercontent.com"
    private val now = 1_000L

    private fun claims(vararg overrides: Pair<String, String>): JsonObject {
        val base = mapOf(
            "aud" to clientId,
            "iss" to "https://accounts.google.com",
            "email_verified" to "true",
            "exp" to "2000",
            "sub" to "google-1",
            "email" to "Ander@Example.com",
            "name" to "Ander",
        ) + overrides
        return JsonObject(base.mapValues { JsonPrimitive(it.value) })
    }

    private fun identity(claims: JsonObject) =
        GoogleTokenVerifier.identityFromClaims(claims, clientId, now)

    @Test
    fun validClaims_produceANormalizedIdentity() {
        val identity = identity(claims())

        assertNotNull(identity)
        assertEquals("google-1", identity!!.subject)
        assertEquals("ander@example.com", identity.email)
        assertEquals("Ander", identity.displayName)
    }

    @Test
    fun aTokenIssuedToAnotherAppIsRejected() {
        assertNull(identity(claims("aud" to "other.apps.googleusercontent.com")))
    }

    @Test
    fun anUnverifiedEmailIsRejected() {
        assertNull(identity(claims("email_verified" to "false")))
    }

    @Test
    fun anExpiredTokenIsRejected() {
        assertNull(identity(claims("exp" to "999")))
    }

    @Test
    fun aForeignIssuerIsRejected() {
        assertNull(identity(claims("iss" to "https://evil.example.com")))
    }

    @Test
    fun aBlankClientIdNeverMatches() {
        assertNull(GoogleTokenVerifier.identityFromClaims(claims("aud" to ""), "", now))
    }
}
