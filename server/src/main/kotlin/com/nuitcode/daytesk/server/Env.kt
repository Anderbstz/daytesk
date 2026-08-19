package com.nuitcode.daytesk.server

import java.io.File

object Env {
    private val values: Map<String, String> = load()

    val databaseUrl: String get() = values["DATABASE_URL"].orEmpty().trim()
    val jwtSecret: String get() = values["JWT_SECRET"]?.trim().orEmpty().ifBlank { "daytesk-dev-secret-change-me" }
    val port: Int get() = values["PORT"]?.toIntOrNull() ?: 8080
    val apiBaseUrl: String get() = values["API_BASE_URL"]?.trim().orEmpty().ifBlank { "http://10.0.2.2:8080" }
    val usesNeon: Boolean get() = databaseUrl.isNotBlank()

    private fun load(): Map<String, String> {
        val file = findEnvFile() ?: return emptyMap()
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val index = line.indexOf("=")
                line.substring(0, index).trim() to line.substring(index + 1).trim().trim('"', '\'')
            }
    }

    private fun findEnvFile(): File? {
        val names = listOf(".env")
        val start = File(System.getProperty("user.dir"))
        var dir: File? = start
        repeat(6) {
            val current = dir ?: return@repeat
            names.forEach { name ->
                val nested = File(File(current, "env"), name)
                if (nested.isFile) return nested
                val direct = File(current, name)
                if (direct.isFile && current.name == "env") return direct
            }
            dir = current.parentFile
        }
        return null
    }
}
