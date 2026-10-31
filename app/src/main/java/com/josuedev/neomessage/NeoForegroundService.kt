package com.josuedev.neomessage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ChildEventListener
import android.content.pm.ServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class NeoForegroundService : Service() {

    private val CHANNEL_ID = "neo_service_channel_silent"
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private val notifiedMsgIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userId = intent?.getStringExtra("USER_ID") ?: ""
        val sp = getSharedPreferences("NeoPrefs", MODE_PRIVATE)
        val userPhone = sp.getString("user_phone", "") ?: ""
        val isOwner = userPhone.contains("9612765041") || userId.contains("9612765041")
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "NeoMessage::TitanWakeLock")
        if (wakeLock?.isHeld == false) wakeLock?.acquire(24 * 60 * 60 * 1000L)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "NeoMessage::TitanWifiLock")
        if (wifiLock?.isHeld == false) wifiLock?.acquire()

        createNotificationChannel()
        updateServiceNotification("🛡️ NeoNode P2P Activo | Listo para transferencias")

        // 1. Iniciar Micro-Servidor P2P embebido en este móvil
        val cleanId = NeoUtils.sanitizeId(userId)
        NeoLocalNodeServer.start(this, myPhone = cleanId)

        // 2. Monitorear progreso de transferencias masivas (2 GB - 4 GB)
        serviceScope.launch {
            NeoP2PTransferManager.transfers.collect { transferMap ->
                val active = transferMap.values.firstOrNull { it.status == NeoP2PTransferManager.TransferStatus.IN_PROGRESS }
                if (active != null) {
                    val modeText = if (active.isLocalMesh) "⚡ [Wi-Fi Local]" else "🌐 [P2P Global]"
                    updateServiceNotification("📦 $modeText ${active.percentage}% (${active.speedMbText}) - ${active.fileName}")
                }
            }
        }

        PocketBaseManager.fetchServerConfig(this)

        if (userId.isNotEmpty()) {
            startListeningForMessages(userId)
            startListeningForCalls(userId)
        }
        
        startListeningForAlerts()
        if (isOwner) {
            startListeningForFirebaseAlerts()
        }

        return START_STICKY
    }

    private fun startListeningForAlerts() {
        val apiUrl = PocketBaseManager.getApiUrl()
        if (apiUrl.isEmpty() || apiUrl.contains("0.0.0.0")) {
            return
        }

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("$apiUrl/api/realtime")
            .header("Accept", "text/event-stream")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val isWifi = NeoUtils.isWifiConnected(this@NeoForegroundService)
                if (isWifi) {
                    updateServiceNotification("⚠️ NeoNode: Reconectando... ❌")
                } else {
                    updateServiceNotification("🛡️ NeoNode: Conectado (Nube/Datos) ✅")
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ startListeningForAlerts() }, 8000)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    return
                }
                
                updateServiceNotification("🛡️ NeoNode: Conectado ✅")
                val source = response.body?.source() ?: return
                
                try {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data:")) {
                            val data = line.substring(5).trim()
                            if (data.isEmpty()) continue
                            
                            val json = JSONObject(data)
                            val record = json.optJSONObject("record") ?: continue
                            val collection = json.optString("collectionName")

                            if (collection == "alertas_yaris") {
                                procesarAlerta(record)
                            } else if (collection == "mensajes") {
                                procesarMensaje(record)
                            }
                        }
                    }
                } catch (_: Exception) {
                    updateServiceNotification("⚠️ Conexión interrumpida")
                } finally {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ startListeningForAlerts() }, 3000)
                }
            }
        })
    }

    private fun procesarAlerta(record: JSONObject) {
        val sp = getSharedPreferences("NeoPrefs", MODE_PRIVATE)
        val userPhone = sp.getString("user_phone", "") ?: ""
        
        if (userPhone.contains("9612765041")) {
            val createdStr = record.optString("created", "")
            var isFresh = true
            try {
                val cleanDate = createdStr.replace("Z", "").replace("T", " ")
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val createdDate = sdf.parse(cleanDate)
                if (createdDate != null && (System.currentTimeMillis() - createdDate.time) > 90000) {
                    isFresh = false
                }
            } catch (_: Exception) {}

            val titulo = record.optString("titulo", "")
            val mensaje = record.optString("mensaje", "")
            
            if (mensaje.contains("TRANSEÚNTE", ignoreCase = true)) {
                android.util.Log.d("NEO_TITAN", "🤫 Pasó un transeúnte: Registro en silencio.")
                return
            }

            if (isFresh) {
                if (titulo.contains("CRÍTICA", ignoreCase = true)) {
                    NeoNotificationHelper.dispararAlarmaFuerte(this)
                }
                
                NeoNotificationHelper.showNotification(
                    this,
                    titulo,
                    mensaje,
                    record.optString("event_id", record.optString("id")),
                    record.optString("video_url")
                )
            }
        }
    }

    private fun procesarMensaje(record: JSONObject) {
        val sp = getSharedPreferences("NeoPrefs", MODE_PRIVATE)
        val myPhone = sp.getString("user_phone", "") ?: ""
        if (record.optString("sender_id") != myPhone) {
            NeoNotificationHelper.showNotification(
                this,
                "💬 ${record.optString("sender_name")}",
                record.optString("contenido"),
                record.optString("id")
            )
        }
    }

    private fun updateServiceNotification(status: String) {
        val sp = getSharedPreferences("NeoPrefs", MODE_PRIVATE)
        val userPhone = sp.getString("user_phone", "") ?: ""
        val isOwner = userPhone.contains("9612765041")

        val cleanTitle = if (isOwner) "NeoMessage Soberano" else "NeoMessage P2P"
        val cleanStatus = if (isOwner) {
            status
        } else {
            status.replace("TITÁN: Vigilando", "Mensajería Activa")
                  .replace("TITÁN: Reconectando", "Reconectando")
                  .replace("TITÁN: Conectado", "Conectado")
                  .replace("🛡️ TITÁN:", "NeoMessage:")
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(cleanTitle)
            .setContentText(cleanStatus)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startListeningForMessages(userId: String) {
        val cleanMyId = NeoUtils.sanitizeId(userId)
        val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
        val chatsRef = db.getReference("chats")
        chatsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { chat ->
                    val chatId = chat.key ?: ""
                    if (chatId.contains(cleanMyId) && cleanMyId.isNotEmpty()) {
                        val messages = chat.child("messages")
                        val lastMsg = messages.children.mapNotNull { it.getValue(CloudMessage::class.java) }.maxByOrNull { it.timestamp }
                        val lastSenderClean = if (lastMsg != null) NeoUtils.sanitizeId(lastMsg.senderId) else ""
                        if (lastMsg != null && lastSenderClean.takeLast(10) != cleanMyId.takeLast(10) && !notifiedMsgIds.contains(lastMsg.id)) {
                            notifiedMsgIds.add(lastMsg.id)

                            // Auto-respuesta con NeoBot 24/7 incluso con pantalla apagada
                            NeoBotManager.dispatchAutoReplyIfNeeded(
                                context = this@NeoForegroundService,
                                chatId = chatId,
                                senderId = lastMsg.senderId,
                                myId = cleanMyId,
                                incomingText = lastMsg.text,
                                msgTimestamp = lastMsg.timestamp
                            )

                            if (Math.abs(System.currentTimeMillis() - lastMsg.timestamp) < 30 * 60 * 1000L) {
                                NeoNotificationHelper.showNotification(this@NeoForegroundService, "Nuevo mensaje de ${lastMsg.senderName}", lastMsg.text, lastMsg.id)
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Escucha en segundo plano desde el Relay Soberano ($0.00 costo)
        serviceScope.launch {
            NeoRelayClient.incomingMessages.collect { (relChatId, relMsg) ->
                val cleanSender = NeoUtils.sanitizeId(relMsg.senderId)
                if (cleanSender.takeLast(10) != cleanMyId.takeLast(10) && !notifiedMsgIds.contains(relMsg.id)) {
                    notifiedMsgIds.add(relMsg.id)
                    NeoBotManager.dispatchAutoReplyIfNeeded(
                        context = this@NeoForegroundService,
                        chatId = relChatId,
                        senderId = relMsg.senderId,
                        myId = cleanMyId,
                        incomingText = relMsg.text,
                        msgTimestamp = relMsg.timestamp
                    )
                    if (relMsg.timestamp > System.currentTimeMillis() - 60000) {
                        NeoNotificationHelper.showNotification(this@NeoForegroundService, "Nuevo mensaje de ${relMsg.senderName}", relMsg.text, relMsg.id)
                    }
                }
            }
        }
    }

    private fun startListeningForCalls(userId: String) {
        val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
        val cleanId = NeoUtils.sanitizeId(userId)
        val target = if (cleanId.isNotEmpty()) cleanId else userId
        val callsRef = db.getReference("calls").child(target)
        callsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val incoming = snapshot.child("callerName").getValue(String::class.java)
                if (incoming != null) {
                    NeoNotificationHelper.showCallNotification(this@NeoForegroundService, incoming)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "NeoMessage Service", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startListeningForFirebaseAlerts() {
        try {
            val db = FirebaseDatabase.getInstance("https://neomessage-19ab4-default-rtdb.firebaseio.com")
            val alertsRef = db.getReference("alerts")
            val query = alertsRef.orderByChild("time").startAt(System.currentTimeMillis().toDouble())
            query.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val title = snapshot.child("title").getValue(String::class.java) ?: ""
                    val message = snapshot.child("message").getValue(String::class.java) ?: ""
                    val eventId = snapshot.key ?: ""

                    val sp = getSharedPreferences("NeoPrefs", MODE_PRIVATE)
                    val userPhone = sp.getString("user_phone", "") ?: ""
                    
                    if (userPhone.contains("9612765041")) {
                        if (message.contains("TRANSEÚNTE", ignoreCase = true)) {
                            android.util.Log.d("NEO_TITAN", "🤫 Pasó un transeúnte por Firebase: Registro en silencio.")
                            return
                        }

                        if (title.contains("CRÍTICA", ignoreCase = true)) {
                            NeoNotificationHelper.dispararAlarmaFuerte(this@NeoForegroundService)
                        }
                        
                        NeoNotificationHelper.showNotification(
                            this@NeoForegroundService,
                            title,
                            message,
                            eventId,
                            ""
                        )
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
            android.util.Log.i("NEO_FB_ALERTS", "📡 Escudo RTDB: Escuchando alertas en tiempo real en la nube...")
        } catch (e: Exception) {
            android.util.Log.e("NEO_FB_ALERTS", "❌ Error al iniciar escucha de alertas Firebase: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        NeoLocalNodeServer.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }
}
