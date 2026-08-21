package com.nuitcode.daytesk.auth

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AuthApi {
    /** Producción (Render). En local sin deploy, usá http://10.0.2.2:8080 */
    const val BASE_URL = "https://daytesk.onrender.com"

    data class LoginResult(
        val token: String,
        val displayName: String,
        val email: String,
    )

    data class SyncSnapshot(
        val contextos: List<SyncContextoDto>,
        val tareas: List<SyncTareaDto>,
        val inbox: List<SyncInboxDto>,
    ) {
        fun isEmpty(): Boolean = contextos.isEmpty() && tareas.isEmpty() && inbox.isEmpty()
    }

    data class SyncContextoDto(
        val cloudKey: String,
        val nombre: String,
        val color: Int,
        val iconId: Int?,
        val orden: Int,
        val esDefault: Boolean,
        val updatedAt: Long,
    )

    data class SyncTareaDto(
        val cloudKey: String,
        val titulo: String,
        val descripcion: String,
        val prioridad: String,
        val contextoKey: String,
        val estado: String,
        val fechaCreacion: Long,
        val fechaVencimiento: Long?,
        val fechaCompletada: Long?,
        val orden: Int,
        val repeticion: String,
        val updatedAt: Long,
    )

    data class SyncInboxDto(
        val cloudKey: String,
        val texto: String,
        val timestamp: Long,
        val procesado: Boolean,
        val updatedAt: Long,
    )

    fun login(identifier: String, password: String): Result<LoginResult> {
        val payload = JSONObject()
            .put("identifier", identifier.trim())
            .put("password", password)
        return authCall("/auth/login", payload)
    }

    fun register(
        email: String,
        username: String,
        password: String,
        displayName: String = username,
    ): Result<LoginResult> {
        val payload = JSONObject()
            .put("email", email.trim())
            .put("username", username.trim())
            .put("password", password)
            .put("displayName", displayName.trim().ifBlank { username.trim() })
        return authCall("/auth/register", payload)
    }

    fun pullSync(token: String): Result<SyncSnapshot> {
        return request("GET", "/sync", token).mapCatching { body ->
            parseSnapshot(JSONObject(body))
        }
    }

    fun pushSync(token: String, snapshot: SyncSnapshot): Result<Unit> {
        return request("PUT", "/sync", token, snapshot.toJson()).map { }
    }

    private fun authCall(path: String, payload: JSONObject): Result<LoginResult> {
        return request("POST", path, token = null, body = payload).mapCatching { body ->
            val json = JSONObject(body)
            val user = json.getJSONObject("user")
            LoginResult(
                token = json.getString("token"),
                displayName = user.optString("displayName", "ander"),
                email = user.optString("email", "anderbstz@gmail.com"),
            )
        }
    }

    private fun request(
        method: String,
        path: String,
        token: String?,
        body: JSONObject? = null,
    ): Result<String> {
        var lastError: Throwable? = null
        repeat(4) { attempt ->
            val attemptResult = runCatching { once(method, path, token, body) }
            val text = attemptResult.getOrNull()
            if (attemptResult.isSuccess && text != null && looksLikeJson(text)) {
                return Result.success(text)
            }
            lastError = attemptResult.exceptionOrNull() ?: IllegalStateException(
                "El servidor está despertando. Esperá unos segundos y volvé a entrar.",
            )
            val retryable = lastError is java.net.ConnectException ||
                lastError is java.net.SocketTimeoutException ||
                lastError?.message?.contains("despertando") == true ||
                lastError?.message?.contains("503") == true ||
                lastError?.message?.contains("502") == true ||
                lastError?.message?.contains("504") == true ||
                (attemptResult.isSuccess && text != null && !looksLikeJson(text))
            if (!retryable || attempt == 3) {
                return Result.failure(
                    lastError ?: IllegalStateException("No se pudo conectar al servidor."),
                )
            }
            Thread.sleep(8_000L * (attempt + 1))
        }
        return Result.failure(lastError ?: IllegalStateException("No se pudo conectar al servidor."))
    }

    private fun once(
        method: String,
        path: String,
        token: String?,
        body: JSONObject?,
    ): String {
        val connection = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 60_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        if (!looksLikeJson(text) || code == 502 || code == 503 || code == 504) {
            error("El servidor está despertando. Esperá unos segundos y volvé a entrar.")
        }
        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "El servidor respondió $code."
            error(message)
        }
        return text
    }

    private fun looksLikeJson(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun parseSnapshot(json: JSONObject): SyncSnapshot {
        val contextos = json.optJSONArray("contextos").orEmpty().mapObjects { item ->
            SyncContextoDto(
                cloudKey = item.getString("cloudKey"),
                nombre = item.getString("nombre"),
                color = item.getInt("color"),
                iconId = item.optNullableInt("iconId"),
                orden = item.getInt("orden"),
                esDefault = item.optBoolean("esDefault"),
                updatedAt = item.optLong("updatedAt"),
            )
        }
        val tareas = json.optJSONArray("tareas").orEmpty().mapObjects { item ->
            SyncTareaDto(
                cloudKey = item.getString("cloudKey"),
                titulo = item.getString("titulo"),
                descripcion = item.optString("descripcion"),
                prioridad = item.getString("prioridad"),
                contextoKey = item.getString("contextoKey"),
                estado = item.getString("estado"),
                fechaCreacion = item.getLong("fechaCreacion"),
                fechaVencimiento = item.optNullableLong("fechaVencimiento"),
                fechaCompletada = item.optNullableLong("fechaCompletada"),
                orden = item.optInt("orden"),
                repeticion = item.optString("repeticion", "NINGUNA"),
                updatedAt = item.optLong("updatedAt"),
            )
        }
        val inbox = json.optJSONArray("inbox").orEmpty().mapObjects { item ->
            SyncInboxDto(
                cloudKey = item.getString("cloudKey"),
                texto = item.getString("texto"),
                timestamp = item.getLong("timestamp"),
                procesado = item.optBoolean("procesado"),
                updatedAt = item.optLong("updatedAt"),
            )
        }
        return SyncSnapshot(contextos, tareas, inbox)
    }

    private fun SyncSnapshot.toJson(): JSONObject {
        val contextosJson = JSONArray()
        contextos.forEach { item ->
            contextosJson.put(
                JSONObject()
                    .put("cloudKey", item.cloudKey)
                    .put("nombre", item.nombre)
                    .put("color", item.color)
                    .put("iconId", item.iconId ?: JSONObject.NULL)
                    .put("orden", item.orden)
                    .put("esDefault", item.esDefault)
                    .put("updatedAt", item.updatedAt),
            )
        }
        val tareasJson = JSONArray()
        tareas.forEach { item ->
            tareasJson.put(
                JSONObject()
                    .put("cloudKey", item.cloudKey)
                    .put("titulo", item.titulo)
                    .put("descripcion", item.descripcion)
                    .put("prioridad", item.prioridad)
                    .put("contextoKey", item.contextoKey)
                    .put("estado", item.estado)
                    .put("fechaCreacion", item.fechaCreacion)
                    .put("fechaVencimiento", item.fechaVencimiento ?: JSONObject.NULL)
                    .put("fechaCompletada", item.fechaCompletada ?: JSONObject.NULL)
                    .put("orden", item.orden)
                    .put("repeticion", item.repeticion)
                    .put("updatedAt", item.updatedAt),
            )
        }
        val inboxJson = JSONArray()
        inbox.forEach { item ->
            inboxJson.put(
                JSONObject()
                    .put("cloudKey", item.cloudKey)
                    .put("texto", item.texto)
                    .put("timestamp", item.timestamp)
                    .put("procesado", item.procesado)
                    .put("updatedAt", item.updatedAt),
            )
        }
        return JSONObject()
            .put("contextos", contextosJson)
            .put("tareas", tareasJson)
            .put("inbox", inboxJson)
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        List(length()) { index -> transform(getJSONObject(index)) }

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)
}
