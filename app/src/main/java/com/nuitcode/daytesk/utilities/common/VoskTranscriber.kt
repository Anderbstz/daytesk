package com.nuitcode.daytesk.utilities.common

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object VoskTranscriber {
    private const val MODEL_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    private const val MODEL_DIR_NAME = "vosk-model-small-es-0.42"
    private const val SAMPLE_RATE = 16000.0f

    suspend fun transcribe(
        context: Context,
        sourceUri: Uri,
        expectedKind: MediaKind,
    ): String = withContext(Dispatchers.IO) {
        val wav = extractWav(context, sourceUri, expectedKind)
        try {
            transcribeWav(context, wav)
        } finally {
            wav.delete()
        }
    }

    /** Decodes [sourceUri] into a 16 kHz mono WAV in the cache directory. */
    suspend fun extractWav(
        context: Context,
        sourceUri: Uri,
        expectedKind: MediaKind,
    ): File = withContext(Dispatchers.IO) {
        val wav = File(context.cacheDir, "transcription/${System.currentTimeMillis()}.wav")
        AudioExtractor.extractOrThrow(context, sourceUri, wav, expectedKind)
        wav
    }

    /** Runs recognition over an already extracted WAV file. */
    suspend fun transcribeWav(context: Context, wav: File): String = withContext(Dispatchers.IO) {
        val modelDir = ensureModel(context)
        recognize(modelDir, wav)
    }

    fun isModelReady(context: Context): Boolean =
        File(File(context.filesDir, "vosk"), "$MODEL_DIR_NAME/am").exists()

    private fun ensureModel(context: Context): File {
        val root = File(context.filesDir, "vosk")
        val modelDir = File(root, MODEL_DIR_NAME)
        val amDir = File(modelDir, "am")
        if (amDir.exists()) return modelDir
        root.mkdirs()
        val zipFile = File(root, "$MODEL_DIR_NAME.zip")
        try {
            download(MODEL_URL, zipFile)
            unzip(zipFile, root)
        } catch (failure: TranscriptionException) {
            throw failure
        } catch (failure: Throwable) {
            throw TranscriptionException(
                "No se pudo descargar el modelo de voz. Revisá tu conexión e intentá de nuevo.",
            )
        } finally {
            zipFile.delete()
        }
        if (!amDir.exists()) {
            throw TranscriptionException("No se pudo preparar el modelo de voz.")
        }
        return modelDir
    }

    private fun download(url: String, dest: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 300_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw TranscriptionException(
                    "No se pudo descargar el modelo de voz (HTTP ${connection.responseCode}).",
                )
            }
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw TranscriptionException("El modelo de voz descargado es inválido.")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun recognize(modelDir: File, wav: File): String {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val model = try {
            Model(modelDir.absolutePath)
        } catch (failure: Throwable) {
            throw TranscriptionException("No se pudo cargar el modelo de voz.")
        }
        val recognizer = try {
            Recognizer(model, SAMPLE_RATE)
        } catch (failure: Throwable) {
            model.close()
            throw TranscriptionException("No se pudo iniciar el reconocedor de voz.")
        }
        return try {
            FileInputStream(wav).use { input ->
                input.skip(44)
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    recognizer.acceptWaveForm(buffer, read)
                }
            }
            JSONObject(recognizer.finalResult).optString("text").trim().ifBlank {
                throw TranscriptionException(
                    "No se reconoció voz en el archivo. Probá con un audio más claro o en español.",
                )
            }
        } catch (failure: TranscriptionException) {
            throw failure
        } catch (failure: Throwable) {
            throw TranscriptionException("Falló el reconocimiento de voz (${failure.javaClass.simpleName}).")
        } finally {
            recognizer.close()
            model.close()
        }
    }
}
