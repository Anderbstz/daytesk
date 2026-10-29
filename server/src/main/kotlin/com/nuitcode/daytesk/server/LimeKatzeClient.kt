package com.nuitcode.daytesk.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for the LimeKatze notifications API.
 *
 * The token is server-to-server: the Android app never sees it. Every call
 * carries the DayTesk user id so LimeKatze can scope the feed without a second
 * authentication layer.
 */
class LimeKatzeClient(
    private val baseUrl: String,
    private val serviceToken: String,
    private val discordId: String,
) : AutoCloseable {
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000 // Render free cold start
            connectTimeoutMillis = 10_000
        }
        install(HttpRequestRetry) {
            maxRetries = 1
            retryOnServerErrors(maxRetries = 1)
            retryOnExceptionIf { _, cause -> cause is IOException }
            delayMillis { 1_000L }
        }
    }

    // Linking is idempotent, but there is no need to repeat it on every call.
    private val linkedUsers = ConcurrentHashMap.newKeySet<Long>()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && serviceToken.isNotBlank() && discordId.isNotBlank()

    suspend fun listNotifications(externalUserId: Long): List<RemoteNotification> {
        ensureLinked(externalUserId)
        val response = http.get("$baseUrl/v1/notifications") {
            authorize(externalUserId)
        }
        return response.successBody<RemoteFeed>().items
    }

    suspend fun reschedule(externalUserId: Long, key: String, atMillis: Long) {
        ensureLinked(externalUserId)
        val response = http.patch("$baseUrl/v1/notifications/${key.encodeURLPathPart()}") {
            authorize(externalUserId)
            contentType(ContentType.Application.Json)
            setBody(RescheduleBody(at = Instant.ofEpochMilli(atMillis).toString()))
        }
        response.ensureSuccess()
    }

    suspend fun delete(externalUserId: Long, key: String) {
        ensureLinked(externalUserId)
        val response = http.delete("$baseUrl/v1/notifications/${key.encodeURLPathPart()}") {
            authorize(externalUserId)
        }
        response.ensureSuccess()
    }

    override fun close() {
        http.close()
    }

    private suspend fun ensureLinked(externalUserId: Long) {
        if (externalUserId in linkedUsers) return

        val response = http.post("$baseUrl/v1/links") {
            header(HttpHeaders.Authorization, "Bearer $serviceToken")
            contentType(ContentType.Application.Json)
            setBody(
                LinkBody(
                    channel = "daytesk",
                    externalId = externalUserId.toString(),
                    toChannel = "discord",
                    toExternalId = discordId,
                ),
            )
        }
        response.ensureSuccess()
        linkedUsers += externalUserId
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize(externalUserId: Long) {
        header(HttpHeaders.Authorization, "Bearer $serviceToken")
        header("X-User-External-Id", externalUserId.toString())
    }

    private suspend inline fun <reified T> HttpResponse.successBody(): T {
        if (!status.isSuccess()) {
            throw LimeKatzeException("LimeKatze returned ${status.value}: ${bodyAsText().take(300)}")
        }
        return body()
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (!status.isSuccess()) {
            throw LimeKatzeException("LimeKatze returned ${status.value}: ${bodyAsText().take(300)}")
        }
    }
}

class LimeKatzeException(message: String) : RuntimeException(message)

fun parseIsoMillis(value: String): Long = OffsetDateTime.parse(value).toInstant().toEpochMilli()

@Serializable
data class RemoteFeed(
    val items: List<RemoteNotification> = emptyList(),
)

@Serializable
data class RemoteNotification(
    val key: String,
    val kind: String = "",
    @SerialName("remote_id") val remoteId: Long = 0,
    val title: String = "",
    val at: String,
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
private data class RescheduleBody(val at: String)

@Serializable
private data class LinkBody(
    val channel: String,
    @SerialName("external_id") val externalId: String,
    @SerialName("to_channel") val toChannel: String,
    @SerialName("to_external_id") val toExternalId: String,
)
