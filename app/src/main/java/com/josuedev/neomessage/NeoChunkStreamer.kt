package com.josuedev.neomessage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * ⚡ NEO CHUNK STREAMER
 * Motor de transmisión por bloques para archivos masivos (2 GB a 4 GB o más).
 * Lee y escribe en streaming con búferes controlados (1 MB) para evitar OutOfMemoryError.
 */
object NeoChunkStreamer {
    const val DEFAULT_CHUNK_SIZE = 1024 * 1024 // 1 MB por chunk

    data class ChunkInfo(
        val chunkIndex: Int,
        val totalChunks: Int,
        val offset: Long,
        val length: Int,
        val checksumSha256: String
    )

    data class FileMeta(
        val fileId: String,
        val fileName: String,
        val totalBytes: Long,
        val totalChunks: Int,
        val mimeType: String
    )

    fun getFileMeta(context: Context, uri: Uri, chunkSize: Int = DEFAULT_CHUNK_SIZE): FileMeta {
        var name = "archivo_${System.currentTimeMillis()}"
        var size = 0L
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"

        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = it.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = it.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}

        if (size <= 0L) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    size = pfd.statSize
                }
            } catch (_: Exception) {}
        }

        val totalChunks = if (size > 0L) {
            ((size + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
        } else 1

        val fileId = "neo_${name.filter { it.isLetterOrDigit() }}_${size}_${System.currentTimeMillis()}"
        return FileMeta(fileId, name, size, totalChunks, mime)
    }

    /**
     * Lee un chunk específico de un Uri sin cargar el archivo completo en memoria RAM.
     */
    fun readChunk(
        context: Context,
        uri: Uri,
        chunkIndex: Int,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): Pair<ByteArray, ChunkInfo>? {
        return try {
            val offset = chunkIndex.toLong() * chunkSize
            context.contentResolver.openInputStream(uri)?.use { stream ->
                var skipped = 0L
                while (skipped < offset) {
                    val justSkipped = stream.skip(offset - skipped)
                    if (justSkipped <= 0) break
                    skipped += justSkipped
                }

                val buffer = ByteArray(chunkSize)
                var bytesRead = 0
                while (bytesRead < chunkSize) {
                    val count = stream.read(buffer, bytesRead, chunkSize - bytesRead)
                    if (count == -1) break
                    bytesRead += count
                }

                if (bytesRead <= 0) return null

                val actualData = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)
                val sha256 = calculateSha256(actualData)
                val info = ChunkInfo(
                    chunkIndex = chunkIndex,
                    totalChunks = 0, // Se complementa con FileMeta
                    offset = offset,
                    length = bytesRead,
                    checksumSha256 = sha256
                )
                Pair(actualData, info)
            }
        } catch (e: Exception) {
            android.util.Log.e("NEO_CHUNK", "Error al leer chunk $chunkIndex: ${e.message}")
            null
        }
    }

    /**
     * Escribe un chunk en una posición exacta del archivo destino (Soporta descargas concurrentes y reanudación).
     */
    fun writeChunk(
        destinationFile: File,
        chunkIndex: Int,
        data: ByteArray,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): Boolean {
        return try {
            val offset = chunkIndex.toLong() * chunkSize
            val parent = destinationFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()

            RandomAccessFile(destinationFile, "rw").use { raf ->
                raf.seek(offset)
                raf.write(data)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("NEO_CHUNK", "Error al escribir chunk $chunkIndex: ${e.message}")
            false
        }
    }

    /**
     * Guarda el estado de chunks completados para reanudar si se pierde la conexión.
     */
    fun getCompletedChunks(trackerFile: File): MutableSet<Int> {
        val completed = mutableSetOf<Int>()
        if (trackerFile.exists()) {
            try {
                trackerFile.forEachLine { line ->
                    line.trim().toIntOrNull()?.let { completed.add(it) }
                }
            } catch (_: Exception) {}
        }
        return completed
    }

    fun markChunkCompleted(trackerFile: File, chunkIndex: Int) {
        try {
            FileOutputStream(trackerFile, true).bufferedWriter().use { writer ->
                writer.write("$chunkIndex\n")
            }
        } catch (_: Exception) {}
    }

    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
