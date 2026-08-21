package com.nuitcode.daytesk.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

data class AuthUser(
    val id: Long,
    val email: String,
    val username: String,
    val displayName: String,
)

object AuthService {
    private const val DEFAULT_EMAIL = "anderbstz@gmail.com"
    private const val DEFAULT_USERNAME = "ander"
    private const val DEFAULT_PASSWORD = "12345678"

    fun seedDefaultUser() {
        Database.connect().use { connection ->
            Database.migrate(connection)
            val exists = connection.prepareStatement(
                "SELECT id FROM users WHERE email = ? OR username = ?",
            ).use { statement ->
                statement.setString(1, DEFAULT_EMAIL)
                statement.setString(2, DEFAULT_USERNAME)
                statement.executeQuery().use { it.next() }
            }
            if (exists) return
            val hash = BCrypt.hashpw(DEFAULT_PASSWORD, BCrypt.gensalt())
            connection.prepareStatement(
                "INSERT INTO users (email, username, display_name, password_hash) VALUES (?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, DEFAULT_EMAIL)
                statement.setString(2, DEFAULT_USERNAME)
                statement.setString(3, DEFAULT_USERNAME)
                statement.setString(4, hash)
                statement.executeUpdate()
            }
        }
    }

    fun register(
        email: String,
        username: String,
        password: String,
        displayName: String,
    ): Result<AuthUser> {
        val cleanEmail = email.trim().lowercase()
        val cleanUser = username.trim().lowercase()
        val cleanName = displayName.trim().ifBlank { cleanUser }
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Ingresá un email válido."))
        }
        if (cleanUser.length < 3) {
            return Result.failure(IllegalArgumentException("El usuario debe tener al menos 3 caracteres."))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("La contraseña debe tener al menos 8 caracteres."))
        }
        if (findUser(cleanEmail) != null || findUser(cleanUser) != null) {
            return Result.failure(IllegalArgumentException("Ese email o usuario ya está registrado."))
        }
        val hash = BCrypt.hashpw(password, BCrypt.gensalt())
        Database.connect().use { connection ->
            Database.migrate(connection)
            connection.prepareStatement(
                "INSERT INTO users (email, username, display_name, password_hash) VALUES (?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, cleanEmail)
                statement.setString(2, cleanUser)
                statement.setString(3, cleanName)
                statement.setString(4, hash)
                statement.executeUpdate()
            }
        }
        val created = findUser(cleanEmail)
            ?: return Result.failure(IllegalStateException("No se pudo crear la cuenta."))
        return Result.success(created)
    }

    fun login(identifier: String, password: String): AuthUser? {
        val user = findUser(identifier.trim()) ?: return null
        val hash = Database.connect().use { connection ->
            connection.prepareStatement(
                "SELECT password_hash FROM users WHERE id = ?",
            ).use { statement ->
                statement.setLong(1, user.id)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString("password_hash") else null
                }
            }
        } ?: return null
        return if (BCrypt.checkpw(password, hash)) user else null
    }

    fun findById(id: Long): AuthUser? {
        Database.connect().use { connection ->
            connection.prepareStatement(
                "SELECT id, email, username, display_name FROM users WHERE id = ?",
            ).use { statement ->
                statement.setLong(1, id)
                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    return AuthUser(
                        id = result.getLong("id"),
                        email = result.getString("email"),
                        username = result.getString("username"),
                        displayName = result.getString("display_name"),
                    )
                }
            }
        }
    }

    fun issueToken(user: AuthUser): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withSubject(user.id.toString())
            .withClaim("email", user.email)
            .withClaim("name", user.displayName)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + 30L * 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(Env.jwtSecret))
    }

    fun userIdFromToken(token: String): Long? {
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(Env.jwtSecret)).build()
            verifier.verify(token).subject.toLongOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    private fun findUser(identifier: String): AuthUser? {
        Database.connect().use { connection ->
            connection.prepareStatement(
                """
                SELECT id, email, username, display_name
                FROM users
                WHERE lower(email) = lower(?) OR lower(username) = lower(?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, identifier)
                statement.setString(2, identifier)
                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    return AuthUser(
                        id = result.getLong("id"),
                        email = result.getString("email"),
                        username = result.getString("username"),
                        displayName = result.getString("display_name"),
                    )
                }
            }
        }
    }
}
