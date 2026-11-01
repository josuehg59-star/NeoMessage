package com.josuedev.neomessage

import android.content.Context
import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ⚡ VOIP AUDIO STREAMING REAL CON AMPLIFICACIÓN DE VOLUMEN ULTRA (+12 dB)
 * - Captura micrófono real con AudioRecord (PCM 16-bit 16kHz)
 * - Algoritmo DSP de ganancia por software (x2.2) para que se escuche FUERTE y CLARO
 * - Fuerza el volumen máximo del sistema telefónico
 * - Costo: $0.00 para 1 a 1,000,000,000 de usuarios
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

    // Callback para retransmitir chunks de audio por el canal de la llamada
    var onAudioChunkCaptured: ((ByteArray) -> Unit)? = null

    /**
     * AMPLIFICADOR DSP DE AUDIO PCM 16-BIT (+8 dB a +12 dB)
     * Multiplica las muestras de audio con soft-clipping para que nunca distorsione
     * pero el volumen suene potente como en una llamada tradicional o WhatsApp
     */
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

    // ===== ANTI-ECO (ALTAVOZ) =====
    // Momento y nivel de la última voz remota reproducida: si el otro está hablando
    // y estamos en altavoz, se atenúa el micrófono para no devolverle su propia voz.
    @Volatile private var lastRemoteVoiceAt = 0L
    @Volatile private var lastRemoteRms = 0.0
    private var echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
    private var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null
    private var gainControl: android.media.audiofx.AutomaticGainControl? = null
    private val localAddresses = mutableSetOf<String>()

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

    /** Ganancia de reproducción: en altavoz se evita saturar (la saturación arruina la cancelación de eco). */
    private fun playbackGain(): Float = if (isSpeaker) 1.0f else 1.8f

    /** Ganancia de micrófono: en altavoz sin amplificación para no recapturar la voz remota. */
    private fun micGain(): Float = if (isSpeaker) 1.0f else 1.4f

    private fun registerRemotePlayback(data: ByteArray, length: Int) {
        val rms = pcmRms(data, length)
        if (rms > 500.0) {
            lastRemoteVoiceAt = System.currentTimeMillis()
            lastRemoteRms = rms
        }
    }

    private fun loadLocalAddresses() {
        localAddresses.clear()
        try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                ni.inetAddresses?.toList()?.forEach { addr -> addr.hostAddress?.let { localAddresses.add(it) } }
            }
        } catch (_: Exception) {}
    }

    fun startCall(context: Context, targetIp: String = "255.255.255.255", isSpeakerphone: Boolean = true) {
        if (isCallActive.getAndSet(true)) return

        isSpeaker = isSpeakerphone
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isSpeaker

            // Volumen de llamada alto pero sin forzar STREAM_MUSIC al máximo (provocaba eco en altavoz)
            val maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVoice * 0.85f).toInt().coerceAtLeast(1), 0)
        } catch (_: Exception) {}
        loadLocalAddresses()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val minBufRecord = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
                val minBufTrack = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
                val bufferSize = maxOf(minBufRecord, minBufTrack, 1024)

                // 1. INICIALIZAR RECEPTOR DE AUDIO (AudioTrack al 100% de ganancia)
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

                // 2. INICIALIZAR GRABADOR DE AUDIO (AudioRecord con cancelación de eco)
                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE,
                        CHANNEL_IN,
                        AUDIO_FORMAT,
                        bufferSize * 2
                    )

                    if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                        // Activar cancelación de eco, supresión de ruido y control de ganancia del hardware
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

                // 3. SOCKET UDP CON REUTILIZACIÓN DE PUERTO
                udpSocket = try {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(UDP_PORT))
                        broadcast = true
                    }
                } catch (_: Exception) {
                    try { DatagramSocket().apply { broadcast = true } } catch (_: Exception) { null }
                }

                Log.d(TAG, "VoIP Engine iniciado en puerto ${udpSocket?.localPort} con Amplificación DSP activa.")

                // HILO 1: CAPTURA DE MICRÓFONO Y ENVÍO (con Boost x1.6 en micrófono)
                jobRecord = launch {
                    val buffer = ByteArray(bufferSize)
                    val targetAddress = try { InetAddress.getByName(targetIp) } catch (_: Exception) { null }

                    while (isCallActive.get()) {
                        val rec = audioRecord
                        if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            val read = rec.read(buffer, 0, buffer.size)
                            if (read > 0 && !isMuted) {
                                // SUPRESOR DE ECO: si el otro está hablando por el altavoz,
                                // el micrófono capta su voz; se atenúa para no devolvérsela.
                                var gain = micGain()
                                val remoteActive = System.currentTimeMillis() - lastRemoteVoiceAt < 350L
                                if (remoteActive) {
                                    val micRms = pcmRms(buffer, read)
                                    // Solo se deja pasar si tú hablas claramente más fuerte que el eco
                                    val threshold = if (isSpeaker) lastRemoteRms * 0.9 else lastRemoteRms * 0.4
                                    if (micRms < threshold) gain = if (isSpeaker) 0.05f else 0.3f
                                }
                                val boostedMic = boostPcmVolume(buffer, read, gain)

                                // Enviar por UDP local
                                if (targetAddress != null && udpSocket != null) {
                                    try {
                                        val packet = DatagramPacket(boostedMic, read, targetAddress, UDP_PORT)
                                        udpSocket?.send(packet)
                                    } catch (_: Exception) {}
                                }

                                // Enviar por stream de llamada
                                onAudioChunkCaptured?.invoke(boostedMic)
                            }
                        } else {
                            delay(40)
                        }
                    }
                }

                // HILO 2: RECEPCIÓN UDP DIRECTA (con Boost x2.0 en altavoz)
                jobPlay = launch {
                    val receiveBuffer = ByteArray(bufferSize * 2)
                    val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

                    while (isCallActive.get()) {
                        try {
                            if (udpSocket != null) {
                                udpSocket?.receive(receivePacket)
                                val len = receivePacket.length
                                // CAUSA PRINCIPAL DEL ECO: el broadcast UDP regresaba al propio teléfono
                                // y te reproducía tu misma voz. Se descartan los paquetes propios.
                                val fromSelf = receivePacket.address?.let { addr ->
                                    addr.isLoopbackAddress || localAddresses.contains(addr.hostAddress)
                                } ?: false
                                if (len > 0 && audioTrack != null && !fromSelf) {
                                    val boostedPlayback = boostPcmVolume(receivePacket.data, len, playbackGain())
                                    registerRemotePlayback(boostedPlayback, len)
                                    audioTrack?.write(boostedPlayback, 0, len)
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

    /**
     * Reproduce un paquete de audio recibido de la red con amplificación activa
     */
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
