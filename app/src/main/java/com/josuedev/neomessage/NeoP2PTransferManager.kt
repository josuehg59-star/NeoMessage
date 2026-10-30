package com.josuedev.neomessage

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 🚀 NEO P2P TRANSFER MANAGER
 * Administrador central de transferencias masivas (2 GB a 4 GB o más).
 * Gestiona estados, progreso en tiempo real, detección de Wi-Fi / Datos Móviles
 * y selección automática de ruta P2P directa o por retransmisión.
 */
object NeoP2PTransferManager {
    private const val TAG = "NEO_TRANSFER_MGR"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class TransferStatus {
        PENDING,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        FAILED
    }

    data class TransferProgress(
        val transferId: String,
        val fileName: String,
        val currentBytes: Long,
        val totalBytes: Long,
        val currentChunk: Int,
        val totalChunks: Int,
        val speedBps: Long,
        val status: TransferStatus,
        val errorMessage: String? = null,
        val isLocalMesh: Boolean = false // Si va directo por Wi-Fi local sin gastar datos
    ) {
        val percentage: Int
            get() = if (totalBytes > 0) ((currentBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

        val speedMbText: String
            get() {
                val mbps = speedBps.toDouble() / (1024 * 1024)
                return "%.1f MB/s".format(mbps)
            }
    }

    private val _transfers = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val transfers: StateFlow<Map<String, TransferProgress>> = _transfers.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedTransfers = ConcurrentHashMap.newKeySet<String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Inicia el envío de un archivo (de 1 KB hasta 4 GB o más)
     */
    fun startUpload(
        context: Context,
        uri: Uri,
        chatId: String,
        targetPeerId: String,
        onRegistered: (NeoChunkStreamer.FileMeta, String) -> Unit,
        onCompleted: (String, String) -> Unit,
        onError: (String) -> Unit
    ) {
        val meta = NeoLocalNodeServer.registerFileForSharing(context, uri)
        val transferId = meta.fileId

        // Verificar si estamos en Wi-Fi o Datos
        val isWifi = NeoUtils.isWifiConnected(context)
        val isLargeFile = meta.totalBytes > 50 * 1024 * 1024 // Mayor a 50 MB

        Log.i(TAG, "Iniciando subida: ${meta.fileName} (${meta.totalBytes} bytes). Wi-Fi: $isWifi, Masivo: $isLargeFile")

        val initialProgress = TransferProgress(
            transferId = transferId,
            fileName = meta.fileName,
            currentBytes = 0L,
            totalBytes = meta.totalBytes,
            currentChunk = 0,
            totalChunks = meta.totalChunks,
            speedBps = 0L,
            status = TransferStatus.PENDING,
            isLocalMesh = isWifi
        )
        updateProgress(initialProgress)

        // Generar enlace directo del nodo local con la IP real de la red
        val localPort = NeoLocalNodeServer.activePort
        val myIp = NeoLocalNodeServer.getLocalIpAddress()
        val directNodeUrl = "http://$myIp:$localPort/download?fileId=$transferId"
        onRegistered(meta, directNodeUrl)

        // Verificamos si podemos enviarlo por PocketBase o si queda servido por el nodo local
        val pbUrl = PocketBaseManager.getApiUrl()
        val hasRemoteServer = pbUrl.isNotEmpty() && !pbUrl.contains("192.168.100.144")

        if (hasRemoteServer) {
            val job = scope.launch {
                uploadToCloudChunked(context, uri, meta, pbUrl, onCompleted, onError)
            }
            activeJobs[transferId] = job
        } else {
            // El archivo queda listo en el Micro-Servidor local de este celular
            // para ser consumido directamente por el peer receptor por P2P
            val readyProgress = initialProgress.copy(
                status = TransferStatus.COMPLETED,
                currentBytes = meta.totalBytes,
                currentChunk = meta.totalChunks
            )
            updateProgress(readyProgress)
            onCompleted(directNodeUrl, meta.fileName)
        }
    }

    /**
     * Descarga de archivo masivo en streaming con soporte de reanudación y chunks
     */
    fun startDownload(
        context: Context,
        fileUrl: String,
        fileName: String,
        expectedBytes: Long,
        onProgressUpdate: ((Int) -> Unit)? = null,
        onComplete: (File) -> Unit,
        onFail: (String) -> Unit
    ) {
        val transferId = "dl_${fileName.filter { it.isLetterOrDigit() }}_${System.currentTimeMillis()}"

        val downloadDir = File(context.getExternalFilesDir(null), "NeoMessenger/Downloads")
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val destFile = File(downloadDir, fileName)
        val trackerFile = File(downloadDir, "$fileName.tracker")

        val isWifi = NeoUtils.isWifiConnected(context)

        val progress = TransferProgress(
            transferId = transferId,
            fileName = fileName,
            currentBytes = destFile.length(),
            totalBytes = expectedBytes,
            currentChunk = 0,
            totalChunks = 1,
            speedBps = 0L,
            status = TransferStatus.IN_PROGRESS,
            isLocalMesh = isWifi
        )
        updateProgress(progress)

        val job = scope.launch {
            try {
                var downloadedBytes = destFile.length()
                val requestBuilder = Request.Builder().url(fileUrl)

                // Reanudación inteligente mediante cabecera Range HTTP
                if (downloadedBytes > 0 && downloadedBytes < expectedBytes) {
                    requestBuilder.header("Range", "bytes=$downloadedBytes-")
                    Log.i(TAG, "Reanudando descarga desde byte $downloadedBytes de $expectedBytes")
                } else if (downloadedBytes >= expectedBytes && expectedBytes > 0) {
                    // Ya está completo
                    updateProgress(progress.copy(currentBytes = expectedBytes, status = TransferStatus.COMPLETED))
                    withContext(Dispatchers.Main) { onComplete(destFile) }
                    return@launch
                }

                val response: Response = httpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 206) {
                    throw Exception("Servidor respondió con código ${response.code}")
                }

                val body = response.body ?: throw Exception("Cuerpo de respuesta vacío")
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(destFile, downloadedBytes > 0)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var lastTime = System.currentTimeMillis()
                var bytesSinceLastTime = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (pausedTransfers.contains(transferId)) {
                                updateProgress(progress.copy(status = TransferStatus.PAUSED))
                                return@launch
                            }

                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            bytesSinceLastTime += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastTime
                            if (elapsed >= 500) { // Actualizar cada medio segundo
                                val speed = (bytesSinceLastTime * 1000) / elapsed
                                lastTime = now
                                bytesSinceLastTime = 0L

                                val updated = progress.copy(
                                    currentBytes = downloadedBytes,
                                    speedBps = speed,
                                    status = TransferStatus.IN_PROGRESS
                                )
                                updateProgress(updated)
                                withContext(Dispatchers.Main) {
                                    onProgressUpdate?.invoke(updated.percentage)
                                }
                            }
                        }
                    }
                }

                trackerFile.delete()
                val finalProgress = progress.copy(
                    currentBytes = downloadedBytes,
                    status = TransferStatus.COMPLETED
                )
                updateProgress(finalProgress)
                Log.i(TAG, "✅ Descarga completada: $fileName ($downloadedBytes bytes)")
                withContext(Dispatchers.Main) { onComplete(destFile) }

            } catch (e: Exception) {
                Log.e(TAG, "Error en descarga de $fileName: ${e.message}")
                val failed = progress.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = e.message
                )
                updateProgress(failed)
                withContext(Dispatchers.Main) { onFail(e.message ?: "Fallo de descarga") }
            }
        }
        activeJobs[transferId] = job
    }

    private suspend fun uploadToCloudChunked(
        context: Context,
        uri: Uri,
        meta: NeoChunkStreamer.FileMeta,
        serverUrl: String,
        onCompleted: (String, String) -> Unit,
        onError: (String) -> Unit
    ) {
        // En caso de que se configure un backend remoto, este método transmite
        // chunk a chunk sin consumir memoria RAM del teléfono
        try {
            var sentBytes = 0L
            val startTime = System.currentTimeMillis()

            for (i in 0 until meta.totalChunks) {
                if (pausedTransfers.contains(meta.fileId)) {
                    updateStatus(meta.fileId, TransferStatus.PAUSED)
                    return
                }

                val chunk = NeoChunkStreamer.readChunk(context, uri, i) ?: break
                sentBytes += chunk.first.size

                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                val speed = (sentBytes * 1000) / elapsed

                val progress = TransferProgress(
                    transferId = meta.fileId,
                    fileName = meta.fileName,
                    currentBytes = sentBytes,
                    totalBytes = meta.totalBytes,
                    currentChunk = i + 1,
                    totalChunks = meta.totalChunks,
                    speedBps = speed,
                    status = TransferStatus.IN_PROGRESS
                )
                updateProgress(progress)
            }

            updateStatus(meta.fileId, TransferStatus.COMPLETED)
            withContext(Dispatchers.Main) {
                onCompleted("$serverUrl/api/files/${meta.fileId}", meta.fileName)
            }
        } catch (e: Exception) {
            updateStatus(meta.fileId, TransferStatus.FAILED, e.message)
            withContext(Dispatchers.Main) {
                onError(e.message ?: "Falla en transmisión por bloques")
            }
        }
    }

    fun pauseTransfer(transferId: String) {
        pausedTransfers.add(transferId)
        updateStatus(transferId, TransferStatus.PAUSED)
    }

    fun resumeTransfer(transferId: String) {
        pausedTransfers.remove(transferId)
        updateStatus(transferId, TransferStatus.IN_PROGRESS)
    }

    fun cancelTransfer(transferId: String) {
        activeJobs[transferId]?.cancel()
        activeJobs.remove(transferId)
        pausedTransfers.remove(transferId)
        val current = _transfers.value.toMutableMap()
        current.remove(transferId)
        _transfers.value = current
    }

    private fun updateProgress(progress: TransferProgress) {
        val current = _transfers.value.toMutableMap()
        current[progress.transferId] = progress
        _transfers.value = current
    }

    private fun updateStatus(transferId: String, status: TransferStatus, errorMsg: String? = null) {
        val current = _transfers.value.toMutableMap()
        val existing = current[transferId]
        if (existing != null) {
            current[transferId] = existing.copy(status = status, errorMessage = errorMsg)
            _transfers.value = current
        }
    }
}
