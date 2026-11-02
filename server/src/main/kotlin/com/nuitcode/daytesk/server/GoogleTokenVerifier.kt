package com.nuitcode.daytesk.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class GoogleIdentity(
    val subject: String,
    val email: String,
    val displayName: String,
)

/**
 * Verifies a Google ID token with Google's `tokeninfo` endpoint, which checks the
 * signature and expiry. The claims still have to be checked here: without the
 * audience check, a token issued to any other app would open a session in this one.
 */
object GoogleTokenVerifier {
    private const val TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token="
    private val VALID_ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun verify(idToken: String, clientId: String): GoogleIdentity? {
        val encoded = URLEncoder.encode(idToken, Charsets.UTF_8)
        val request = HttpRequest.newBuilder(URI("$TOKEN_INFO_URL$encoded"))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null
        return identityFromClaims(
            Json.parseToJsonElement(response.body()).jsonObject,
            clientId,
            System.currentTimeMillis() / 1000,
        )
    }

    internal fun identityFromClaims(
        claims: JsonObject,
        clientId: String,
        nowSeconds: Long,
    ): GoogleIdentity? {
        fun claim(name: String): String? = claims[name]?.jsonPrimitive?.content

        if (clientId.isBlank() || claim("aud") != clientId) return null
        if (claim("iss") !in VALID_ISSUERS) return null
        if (claim("email_verified") != "true") return null
        val expiresAt = claim("exp")?.toLongOrNull() ?: return null
        if (expiresAt <= nowSeconds) return null
        val subject = claim("sub")?.takeIf { it.isNotBlank() } ?: return null
        val email = claim("email")?.takeIf { it.isNotBlank() } ?: return null
        return GoogleIdentity(
            subject = subject,
            email = email.lowercase(),
            displayName = claim("name")?.takeIf { it.isNotBlank() } ?: email.substringBefore("@"),
        )
    }
}
