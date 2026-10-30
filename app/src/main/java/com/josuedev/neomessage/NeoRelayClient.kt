package com.josuedev.neomessage

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object NeoRelayClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Sin timeout para stream permanente
        .retryOnConnectionFailure(true)
        .build()

    private var streamCall: Call? = null
    private var isConnecting = false
    private var currentUserId: String = ""

    // Flujo reactivo de mensajes recibidos por Relay
    private val _incomingMessages = MutableSharedFlow<Pair<String, CloudMessage>>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    fun getRelayUrl(context: Context): String {
        val sp = context.getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
        return sp.getString("custom_relay_url", NetworkConfig.NEO_RELAY_URL) ?: NetworkConfig.NEO_RELAY_URL
    }

    fun setCustomRelayUrl(context: Context, url: String) {
        val sp = context.getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
        sp.edit().putString("custom_relay_url", url).apply()
    }

    /**
     * Conexión permanente al servidor Relay ($0.00 costo)
     * Utiliza Server-Sent Events (SSE) nativo sobre HTTP.
     */
    fun connect(context: Context, userId: String) {
        val cleanUser = NeoUtils.sanitizeId(userId)
        if (cleanUser.isEmpty() || (cleanUser == currentUserId && streamCall != null && !streamCall!!.isCanceled())) return
        
        currentUserId = cleanUser
        disconnect()

        CoroutineScope(Dispatchers.IO).launch {
            val baseUrl = getRelayUrl(context)
            val streamUrl = "$baseUrl/stream?user=$cleanUser"
            android.util.Log.d("NEO_RELAY", "📡 Conectando a NeoRelay Sovereign en: $streamUrl")

            while (isActive && currentUserId.isNotEmpty()) {
                try {
                    val request = Request.Builder()
                        .url(streamUrl)
                        .header("Accept", "text/event-stream")
                        .build()

                    val call = client.newCall(request)
                    streamCall = call
                    val response = call.execute()

                    if (response.isSuccessful) {
                        android.util.Log.i("NEO_RELAY", "🟢 Conectado con éxito a NeoRelay ($cleanUser)")
                        val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return@launch))

                        var line: String? = reader.readLine()
                        while (line != null && isActive) {
                            if (line.startsWith("data:")) {
                                val jsonStr = line.substring(5).trim()
                                if (jsonStr.isNotEmpty()) {
                                    handleIncomingRelayPayload(context, jsonStr)
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NEO_RELAY", "Reconectando en 3s: ${e.message}")
                }
                delay(3000)
            }
        }
    }

    fun disconnect() {
        try {
            streamCall?.cancel()
            streamCall = null
        } catch (_: Exception) {}
    }

    private fun handleIncomingRelayPayload(context: Context, jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val chatId = obj.optString("chatId")
            val msgObj = obj.optJSONObject("message") ?: return

            val msg = CloudMessage(
                id = msgObj.optString("id"),
                text = msgObj.optString("text"),
                timestamp = msgObj.optLong("timestamp", System.currentTimeMillis()),
                senderId = msgObj.optString("senderId"),
                senderName = msgObj.optString("senderName"),
                rank = msgObj.optString("rank", "")
            )

            // 1. Guardar en SQLite Room local
            val localDb = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                localDb.messageDao().insertMessage(msg.toEntity(chatId))
            }

            // 2. Emitir al flujo reactivo para actualizar pantalla de chat si está abierta
            _incomingMessages.tryEmit(chatId to msg)

            // 3. Notificación instantánea y limpia
            NeoNotificationHelper.showNotification(
                context = context,
                title = msg.senderName.ifEmpty { "Nuevo mensaje" },
                message = msg.text,
                messageId = msg.id
            )

            // 4. Despacho a NeoBot si el bot está activado en el dispositivo
            NeoBotManager.dispatchAutoReplyIfNeeded(
                context = context,
                chatId = chatId,
                senderId = msg.senderId,
                myId = currentUserId,
                incomingText = msg.text,
                msgTimestamp = msg.timestamp
            )

            android.util.Log.i("NEO_RELAY", "⚡ Mensaje recibido vía Relay en < 1ms: ${msg.text.take(30)}")
        } catch (e: Exception) {
            android.util.Log.e("NEO_RELAY", "Error procesando payload de Relay: ${e.message}")
        }
    }

    /**
     * Envío directo Celular ➔ Relay ➔ Celular ($0.00 costo)
     */
    fun sendMessage(
        context: Context,
        chatId: String,
        recipientPhone: String,
        message: CloudMessage,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = getRelayUrl(context)
                val sendUrl = "$baseUrl/send"
                val cleanTo = NeoUtils.sanitizeId(recipientPhone)

                val payload = JSONObject().apply {
                    put("to", cleanTo)
                    put("chatId", chatId)
                    val msgObj = JSONObject().apply {
                        put("id", message.id)
                        put("text", message.text)
                        put("timestamp", message.timestamp)
                        put("senderId", message.senderId)
                        put("senderName", message.senderName)
                        put("rank", message.rank)
                    }
                    put("message", msgObj)
                }

                val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(sendUrl)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    android.util.Log.i("NEO_RELAY", "✅ Mensaje despachado a Relay hacia $cleanTo con éxito")
                    withContext(Dispatchers.Main) { onSuccess?.invoke() }
                } else {
                    withContext(Dispatchers.Main) { onError?.invoke("Código ${response.code}") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError?.invoke(e.message ?: "Error desconocido") }
            }
        }
    }
}
