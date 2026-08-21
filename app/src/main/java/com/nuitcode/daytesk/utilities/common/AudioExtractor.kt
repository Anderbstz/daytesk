package com.nuitcode.daytesk.utilities.common

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class MediaKind {
    AUDIO,
    VIDEO,
}

object AudioExtractor {

    private const val OUTPUT_SAMPLE_RATE_HZ = 16_000
    private const val WAV_HEADER_SIZE = 44
    private const val TIMEOUT_US = 50_000L
    private const val MAX_LOOPS = 400_000
    private const val MAX_SECONDS = 20
    private const val INFO_OUTPUT_BUFFERS_CHANGED = -3

    fun extractAudioTrackToWav(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        expectedKind: MediaKind? = null,
    ): Boolean = runCatching {
        extractOrThrow(context, sourceUri, outputFile, expectedKind)
    }.isSuccess

    /**
     * Writes a 16 kHz mono PCM WAV from the audio track of [sourceUri].
     * Throws [TranscriptionException] naming the stage that failed.
     */
    fun extractOrThrow(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        expectedKind: MediaKind? = null,
    ) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val mime = context.contentResolver.getType(sourceUri).orEmpty()
        if (expectedKind == MediaKind.AUDIO && mime.startsWith("video/")) {
            throw TranscriptionException(
                "Elegiste un video. Usá la herramienta \"Transcribir video\".",
            )
        }
        if (expectedKind == MediaKind.VIDEO && mime.startsWith("audio/")) {
            throw TranscriptionException(
                "Elegiste un audio. Usá la herramienta \"Transcribir audio\".",
            )
        }

        val copied = copyUriToCache(context, sourceUri, mime, expectedKind)
        try {
            if (writeIfPcmWav(copied, outputFile)) return
            try {
                decodeOrThrow(copied, outputFile)
            } catch (first: TranscriptionException) {
                val remuxed = remuxAudioTrack(copied) ?: throw first
                try {
                    decodeOrThrow(remuxed, outputFile)
                } finally {
                    remuxed.delete()
                }
            }
        } finally {
            copied.delete()
        }

        if (!outputFile.exists() || outputFile.length() <= WAV_HEADER_SIZE) {
            outputFile.delete()
            throw TranscriptionException("El archivo no tiene audio audible para transcribir.")
        }
    }

    private fun copyUriToCache(
        context: Context,
        uri: Uri,
        mime: String,
        expectedKind: MediaKind?,
    ): File {
        val dest = File(
            context.cacheDir,
            "transcription/source-${System.currentTimeMillis()}${extensionFor(mime, expectedKind, uri)}",
        )
        dest.parentFile?.mkdirs()
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw TranscriptionException("No se pudo abrir el archivo elegido.")
            stream.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        } catch (failure: TranscriptionException) {
            dest.delete()
            throw failure
        } catch (failure: Throwable) {
            dest.delete()
            throw TranscriptionException(
                "No se pudo leer el archivo elegido (${failure.javaClass.simpleName}).",
            )
        }
        if (dest.length() == 0L) {
            dest.delete()
            throw TranscriptionException("El archivo elegido está vacío.")
        }
        return dest
    }

    private fun decodeOrThrow(source: File, outputFile: File) {
        val extractor = MediaExtractor()
        val format: MediaFormat
        val codec: MediaCodec
        try {
            openExtractor(extractor, source)
            val trackIndex = findAudioTrackIndex(extractor)
            if (trackIndex < 0) {
                extractor.release()
                throw TranscriptionException("El archivo no tiene pista de audio.")
            }
            format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
                extractor.release()
                throw TranscriptionException("No se reconoce el formato de audio del archivo.")
            }
            codec = MediaCodec.createDecoderByType(mime)
        } catch (failure: TranscriptionException) {
            throw failure
        } catch (failure: Throwable) {
            extractor.release()
            throw TranscriptionException(
                "No se pudo abrir el archivo: formato no soportado (${failure.javaClass.simpleName}).",
            )
        }

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channelCount = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val pcmBytes = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var loops = 0

            while (!sawOutputEos && loops++ < MAX_LOOPS) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer == null) break
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                val maxPcmBytes = sampleRate.coerceAtLeast(1) * channelCount.coerceAtLeast(1) * 2 * MAX_SECONDS * 60
                if (pcmBytes.size() >= maxPcmBytes) {
                    sawOutputEos = true
                    break
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER, INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            pcmBytes.write(chunk)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }

            val mono16k = toMono16kPcm(
                pcmBytes.toByteArray(),
                sampleRate = sampleRate.coerceAtLeast(1),
                channelCount = channelCount.coerceAtLeast(1),
                pcmEncoding = pcmEncoding,
            )
            if (mono16k.isEmpty()) {
                throw TranscriptionException("No se pudo decodificar el audio del archivo.")
            }
            writeWavFile(outputFile, mono16k)
        } catch (failure: TranscriptionException) {
            throw failure
        } catch (failure: Throwable) {
            throw TranscriptionException(
                "Falló la decodificación del audio (${failure.javaClass.simpleName}).",
            )
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }
    }

    private fun openExtractor(extractor: MediaExtractor, source: File) {
        extractor.setDataSource(source.absolutePath)
    }

    private fun extensionFor(mime: String, expectedKind: MediaKind?, uri: Uri): String {
        val fromPath = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { ext -> ext.length in 2..5 && ext.all { it.isLetterOrDigit() } }
        if (fromPath != null) return ".$fromPath"
        val lower = mime.lowercase()
        return when {
            lower.contains("mp4") || lower.contains("mpeg4") -> if (expectedKind == MediaKind.AUDIO) ".m4a" else ".mp4"
            lower.contains("quicktime") || lower.contains("mov") -> ".mov"
            lower.contains("webm") -> ".webm"
            lower.contains("matroska") || lower.contains("mkv") -> ".mkv"
            lower.contains("3gpp") -> ".3gp"
            lower.contains("wav") -> ".wav"
            lower == "audio/mpeg" || lower.contains("mp3") -> ".mp3"
            lower.contains("aac") -> ".aac"
            lower.contains("ogg") -> ".ogg"
            expectedKind == MediaKind.VIDEO -> ".mp4"
            else -> ".m4a"
        }
    }

    private fun writeIfPcmWav(source: File, outputFile: File): Boolean {
        if (source.length() <= WAV_HEADER_SIZE) return false
        val header = ByteArray(WAV_HEADER_SIZE)
        RandomAccessFile(source, "r").use { raf ->
            raf.readFully(header)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val riff = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            val chunkSize = buffer.int
            val wave = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            val fmt = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            if (riff != "RIFF" || wave != "WAVE" || fmt != "fmt ") return false
            val fmtSize = buffer.int
            val audioFormat = buffer.short.toInt()
            val channels = buffer.short.toInt().coerceAtLeast(1)
            val sampleRate = buffer.int.coerceAtLeast(1)
            buffer.int // byte rate
            buffer.short // block align
            val bits = buffer.short.toInt()
            if (audioFormat != 1 || (bits != 16 && bits != 8) || fmtSize < 16) return false
            var dataOffset = 12 + 8 + fmtSize
            if (dataOffset + 8 > source.length()) return false
            raf.seek(dataOffset.toLong())
            val dataId = ByteArray(4)
            raf.readFully(dataId)
            if (String(dataId, Charsets.US_ASCII) != "data") return false
            val dataSizeBuf = ByteArray(4)
            raf.readFully(dataSizeBuf)
            val dataSize = ByteBuffer.wrap(dataSizeBuf).order(ByteOrder.LITTLE_ENDIAN).int
                .coerceAtMost((source.length() - raf.filePointer).toInt())
                .coerceAtLeast(0)
            val pcm = ByteArray(dataSize)
            raf.readFully(pcm)
            val encoding = if (bits == 8) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT
            val mono16k = toMono16kPcm(pcm, sampleRate, channels, encoding)
            if (mono16k.isEmpty()) return false
            writeWavFile(outputFile, mono16k)
            return true
        }
    }

    private fun remuxAudioTrack(source: File): File? {
        val extractor = MediaExtractor()
        return try {
            openExtractor(extractor, source)
            val trackIndex = findAudioTrackIndex(extractor)
            if (trackIndex < 0) return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val dest = File(source.parentFile, "${source.nameWithoutExtension}-audio.m4a")
            if (dest.exists()) dest.delete()
            val muxer = android.media.MediaMuxer(
                dest.absolutePath,
                android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            val muxerTrack = muxer.addTrack(format)
            muxer.start()
            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            if (dest.length() > 0L) dest else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun findAudioTrackIndex(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return index
        }
        return -1
    }

    private fun toMono16kPcm(
        raw: ByteArray,
        sampleRate: Int,
        channelCount: Int,
        pcmEncoding: Int,
    ): ByteArray {
        val mono = downmixToMono(raw, channelCount, pcmEncoding)
        val resampled = resample(mono, sampleRate, OUTPUT_SAMPLE_RATE_HZ)
        val out = ByteArray(resampled.size * 2)
        val buffer = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        resampled.forEach { buffer.putShort(it) }
        return out
    }

    private fun downmixToMono(raw: ByteArray, channelCount: Int, pcmEncoding: Int): ShortArray {
        val samples = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> floatToShorts(raw)
            AudioFormat.ENCODING_PCM_8BIT -> eightBitToShorts(raw)
            else -> le16ToShorts(raw)
        }
        if (channelCount <= 1) return samples
        val frames = samples.size / channelCount
        val mono = ShortArray(frames)
        var index = 0
        for (frame in 0 until frames) {
            var sum = 0
            repeat(channelCount) {
                sum += samples[index++]
            }
            mono[frame] = (sum / channelCount).toShort()
        }
        return mono
    }

    private fun le16ToShorts(raw: ByteArray): ShortArray {
        val count = raw.size / 2
        val out = ShortArray(count)
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) out[i] = buffer.short
        return out
    }

    private fun eightBitToShorts(raw: ByteArray): ShortArray =
        ShortArray(raw.size) { index ->
            (((raw[index].toInt() and 0xFF) - 128) shl 8).toShort()
        }

    private fun floatToShorts(raw: ByteArray): ShortArray {
        val count = raw.size / 4
        val out = ShortArray(count)
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            val value = buffer.float.coerceIn(-1f, 1f)
            out[i] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun resample(samples: ShortArray, sourceRate: Int, targetRate: Int): ShortArray {
        if (samples.isEmpty() || sourceRate == targetRate) return samples
        val outLength = ((samples.size.toLong() * targetRate) / sourceRate)
            .toInt()
            .coerceAtLeast(1)
        val out = ShortArray(outLength)
        val last = (samples.size - 1).coerceAtLeast(0)
        for (i in 0 until outLength) {
            val srcPos = if (outLength == 1) 0.0 else i.toDouble() * last / (outLength - 1)
            val index = srcPos.toInt().coerceIn(0, last)
            val next = (index + 1).coerceAtMost(last)
            val fraction = srcPos - index
            val a = samples[index].toInt()
            val b = samples[next].toInt()
            out[i] = (a + ((b - a) * fraction)).toInt().toShort()
        }
        return out
    }

    private fun writeWavFile(outputFile: File, pcm: ByteArray) {
        RandomAccessFile(outputFile, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(WAV_HEADER_SIZE))
            raf.write(pcm)
            val byteRate = OUTPUT_SAMPLE_RATE_HZ * 2
            val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(pcm.size + WAV_HEADER_SIZE - 8)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(1)
            header.putInt(OUTPUT_SAMPLE_RATE_HZ)
            header.putInt(byteRate)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(pcm.size)
            raf.seek(0)
            raf.write(header.array())
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default
}
