package com.josuedev.neomessage

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object NeoVoiceNoteManager {
    private const val TAG = "NEO_VOICE"
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTime = 0L

    private var activePlayer: MediaPlayer? = null
    private var currentPlayingId: String? = null

    // 1. INICIAR GRABACIÓN DE NOTA DE VOZ (AAC/M4A)
    fun startRecording(context: Context): Boolean {
        return try {
            stopPlayback()

            val file = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.m4a")
            currentOutputFile = file

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(22050)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recordingStartTime = System.currentTimeMillis()
            Log.d(TAG, "Grabando nota de voz en: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando grabación: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    // 2. DETENER GRABACIÓN Y RETORNAR PAYLOAD BASE64
    fun stopRecording(): Pair<String, Int>? {
        val recorder = mediaRecorder ?: return null
        val file = currentOutputFile ?: return null

        return try {
            recorder.stop()
            recorder.release()
            mediaRecorder = null

            val durationSeconds = maxOf(1, ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt())
            val bytes = file.readBytes()
            file.delete()

            if (bytes.isNotEmpty()) {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "[Voice] $b64|$durationSeconds" to durationSeconds
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo grabación: ${e.message}", e)
            try { file.delete() } catch (_: Exception) {}
            null
        } finally {
            mediaRecorder = null
            currentOutputFile = null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        try {
            currentOutputFile?.delete()
        } catch (_: Exception) {}
        currentOutputFile = null
    }

    // 3. REPRODUCIR NOTA DE VOZ DESDE BASE64
    fun playVoiceNote(
        context: Context,
        msgId: String,
        base64Data: String,
        onCompletion: () -> Unit
    ) {
        if (currentPlayingId == msgId && activePlayer?.isPlaying == true) {
            stopPlayback()
            onCompletion()
            return
        }

        stopPlayback()

        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "play_voice_${msgId.filter { it.isLetterOrDigit() }}.m4a")
            FileOutputStream(tempFile).use { it.write(bytes) }

            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    currentPlayingId = null
                    onCompletion()
                }
            }
            activePlayer = player
            currentPlayingId = msgId
        } catch (e: Exception) {
            Log.e(TAG, "Error reproduciendo nota de voz: ${e.message}", e)
            stopPlayback()
            onCompletion()
        }
    }

    fun stopPlayback() {
        try {
            activePlayer?.stop()
            activePlayer?.release()
        } catch (_: Exception) {}
        activePlayer = null
        currentPlayingId = null
    }

    fun isPlaying(msgId: String): Boolean {
        return currentPlayingId == msgId && activePlayer?.isPlaying == true
    }
}
