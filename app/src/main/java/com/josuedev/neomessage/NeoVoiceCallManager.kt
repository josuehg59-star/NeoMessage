package com.josuedev.neomessage

import android.content.Context
import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ⚡ MOTOR VOIP & VIDEO P2P DIRECTO (UDP + STUN HOLE-PUNCHING)
 * - Firebase SOLO se usa para enviar el aviso inicial de IP:Puerto (< 1 KB).
 * - El audio y el video viajan directo de celular a celular por WiFi o Datos Móviles (0 MB en Firebase).
 * - Cancelación de eco por hardware (AcousticEchoCanceler) + supresión en altavoz.
 */
object NeoVoiceCallManager {
    private const val TAG = "NEO_VOIP"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val UDP_PORT = 3088

    private val isCallActive = AtomicBoolean(false)
    private var isMuted = false
    private var isSpeaker = true

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var udpSocket: DatagramSocket? = null
    private var jobRecord: Job? = null
    private var jobPlay: Job? = null
    private var jobHolePunch: Job? = null

    var onAudioChunkCaptured: ((ByteArray) -> Unit)? = null
    var onRemoteVideoFrameReceived: ((ByteArray) -> Unit)? = null
    var onLocalEndpointDiscovered: ((Map<String, Any>) -> Unit)? = null

    private val peerEndpoints = CopyOnWriteArraySet<InetSocketAddress>()
    @Volatile private var lockedPeerAddress: InetSocketAddress? = null
    @Volatile private var lastDirectUdpPacketAt = 0L

    @Volatile private var lastRemoteVoiceAt = 0L
    @Volatile private var lastRemoteRms = 0.0
    private var echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
    private var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null
    private var gainControl: android.media.audiofx.AutomaticGainControl? = null
    private val localAddresses = mutableSetOf<String>()

    /**
     * Indica si la conexión directa P2P UDP entre los teléfonos está activa (0 gasto en Firebase)
     */
    fun isDirectP2pActive(): Boolean {
        return System.currentTimeMillis() - lastDirectUdpPacketAt < 4500L
    }

    /**
     * Registra las direcciones IP:Puerto (pública STUN y local WiFi) del otro teléfono recibidas en el aviso de Firebase
     */
    fun registerPeerEndpoints(pubIp: String?, pubPort: Int, locIp: String?, locPort: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!locIp.isNullOrBlank() && locPort > 0 && !localAddresses.contains(locIp)) {
                    peerEndpoints.add(InetSocketAddress(InetAddress.getByName(locIp), locPort))
                }
            } catch (_: Exception) {}
            try {
                if (!pubIp.isNullOrBlank() && pubPort > 0 && !localAddresses.contains(pubIp)) {
                    peerEndpoints.add(InetSocketAddress(InetAddress.getByName(pubIp), pubPort))
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Envía un cuadro de video JPEG directamente por UDP P2P (sin pasar por Firebase)
     */
    fun sendVideoFrameUdp(jpegBytes: ByteArray) {
        if (!isCallActive.get() || udpSocket == null || jpegBytes.isEmpty() || jpegBytes.size > 60000) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packetBytes = ByteArray(jpegBytes.size + 4)
                packetBytes[0] = 'V'.code.toByte()
                packetBytes[1] = 'I'.code.toByte()
                packetBytes[2] = 'D'.code.toByte()
                packetBytes[3] = 0
                System.arraycopy(jpegBytes, 0, packetBytes, 4, jpegBytes.size)
                sendRawUdpToPeers(packetBytes, packetBytes.size, includeBroadcast = false)
            } catch (_: Exception) {}
        }
    }

    private fun sendRawUdpToPeers(data: ByteArray, length: Int, includeBroadcast: Boolean = true) {
        val sock = udpSocket ?: return
        val locked = lockedPeerAddress
        if (locked != null && isDirectP2pActive()) {
            try {
                sock.send(DatagramPacket(data, length, locked.address, locked.port))
                return
            } catch (_: Exception) {}
        }
        for (ep in peerEndpoints) {
            try {
                sock.send(DatagramPacket(data, length, ep.address, ep.port))
            } catch (_: Exception) {}
        }
        if (includeBroadcast && peerEndpoints.isEmpty()) {
            try {
                sock.send(DatagramPacket(data, length, InetAddress.getByName("255.255.255.255"), UDP_PORT))
            } catch (_: Exception) {}
        }
    }

    /**
     * Consulta STUN RFC 5389 gratuita de Google sobre el mismo DatagramSocket para abrir el NAT y obtener IP:Puerto público
     */
    private fun queryStunPublicEndpoint(sock: DatagramSocket): Pair<String, Int>? {
        val stunServers = listOf("stun.l.google.com" to 19302, "stun1.l.google.com" to 19302)
        for ((host, port) in stunServers) {
            try {
                val req = ByteArray(20)
                req[0] = 0x00
                req[1] = 0x01 // Binding Request
                req[2] = 0x00
                req[3] = 0x00 // Length 0
                // Magic Cookie 0x2112A442
                req[4] = 0x21
                req[5] = 0x12
                req[6] = 0xA4.toByte()
                req[7] = 0x42
                val txId = ByteArray(12)
                SecureRandom().nextBytes(txId)
                System.arraycopy(txId, 0, req, 8, 12)

                val serverAddr = InetAddress.getByName(host)
                sock.send(DatagramPacket(req, req.size, serverAddr, port))

                val respBuf = ByteArray(512)
                val respPkt = DatagramPacket(respBuf, respBuf.size)
                val prevTimeout = sock.soTimeout
                sock.soTimeout = 1200
                try {
                    sock.receive(respPkt)
                    val len = respPkt.length
                    if (len >= 20 && respBuf[0] == 0x01.toByte() && respBuf[1] == 0x01.toByte()) {
                        var idx = 20
                        while (idx + 4 <= len) {
                            val attrType = ((respBuf[idx].toInt() and 0xFF) shl 8) or (respBuf[idx + 1].toInt() and 0xFF)
                            val attrLen = ((respBuf[idx + 2].toInt() and 0xFF) shl 8) or (respBuf[idx + 3].toInt() and 0xFF)
                            idx += 4
                            if (idx + attrLen > len) break
                            // XOR-MAPPED-ADDRESS (0x0020) or MAPPED-ADDRESS (0x0001)
                            if ((attrType == 0x0020 || attrType == 0x0001) && attrLen >= 8) {
                                val family = respBuf[idx + 1].toInt() and 0xFF
                                if (family == 0x01) { // IPv4
                                    val rawPort = ((respBuf[idx + 2].toInt() and 0xFF) shl 8) or (respBuf[idx + 3].toInt() and 0xFF)
                                    val mappedPort = if (attrType == 0x0020) rawPort xor 0x2112 else rawPort
                                    val ipBytes = ByteArray(4)
                                    val cookie = byteArrayOf(0x21, 0x12, 0xA4.toByte(), 0x42)
                                    for (b in 0..3) {
                                        ipBytes[b] = if (attrType == 0x0020) (respBuf[idx + 4 + b].toInt() xor cookie[b].toInt()).toByte() else respBuf[idx + 4 + b]
                                    }
                                    val ipStr = InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                                    if (ipStr.isNotBlank() && mappedPort > 0) {
                                        return ipStr to mappedPort
                                    }
                                }
                            }
                            idx += (attrLen + 3) and -4
                        }
                    }
                } finally {
                    sock.soTimeout = prevTimeout
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun boostPcmVolume(buffer: ByteArray, length: Int, gain: Float = 2.2f): ByteArray {
        val boosted = ByteArray(length)
        var i = 0
        while (i < length - 1) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            val amplified = (sample * gain).toInt().coerceIn(-32768, 32767)
            boosted[i] = (amplified and 0xFF).toByte()
            boosted[i + 1] = ((amplified shr 8) and 0xFF).toByte()
            i += 2
        }
        return boosted
    }

    private fun pcmRms(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i < length - 1) {
            val sample = ((buffer[i + 1].toInt()) shl 8) or (buffer[i].toInt() and 0xFF)
            sum += sample.toDouble() * sample
            count++
            i += 2
        }
        return if (count == 0) 0.0 else kotlin.math.sqrt(sum / count)
    }

    private fun playbackGain(): Float = if (isSpeaker) 1.0f else 1.8f
    private fun micGain(): Float = if (isSpeaker) 1.0f else 1.4f

    private fun registerRemotePlayback(data: ByteArray, length: Int) {
        val rms = pcmRms(data, length)
        if (rms > 500.0) {
            lastRemoteVoiceAt = System.currentTimeMillis()
            lastRemoteRms = rms
        }
    }

    private fun loadLocalAddresses(): String {
        localAddresses.clear()
        var bestLanIp = ""
        try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                ni.inetAddresses?.toList()?.forEach { addr ->
                    addr.hostAddress?.let { ip ->
                        localAddresses.add(ip)
                        if (addr is Inet4Address && !addr.isLoopbackAddress && bestLanIp.isEmpty()) {
                            bestLanIp = ip
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return bestLanIp
    }

    fun startCall(context: Context, targetIp: String = "255.255.255.255", isSpeakerphone: Boolean = true) {
        if (isCallActive.getAndSet(true)) return

        isSpeaker = isSpeakerphone
        peerEndpoints.clear()
        lockedPeerAddress = null
        lastDirectUdpPacketAt = 0L

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isSpeaker
            val maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVoice * 0.85f).toInt().coerceAtLeast(1), 0)
        } catch (_: Exception) {}
        val myLanIp = loadLocalAddresses()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val minBufRecord = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
                val minBufTrack = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
                val bufferSize = maxOf(minBufRecord, minBufTrack, 1024)

                // 1. INICIALIZAR RECEPTOR DE AUDIO
                try {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()

                    val audioFormatObj = AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .setEncoding(AUDIO_FORMAT)
                        .build()

                    audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(audioAttributes)
                        .setAudioFormat(audioFormatObj)
                        .setBufferSizeInBytes(bufferSize * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                        try { audioTrack?.setVolume(1.0f) } catch (_: Exception) {}
                        audioTrack?.play()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "AudioTrack init error: ${e.message}")
                }

                // 2. INICIALIZAR GRABADOR DE AUDIO CON CANCELACIÓN DE ECO
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE,
                        CHANNEL_IN,
                        AUDIO_FORMAT,
                        bufferSize * 2
                    )

                    if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                        val sessionId = audioRecord!!.audioSessionId
                        try {
                            if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                                echoCanceler = android.media.audiofx.AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                            }
                        } catch (_: Exception) {}
                        try {
                            if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                                noiseSuppressor = android.media.audiofx.NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                            }
                        } catch (_: Exception) {}
                        try {
                            if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                                gainControl = android.media.audiofx.AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                            }
                        } catch (_: Exception) {}
                        audioRecord?.startRecording()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "AudioRecord init error: ${e.message}")
                }

                // 3. SOCKET UDP P2P + DESCUBRIMIENTO STUN (SOLO AVISO LIGERO A FIREBASE)
                udpSocket = try {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(UDP_PORT))
                        broadcast = true
                    }
                } catch (_: Exception) {
                    try { DatagramSocket().apply { broadcast = true } } catch (_: Exception) { null }
                }

                val localPort = udpSocket?.localPort ?: UDP_PORT
                val stunRes = udpSocket?.let { queryStunPublicEndpoint(it) }
                val endpointMap = mutableMapOf<String, Any>(
                    "locIp" to myLanIp,
                    "locPort" to localPort,
                    "ts" to System.currentTimeMillis()
                )
                if (stunRes != null) {
                    endpointMap["pubIp"] = stunRes.first
                    endpointMap["pubPort"] = stunRes.second
                }
                onLocalEndpointDiscovered?.invoke(endpointMap)

                // HILO 0: HOLE-PUNCHING KEEP-ALIVE UDP DIRECTO CADA 800ms
                jobHolePunch = launch {
                    val ping = byteArrayOf('P'.code.toByte(), '2'.code.toByte(), 'P'.code.toByte(), '0'.code.toByte())
                    while (isCallActive.get()) {
                        sendRawUdpToPeers(ping, ping.size, includeBroadcast = true)
                        delay(800)
                    }
                }

                // HILO 1: CAPTURA DE MICRÓFONO Y ENVÍO P2P
                jobRecord = launch {
                    val buffer = ByteArray(bufferSize)
                    while (isCallActive.get()) {
                        val rec = audioRecord
                        if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            val read = rec.read(buffer, 0, buffer.size)
                            if (read > 0 && !isMuted) {
                                var gain = micGain()
                                val remoteActive = System.currentTimeMillis() - lastRemoteVoiceAt < 350L
                                if (remoteActive) {
                                    val micRms = pcmRms(buffer, read)
                                    val threshold = if (isSpeaker) lastRemoteRms * 0.9 else lastRemoteRms * 0.4
                                    if (micRms < threshold) gain = if (isSpeaker) 0.05f else 0.3f
                                }
                                val boostedMic = boostPcmVolume(buffer, read, gain)

                                // Enviar directo por UDP P2P (WiFi / Datos del teléfono)
                                sendRawUdpToPeers(boostedMic, read, includeBroadcast = true)

                                // Solo usar respaldo si aún no engancha el túnel UDP directo
                                if (!isDirectP2pActive()) {
                                    onAudioChunkCaptured?.invoke(boostedMic)
                                }
                            }
                        } else {
                            delay(40)
                        }
                    }
                }

                // HILO 2: RECEPCIÓN UDP DIRECTA (AUDIO Y VIDEO P2P)
                jobPlay = launch {
                    val receiveBuffer = ByteArray(65507)
                    val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

                    while (isCallActive.get()) {
                        try {
                            if (udpSocket != null) {
                                udpSocket?.receive(receivePacket)
                                val len = receivePacket.length
                                val senderAddr = receivePacket.address
                                val senderPort = receivePacket.port
                                val fromSelf = senderAddr?.let { addr ->
                                    addr.isLoopbackAddress || localAddresses.contains(addr.hostAddress)
                                } ?: false

                                if (len > 0 && !fromSelf && senderAddr != null) {
                                    lastDirectUdpPacketAt = System.currentTimeMillis()
                                    lockedPeerAddress = InetSocketAddress(senderAddr, senderPort)

                                    val d = receivePacket.data
                                    if (len == 4 && d[0] == 'P'.code.toByte() && d[1] == '2'.code.toByte() && d[2] == 'P'.code.toByte()) {
                                        if (d[3] == '0'.code.toByte()) {
                                            val pong = byteArrayOf('P'.code.toByte(), '2'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())
                                            try { udpSocket?.send(DatagramPacket(pong, pong.size, senderAddr, senderPort)) } catch (_: Exception) {}
                                        }
                                    } else if (len > 4 && d[0] == 'V'.code.toByte() && d[1] == 'I'.code.toByte() && d[2] == 'D'.code.toByte() && d[3] == 0.toByte()) {
                                        val jpegBytes = ByteArray(len - 4)
                                        System.arraycopy(d, 4, jpegBytes, 0, len - 4)
                                        onRemoteVideoFrameReceived?.invoke(jpegBytes)
                                    } else if (audioTrack != null) {
                                        val boostedPlayback = boostPcmVolume(d, len, playbackGain())
                                        registerRemotePlayback(boostedPlayback, len)
                                        audioTrack?.write(boostedPlayback, 0, len)
                                    }
                                }
                            } else {
                                delay(80)
                            }
                        } catch (_: Exception) {}
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando VoIP: ${e.message}", e)
            }
        }
    }

    fun playAudioChunk(data: ByteArray) {
        if (!isCallActive.get() || audioTrack == null || data.isEmpty()) return
        try {
            val boosted = boostPcmVolume(data, data.size, playbackGain())
            registerRemotePlayback(boosted, boosted.size)
            audioTrack?.write(boosted, 0, boosted.size)
        } catch (_: Exception) {}
    }

    fun setMute(mute: Boolean) {
        isMuted = mute
    }

    fun setSpeaker(context: Context, speaker: Boolean) {
        isSpeaker = speaker
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = speaker
        } catch (_: Exception) {}
    }

    fun endCall(context: Context) {
        if (!isCallActive.getAndSet(false)) return

        onAudioChunkCaptured = null
        onRemoteVideoFrameReceived = null
        onLocalEndpointDiscovered = null
        peerEndpoints.clear()
        lockedPeerAddress = null
        lastDirectUdpPacketAt = 0L

        jobHolePunch?.cancel()
        jobRecord?.cancel()
        jobPlay?.cancel()

        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { gainControl?.release() } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
        lastRemoteVoiceAt = 0L

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isMicrophoneMute = false
        } catch (_: Exception) {}

        Log.d(TAG, "VoIP Engine detenido con éxito.")
    }
}
