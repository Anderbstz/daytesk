package com.nuitcode.daytesk.server

import java.io.File

object Env {
    val databaseUrl: String get() = read("DATABASE_URL")
    val jwtSecret: String get() = read("JWT_SECRET").ifBlank { "daytesk-dev-secret-change-me" }
    val port: Int get() = read("PORT").toIntOrNull() ?: 8080
    val apiBaseUrl: String get() = read("API_BASE_URL").ifBlank { "http://10.0.2.2:8080" }
    val usesNeon: Boolean get() = databaseUrl.isNotBlank()

    private fun read(name: String): String {
        val fromEnv = System.getenv(name)?.trim()?.trim('"', '\'')
        if (!fromEnv.isNullOrEmpty()) return fromEnv
        val ignoreCase = System.getenv()?.entries
            ?.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.trim()
            ?.trim('"', '\'')
        if (!ignoreCase.isNullOrEmpty()) return ignoreCase
        return loadEnvFile()[name].orEmpty()
    }

    private fun loadEnvFile(): Map<String, String> {
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
