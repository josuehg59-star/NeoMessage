package com.josuedev.neomessage

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 📱 NEO LOCAL NODE SERVER (v10.5 SOVEREIGN P2P & UDP MESH)
 * Micro-servidor embebido y autónomo que corre en cada teléfono Android.
 * Soporta dos vías de entrega ultrarrápida sin internet:
 * 1. Baliza y difusión UDP en puerto 8898 (entrega instantánea en subred local sin necesidad de conocer la IP previa).
 * 2. Micro-servidor HTTP en puerto 8899 para transferencias masivas y streaming de fotos/archivos.
 */
object NeoLocalNodeServer {
    private const val TAG = "NEO_NODE_SERVER"
    const val DEFAULT_PORT = 8899
    const val UDP_BEACON_PORT = 8898

    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var currentMyPhone: String = ""

    // Archivos registrados listos para compartir desde este nodo
    private val sharedFiles = ConcurrentHashMap<String, Uri>()
    private val sharedMeta = ConcurrentHashMap<String, NeoChunkStreamer.FileMeta>()

    // Peers locales descubiertos en la misma red Wi-Fi o Hotspot
    data class DiscoveredPeer(
        val peerId: String,
        val host: String,
        val port: Int,
        val lastSeen: Long = System.currentTimeMillis()
    )
    val activeLocalPeers = ConcurrentHashMap<String, DiscoveredPeer>()

    var activePort: Int = DEFAULT_PORT
        private set

    fun findLocalPeer(phone: String): DiscoveredPeer? {
        val clean = NeoUtils.sanitizeId(phone)
        val direct = activeLocalPeers[clean]
        if (direct != null) return direct
        
        // Búsqueda por coincidencia de los últimos 10 dígitos
        return activeLocalPeers.values.firstOrNull { 
            NeoUtils.sanitizeId(it.peerId).takeLast(10) == clean.takeLast(10) 
        }
    }

    fun start(context: Context, preferredPort: Int = DEFAULT_PORT, myPhone: String = "") {
        if (isRunning) return

        if (myPhone.isNotBlank()) {
            currentMyPhone = NeoUtils.sanitizeId(myPhone)
        } else {
            val sp = context.getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
            currentMyPhone = NeoUtils.sanitizeId(sp.getString("user_id", "") ?: "")
        }

        // Adquirir MulticastLock para que el chip Wi-Fi no descarte paquetes UDP
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("NeoMeshLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}

        scope.launch {
            try {
                var portToTry = preferredPort
                var bound = false
                while (!bound && portToTry < preferredPort + 20) {
                    try {
                        serverSocket = ServerSocket(portToTry)
                        activePort = portToTry
                        bound = true
                    } catch (_: IOException) {
                        portToTry++
                    }
                }

                if (!bound) {
                    Log.e(TAG, "No se pudo enlazar ningún puerto para el micro-servidor")
                    return@launch
                }

                isRunning = true
                Log.i(TAG, "🟢 Micro-Servidor NeoNode ACTIVO en puerto $activePort (ID: $currentMyPhone)")

                // 1. Registrar en la red local mediante NSD (mDNS)
                NeoTripleShieldManager.registerService(context, activePort, currentMyPhone)
                NeoTripleShieldManager.discoverPeers(context) {}

                // 2. Iniciar Baliza y Receptor UDP Mesh en puerto 8898
                startUdpMesh(context)

                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: continue
                        scope.launch { handleClient(context, clientSocket) }
                    } catch (e: Exception) {
                        if (isRunning) Log.w(TAG, "Excepción en bucle de aceptación: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando NeoLocalNodeServer: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        try { udpSocket?.close() } catch (_: Exception) {}
        try { if (multicastLock?.isHeld == true) multicastLock?.release() } catch (_: Exception) {}
        serverSocket = null
        udpSocket = null
        multicastLock = null
        Log.i(TAG, "🔴 Micro-Servidor NeoNode detenido")
    }

    fun getBroadcastAddresses(): List<InetAddress> {
        val broadcastList = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (interfaceAddress in iface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        broadcastList.add(broadcast)
                    }
                }
            }
        } catch (_: Exception) {}
        if (broadcastList.isEmpty()) {
            try { broadcastList.add(InetAddress.getByName("255.255.255.255")) } catch (_: Exception) {}
        }
        return broadcastList
    }

    /**
     * MESH UDP: Escucha balizas y MENSAJES DIRECTOS instantáneos en la red local
     */
    private fun startUdpMesh(context: Context) {
        // Receptor UDP
        scope.launch(Dispatchers.IO) {
            try {
                udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(UDP_BEACON_PORT))
                    broadcast = true
                }
                val buffer = ByteArray(4096)
                while (isRunning && udpSocket?.isClosed == false) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket?.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()

                        // CASO 1: BALIZA DE PRESENCIA (NEO_BEACON:PHONE:PORT)
                        if (text.startsWith("NEO_BEACON:")) {
                            val parts = text.split(":")
                            if (parts.size >= 3) {
                                val peerPhone = NeoUtils.sanitizeId(parts[1])
                                val peerPort = parts[2].toIntOrNull() ?: DEFAULT_PORT
                                val peerHost = packet.address?.hostAddress ?: ""
                                
                                if (peerPhone.isNotEmpty() && peerPhone != currentMyPhone && peerHost.isNotEmpty()) {
                                    activeLocalPeers[peerPhone] = DiscoveredPeer(
                                        peerId = peerPhone,
                                        host = peerHost,
                                        port = peerPort
                                    )
                                }
                            }
                        }
                        // CASO 2: MENSAJE DIRECTO EN RED LOCAL (NEO_MSG:TARGET_PHONE:JSON)
                        else if (text.startsWith("NEO_MSG:")) {
                            val firstColon = text.indexOf(":", 8)
                            if (firstColon > 8) {
                                val targetPhone = NeoUtils.sanitizeId(text.substring(8, firstColon))
                                val jsonStr = text.substring(firstColon + 1)
                                
                                // Si este mensaje es para mí (comparando últimos 10 dígitos)
                                if (currentMyPhone.isNotEmpty() && targetPhone.takeLast(10) == currentMyPhone.takeLast(10)) {
                                    val json = JSONObject(jsonStr)
                                    val msgId = json.optString("id", java.util.UUID.randomUUID().toString())
                                    val msgText = json.optString("text", "")
                                    val senderId = json.optString("senderId", "")
                                    val senderName = json.optString("senderName", "Contacto NeoMessage")
                                    val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                                    val chatId = json.optString("chatId", "")

                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val dao = AppDatabase.getDatabase(context).messageDao()
                                            val entity = MessageEntity(
                                                id = msgId,
                                                text = msgText,
                                                timestamp = timestamp,
                                                senderId = senderId,
                                                senderName = senderName,
                                                chatId = chatId
                                            )
                                            dao.insertMessage(entity)

                                            // Timbre y notificación
                                            try {
                                                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                                RingtoneManager.getRingtone(context, uri)?.play()
                                            } catch (_: Exception) {}

                                            NeoNotificationHelper.showNotification(
                                                context,
                                                "Mensaje de $senderName",
                                                msgText,
                                                msgId
                                            )
                                            Log.i(TAG, "✅ Mensaje UDP recibido y guardado con éxito: $msgId")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error guardando mensaje UDP: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP Mesh Listener no disponible: ${e.message}")
            }
        }

        // Emisor periódico de presencia UDP (cada 5 segundos)
        scope.launch(Dispatchers.IO) {
            while (isRunning) {
                try {
                    if (currentMyPhone.isNotEmpty()) {
                        val broadcastMsg = "NEO_BEACON:$currentMyPhone:$activePort"
                        val bytes = broadcastMsg.toByteArray(Charsets.UTF_8)
                        val broadcastAddrs = getBroadcastAddresses()
                        
                        val sendSocket = DatagramSocket()
                        sendSocket.broadcast = true
                        for (addr in broadcastAddrs) {
                            val sendPacket = DatagramPacket(bytes, bytes.size, addr, UDP_BEACON_PORT)
                            sendSocket.send(sendPacket)
                        }
                        sendSocket.close()
                    }
                } catch (_: Exception) {}
                delay(5000)
            }
        }
    }

    /**
     * Emite un mensaje instantáneo por UDP a todos los canales de la red local.
     * Llega en < 1 milisegundo a cualquier teléfono en la misma red Wi-Fi o Hotspot.
     */
    fun broadcastUdpMessage(targetPhone: String, message: CloudMessage, chatId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val cleanTarget = NeoUtils.sanitizeId(targetPhone)
                val json = JSONObject().apply {
                    put("id", message.id)
                    put("text", message.text)
                    put("senderId", message.senderId)
                    put("senderName", message.senderName)
                    put("timestamp", message.timestamp)
                    put("chatId", chatId)
                }
                val payload = "NEO_MSG:$cleanTarget:${json.toString()}".toByteArray(Charsets.UTF_8)
                val sendSocket = DatagramSocket()
                sendSocket.broadcast = true
                for (addr in getBroadcastAddresses()) {
                    val packet = DatagramPacket(payload, payload.size, addr, UDP_BEACON_PORT)
                    sendSocket.send(packet)
                }
                sendSocket.close()
                Log.i(TAG, "⚡ Mensaje UDP emitido a red local hacia $cleanTarget")
            } catch (e: Exception) {
                Log.w(TAG, "Error emitiendo mensaje UDP: ${e.message}")
            }
        }
    }

    /**
     * Envía un mensaje directamente al micro-servidor del destinatario por HTTP local
     */
    fun sendDirectMessage(
        targetHost: String,
        targetPort: Int,
        message: CloudMessage,
        chatId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("id", message.id)
                    put("text", message.text)
                    put("senderId", message.senderId)
                    put("senderName", message.senderName)
                    put("timestamp", message.timestamp)
                    put("chatId", chatId)
                }
                val body = json.toString().toByteArray(Charsets.UTF_8)
                val socket = Socket()
                socket.connect(InetSocketAddress(targetHost, targetPort), 3500)
                socket.soTimeout = 5000

                val out = BufferedOutputStream(socket.getOutputStream())
                val req = "POST /api/message HTTP/1.1\r\n" +
                          "Host: $targetHost:$targetPort\r\n" +
                          "Content-Type: application/json\r\n" +
                          "Content-Length: ${body.size}\r\n" +
                          "Connection: close\r\n\r\n"
                out.write(req.toByteArray(Charsets.UTF_8))
                out.write(body)
                out.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val statusLine = reader.readLine()
                socket.close()

                if (statusLine != null && statusLine.contains("200")) {
                    withContext(Dispatchers.Main) { onSuccess() }
                } else {
                    withContext(Dispatchers.Main) { onError("Rechazado: $statusLine") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Fallo de conexión P2P") }
            }
        }
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    /**
     * Registra un archivo local para que otro dispositivo lo descargue por P2P
     */
    fun registerFileForSharing(context: Context, uri: Uri): NeoChunkStreamer.FileMeta {
        val meta = NeoChunkStreamer.getFileMeta(context, uri)
        sharedFiles[meta.fileId] = uri
        sharedMeta[meta.fileId] = meta
        Log.i(TAG, "📁 Archivo registrado para compartir P2P: ${meta.fileName} (${meta.totalBytes} bytes)")
        return meta
    }

    private fun handleClient(context: Context, socket: Socket) {
        try {
            socket.soTimeout = 30000 // 30s timeout
            val rawInput = socket.getInputStream()
            val input = BufferedReader(InputStreamReader(rawInput, Charsets.UTF_8))
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = input.readLine() ?: return socket.close()
            val parts = requestLine.split(" ")
            if (parts.size < 2) return socket.close()

            val method = parts[0].uppercase()
            val pathWithQuery = parts[1]
            val path = pathWithQuery.substringBefore("?")
            val queryParams = parseQueryParams(pathWithQuery)

            // Leer cabeceras HTTP
            val headers = mutableMapOf<String, String>()
            var headerLine = input.readLine()
            while (!headerLine.isNullOrEmpty()) {
                val colonIdx = headerLine.indexOf(":")
                if (colonIdx > 0) {
                    val key = headerLine.substring(0, colonIdx).trim().lowercase()
                    val value = headerLine.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
                headerLine = input.readLine()
            }

            when {
                // ENDPOINT DE RECEPCIÓN DIRECTA DE MENSAJES P2P (0 DATOS, 0 SERVIDORES)
                path == "/api/message" && method == "POST" -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val bodyChars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = input.read(bodyChars, readTotal, contentLength - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                    val jsonStr = String(bodyChars, 0, readTotal)
                    val json = JSONObject(jsonStr)

                    val msgId = json.optString("id", java.util.UUID.randomUUID().toString())
                    val text = json.optString("text", "")
                    val senderId = json.optString("senderId", "")
                    val senderName = json.optString("senderName", "Contacto P2P")
                    val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    val chatId = json.optString("chatId", "")

                    scope.launch(Dispatchers.IO) {
                        try {
                            val dao = AppDatabase.getDatabase(context).messageDao()
                            val entity = MessageEntity(
                                id = msgId,
                                text = text,
                                timestamp = timestamp,
                                senderId = senderId,
                                senderName = senderName,
                                chatId = chatId
                            )
                            dao.insertMessage(entity)

                            // Sonido y notificación inmediata
                            try {
                                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                RingtoneManager.getRingtone(context, uri)?.play()
                            } catch (_: Exception) {}

                            NeoNotificationHelper.showNotification(
                                context,
                                "Mensaje de $senderName",
                                text,
                                msgId
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error persistiendo mensaje P2P recibido: ${e.message}")
                        }
                    }

                    val resJson = JSONObject().apply {
                        put("status", "received")
                        put("id", msgId)
                    }.toString()
                    sendHttpResponse(output, 200, "OK", "application/json", resJson.toByteArray(Charsets.UTF_8))
                }

                path == "/ping" -> {
                    val responseJson = JSONObject().apply {
                        put("status", "ok")
                        put("node", "NeoMessenger-EdgeNode")
                        put("phone", currentMyPhone)
                        put("port", activePort)
                    }.toString()
                    sendHttpResponse(output, 200, "OK", "application/json", responseJson.toByteArray(Charsets.UTF_8))
                }

                path == "/meta" -> {
                    val fileId = queryParams["fileId"] ?: ""
                    val meta = sharedMeta[fileId]
                    if (meta != null) {
                        val json = JSONObject().apply {
                            put("fileId", meta.fileId)
                            put("fileName", meta.fileName)
                            put("totalBytes", meta.totalBytes)
                            put("totalChunks", meta.totalChunks)
                            put("mimeType", meta.mimeType)
                        }.toString()
                        sendHttpResponse(output, 200, "OK", "application/json", json.toByteArray(Charsets.UTF_8))
                    } else {
                        sendHttpResponse(output, 404, "Not Found", "text/plain", "Archivo no encontrado".toByteArray(Charsets.UTF_8))
                    }
                }

                path == "/chunk" && method == "GET" -> {
                    val fileId = queryParams["fileId"] ?: ""
                    val chunkIdx = queryParams["chunk"]?.toIntOrNull() ?: 0
                    val uri = sharedFiles[fileId]

                    if (uri != null) {
                        val chunkData = NeoChunkStreamer.readChunk(context, uri, chunkIdx)
                        if (chunkData != null) {
                            sendChunkResponse(output, chunkData.first, chunkData.second)
                        } else {
                            sendHttpResponse(output, 404, "Not Found", "text/plain", "Fin de archivo o chunk inválido".toByteArray(Charsets.UTF_8))
                        }
                    } else {
                        sendHttpResponse(output, 404, "Not Found", "text/plain", "Archivo no disponible".toByteArray(Charsets.UTF_8))
                    }
                }

                path == "/download" && method == "GET" -> {
                    val fileId = queryParams["fileId"] ?: ""
                    val uri = sharedFiles[fileId]
                    val meta = sharedMeta[fileId]

                    if (uri != null && meta != null) {
                        streamEntireFile(context, uri, meta, headers["range"], output)
                    } else {
                        sendHttpResponse(output, 404, "Not Found", "text/plain", "Archivo no encontrado".toByteArray(Charsets.UTF_8))
                    }
                }

                else -> {
                    sendHttpResponse(output, 400, "Bad Request", "text/plain", "Endpoint desconocido".toByteArray(Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error atendiendo cliente: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun streamEntireFile(
        context: Context,
        uri: Uri,
        meta: NeoChunkStreamer.FileMeta,
        rangeHeader: String?,
        output: OutputStream
    ) {
        var startByte = 0L
        var endByte = meta.totalBytes - 1

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.removePrefix("bytes=").split("-")
            startByte = range.getOrNull(0)?.toLongOrNull() ?: 0L
            if (range.size > 1 && range[1].isNotEmpty()) {
                endByte = range[1].toLongOrNull() ?: endByte
            }
        }

        val contentLength = endByte - startByte + 1
        val isPartial = rangeHeader != null

        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
        val headerBuilder = StringBuilder(statusLine)
            .append("Content-Type: ${meta.mimeType}\r\n")
            .append("Content-Length: $contentLength\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Disposition: attachment; filename=\"${meta.fileName}\"\r\n")

        if (isPartial) {
            headerBuilder.append("Content-Range: bytes $startByte-$endByte/${meta.totalBytes}\r\n")
        }
        headerBuilder.append("Connection: close\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray())
        output.flush()

        // Streaming por búfer de 64 KB sin saturar RAM
        context.contentResolver.openInputStream(uri)?.use { stream ->
            var skipped = 0L
            while (skipped < startByte) {
                val s = stream.skip(startByte - skipped)
                if (s <= 0) break
                skipped += s
            }

            val buffer = ByteArray(64 * 1024)
            var remaining = contentLength
            while (remaining > 0) {
                val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                val read = stream.read(buffer, 0, toRead)
                if (read == -1) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.flush()
        }
    }

    private fun sendChunkResponse(output: OutputStream, data: ByteArray, info: NeoChunkStreamer.ChunkInfo) {
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "X-Chunk-Index: ${info.chunkIndex}\r\n" +
                "X-Chunk-Offset: ${info.offset}\r\n" +
                "X-Chunk-Sha256: ${info.checksumSha256}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray())
        output.write(data)
        output.flush()
    }

    private fun sendHttpResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray
    ) {
        val headers = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray())
        output.write(body)
        output.flush()
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        val query = url.substringAfter("?", "")
        if (query.isEmpty()) return emptyMap()
        return query.split("&").associate { param ->
            val parts = param.split("=")
            val key = parts.getOrNull(0) ?: ""
            val value = parts.getOrNull(1) ?: ""
            key to value
        }
    }
}
