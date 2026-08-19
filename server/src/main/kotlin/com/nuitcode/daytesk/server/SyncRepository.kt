package com.nuitcode.daytesk.server

import kotlinx.serialization.Serializable
import java.sql.Types

object SyncRepository {
    fun migrate(connection: java.sql.Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS user_contextos (
                    user_id BIGINT NOT NULL,
                    cloud_key TEXT NOT NULL,
                    nombre TEXT NOT NULL,
                    color INT NOT NULL,
                    icon_id INT,
                    orden INT NOT NULL,
                    es_default INT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (user_id, cloud_key)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS user_tareas (
                    user_id BIGINT NOT NULL,
                    cloud_key TEXT NOT NULL,
                    titulo TEXT NOT NULL,
                    descripcion TEXT NOT NULL,
                    prioridad TEXT NOT NULL,
                    contexto_key TEXT NOT NULL,
                    estado TEXT NOT NULL,
                    fecha_creacion BIGINT NOT NULL,
                    fecha_vencimiento BIGINT,
                    fecha_completada BIGINT,
                    orden INT NOT NULL,
                    repeticion TEXT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (user_id, cloud_key)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS user_inbox (
                    user_id BIGINT NOT NULL,
                    cloud_key TEXT NOT NULL,
                    texto TEXT NOT NULL,
                    timestamp BIGINT NOT NULL,
                    procesado INT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (user_id, cloud_key)
                )
                """.trimIndent(),
            )
        }
    }

    fun load(userId: Long): SyncPayload {
        Database.connect().use { connection ->
            migrate(connection)
            val contextos = mutableListOf<SyncContexto>()
            connection.prepareStatement(
                "SELECT cloud_key, nombre, color, icon_id, orden, es_default, updated_at FROM user_contextos WHERE user_id = ?",
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        contextos += SyncContexto(
                            cloudKey = result.getString("cloud_key"),
                            nombre = result.getString("nombre"),
                            color = result.getInt("color"),
                            iconId = result.getInt("icon_id").takeIf { !result.wasNull() },
                            orden = result.getInt("orden"),
                            esDefault = result.getInt("es_default") == 1,
                            updatedAt = result.getLong("updated_at"),
                        )
                    }
                }
            }
            val tareas = mutableListOf<SyncTarea>()
            connection.prepareStatement(
                """
                SELECT cloud_key, titulo, descripcion, prioridad, contexto_key, estado,
                       fecha_creacion, fecha_vencimiento, fecha_completada, orden, repeticion, updated_at
                FROM user_tareas WHERE user_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        tareas += SyncTarea(
                            cloudKey = result.getString("cloud_key"),
                            titulo = result.getString("titulo"),
                            descripcion = result.getString("descripcion"),
                            prioridad = result.getString("prioridad"),
                            contextoKey = result.getString("contexto_key"),
                            estado = result.getString("estado"),
                            fechaCreacion = result.getLong("fecha_creacion"),
                            fechaVencimiento = result.getLong("fecha_vencimiento").takeIf { !result.wasNull() },
                            fechaCompletada = result.getLong("fecha_completada").takeIf { !result.wasNull() },
                            orden = result.getInt("orden"),
                            repeticion = result.getString("repeticion"),
                            updatedAt = result.getLong("updated_at"),
                        )
                    }
                }
            }
            val inbox = mutableListOf<SyncInbox>()
            connection.prepareStatement(
                "SELECT cloud_key, texto, timestamp, procesado, updated_at FROM user_inbox WHERE user_id = ?",
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        inbox += SyncInbox(
                            cloudKey = result.getString("cloud_key"),
                            texto = result.getString("texto"),
                            timestamp = result.getLong("timestamp"),
                            procesado = result.getInt("procesado") == 1,
                            updatedAt = result.getLong("updated_at"),
                        )
                    }
                }
            }
            return SyncPayload(contextos = contextos, tareas = tareas, inbox = inbox)
        }
    }

    fun replace(userId: Long, payload: SyncPayload) {
        Database.connect().use { connection ->
            migrate(connection)
            connection.autoCommit = false
            try {
                connection.prepareStatement("DELETE FROM user_tareas WHERE user_id = ?").use {
                    it.setLong(1, userId)
                    it.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM user_inbox WHERE user_id = ?").use {
                    it.setLong(1, userId)
                    it.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM user_contextos WHERE user_id = ?").use {
                    it.setLong(1, userId)
                    it.executeUpdate()
                }
                payload.contextos.forEach { item ->
                    connection.prepareStatement(
                        """
                        INSERT INTO user_contextos
                        (user_id, cloud_key, nombre, color, icon_id, orden, es_default, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setLong(1, userId)
                        statement.setString(2, item.cloudKey)
                        statement.setString(3, item.nombre)
                        statement.setInt(4, item.color)
                        if (item.iconId == null) statement.setNull(5, Types.INTEGER) else statement.setInt(5, item.iconId)
                        statement.setInt(6, item.orden)
                        statement.setInt(7, if (item.esDefault) 1 else 0)
                        statement.setLong(8, item.updatedAt)
                        statement.executeUpdate()
                    }
                }
                payload.tareas.forEach { item ->
                    connection.prepareStatement(
                        """
                        INSERT INTO user_tareas
                        (user_id, cloud_key, titulo, descripcion, prioridad, contexto_key, estado,
                         fecha_creacion, fecha_vencimiento, fecha_completada, orden, repeticion, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setLong(1, userId)
                        statement.setString(2, item.cloudKey)
                        statement.setString(3, item.titulo)
                        statement.setString(4, item.descripcion)
                        statement.setString(5, item.prioridad)
                        statement.setString(6, item.contextoKey)
                        statement.setString(7, item.estado)
                        statement.setLong(8, item.fechaCreacion)
                        if (item.fechaVencimiento == null) statement.setNull(9, Types.BIGINT) else statement.setLong(9, item.fechaVencimiento)
                        if (item.fechaCompletada == null) statement.setNull(10, Types.BIGINT) else statement.setLong(10, item.fechaCompletada)
                        statement.setInt(11, item.orden)
                        statement.setString(12, item.repeticion)
                        statement.setLong(13, item.updatedAt)
                        statement.executeUpdate()
                    }
                }
                payload.inbox.forEach { item ->
                    connection.prepareStatement(
                        """
                        INSERT INTO user_inbox (user_id, cloud_key, texto, timestamp, procesado, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setLong(1, userId)
                        statement.setString(2, item.cloudKey)
                        statement.setString(3, item.texto)
                        statement.setLong(4, item.timestamp)
                        statement.setInt(5, if (item.procesado) 1 else 0)
                        statement.setLong(6, item.updatedAt)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = true
            }
        }
    }
}

@Serializable
data class SyncPayload(
    val contextos: List<SyncContexto> = emptyList(),
    val tareas: List<SyncTarea> = emptyList(),
    val inbox: List<SyncInbox> = emptyList(),
)

@Serializable
data class SyncContexto(
    val cloudKey: String,
    val nombre: String,
    val color: Int,
    val iconId: Int? = null,
    val orden: Int,
    val esDefault: Boolean = false,
    val updatedAt: Long,
)

@Serializable
data class SyncTarea(
    val cloudKey: String,
    val titulo: String,
    val descripcion: String = "",
    val prioridad: String,
    val contextoKey: String,
    val estado: String,
    val fechaCreacion: Long,
    val fechaVencimiento: Long? = null,
    val fechaCompletada: Long? = null,
    val orden: Int = 0,
    val repeticion: String = "NINGUNA",
    val updatedAt: Long,
)

@Serializable
data class SyncInbox(
    val cloudKey: String,
    val texto: String,
    val timestamp: Long,
    val procesado: Boolean = false,
    val updatedAt: Long,
)
