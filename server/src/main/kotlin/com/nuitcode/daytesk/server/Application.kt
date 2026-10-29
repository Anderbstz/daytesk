package com.nuitcode.daytesk.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
    AuthService.seedDefaultUser()
    val source = if (Env.usesNeon) "Neon" else "memoria (llená DATABASE_URL en Render o env/.env)"
    val hasUrl = !System.getenv("DATABASE_URL").isNullOrBlank()
    println("Daytesk API en http://127.0.0.1:${Env.port}  [$source]")
    println("getenv DATABASE_URL: ${if (hasUrl) "presente" else "ausente"}")
    println("Usuario default: ander / anderbstz@gmail.com")

    val limeKatze = if (
        Env.limekatzeApiUrl.isNotBlank() && Env.limekatzeServiceToken.isNotBlank()
    ) {
        LimeKatzeClient(
            baseUrl = Env.limekatzeApiUrl,
            serviceToken = Env.limekatzeServiceToken,
            discordId = Env.limekatzeDiscordId,
        )
    } else {
        null
    }
    println(
        "LimeKatze: " + when {
            limeKatze == null -> "no configurado"
            !limeKatze.isConfigured -> "configuracion incompleta (falta LIMEKATZE_DISCORD_ID)"
            else -> "configurado"
        },
    )

    embeddedServer(Netty, port = Env.port, host = "0.0.0.0") {
        monitor.subscribe(ApplicationStopped) { limeKatze?.close() }

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorBody(cause.message ?: "Error interno"),
                )
            }
        }
        routing {
            get("/") {
                call.respondText(
                    "Daytesk API está al aire. Esto no es una web: el login es en la app Android.\n" +
                        "Chequeo: GET /health",
                    ContentType.Text.Plain,
                )
            }
            get("/health") {
                call.respond(HealthBody(ok = true, database = if (Env.usesNeon) "neon" else "memory"))
            }
            post("/auth/login") {
                val body = call.receive<LoginRequest>()
                val user = AuthService.login(body.identifier(), body.password)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorBody("Usuario o contraseña incorrectos."))
                    return@post
                }
                call.respond(
                    LoginResponse(
                        token = AuthService.issueToken(user),
                        user = UserBody(
                            id = user.id,
                            email = user.email,
                            username = user.username,
                            displayName = user.displayName,
                        ),
                    ),
                )
            }
            post("/auth/register") {
                val body = call.receive<RegisterRequest>()
                val result = AuthService.register(
                    email = body.email,
                    username = body.username,
                    password = body.password,
                    displayName = body.displayName ?: body.username,
                )
                result.fold(
                    onSuccess = { user ->
                        call.respond(
                            LoginResponse(
                                token = AuthService.issueToken(user),
                                user = UserBody(
                                    id = user.id,
                                    email = user.email,
                                    username = user.username,
                                    displayName = user.displayName,
                                ),
                            ),
                        )
                    },
                    onFailure = { failure ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorBody(failure.message ?: "No se pudo crear la cuenta."),
                        )
                    },
                )
            }
            get("/auth/me") {
                val header = call.request.headers[HttpHeaders.Authorization].orEmpty()
                val token = header.removePrefix("Bearer ").trim()
                val userId = AuthService.userIdFromToken(token)
                val user = userId?.let { AuthService.findById(it) }
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorBody("Sesión inválida."))
                    return@get
                }
                call.respond(
                    UserBody(
                        id = user.id,
                        email = user.email,
                        username = user.username,
                        displayName = user.displayName,
                    ),
                )
            }
            get("/sync") {
                val userId = call.bearerUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorBody("Sesión inválida."))
                    return@get
                }
                val local = SyncRepository.load(userId)
                val client = limeKatze
                val merged = if (client != null && client.isConfigured) {
                    try {
                        val remote = client.listNotifications(userId)
                        local.copy(
                            recordatorios = mergeRemoteNotifications(
                                local = local.recordatorios,
                                remote = remote,
                                muted = SyncRepository.mutedKeys(userId),
                            ),
                        )
                    } catch (failure: Throwable) {
                        call.application.log.warn(
                            "LimeKatze feed failed; serving local data only",
                            failure,
                        )
                        local
                    }
                } else {
                    local
                }
                call.respond(merged)
            }
            put("/sync") {
                val userId = call.bearerUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorBody("Sesión inválida."))
                    return@put
                }
                val payload = call.receive<SyncPayload>()
                SyncRepository.replace(userId, payload)
                call.respond(payload)
            }
            patch("/limekatze/notifications/{key}") {
                val userId = call.bearerUserId()
                val key = call.parameters["key"]
                if (userId == null || key == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorBody("Sesión inválida."))
                    return@patch
                }
                val client = limeKatze
                if (client == null || !client.isConfigured) {
                    call.respond(HttpStatusCode.ServiceUnavailable, ErrorBody("LimeKatze no está configurado."))
                    return@patch
                }
                val body = call.receive<RemoteRescheduleBody>()
                try {
                    client.reschedule(userId, key, body.fecha)
                    call.respond(HttpStatusCode.NoContent)
                } catch (failure: Throwable) {
                    call.respond(HttpStatusCode.BadGateway, ErrorBody(failure.message ?: "No se pudo reprogramar."))
                }
            }
            delete("/limekatze/notifications/{key}") {
                val userId = call.bearerUserId()
                val key = call.parameters["key"]
                if (userId == null || key == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorBody("Sesión inválida."))
                    return@delete
                }
                val client = limeKatze
                if (client == null || !client.isConfigured) {
                    call.respond(HttpStatusCode.ServiceUnavailable, ErrorBody("LimeKatze no está configurado."))
                    return@delete
                }
                try {
                    client.delete(userId, key)
                    call.respond(HttpStatusCode.NoContent)
                } catch (failure: Throwable) {
                    call.respond(HttpStatusCode.BadGateway, ErrorBody(failure.message ?: "No se pudo eliminar."))
                }
            }
            post("/limekatze/notifications/{key}/mute") {
                val userId = call.bearerUserId()
                val key = call.parameters["key"]
                if (userId == null || key == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorBody("Sesión inválida."))
                    return@post
                }
                val body = call.receive<MuteBody>()
                SyncRepository.setMuted(userId, key, body.silenciado)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }.start(wait = true)
}

private fun io.ktor.server.application.ApplicationCall.bearerUserId(): Long? {
    val header = request.headers[HttpHeaders.Authorization].orEmpty()
    val token = header.removePrefix("Bearer ").trim()
    return AuthService.userIdFromToken(token)
}

@Serializable
data class RegisterRequest(
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val displayName: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String? = null,
    val username: String? = null,
    val identifier: String? = null,
    val password: String = "",
) {
    fun identifier(): String = identifier?.ifBlank { null }
        ?: email?.ifBlank { null }
        ?: username.orEmpty()
}

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserBody,
)

@Serializable
data class UserBody(
    val id: Long,
    val email: String,
    val username: String,
    val displayName: String,
)

@Serializable
data class ErrorBody(val error: String)

@Serializable
data class HealthBody(val ok: Boolean, val database: String)

@Serializable
data class RemoteRescheduleBody(val fecha: Long)

@Serializable
data class MuteBody(val silenciado: Boolean = true)

/**
 * Appends LimeKatze items as DayTesk reminders.
 *
 * `cloudKey` is namespaced so a remote key such as `task:42` can never collide
 * with a local one. Local rows win by key: an old pull is replaced by the fresh
 * remote copy instead of duplicating the alarm.
 */
private fun mergeRemoteNotifications(
    local: List<SyncRecordatorio>,
    remote: List<RemoteNotification>,
    muted: Set<String>,
): List<SyncRecordatorio> {
    val localKeys = local.map { it.cloudKey }.toSet()
    val mapped = remote.mapNotNull { item ->
        val at = runCatching { parseIsoMillis(item.at) }.getOrNull() ?: return@mapNotNull null
        val updated = runCatching { parseIsoMillis(item.updatedAt) }.getOrNull() ?: at
        SyncRecordatorio(
            cloudKey = "limekatze:${item.key}",
            texto = item.title,
            fecha = at,
            repeticion = "NINGUNA",
            fechaCreacion = updated,
            updatedAt = updated,
            origin = "LIMEKATZE",
            remoteKey = item.key,
            silenciado = item.key in muted,
        )
    }.filter { it.cloudKey !in localKeys }

    return local + mapped
}
