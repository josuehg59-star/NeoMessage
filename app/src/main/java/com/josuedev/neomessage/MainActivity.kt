package com.josuedev.neomessage



import android.app.NotificationChannel

import android.app.NotificationManager

import android.content.Context

import android.view.WindowManager

import android.os.Build

import android.os.Bundle

import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.compose.setContent

import androidx.activity.result.contract.ActivityResultContracts

import androidx.biometric.BiometricPrompt

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.ui.draw.clip

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*

import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat

import androidx.fragment.app.FragmentActivity

import com.google.firebase.FirebaseApp

import com.google.firebase.auth.FirebaseAuth

import com.google.firebase.database.ServerValue

import com.google.firebase.messaging.FirebaseMessaging



// --- ESTADO GLOBAL DE NEOMESSAGE ---

data class NeoState(

    val myName: String = "",

    val userId: String = "",

    val currentScreen: Screen = Screen.Login,

    val activeChatName: String = "",

    val activeChatId: String = "",

    val activeCallId: String = "",

    val isAppUnlocked: Boolean = true,

    val isCallVideo: Boolean = false

)



data class IncomingCall(

    val callerName: String,

    val callerId: String,

    val channelId: String,

    val isVideo: Boolean

)



class MainActivity : FragmentActivity() {

    private val videoUrlState = mutableStateOf("")



    override fun onNewIntent(intent: android.content.Intent) {

        super.onNewIntent(intent)

        intent.getStringExtra("VIDEO_URL")?.let {

            videoUrlState.value = it

        }

    }



    override fun onPause() {
        super.onPause()
        try { NeoBackupManager.autoBackupLatest(this) } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        try { NeoBackupManager.autoRestoreIfEmpty(this) } catch (_: Exception) {}

        

        intent?.getStringExtra("VIDEO_URL")?.let {

            videoUrlState.value = it

        }

        

        try {

            FirebaseApp.initializeApp(this)

            // El servidor ahora maneja las suscripciones de forma privada

            

            FirebaseAuth.getInstance().signInAnonymously()

                .addOnSuccessListener { android.util.Log.d("NEO_AUTH", "Protocolo de Acceso: ANON_GRANTED") }

                .addOnFailureListener { e -> android.util.Log.e("NEO_AUTH", "Falla en Acceso: ", e) }

        } catch (e: Exception) {

            android.util.Log.e("NEO_ERROR", "Error de Firebase: ${e.message}")

        }



        // --- RADAR DE ERRORES TITÁN (LOGUEO PASIVO) ---

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->

            try {

                android.util.Log.e("NEO_CRASH", "FALLA CRÍTICA DETECTADA: ${throwable.message}")

                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)

                val crashRef = db.getReference("crashes").push()

                crashRef.setValue(mapOf(

                    "error" to throwable.message,

                    "trace" to throwable.stackTraceToString(),

                    "device" to android.os.Build.MODEL,

                    "version" to android.os.Build.VERSION.RELEASE,

                    "time" to com.google.firebase.database.ServerValue.TIMESTAMP

                ))

            } catch (ignored: Exception) {}

            android.os.Process.killProcess(android.os.Process.myPid())

            java.util.concurrent.TimeUnit.SECONDS.sleep(1)

        }







        createNotificationChannel()



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {

            setShowWhenLocked(true)

            setTurnScreenOn(true)

        }

        @Suppress("DEPRECATION")

        window.addFlags(

            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or

            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or

            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or

            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON

        )



        val sp = getSharedPreferences("NeoPrefs", MODE_PRIVATE)

        val rawPhone = sp.getString("user_phone", "") ?: ""

        val savedPhone = NeoUtils.sanitizeId(rawPhone)

        val savedName = sp.getString("user_name", "") ?: ""

        val needsBio = sp.getBoolean("biometric_lock", false)



        // Sincronizar estado real del bot con Firebase (inactivo por defecto)

        val cleanPhone10 = savedPhone?.takeLast(10) ?: ""

        if (cleanPhone10.isNotEmpty()) {

            try {

                com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)

                    .getReference("bot_configs").child(cleanPhone10).child("isEnabled")

                    .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {

                        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {

                            val enabled = snapshot.getValue(Boolean::class.java) ?: false

                            sp.edit().putBoolean("bot_sales_active", enabled).apply()

                            NeoBotManager.getPrefs(this@MainActivity).edit().putBoolean("bot_active_switch", enabled).apply()

                        }

                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}

                    })

            } catch (_: Exception) {}

        }



        checkPermissions()

        

        // --- MOTOR DE AUTENTICACIÓN NEO (GLOBAL) ---

        FirebaseAuth.getInstance().signInAnonymously()

        

        // --- SUSCRIPCIÓN PRIVADA TITÁN (SOLO PARA EL DUEÑO) ---

        if (savedPhone?.contains("9612765041") == true) {

            FirebaseMessaging.getInstance().subscribeToTopic("vigilancia_9612765041")

                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {

                        android.util.Log.d("NEO_AUTH", "Seguridad Yaris: PRIVADA ACTIVADA 🛡️")

                        Toast.makeText(this, "🛡️ SEGURIDAD TITÁN: PRIVADA ACTIVADA", Toast.LENGTH_LONG).show()

                    }

                }

        } else {

            // Desuscribir a cualquier otro usuario del canal de seguridad

            FirebaseMessaging.getInstance().unsubscribeFromTopic("vigilancia_9612765041")

            FirebaseMessaging.getInstance().unsubscribeFromTopic("yaris_alerts")

        }

        

        // --- ACTIVAR RADAR TITÁN ---

        PocketBaseManager.fetchServerConfig(this)

        

        setContent {

            NeoMessageApp(savedPhone, savedName, needsBio, videoUrlState)

        }

    }



    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val nm = getSystemService(NotificationManager::class.java) ?: return

            

            // Canal para Mensajes

            val msgChannel = NotificationChannel("neo_messages", "NeoMessage: Mensajes", NotificationManager.IMPORTANCE_HIGH).apply {

                description = "Protocolo de Mensajería"

                enableLights(true)

                lightColor = android.graphics.Color.GREEN

                enableVibration(true)

                setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION), null)

            }

            nm.createNotificationChannel(msgChannel)



            // Canal para LLAMADAS (Prioridad Crítica)

            val callChannel = NotificationChannel("neo_calls", "NeoMessage: Llamadas", NotificationManager.IMPORTANCE_HIGH).apply {

                description = "Protocolo de Comunicación de Voz"

                enableLights(true)

                lightColor = android.graphics.Color.RED

                enableVibration(true)

                setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE), null)

                // Esto hace que la notificación aparezca en la pantalla de bloqueo de forma prominente

                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                    setAllowBubbles(true)

                }

            }

            nm.createNotificationChannel(callChannel)

        }

    }



    private fun checkPermissions() {

        val permissions = mutableListOf(

            android.Manifest.permission.RECORD_AUDIO,

            android.Manifest.permission.CAMERA,

            android.Manifest.permission.READ_CONTACTS,

            android.Manifest.permission.ACCESS_FINE_LOCATION,

            android.Manifest.permission.ACCESS_COARSE_LOCATION

        )

        

        // Notificaciones y Media en Android 13+

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)

            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)

            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)

        } else {

            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)

            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

        }

        

        val toRequest = permissions.filter { 

            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED 

        }

        

        if (toRequest.isNotEmpty()) {

            requestPermissions(toRequest.toTypedArray(), 101)

        }



        // --- ESCUDO ANTI-CIERRE PARA OPPO / XIAOMI / SAMSUNG ---

        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {

                try {

                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {

                        data = android.net.Uri.parse("package:$packageName")

                    }

                    startActivity(intent)

                } catch (e: Exception) {}

            }

        }

    }

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun NeoMessageApp(savedPhone: String, savedName: String, needsBio: Boolean, videoUrlState: MutableState<String>) {

    val context = androidx.compose.ui.platform.LocalContext.current

    val sp = remember { context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE) }



    // --- SOLICITADOR DE PERMISOS MAESTRO ---

    val permissionLauncher = rememberLauncherForActivityResult(

        ActivityResultContracts.RequestMultiplePermissions()

    ) { perms ->

        val allGranted = perms.values.all { it }

        if (!allGranted) {

            Toast.makeText(context, "Protocolo NeoMessage: Se requieren permisos para archivos y llamadas.", Toast.LENGTH_LONG).show()

        }

    }

    

    LaunchedEffect(Unit) {

        val permissions = mutableListOf(

            android.Manifest.permission.CAMERA,

            android.Manifest.permission.RECORD_AUDIO,

            android.Manifest.permission.READ_CONTACTS,

            android.Manifest.permission.ACCESS_FINE_LOCATION,

            android.Manifest.permission.ACCESS_COARSE_LOCATION

        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {

            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)

            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)

            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)

        } else {

            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)

            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

        }

        permissionLauncher.launch(permissions.toTypedArray())



        // --- SISTEMA DE SEGURIDAD TITÁN SINCRONIZADO ---

        // Se eliminó el detector de RTDB para evitar avalanchas de mensajes viejos.

        // Ahora todo funciona por Notificaciones Push (FCM) con caducidad de 1 minuto.

    }

    

    val currentTheme = remember { mutableStateOf(sp.getString("neo_theme", "Nuclear") ?: "Nuclear") }

    

    val bgColor = when(currentTheme.value) {

        "Nuclear" -> Color(0xFF020617)

        "Matrix" -> Color(0xFF001A00)

        "Galactic" -> Color(0xFF0F172A)

        "Titan" -> Color(0xFF1E1E1E)

        else -> Color(0xFF0F172A)

    }



    val accentColor = when(currentTheme.value) {

        "Nuclear" -> Color(0xFF00FF9C)

        "Matrix" -> Color(0xFF00FF41)

        "Galactic" -> Color(0xFF00BFFF)

        "Titan" -> Color(0xFFFFCC00)

        else -> NeoTheme.Green

    }



    val surfaceColor = Color(0xFF111827)

    val textColor = Color.White

    val textSubColor = Color.LightGray

    val accentGradient = Brush.linearGradient(listOf(accentColor, accentColor.copy(0.7f)))

    var currentRingtone by remember { mutableStateOf<android.media.Ringtone?>(null) }

    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    var incomingCallInfo by remember { mutableStateOf<IncomingCall?>(null) }



    LaunchedEffect(Unit) {

        NeoUpdateManager.checkForUpdates(context) { info ->

            availableUpdate = info

        }

    }



    var isLoginLoading by remember { mutableStateOf(false) }

    val currentProjectId = "neomessage-19ab4"

    val savedProjectId = sp.getString("active_project_id", "") ?: ""



    var state by remember { 

        if (savedProjectId != currentProjectId) {

            sp.edit().putString("active_project_id", currentProjectId).putBoolean("is_neo_unified", true).apply()

        }

        val isLogged = savedName.isNotEmpty() && savedPhone.isNotEmpty()

        

        mutableStateOf(NeoState(

            myName = savedName,

            userId = savedPhone,

            currentScreen = if (isLogged) Screen.ChatsList else Screen.Login,

            isAppUnlocked = !needsBio

        )) 

    }



    // --- ACCIÓN MAESTRA PARA CAMBIAR TEMA ---

    val updateTheme = { newTheme: String ->

        currentTheme.value = newTheme

        sp.edit().putString("neo_theme", newTheme).apply()

    }



    fun authenticate() {

        val bm = androidx.biometric.BiometricManager.from(context)

        val canBio = bm.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

        if (!canBio) {

            // El teléfono no tiene sensor de huellas o no está registrado: evitar excepciones

            return

        }

        val activity = context as? FragmentActivity ?: return

        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(activity, executor,

            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {

                    state = state.copy(isAppUnlocked = true)

                }

            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()

            .setTitle("NeoMessage Lock")

            .setSubtitle("Autenticación biométrica requerida")

            .setNegativeButtonText("Usar PIN")

            .build()

        biometricPrompt.authenticate(promptInfo)

    }



    LaunchedEffect(state.userId) {

        if (state.userId.isEmpty()) return@LaunchedEffect

        

        // --- PROTECCIÓN ANTI-SUPLANTACIÓN: VINCULACIÓN DE HARDWARE FÍSICO ---
        try {
            val myCurrentDeviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
            val myCurrentDeviceModel = "${android.os.Build.MANUFACTURER.uppercase()} ${android.os.Build.MODEL}".trim()
            val dbCheck = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            val uRef = dbCheck.getReference("users").child(state.userId)
            uRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val regDevId = snapshot.child("deviceId").getValue(String::class.java)
                    if (regDevId.isNullOrBlank()) {
                        // Enlazar automáticamente este teléfono físico a la cuenta activa
                        uRef.child("deviceId").setValue(myCurrentDeviceId)
                        uRef.child("deviceModel").setValue(myCurrentDeviceModel)
                        sp.edit().putString("device_id", myCurrentDeviceId).putString("device_model", myCurrentDeviceModel).apply()
                    } else if (regDevId != myCurrentDeviceId && myCurrentDeviceId.isNotBlank()) {
                        // El número fue transferido o se abrió en otro teléfono: cerrar sesión por seguridad
                        Toast.makeText(context, "⚠️ Tu número fue transferido a otro teléfono físico. Sesión cerrada por seguridad.", Toast.LENGTH_LONG).show()
                        sp.edit().clear().apply()
                        state = NeoState(currentScreen = Screen.Login)
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        } catch (_: Exception) {}

        // Iniciar Micro-Servidor P2P y Baliza UDP inmediatamente al abrir la app
        NeoLocalNodeServer.start(context, myPhone = state.userId)

        

        if (!state.isAppUnlocked && state.currentScreen != Screen.Login) authenticate()

        

        val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)

        val userRef = db.getReference("users").child(state.userId)

        val currentLocalSession = sp.getString("last_session_id", "") ?: ""

        

        val userData = mapOf(
            "id" to state.userId, 
            "name" to state.myName, 
            "online" to true,
            "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP, 
            "status" to "Conectado"
        )
        userRef.updateChildren(userData)

        db.getReference(".info/connected").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    userRef.child("online").setValue(true)
                    userRef.child("status").setValue("Conectado")
                    userRef.child("lastSeen").setValue(com.google.firebase.database.ServerValue.TIMESTAMP)

                    userRef.child("online").onDisconnect().setValue(false)
                    userRef.child("status").onDisconnect().setValue("Desconectado")
                    userRef.child("lastSeen").onDisconnect().setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })



        // --- GUARDIÁN DE SESIÓN MAESTRO ---

        userRef.child("sessionId").addValueEventListener(object : com.google.firebase.database.ValueEventListener {

            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {

                val serverSession = snapshot.getValue(String::class.java)

                if (serverSession != null) {

                    sp.edit().putString("last_session_id", serverSession).apply()

                }

            }

            override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}

        })

        

        // --- LANZAR SERVICIO PERSISTENTE (ANTIKILL OPPO/HUAWEI) ---

        val serviceIntent = android.content.Intent(context, NeoForegroundService::class.java).apply {

            putExtra("USER_ID", state.userId)

        }

        if (state.userId.isNotEmpty()) {

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

                context.startForegroundService(serviceIntent)

            } else {

                context.startService(serviceIntent)

            }

            // Conexión soberana a NeoRelay ($0.00 costo)

            NeoRelayClient.connect(context, state.userId)

        }



        // --- AHORRO DE DATOS EXTREMO ---

        // Desconectar Firebase si la app no está visible para ahorrar megas

        val dbInstance = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)

        

        // --- DETECTOR LIGERO DE AVISOS (INBOX < 200 BYTES, CERO DESCARGA MASIVA DE CHATS) ---
        val notifiedMsgIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val myInboxKey = NeoUtils.sanitizeId(state.userId).takeLast(10)
        if (myInboxKey.length == 10) {
            val inboxRef = db.getReference("inbox").child(myInboxKey)
            inboxRef.addChildEventListener(object : com.google.firebase.database.ChildEventListener {
                override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                    try {
                        val msgId = snapshot.child("id").getValue(String::class.java) ?: snapshot.key ?: return
                        val chatId = snapshot.child("chatId").getValue(String::class.java) ?: ""
                        val senderId = snapshot.child("senderId").getValue(String::class.java) ?: ""
                        val senderName = snapshot.child("senderName").getValue(String::class.java) ?: senderId
                        val textPreview = snapshot.child("text").getValue(String::class.java) ?: "Nuevo mensaje"
                        val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                        val cleanMyId = NeoUtils.sanitizeId(state.userId)
                        val lastSenderClean = NeoUtils.sanitizeId(senderId)

                        // Borrar el aviso de inmediato en Firebase para que nunca ocupe espacio
                        snapshot.ref.removeValue()

                        // Respaldar el mensaje completo en SQLite local y limpiarlo de Firebase
                        if (chatId.isNotEmpty() && msgId.isNotEmpty()) {
                            db.getReference("chats").child(chatId).child("messages").child(msgId).get()
                                .addOnSuccessListener { msgSnap ->
                                    val fullMsg = msgSnap.getValue(CloudMessage::class.java)
                                    if (fullMsg != null) {
                                        Thread {
                                            try {
                                                kotlinx.coroutines.runBlocking { AppDatabase.getDatabase(context).messageDao().insertMessage(fullMsg.toEntity(chatId)) }
                                            } catch (_: Exception) {}
                                        }.start()
                                    }
                                }
                        }

                        if (lastSenderClean.takeLast(10) != cleanMyId.takeLast(10) && !notifiedMsgIds.contains(msgId) && textPreview != "[DELETED]" && !textPreview.contains("Este mensaje fue eliminado")) {
                            notifiedMsgIds.add(msgId)
                            NeoBotManager.dispatchAutoReplyIfNeeded(
                                context = context,
                                chatId = chatId,
                                senderId = senderId,
                                myId = cleanMyId,
                                incomingText = textPreview,
                                msgTimestamp = ts,
                                msgId = msgId
                            )
                            if (ts > System.currentTimeMillis() - 120000) {
                                if (state.currentScreen != Screen.ChatRoom || state.activeChatId != chatId) {
                                    if (!textPreview.contains("TRANSEÚNTE", ignoreCase = true)) {
                                        NeoNotificationHelper.showNotification(
                                            context,
                                            "Nuevo mensaje de $senderName",
                                            textPreview,
                                            msgId, "", chatId, senderId
                                        )
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {}
                override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }



        // --- SINCRONIZACIÓN PC ⇄ CELULAR: mensajes enviados desde otro dispositivo de MI cuenta ---
        val mySyncKey = NeoUtils.sanitizeId(state.userId).takeLast(10)
        if (mySyncKey.length == 10) {
            db.getReference("sync").child(mySyncKey).addChildEventListener(object : com.google.firebase.database.ChildEventListener {
                override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                    try {
                        val origin = snapshot.child("origin").getValue(String::class.java) ?: ""
                        val syncMsg = snapshot.child("msg").getValue(CloudMessage::class.java)
                        val syncChatId = snapshot.child("chatId").getValue(String::class.java) ?: ""
                        if (syncMsg == null || syncChatId.isEmpty()) { snapshot.ref.removeValue(); return }
                        if (origin == "app") {
                            if (System.currentTimeMillis() - syncMsg.timestamp > 3L * 24 * 3600 * 1000) snapshot.ref.removeValue()
                            return
                        }
                        Thread {
                            try {
                                kotlinx.coroutines.runBlocking {
                                    AppDatabase.getDatabase(context).messageDao().insertMessage(syncMsg.toEntity(syncChatId))
                                }
                            } catch (_: Exception) {}
                        }.start()
                        snapshot.ref.removeValue()
                    } catch (_: Exception) {}
                }
                override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {}
                override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }

        // --- DETECTOR DE LLAMADAS ENTRANTES ---

        val cleanMyPhone = NeoUtils.sanitizeId(state.userId)

        val blockedUsersList = java.util.concurrent.CopyOnWriteArrayList<String>()
        val currentBlockedNode = if (cleanMyPhone.isNotEmpty()) cleanMyPhone else state.userId
        if (currentBlockedNode.isNotEmpty()) {
            db.getReference("blocked").child(currentBlockedNode).addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snap: com.google.firebase.database.DataSnapshot) {
                    blockedUsersList.clear()
                    snap.children.forEach { child -> child.key?.let { blockedUsersList.add(it) } }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }

        val callTargetNode = if (cleanMyPhone.isNotEmpty()) cleanMyPhone else state.userId

        val callsRef = db.getReference("calls").child(callTargetNode)

        callsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {

            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {

                val incoming = snapshot.child("callerName").getValue(String::class.java)

                val chId = snapshot.child("channelId").getValue(String::class.java) ?: ""

                val cId = snapshot.child("callerId").getValue(String::class.java) ?: ""

                val isVideo = snapshot.child("isVideo").getValue(Boolean::class.java) ?: false

                if (incoming != null) {

                    val cleanCaller = NeoUtils.sanitizeId(cId)

                    if (cleanCaller.isNotEmpty() && cleanMyPhone.isNotEmpty() && cleanCaller == cleanMyPhone) {
                        return
                    }

                    // BLOQUEO TOTAL EN LLAMADAS Y VIDEOLLAMADAS (NO TIMBRAR NI PERMITIR ENTRADA)
                    val isBlockedCaller = cleanCaller.isNotEmpty() && (
                        blockedUsersList.contains(cleanCaller) || 
                        blockedUsersList.any { it.takeLast(10) == cleanCaller.takeLast(10) }
                    )
                    if (isBlockedCaller) {
                        try { snapshot.ref.removeValue() } catch (_: Exception) {}
                        return
                    }

                    if (state.currentScreen != Screen.Calling) {

                        NeoNotificationHelper.showCallNotification(context, incoming)

                        if (currentRingtone == null) {

                            try {

                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

                                audioManager.mode = android.media.AudioManager.MODE_RINGTONE

                                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)

                                currentRingtone = android.media.RingtoneManager.getRingtone(context, uri)

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                                    currentRingtone?.isLooping = true

                                }

                                currentRingtone?.play()

                            } catch (_: Exception) {}

                        }

                        // Registrar llamada entrante para contestar o rechazar

                        incomingCallInfo = IncomingCall(incoming, cId, chId, isVideo)

                    }

                } else {

                    incomingCallInfo = null

                    NeoNotificationHelper.cancelCallNotification(context)

                    currentRingtone?.stop()

                    currentRingtone = null

                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

                    audioManager.mode = android.media.AudioManager.MODE_NORMAL

                }

            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}

        })

    }



    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            if (state.userId.isNotBlank()) {
                val uRef = db.getReference("users").child(state.userId)
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                    uRef.child("online").setValue(false)
                    uRef.child("status").setValue("Desconectado")
                    uRef.child("lastSeen").setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    NeoUpdateManager.resumePendingInstallIfAny(context)
                    com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).goOnline()
                    uRef.child("online").setValue(true)
                    uRef.child("status").setValue("Conectado")
                    uRef.child("lastSeen").setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }

    }



    MaterialTheme(colorScheme = darkColorScheme()) {

        Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {

            if (!state.isAppUnlocked && state.currentScreen != Screen.Login) {

                LockScreen(onUnlock = { authenticate() })

            } else {

                var backPressedTime by remember { mutableLongStateOf(0L) }



                androidx.activity.compose.BackHandler(enabled = state.currentScreen != Screen.Login) {

                    when (state.currentScreen) {

                        Screen.ChatRoom,

                        Screen.Contacts,

                        Screen.Status,

                        Screen.Communities,

                        Screen.HistoryCalls,

                        Screen.NeoAI,

                        Screen.Settings,

                        Screen.StarredMessages,

                        Screen.HelpMenu,

                        Screen.LinkedDevices,

                        Screen.CorteCaja -> {

                            state = state.copy(currentScreen = Screen.ChatsList)

                        }

                        Screen.Account,

                        Screen.Privacy,

                        Screen.ChatsSettings,

                        Screen.NotificationsSettings,

                        Screen.StorageSettings,

                        Screen.Avatar,

                        Screen.Language,

                        Screen.Accessibility,

                        Screen.InviteFriends,

                        Screen.BroadcastsSettings,

                        Screen.BusinessTools,

                        Screen.Profile,

                        Screen.ListsSettings,

                        Screen.Vault -> {

                            state = state.copy(currentScreen = Screen.Settings)

                        }

                        Screen.Security,

                        Screen.TwoStepVerification,

                        Screen.ChangeNumber -> {

                            state = state.copy(currentScreen = Screen.Account)

                        }

                        Screen.BlockedContacts,

                        Screen.AppLock -> {

                            state = state.copy(currentScreen = Screen.Privacy)

                        }

                        Screen.Calling -> {

                            state = state.copy(currentScreen = Screen.ChatsList)

                        }

                        Screen.ChatsList -> {

                            val now = System.currentTimeMillis()

                            if (now - backPressedTime < 2000L) {

                                (context as? android.app.Activity)?.finish()

                            } else {

                                backPressedTime = now

                                Toast.makeText(context, "Presiona atrás de nuevo para salir", Toast.LENGTH_SHORT).show()

                            }

                        }

                        else -> {

                            state = state.copy(currentScreen = Screen.ChatsList)

                        }

                    }

                }



                when (state.currentScreen) {

                    Screen.Login -> LoginView(

                        onLogin = { n, p, pin ->

                            val cleanP = NeoUtils.sanitizeId(p)

                            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

                            isLoginLoading = true

                            

                            // Aseguramos que tenemos sesión antes de escribir

                            if (auth.currentUser == null) {

                                auth.signInAnonymously().addOnCompleteListener { authTask ->

                                    if (authTask.isSuccessful) {

                                        performNeoLogin(n, cleanP, pin, sp, state, { state = it }, context, { isLoginLoading = it })

                                    } else {

                                        performNeoLogin(n, cleanP, pin, sp, state, { state = it }, context, { isLoginLoading = it })

                                    }

                                }

                            } else {

                                performNeoLogin(n, cleanP, pin, sp, state, { state = it }, context, { isLoginLoading = it })

                            }

                        },

                        bgColor = bgColor, textColor = textColor,

                        isLoading = isLoginLoading

                    )

                    Screen.ChatsList -> ChatsListScreen(

                        userId = state.userId,

                        onNavigateToSettings = { state = state.copy(currentScreen = Screen.Settings) },

                        onNavigateToChat = { name, id ->

                            if (id == "neo_ai_chat") {

                                state = state.copy(currentScreen = Screen.NeoAI)

                            } else {

                                state = state.copy(activeChatName = name, activeChatId = id, currentScreen = Screen.ChatRoom)

                            }

                        },

                        onNavigateToStatus = { state = state.copy(currentScreen = Screen.Status) },

                        onNavigateToCalls = { state = state.copy(currentScreen = Screen.HistoryCalls) },

                        onNavigateToCommunities = { state = state.copy(currentScreen = Screen.Communities) },

                        onNavigateToContacts = { state = state.copy(currentScreen = Screen.Contacts) },

                        onNavigateToLinkedDevices = { state = state.copy(currentScreen = Screen.LinkedDevices) },

                        onNavigateToStarred = { state = state.copy(currentScreen = Screen.StarredMessages) },

                        onNavigateToHelp = { state = state.copy(currentScreen = Screen.HelpMenu) },

                        onNavigateToBroadcasts = { state = state.copy(currentScreen = Screen.BroadcastsSettings) },

                        onNavigateToAi = { state = state.copy(currentScreen = Screen.NeoAI) },

                        textColor = textColor, accentGradient = accentGradient,

                        onNavigateToCorteCaja = { state = state.copy(currentScreen = Screen.CorteCaja) }

                    )

                    Screen.ChatRoom -> ChatRoomScreen(

                        chatId = state.activeChatId,

                        chatName = state.activeChatName,

                        myName = state.myName,

                        userId = state.userId,

                        bgColor = bgColor, surfaceColor = surfaceColor,

                        textColor = textColor, textSubColor = textSubColor,

                        accentGradient = accentGradient,

                        onBack = { state = state.copy(currentScreen = Screen.ChatsList) },

                        onNavigateToChat = { targetName, targetId ->

                            state = state.copy(activeChatName = targetName, activeChatId = targetId, currentScreen = Screen.ChatRoom)

                        },

                        onNavigateToCalling = { otherId, isVideo -> 

                            val cleanMe = NeoUtils.sanitizeId(state.userId)

                            val cleanOther = NeoUtils.sanitizeId(otherId)

                            val callChannel = if (cleanMe.isNotEmpty() && cleanOther.isNotEmpty()) NeoUtils.getChatId(cleanMe, cleanOther) else "call_${otherId}"

                            state = state.copy(activeChatName = state.activeChatName, activeCallId = callChannel, currentScreen = Screen.Calling, isCallVideo = isVideo)

                            val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)

                            val callData = mapOf(

                                "callerName" to state.myName,

                                "callerId" to state.userId,

                                "channelId" to callChannel,

                                "isVideo" to isVideo,

                                "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP

                            )

                            if (cleanOther.isNotEmpty()) {

                                db.getReference("calls").child(cleanOther).setValue(callData)

                            }

                            if (otherId.isNotEmpty() && otherId != cleanOther) {

                                db.getReference("calls").child(otherId).setValue(callData)

                            }

                        }

                    )

                    Screen.Settings -> SettingsScreen(

                        myName = state.myName, userId = state.userId,

                        onBack = { state = state.copy(currentScreen = Screen.ChatsList) },

                        onNavigateToLinkedDevices = { state = state.copy(currentScreen = Screen.LinkedDevices) },

                        onNavigateToStarred = { state = state.copy(currentScreen = Screen.StarredMessages) },

                        onNavigateToHelp = { state = state.copy(currentScreen = Screen.HelpMenu) },

                        onNavigateToVault = { state = state.copy(currentScreen = Screen.Vault) },

                        onNavigateToAccount = { state = state.copy(currentScreen = Screen.Account) },

                        onNavigateToPrivacy = { state = state.copy(currentScreen = Screen.Privacy) },

                        onNavigateToChats = { state = state.copy(currentScreen = Screen.ChatsSettings) },

                        onNavigateToNotifications = { state = state.copy(currentScreen = Screen.NotificationsSettings) },

                        onNavigateToStorage = { state = state.copy(currentScreen = Screen.StorageSettings) },

                        onNavigateToAvatar = { state = state.copy(currentScreen = Screen.Avatar) },

                        onNavigateToLanguage = { state = state.copy(currentScreen = Screen.Language) },

                        onNavigateToAccessibility = { state = state.copy(currentScreen = Screen.Accessibility) },

                        onNavigateToInvite = { state = state.copy(currentScreen = Screen.InviteFriends) },

                        onNavigateToBroadcasts = { state = state.copy(currentScreen = Screen.BroadcastsSettings) },

                        onNavigateToBusiness = { state = state.copy(currentScreen = Screen.BusinessTools) },

                        onNavigateToProfile = { state = state.copy(currentScreen = Screen.Profile) },

                        onNavigateToLists = { state = state.copy(currentScreen = Screen.ListsSettings) },

                        onNavigateToChat = { name, id -> state = state.copy(activeChatName = name, activeChatId = id, currentScreen = Screen.ChatRoom) }

                    )

                    Screen.Account -> AccountSettingsScreen(

                        userId = state.userId,

                        bgColor = bgColor, textColor = textColor, textSubColor = textSubColor, 

                        onBack = { state = state.copy(currentScreen = Screen.Settings) },

                        onNavigateToSecurity = { state = state.copy(currentScreen = Screen.Security) },

                        onNavigateToTwoStep = { state = state.copy(currentScreen = Screen.TwoStepVerification) },

                        onNavigateToChangeNumber = { state = state.copy(currentScreen = Screen.ChangeNumber) },

                        onLogout = { sp.edit().clear().apply(); state = NeoState(currentScreen = Screen.Login) }

                    )

                    Screen.Privacy -> PrivacySettingsScreen(

                        userId = state.userId,

                        onBack = { state = state.copy(currentScreen = Screen.Settings) },

                        onNavigateToBlocked = { state = state.copy(currentScreen = Screen.BlockedContacts) },

                        onNavigateToAppLock = { state = state.copy(currentScreen = Screen.AppLock) }

                    )

                    Screen.ChatsSettings -> ChatsSettingsScreen(

                        onBack = { state = state.copy(currentScreen = Screen.Settings) },

                        onThemeChange = updateTheme

                    )

                    Screen.NotificationsSettings -> NotificationsSettingsScreen(onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.StorageSettings -> StorageSettingsScreen(onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.Avatar -> AvatarScreen(bgColor = bgColor, textColor = textColor, textSubColor = textSubColor, onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.ListsSettings -> ListsSettingsScreen(bgColor = bgColor, textColor = textColor, textSubColor = textSubColor, onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.Profile -> ProfileScreen(myName = state.myName, userId = state.userId, bgColor = bgColor, textColor = textColor, textSubColor = textSubColor, onBack = { state = state.copy(currentScreen = Screen.Settings) }, onLogout = { sp.edit().clear().apply(); state = NeoState(currentScreen = Screen.Login) })

                    Screen.Language -> LanguageScreen(bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.Accessibility -> AccessibilityScreen(bgColor = bgColor, textColor = textColor, textSubColor = textSubColor, onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.InviteFriends -> InviteFriendsScreen(onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.BroadcastsSettings -> BroadcastsSettingsScreen(onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.Security -> SecurityScreen(onBack = { state = state.copy(currentScreen = Screen.Account) })

                    Screen.TwoStepVerification -> TwoStepVerificationScreen(onBack = { state = state.copy(currentScreen = Screen.Account) })

                    Screen.ChangeNumber -> ChangeNumberScreen(onBack = { state = state.copy(currentScreen = Screen.Account) })

                    Screen.BlockedContacts -> BlockedContactsScreen(onBack = { state = state.copy(currentScreen = Screen.Privacy) })

                    Screen.AppLock -> AppLockScreen(onBack = { state = state.copy(currentScreen = Screen.Privacy) })

                    Screen.BusinessTools -> NeoBotConfigScreen(onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.Contacts -> ContactsScreen(

                        myId = state.userId,

                        bgColor = bgColor, surfaceColor = surfaceColor,

                        textColor = textColor, textSubColor = textSubColor,

                        accentGradient = accentGradient,

                        onBack = { state = state.copy(currentScreen = Screen.ChatsList) },

                        onNavigateToChat = { name, id -> state = state.copy(activeChatName = name, activeChatId = id, currentScreen = Screen.ChatRoom) },

                        onNavigateToCall = { name, callId -> 

                           state = state.copy(activeChatName = name, activeCallId = callId, currentScreen = Screen.Calling) 

                           val callRef = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("calls").child(callId)

                           callRef.setValue(mapOf("callerName" to state.myName, "callerId" to state.userId, "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP))

                        }

                    )

                    Screen.Status -> StatusScreen(userId = state.userId, myName = state.myName, bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, accentGradient = accentGradient, onBack = { state = state.copy(currentScreen = Screen.ChatsList) })

                    Screen.Communities -> CommunitiesScreen(

                        bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, accentGradient = accentGradient, 

                        onBack = { state = state.copy(currentScreen = Screen.ChatsList) },

                        onNavigateToChat = { name, id -> state = state.copy(activeChatName = name, activeChatId = id, currentScreen = Screen.ChatRoom) },

                        onNavigateToCall = { name, callId ->

                            state = state.copy(activeChatName = name, activeCallId = callId, currentScreen = Screen.Calling)

                            val callRef = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("calls").child(callId)

                            callRef.setValue(mapOf("callerName" to state.myName, "callerId" to state.userId, "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP))

                        }

                    )

                    Screen.Vault -> VaultScreen(userId = state.userId, bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, accentGradient = accentGradient, onBack = { state = state.copy(currentScreen = Screen.ChatsList) })

                    Screen.HistoryCalls -> HistoryCallsScreen(

                        bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, accentGradient = accentGradient, 

                        onCallClick = { name -> 

                           if (name.contains("Neo") || state.userId.contains("owner")) state = state.copy(currentScreen = Screen.Contacts)

                           else state = state.copy(activeChatName = name, currentScreen = Screen.Calling)

                        }

                    )

                    Screen.LinkedDevices -> LinkedDevicesScreen(bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, accentGradient = accentGradient, onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.StarredMessages -> StarredMessagesScreen(userId = state.userId, bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, accentGradient = accentGradient, onBack = { state = state.copy(currentScreen = Screen.Settings) })

                    Screen.HelpMenu -> HelpMenuScreen(bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, onBack = { state = state.copy(currentScreen = Screen.Settings) }, onNavigateToHelpCenter = { state = state.copy(currentScreen = Screen.HelpCenter) }, onNavigateToFeedback = { state = state.copy(currentScreen = Screen.Feedback) }, onNavigateToAppInfo = { state = state.copy(currentScreen = Screen.AppInfo) })

                    Screen.HelpCenter -> HelpCenterScreen(userId = state.userId, bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, onBack = { state = state.copy(currentScreen = Screen.HelpMenu) })

                    Screen.Feedback -> FeedbackScreen(userId = state.userId, bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor, onBack = { state = state.copy(currentScreen = Screen.HelpMenu) })

                    Screen.AppInfo -> AppInfoScreen(bgColor = bgColor, textColor = textColor, textSubColor = textSubColor, onBack = { state = state.copy(currentScreen = Screen.HelpMenu) })

                    Screen.Calling -> CallingScreen(

                        chatName = state.activeChatName,

                        callChannel = state.activeCallId,

                        myId = state.userId,

                        otherId = if (state.activeChatId.isNotEmpty()) state.activeChatId else state.activeChatName,

                        initialIsVideo = state.isCallVideo,

                        onHangUp = { 

                            val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)

                            val cleanMe = NeoUtils.sanitizeId(state.userId)

                            db.getReference("calls").child(state.userId).removeValue()

                            if (cleanMe.isNotEmpty()) db.getReference("calls").child(cleanMe).removeValue()

                            if (state.activeCallId.isNotEmpty()) db.getReference("calls").child(state.activeCallId).removeValue()

                            currentRingtone?.stop(); currentRingtone = null

                            state = state.copy(currentScreen = Screen.ChatsList, activeCallId = "") 

                        }

                    )

                    Screen.CorteCaja -> CorteDeCajaScreen(onBack = { state = state.copy(currentScreen = Screen.ChatsList) })

                    Screen.NeoAI -> NeoAiScreen(onBack = { state = state.copy(currentScreen = Screen.ChatsList) }, bgColor = bgColor, surfaceColor = surfaceColor, textColor = textColor, textSubColor = textSubColor)

                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                        Text("Módulo en desarrollo", color = Color.White)

                        Button(onClick = { state = state.copy(currentScreen = Screen.ChatsList) }) { Text("Volver") }

                    }

                }

            }

        }

        

        // --- DIÁLOGO PROFESIONAL PARA CONTESTAR / RECHAZAR LLAMADA (VOZ VS VIDEO) ---

        if (incomingCallInfo != null && state.currentScreen != Screen.Calling) {

            val call = incomingCallInfo!!

            AlertDialog(

                onDismissRequest = {},

                containerColor = Color(0xFF0F172A),

                shape = RoundedCornerShape(24.dp),

                icon = {

                    Box(

                        modifier = Modifier.size(64.dp).clip(CircleShape).background((if (call.isVideo) Color(0xFF00BFFF) else Color(0xFF00FF7F)).copy(alpha = 0.2f)),

                        contentAlignment = Alignment.Center

                    ) {

                        Icon(

                            if (call.isVideo) Icons.Filled.Videocam else Icons.Filled.Call,

                            null,

                            tint = if (call.isVideo) Color(0xFF00BFFF) else Color(0xFF00FF7F),

                            modifier = Modifier.size(36.dp)

                        )

                    }

                },

                title = {

                    Text(

                        if (call.isVideo) "📹 VIDEOLLAMADA ENTRANTE" else "📞 LLAMADA DE VOZ ENTRANTE",

                        color = Color.White,

                        fontWeight = FontWeight.Bold,

                        fontSize = 18.sp

                    )

                },

                text = {

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {

                        Text(call.callerName, color = if (call.isVideo) Color(0xFF00BFFF) else Color(0xFF00FF7F), fontWeight = FontWeight.Black, fontSize = 22.sp)

                        Spacer(Modifier.height(4.dp))

                        Text(

                            if (call.isVideo) "Te está llamando por videollamada..." else "Te está llamando por llamada de voz...",

                            color = Color.LightGray,

                            fontSize = 13.sp

                        )

                    }

                },

                confirmButton = {

                    Button(

                        onClick = {

                            currentRingtone?.stop()

                            currentRingtone = null

                            val ch = if (call.channelId.isNotEmpty()) call.channelId else state.activeCallId

                            state = state.copy(activeChatName = call.callerName, activeCallId = ch, currentScreen = Screen.Calling, isCallVideo = call.isVideo)

                            incomingCallInfo = null

                        },

                        colors = ButtonDefaults.buttonColors(containerColor = if (call.isVideo) Color(0xFF00BFFF) else Color(0xFF00FF7F)),

                        shape = RoundedCornerShape(12.dp)

                    ) {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Icon(if (call.isVideo) Icons.Filled.Videocam else Icons.Filled.Call, null, tint = Color.Black, modifier = Modifier.size(18.dp))

                            Spacer(Modifier.width(6.dp))

                            Text("CONTESTAR", color = Color.Black, fontWeight = FontWeight.Bold)

                        }

                    }

                },

                dismissButton = {

                    Button(

                        onClick = {

                            currentRingtone?.stop()

                            currentRingtone = null

                            incomingCallInfo = null

                            val cleanNode = NeoUtils.sanitizeId(state.userId)

                            val targetNode = if (cleanNode.isNotEmpty()) cleanNode else state.userId

                            com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("calls").child(targetNode).removeValue()

                        },

                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),

                        shape = RoundedCornerShape(12.dp)

                    ) {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Icon(Icons.Filled.CallEnd, null, tint = Color.White, modifier = Modifier.size(18.dp))

                            Spacer(Modifier.width(6.dp))

                            Text("RECHAZAR", color = Color.White, fontWeight = FontWeight.Bold)

                        }

                    }

                }

            )

        }



        // --- DIÁLOGO DE ACTUALIZACIÓN AUTOMÁTICA ---
        var updateDownloadProgress by remember { mutableIntStateOf(0) }
        LaunchedEffect(availableUpdate?.versionCode) {
            val upd = availableUpdate
            if (upd != null) {
                updateDownloadProgress = 1
                NeoUpdateManager.downloadAndInstallApk(
                    context = context,
                    apkUrl = upd.apkUrl,
                    versionCode = upd.versionCode,
                    onProgress = { pct -> updateDownloadProgress = pct }
                )
            }
        }

        if (availableUpdate != null) {
            val upd = availableUpdate!!
            AlertDialog(
                onDismissRequest = { if (!upd.isMandatory) availableUpdate = null },
                containerColor = Color(0xFF0F172A),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚀", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Actualizando NeoMessage", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Nueva versión: v${upd.versionName}", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold)
                        Text(upd.changelog, color = Color.LightGray, fontSize = 13.sp)
                        if (updateDownloadProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { updateDownloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF00FF7F),
                                trackColor = Color(0xFF1E293B)
                            )
                            Text(
                                "Descargando actualización automáticamente... $updateDownloadProgress%",
                                color = Color(0xFF00FF7F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (updateDownloadProgress >= 100) {
                            Text(
                                "✅ Descarga completada (100%). Abriendo instalador automático...",
                                color = Color(0xFF00FF7F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                "Iniciando descarga automática...",
                                color = Color(0xFFFF9800),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            NeoUpdateManager.downloadAndInstallApk(
                                context = context,
                                apkUrl = upd.apkUrl,
                                versionCode = upd.versionCode,
                                onProgress = { pct -> updateDownloadProgress = pct }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                    ) {
                        Text(
                            if (updateDownloadProgress in 1..99) "DESCARGANDO ($updateDownloadProgress%)..." else "INSTALAR AHORA",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    if (!upd.isMandatory) {
                        TextButton(onClick = { availableUpdate = null }) {
                            Text("MÁS TARDE", color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            )
        }

        // --- TEMAS GESTIONADOS DESDE AJUSTES DE CHATS ---

        

        // --- DIÁLOGO DE CONFIRMACIÓN DE VIDEO (DATA-SAVING) ---

        var showVideoDialog by remember { mutableStateOf(false) }

        var videoToPlay by remember { mutableStateOf("") }

        

        LaunchedEffect(videoUrlState.value) {

            if (videoUrlState.value.isNotEmpty()) {

                videoToPlay = videoUrlState.value

                videoUrlState.value = "" // limpiar el estado

                showVideoDialog = true

            }

        }



        if (showVideoDialog) {

            val isWifi = remember(context) { NeoUtils.isWifiConnected(context) }

            AlertDialog(

                onDismissRequest = { showVideoDialog = false },

                title = {

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Icon(Icons.Filled.Shield, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(24.dp))

                        Spacer(Modifier.width(8.dp))

                        Text("Videograbación Titán", fontWeight = FontWeight.Bold, color = Color.White)

                    }

                },

                containerColor = Color(0xFF1E293B),

                text = {

                    Column {

                        Text("Se ha recibido una alerta de video de seguridad de las cámaras del Yaris.", color = Color.White)

                        Spacer(Modifier.height(12.dp))

                        if (isWifi) {

                            Text("📡 Estás conectado a Wi-Fi. Puedes ver el video sin consumir tus datos.", color = Color(0xFF00FF7F), fontSize = 13.sp)

                        } else {

                            Text("⚠️ ALERTA DE DATOS: Estás conectado a una red móvil. Abrir este video consumirá megas de tu plan de datos.", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        }

                    }

                },

                confirmButton = {

                    Button(

                        onClick = {

                            showVideoDialog = false

                            try {

                                val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(videoToPlay)).apply {

                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK

                                }

                                context.startActivity(browserIntent)

                            } catch (e: Exception) {

                                Toast.makeText(context, "Error al abrir reproductor", Toast.LENGTH_SHORT).show()

                            }

                        },

                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))

                    ) {

                        Text("REPRODUCIR VIDEO", color = Color.Black, fontWeight = FontWeight.Bold)

                    }

                },

                dismissButton = {

                    TextButton(onClick = { showVideoDialog = false }) {

                        Text("CANCELAR", color = Color.LightGray)

                    }

                }

            )

        }

    }

}



private fun performNeoLogin(

    name: String,

    phone: String,

    pin: String = "",

    sp: android.content.SharedPreferences,

    state: NeoState,

    updateState: (NeoState) -> Unit,

    context: android.content.Context,

    updateLoading: (Boolean) -> Unit

) {

    updateLoading(true)

    val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)

    val userRef = db.getReference("users").child(phone)



    userRef.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {

        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {

            val existingName = snapshot.child("name").getValue(String::class.java)

            // SI EL USUARIO DECIDIÓ CAMBIAR O CONFIRMAR SU NOMBRE: SE ACTUALIZA SÍ O SÍ

            val finalName = if (name.isNotBlank()) name else (existingName ?: "Usuario Neo")

            if (existingName != null && !existingName.equals(finalName, ignoreCase = true)) {

                Toast.makeText(context, "✅ Nombre actualizado a '$finalName'.", Toast.LENGTH_SHORT).show()

            }



            val newSessionId = java.util.UUID.randomUUID().toString()
            val currentDeviceId = try {
                android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
            } catch (_: Exception) { "" }
            val currentDeviceModel = "${android.os.Build.MANUFACTURER.uppercase()} ${android.os.Build.MODEL}".trim()

            val spEditor = sp.edit()
                .putString("user_name", finalName)
                .putString("user_phone", phone)
                .putString("last_session_id", newSessionId)
                .putString("device_id", currentDeviceId)
                .putString("device_model", currentDeviceModel)
                .putBoolean("is_neo_unified", true)
            if (pin.isNotBlank()) {
                spEditor.putString("security_pin", pin).putString("nexus_pin", pin)
            }
            spEditor.apply()



            updateLoading(false)

            updateState(state.copy(

                myName = finalName,

                userId = phone,

                currentScreen = Screen.ChatsList,

                isAppUnlocked = true

            ))



            val updates = mutableMapOf<String, Any>(
                "id" to phone,
                "name" to finalName,
                "loginTime" to System.currentTimeMillis(),
                "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "status" to "Online",
                "sessionId" to newSessionId,
                "deviceId" to currentDeviceId,
                "deviceModel" to currentDeviceModel
            )
            if (pin.isNotBlank()) {
                updates["securityPin"] = pin
            }

            userRef.updateChildren(updates)

        }



        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {

            val newSessionId = java.util.UUID.randomUUID().toString()

            sp.edit()

                .putString("user_name", name)

                .putString("user_phone", phone)

                .putString("last_session_id", newSessionId)

                .putBoolean("is_neo_unified", true)

                .apply()



            updateLoading(false)

            updateState(state.copy(

                myName = name,

                userId = phone,

                currentScreen = Screen.ChatsList,

                isAppUnlocked = true

            ))

        }

    })

}



@Composable

fun LockScreen(onUnlock: () -> Unit) {

    val context = androidx.compose.ui.platform.LocalContext.current

    val sp = remember { context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE) }

    val savedPin = remember { sp.getString("app_lock_pin", "1234") ?: "1234" }

    var pinInput by remember { mutableStateOf("") }

    var errorMsg by remember { mutableStateOf<String?>(null) }



    val bm = remember { androidx.biometric.BiometricManager.from(context) }

    val canBio = remember {

        bm.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

    }



    Column(

        Modifier.fillMaxSize().background(Color(0xFF070B14)).padding(24.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center

    ) {

        NeoLogo(Modifier.size(130.dp))

        Spacer(Modifier.height(20.dp))

        Text("NeoMessage Protegido", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)

        Spacer(Modifier.height(8.dp))

        Text(

            if (canBio) "Ingresa tu PIN de 4 dígitos o usa tu huella dactilar" else "Ingresa tu PIN de seguridad de 4 dígitos",

            color = Color.Gray,

            fontSize = 13.sp,

            textAlign = androidx.compose.ui.text.style.TextAlign.Center

        )

        Spacer(Modifier.height(24.dp))



        OutlinedTextField(

            value = pinInput,

            onValueChange = {

                if (it.length <= 4 && it.all { c -> c.isDigit() }) {

                    pinInput = it

                    errorMsg = null

                    if (it == savedPin || (savedPin.isEmpty() && it.length == 4)) {

                        onUnlock()

                    }

                }

            },

            label = { Text("PIN de seguridad") },

            placeholder = { Text("••••") },

            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),

            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(

                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword

            ),

            singleLine = true,

            modifier = Modifier.width(220.dp),

            colors = OutlinedTextFieldDefaults.colors(

                focusedTextColor = Color.White,

                unfocusedTextColor = Color.White,

                focusedBorderColor = NeoTheme.Green,

                unfocusedBorderColor = Color.Gray

            )

        )



        if (errorMsg != null) {

            Text(errorMsg!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))

        }



        Spacer(Modifier.height(16.dp))

        Button(

            onClick = {

                if (pinInput == savedPin || (savedPin.isEmpty() && pinInput.length == 4)) {

                    onUnlock()

                } else {

                    errorMsg = "PIN incorrecto"

                }

            },

            colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green),

            shape = RoundedCornerShape(12.dp),

            modifier = Modifier.width(220.dp)

        ) {

            Text("DESBLOQUEAR", color = Color.Black, fontWeight = FontWeight.Bold)

        }



        if (canBio) {

            Spacer(Modifier.height(12.dp))

            OutlinedButton(

                onClick = onUnlock,

                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeoTheme.Green),

                border = androidx.compose.foundation.BorderStroke(1.dp, NeoTheme.Green),

                shape = RoundedCornerShape(12.dp),

                modifier = Modifier.width(220.dp)

            ) {

                Icon(Icons.Filled.Fingerprint, null, tint = NeoTheme.Green, modifier = Modifier.size(18.dp))

                Spacer(Modifier.width(8.dp))

                Text("USAR HUELLA", fontWeight = FontWeight.Bold)

            }

        }

    }

}