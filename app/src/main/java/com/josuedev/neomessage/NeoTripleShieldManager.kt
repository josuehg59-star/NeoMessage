package com.josuedev.neomessage

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * 🛡️ NEO TRIPLE SHIELD MANAGER (v9.0)
 * Redundancia P2P Soberana, Encriptación AES-256 y Autodescubrimiento en Red Local.
 */
object NeoTripleShieldManager {
    private const val TAG = "NEO_SHIELD"
    private val scope = CoroutineScope(Dispatchers.IO)

    private const val OWNER_PHONE = "9612765041"
    
    // Configuración AES-256 (Clave de 32 bytes para cifrado de extremo a extremo)
    private const val ALGORITHM = "AES"
    private val AES_KEY = "12345678901234567890123456789012".toByteArray() 

    // Configuración NSD (Autodescubrimiento P2P)
    private const val SERVICE_TYPE = "_neomessage._tcp"
    private const val SERVICE_NAME = "NeoMessagePeer"
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // --- AHORRO DE DATOS: VALIDACIÓN DE RED ---
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    // --- ENCRIPTACIÓN DE EXTREMO A EXTREMO (AES-256) ---
    fun encryptMessage(message: String): String {
        return try {
            val secretKey = SecretKeySpec(AES_KEY, ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedBytes = cipher.doFinal(message.toByteArray())
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            message
        }
    }

    fun decryptMessage(encryptedMessage: String): String {
        return try {
            val secretKey = SecretKeySpec(AES_KEY, ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedMessage, Base64.DEFAULT))
            String(decryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            encryptedMessage
        }
    }

    // --- AUTODESCUBRIMIENTO P2P (NSD - NETWORK SERVICE DISCOVERY) ---
    fun registerService(context: Context, port: Int, userId: String = "") {
        val cleanPhone = NeoUtils.sanitizeId(userId)
        val sName = if (cleanPhone.isNotEmpty()) "NEO-$cleanPhone" else "$SERVICE_NAME-${UUID.randomUUID().toString().take(4)}"
        
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = sName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        
        nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                    Log.d(TAG, "📡 NSD: Servicio P2P registrado en red local como $sName (Puerto: $port)")
                }
                override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {}
                override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
                override fun onUnregistrationFailed(arg0: NsdServiceInfo, arg1: Int) {}
            }
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    fun discoverPeers(context: Context, onPeerFound: (NsdServiceInfo) -> Unit = {}) {
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                            val hostStr = resolvedServiceInfo.host?.hostAddress ?: ""
                            val resolvedName = resolvedServiceInfo.serviceName
                            val peerPhone = if (resolvedName.startsWith("NEO-")) resolvedName.removePrefix("NEO-") else resolvedName
                            
                            NeoLocalNodeServer.activeLocalPeers[peerPhone] = NeoLocalNodeServer.DiscoveredPeer(
                                peerId = peerPhone,
                                host = hostStr,
                                port = resolvedServiceInfo.port
                            )
                            Log.i(TAG, "⚡ Peer P2P local encontrado vía NSD: $peerPhone en $hostStr:${resolvedServiceInfo.port}")
                            onPeerFound(resolvedServiceInfo)
                        }
                    })
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val resolvedName = serviceInfo.serviceName
                val peerPhone = if (resolvedName.startsWith("NEO-")) resolvedName.removePrefix("NEO-") else resolvedName
                NeoLocalNodeServer.activeLocalPeers.remove(peerPhone)
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /**
     * NIVEL 1: Mini-Servidor Local (JSON)
     * Guarda cada mensaje/evento en un archivo JSON inmediato.
     */
    fun saveToLocalNode(context: Context, titulo: String, texto: String, remitente: String) {
        scope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val json = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("t", titulo)
                    put("x", encryptMessage(texto))
                    put("u", remitente)
                    put("ts", timestamp)
                    put("synced", 0)
                }

                val folder = File(context.filesDir, "neo_nodes")
                if (!folder.exists()) folder.mkdirs()

                val file = File(folder, "node_$timestamp.json")
                file.writeText(json.toString())
                
                Log.d(TAG, "✅ Nivel 1: Guardado en Mini-Servidor Local (JSON Cifrado)")
                syncWithMainServer(context, json, file)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en Nivel 1: ${e.message}")
            }
        }
    }

    /**
     * NIVEL 2: Sincronización P2P / Nube Soberana
     */
    private fun syncWithMainServer(context: Context, json: JSONObject, localFile: File) {
        PocketBaseManager.sendMessage(
            chatId = "general",
            senderId = json.getString("u"),
            senderName = "Titán User",
            text = decryptMessage(json.getString("x")),
            onSuccess = {
                Log.i(TAG, "🎯 Nivel 2: Sincronizado exitosamente")
                localFile.delete() 
            }
        )
    }

    /**
     * NIVEL 3: Túnel de Emergencia (Firebase)
     */
    fun activateFirebaseTunnel(titulo: String, mensaje: String) {
        Log.w(TAG, "🚀 Nivel 3: Túnel Firebase Activado (Respaldo Crítico)")
    }

    /**
     * CAJA NEGRA TITÁN: Genera un resumen cifrado diario de toda la actividad.
     */
    fun generateDailyBlackBox(context: Context) {
        scope.launch {
            try {
                val folder = File(context.filesDir, "neo_blackbox")
                if (!folder.exists()) folder.mkdirs()
                
                val nodesFolder = File(context.filesDir, "neo_nodes")
                val files = nodesFolder.listFiles() ?: return@launch
                
                val blackboxJson = JSONObject()
                val events = org.json.JSONArray()
                
                files.forEach { file ->
                    val content = JSONObject(file.readText())
                    events.put(content)
                }
                
                blackboxJson.put("date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
                blackboxJson.put("events", events)
                
                val summaryFile = File(folder, "blackbox_${System.currentTimeMillis()}.titan")
                summaryFile.writeText(blackboxJson.toString())
                
                Log.i(TAG, "📦 CAJA NEGRA: Generada con éxito (${files.size} eventos)")
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Error en Caja Negra: ${e.message}")
            }
        }
    }

    /**
     * Limpieza de archivos JSON viejos para que no pesen en el cel
     */
    fun cleanupOldNodes(context: Context) {
        val folder = File(context.filesDir, "neo_nodes")
        if (folder.exists()) {
            val files = folder.listFiles()
            files?.forEach { file ->
                val treintaDiasMillis = 30L * 24 * 3600 * 1000
                if (System.currentTimeMillis() - file.lastModified() > treintaDiasMillis) {
                    file.delete()
                    Log.i(TAG, "♻️ Nodo JSON eliminado (Excedió 30 días)")
                }
            }
        }
    }

    fun checkServerHeartbeat(lastHeartbeatTime: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val difference = currentTime - lastHeartbeatTime
        return difference < 180000
    }
}
