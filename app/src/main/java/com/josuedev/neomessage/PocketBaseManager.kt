package com.josuedev.neomessage

import android.content.Context
import android.net.Uri
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

object PocketBaseManager {
    // Configuración dinámica (Ya no está atado a IP fija de casa)
    private var BASE_URL = ""
    private const val COLLECTION = "videos_yaris"

    fun getApiUrl(): String = BASE_URL

    fun setApiUrl(url: String) {
        BASE_URL = url.trimEnd('/')
    }

    fun fetchServerConfig(context: android.content.Context? = null) {
        if (context == null) return
        
        // 1. Primero intentar leer la URL configurada por el usuario en preferencias
        val prefs = context.getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
        val savedServer = prefs.getString("custom_server_url", "") ?: ""
        if (savedServer.isNotEmpty()) {
            BASE_URL = savedServer.trimEnd('/')
            android.util.Log.i("NEO_PB", "🎯 Servidor cargado desde preferencias: $BASE_URL")
            return
        }

        // 2. Si no hay guardada, consultar radar dinámico en Firebase
        buscarEnFirebase(context)
    }

    private fun buscarEnFirebase(context: android.content.Context?) {
        val fbRequest = Request.Builder()
            .url("https://neomessage-19ab4-default-rtdb.firebaseio.com/server_config.json")
            .build()
        
        client.newCall(fbRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.w("NEO_PB", "Sin servidor remoto disponible. Operando en modo P2P Local.")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val data = JSONObject(response.body?.string() ?: "{}")
                    val lIp = data.optString("local_ip")
                    val pIp = data.optString("public_ip")
                    val port = data.optString("port", "8090")
                    
                    val isWifi = context?.let { NeoUtils.isWifiConnected(it) } ?: false
                    
                    if (isWifi && lIp.isNotEmpty()) {
                        BASE_URL = "http://$lIp:$port"
                        android.util.Log.i("NEO_PB", "📡 Radar Titan (Wi-Fi): Conectado localmente en $BASE_URL")
                    } else if (pIp.isNotEmpty()) {
                        val formattedIp = if (pIp.contains(":")) "[$pIp]" else pIp
                        BASE_URL = "http://$formattedIp:$port"
                        android.util.Log.i("NEO_PB", "📡 Radar Titan (Datos/WAN): Conectado vía WAN en $BASE_URL")
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Subida inteligente de archivos:
     * Si el archivo es grande (> 15 MB) o no hay servidor central,
     * se transfiere directamente vía P2P por Chunks (soporta 2 GB a 4 GB sin crasheos).
     */
    fun uploadFile(
        context: Context,
        uri: Uri,
        descripcion: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val fileSize = NeoUtils.getFileSize(context, uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val isImage = mimeType.contains("image")
        
        // Si no hay servidor central o el archivo es mayor a 15 MB, usar P2P directo
        if (BASE_URL.isEmpty() || fileSize > 15 * 1024 * 1024) {
            android.util.Log.i("NEO_PB", "Transferencia masiva ($fileSize bytes). Enrutando vía NeoP2PTransferManager.")
            NeoP2PTransferManager.startUpload(
                context = context,
                uri = uri,
                chatId = "direct",
                targetPeerId = "p2p_peer",
                onRegistered = { meta, directUrl ->
                    android.util.Log.i("NEO_PB", "Archivo registrado en nodo local: $directUrl")
                },
                onCompleted = { url, _ ->
                    onSuccess(url)
                },
                onError = { err ->
                    onError(err)
                }
            )
            return
        }

        // Para imágenes pequeñas con servidor remoto activo
        val requestBody = if (isImage) {
            val compressedBytes = NeoUtils.compressImage(context, uri, 75)
            if (compressedBytes != null) {
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("descripcion", descripcion)
                    .addFormDataPart(
                        "archivo",
                        "img_${System.currentTimeMillis()}.jpg",
                        RequestBody.create("image/jpeg".toMediaTypeOrNull(), compressedBytes)
                    )
                    .build()
            } else {
                buildMultipartFromFile(context, uri, descripcion)
            }
        } else {
            buildMultipartFromFile(context, uri, descripcion)
        }

        if (requestBody == null) return onError("Error al procesar archivo")

        val request = Request.Builder()
            .url("$BASE_URL/api/collections/$COLLECTION/records")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Fallback automático al nodo P2P local si falla la conexión
                android.util.Log.w("NEO_PB", "Fallo servidor central. Alternando a nodo P2P: ${e.message}")
                NeoP2PTransferManager.startUpload(
                    context = context,
                    uri = uri,
                    chatId = "direct",
                    targetPeerId = "p2p_peer",
                    onRegistered = { _, _ -> },
                    onCompleted = { url, _ -> onSuccess(url) },
                    onError = { err -> onError(err) }
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val id = json.getString("id")
                    val fileName = json.getString("archivo")
                    val downloadUrl = "$BASE_URL/api/files/$COLLECTION/$id/$fileName"
                    onSuccess(downloadUrl)
                } else {
                    onError("Servidor Rechazó: ${response.code}")
                }
            }
        })
    }

    private fun buildMultipartFromFile(context: Context, uri: Uri, desc: String): RequestBody? {
        val file = uriToFile(context, uri) ?: return null
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("descripcion", desc)
            .addFormDataPart("archivo", file.name, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()
    }

    private fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File(context.cacheDir, "pb_upload_${UUID.randomUUID()}")
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (_: Exception) {
            null
        }
    }

    fun sendMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        text: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        if (BASE_URL.isEmpty()) {
            onFailure("Modo P2P Activo (Sin servidor central configurado)")
            return
        }

        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("sender_id", senderId)
            put("sender_name", senderName)
            put("contenido", text)
        }
        
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$BASE_URL/api/collections/mensajes/records")
            .post(body)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("NEO_PB", "Fallo envío PocketBase: ${e.message}")
                onFailure(e.message ?: "Fallo de conexión")
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure("Error del servidor: ${response.code}")
                }
            }
        })
    }

    fun subscribeToAlerts(clientId: String) {
        if (BASE_URL.isEmpty()) return

        val subs = org.json.JSONArray()
        subs.put("alertas_yaris")
        subs.put("mensajes")

        val json = JSONObject().apply {
            put("clientId", clientId)
            put("subscriptions", subs)
        }
        
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("$BASE_URL/api/realtime")
            .post(body)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("NEO_PB", "Fallo suscripción: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    android.util.Log.i("NEO_PB", "✅ Suscripción Titán Activa")
                }
            }
        })
    }
}
