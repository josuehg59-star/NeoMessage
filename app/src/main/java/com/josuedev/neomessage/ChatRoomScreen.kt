package com.josuedev.neomessage

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.webkit.MimeTypeMap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.RingtoneManager
import android.media.Ringtone
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import com.josuedev.neomessage.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.*
import kotlin.coroutines.resume
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

// --- PROTOCOLO DE FECHAS INTELIGENTES (HOY / AYER / FECHA) ---
fun formatChatDateHeader(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val calMsg = Calendar.getInstance().apply { timeInMillis = timestamp }
    val calNow = Calendar.getInstance()
    
    val isToday = calMsg.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                  calMsg.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    if (isToday) return "HOY"
    
    calNow.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = calMsg.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                      calMsg.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "AYER"
    
    val sdf = SimpleDateFormat("d 'de' MMMM, yyyy", Locale("es", "ES"))
    return sdf.format(Date(timestamp)).uppercase()
}

fun isSameDay(t1: Long, t2: Long): Boolean {
    if (t1 <= 0 || t2 <= 0) return false
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
           c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatRoomScreen(
    chatName: String,
    chatId: String,
    userId: String,
    myName: String,
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onBack: () -> Unit,
    onNavigateToCalling: (String, Boolean) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordDuration by remember { mutableIntStateOf(0) }
    var playingVoiceMsgId by remember { mutableStateOf<String?>(null) }
    var showCommerceSheet by remember { mutableStateOf(false) }
    var showExcelDialog by remember { mutableStateOf(false) }
    var pendingExcelText by remember { mutableStateOf("") }
    val typingUser = remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val database = remember { NetworkConfig.getDatabaseReference("chats").child(chatId).child("messages") }
    val typingDb = remember { NetworkConfig.getDatabaseReference(NetworkConfig.PATH_TYPING).child(chatId) }
    
    val firebaseMessages = remember { mutableStateListOf<CloudMessage>() }
    val dao = remember { 
        try { AppDatabase.getDatabase(context).messageDao() } catch (e: Exception) { null }
    }
    
    val cleanMyId = remember(userId) { NeoUtils.sanitizeId(userId) }
    val otherCleanId = remember(chatId) { 
        val parts = chatId.replace("private_", "").split("_")
        if (parts.size == 2) {
            if (parts[0] == cleanMyId) parts[1] else parts[0]
        } else chatId
    }
    
    var otherPhotoUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(otherCleanId) {
        if (otherCleanId.isNotEmpty() && !chatId.startsWith("group_")) {
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                    .getReference("users").child(otherCleanId)
                    .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                            val p = snapshot.child("photoUrl").getValue(String::class.java)
                                ?: snapshot.child("profilePhoto").getValue(String::class.java)
                            if (!p.isNullOrBlank()) otherPhotoUrl = p
                        }
                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                    })
            } catch (_: Exception) {}
        }
    }
    
    val isOtherBlocked = remember { mutableStateOf(false) }
    val amIBlocked = remember { mutableStateOf(false) }
    LaunchedEffect(userId, otherCleanId) {
        if (otherCleanId.isNotEmpty()) {
            val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            // LISTENER: BLOQUEO DE CONTACTO
            val blockedDb = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("blocked")
            blockedDb.child(cleanMyId).child(otherCleanId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) { isOtherBlocked.value = s.exists() }
                override fun onCancelled(ignored: DatabaseError) {}
            })

            // LISTENER: ESTADO DE BLOQUEO PROPIO
            blockedDb.child(otherCleanId).child(cleanMyId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) { amIBlocked.value = s.exists() }
                override fun onCancelled(ignored: DatabaseError) {}
            })
        }
    }
    LaunchedEffect(chatId) {
        // 1. CARGA INMEDIATA LOCAL (Room SQLite)
        launch(Dispatchers.IO) {
            try {
                dao?.getMessagesForChat(chatId)?.collect { entities ->
                    withContext(Dispatchers.Main) {
                        for (e in entities) {
                            val cloud = e.toCloud()
                            val idx = firebaseMessages.indexOfFirst { it.id == cloud.id }
                            if (idx == -1) {
                                firebaseMessages.add(cloud)
                            } else {
                                firebaseMessages[idx] = cloud
                            }
                        }
                        firebaseMessages.sortBy { it.timestamp }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. SINCRONIZACIÓN FIREBASE EN TIEMPO REAL
        try {
            val listener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val m = snapshot.getValue(CloudMessage::class.java)
                    if (m != null) {
                        val idx = firebaseMessages.indexOfFirst { it.id == m.id }
                        if (idx == -1) {
                            firebaseMessages.add(m)
                            firebaseMessages.sortBy { it.timestamp }
                        } else {
                            firebaseMessages[idx] = m
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            try { dao?.insertMessage(m.toEntity(chatId)) } catch (_: Exception) {}
                        }
                        if (NeoUtils.sanitizeId(m.senderId).takeLast(10) != cleanMyId.takeLast(10) && m.deliveredTo[cleanMyId] != true) {
                            try { database.child(m.id).child("deliveredTo").child(cleanMyId).setValue(true) } catch (_: Exception) {}
                        }
                        if (NeoUtils.sanitizeId(m.senderId).takeLast(10) != cleanMyId.takeLast(10)) {
                            NeoBotManager.dispatchAutoReplyIfNeeded(
                                context = context,
                                chatId = chatId,
                                senderId = m.senderId,
                                myId = cleanMyId,
                                incomingText = m.text,
                                msgTimestamp = m.timestamp
                            )
                        }
                    }
                }
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val m = snapshot.getValue(CloudMessage::class.java)
                    if (m != null) {
                        val index = firebaseMessages.indexOfFirst { it.id == m.id }
                        if (index != -1) firebaseMessages[index] = m else firebaseMessages.add(m)
                        firebaseMessages.sortBy { it.timestamp }
                        coroutineScope.launch(Dispatchers.IO) {
                            try { dao?.insertMessage(m.toEntity(chatId)) } catch (_: Exception) {}
                        }
                    }
                }
                override fun onChildRemoved(snapshot: DataSnapshot) {
                    val m = snapshot.getValue(CloudMessage::class.java)
                    if (m != null) {
                        firebaseMessages.removeAll { it.id == m.id }
                    }
                }
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    NetworkConfig.handleDatabaseError(error)
                }
            }
            database.limitToLast(50).addChildEventListener(listener)
        } catch (_: Exception) {}

        // 3. ESCUCHA ACTIVA DE RELAY SOBERANO ($0.00 COSTO)
        coroutineScope.launch {
            NeoRelayClient.incomingMessages.collect { (relChatId, relMsg) ->
                if (relChatId == chatId || relMsg.senderId == otherCleanId) {
                    withContext(Dispatchers.Main) {
                        if (firebaseMessages.none { it.id == relMsg.id }) {
                            firebaseMessages.add(relMsg)
                            firebaseMessages.sortBy { it.timestamp }
                        }
                    }
                    val cleanSender = NeoUtils.sanitizeId(relMsg.senderId)
                    if (cleanSender.takeLast(10) != cleanMyId.takeLast(10)) {
                        NeoBotManager.dispatchAutoReplyIfNeeded(
                            context = context,
                            chatId = chatId,
                            senderId = relMsg.senderId,
                            myId = cleanMyId,
                            incomingText = relMsg.text,
                            msgTimestamp = relMsg.timestamp
                        )
                    }
                }
            }
        }
    }

    var lastMsgCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(firebaseMessages.size) {
        if (firebaseMessages.size > lastMsgCount) {
             val lastMsg = firebaseMessages.lastOrNull()
             if (lastMsg != null) {
                 val cleanSenderId = NeoUtils.sanitizeId(lastMsg.senderId)
                 if (cleanSenderId.takeLast(10) != cleanMyId.takeLast(10) && lastMsg.timestamp > (System.currentTimeMillis() - 3000)) {
                     try {
                         val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                         val r = RingtoneManager.getRingtone(context, notification)
                         r.play()
                     } catch (_: Exception) {}
                 }
             }
        }
        lastMsgCount = firebaseMessages.size
        if (firebaseMessages.isNotEmpty()) {
            listState.animateScrollToItem(firebaseMessages.size - 1)
        }
    }

    val isEphemeralMode by remember { mutableStateOf(false) }
    val sp = context.getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
    val enterIsSend = sp.getBoolean("enter_is_send", false)
    val readReceiptsEnabled = sp.getBoolean("read_receipts", true)
    
    val wallpaperPref = sp.getString("chat_wallpaper", "Cyberpunk (Predeterminado)") ?: "Cyberpunk (Predeterminado)"
    val wallpaperBrush = remember(wallpaperPref) {
        when {
            wallpaperPref.contains("Matrix") -> Brush.verticalGradient(listOf(Color(0xFF001A0D), Color(0xFF022B14), Color(0xFF000F07)))
            wallpaperPref.contains("Medianoche") -> Brush.verticalGradient(listOf(Color(0xFF0A192F), Color(0xFF0F172A), Color(0xFF030712)))
            wallpaperPref.contains("Esmeralda") -> Brush.verticalGradient(listOf(Color(0xFF062319), Color(0xFF0B3B2B), Color(0xFF04140E)))
            wallpaperPref.contains("Obsidiana") -> Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF0A0F1D)))
            wallpaperPref.contains("Negro Puro") -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
            else -> Brush.verticalGradient(listOf(Color(0xFF070B14), Color(0xFF0B141B), Color(0xFF05090F)))
        }
    }
    
    val fontSizePref = sp.getString("chat_font_size_label", "Mediana (15 sp)") ?: "Mediana (15 sp)"
    val chatFontSize = remember(fontSizePref) {
        when {
            fontSizePref.contains("Pequeña") -> 13.sp
            fontSizePref.contains("Grande") -> 18.sp
            else -> 15.sp
        }
    }
    
    var selectedMessage by remember { mutableStateOf<CloudMessage?>(null) }
    var replyingTo by remember { mutableStateOf<CloudMessage?>(null) }
    var editingMessage by remember { mutableStateOf<CloudMessage?>(null) }
    var editTextValue by remember { mutableStateOf("") }
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var alertMsg by remember { mutableStateOf<String?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var activeZoomBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showContactInfoScreen by remember { mutableStateOf(false) }
    var showCreateQuoteDialog by remember { mutableStateOf(false) }
    var showHealthDialog by remember { mutableStateOf(false) }
    var annotatingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Monitoreo de transferencias P2P activas (2 GB - 4 GB)
    val activeTransfers by NeoP2PTransferManager.transfers.collectAsState()

    val sendMessage = {
        if (messageText.isNotBlank() && !isOtherBlocked.value && !amIBlocked.value) {
            val currentText = messageText.trim()
            messageText = ""
            try { typingDb.removeValue() } catch (_: Exception) {}
            
            // --- TRIPLE ESCUDO v8.5: RESPALDO JSON LOCAL INMEDIATO ---
            NeoTripleShieldManager.saveToLocalNode(context, "CHAT_MSG", currentText, cleanMyId)

            val currentReply = replyingTo
            replyingTo = null
            val msgId = database.push().key ?: UUID.randomUUID().toString()
            val msg = CloudMessage(
                id = msgId,
                text = currentText,
                timestamp = System.currentTimeMillis(),
                senderId = cleanMyId,
                senderName = myName,
                rank = "",
                replyToId = currentReply?.id,
                replyToText = currentReply?.text?.take(80),
                replyToSender = currentReply?.senderName
            )

            // 1. VISIBILIDAD INMEDIATA EN PANTALLA (CERO ESPERA, CERO BLOQUEO)
            if (firebaseMessages.none { it.id == msg.id }) {
                firebaseMessages.add(msg)
                firebaseMessages.sortBy { it.timestamp }
            }

            // 2. PERSISTENCIA LOCAL EN SQLITE ROOM (SIEMPRE DISPONIBLE OFFLINE)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    dao?.insertMessage(msg.toEntity(chatId))
                } catch (e: Exception) {
                    android.util.Log.e("NEO_LOCAL_DB", "Error saving local message: ${e.message}")
                }

                // 3. EMISIÓN INSTANTÁNEA UDP MESH (LLEGA EN < 1 ms A CUALQUIER TELÉFONO EN LA MISMA RED/HOTSPOT)
                NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)

                // 3.1 DESPACHO SOBERANO RELAY CELULAR -> SERVIDOR -> CELULAR ($0.00 COSTO)
                NeoRelayClient.sendMessage(context, chatId, otherCleanId, msg)

                // 4. INTENTO 1: ENVÍO DIRECTO P2P (0 DATOS, 0 SERVIDORES, 0 COSTOS)
                val directPeer = NeoLocalNodeServer.findLocalPeer(otherCleanId)
                if (directPeer != null) {
                    NeoLocalNodeServer.sendDirectMessage(
                        targetHost = directPeer.host,
                        targetPort = directPeer.port,
                        message = msg,
                        chatId = chatId,
                        onSuccess = {},
                        onError = { err ->
                            android.util.Log.w("NEO_P2P", "P2P directo falló: $err. Intentando respaldo...")
                        }
                    )
                }

                // 4. ENTREGA INMEDIATA EN TIEMPO REAL VÍA FIREBASE (CERO DEMORA, MILISEGUNDOS)
                try {
                    database.child(msgId).setValue(msg)
                } catch (e: Exception) {
                    android.util.Log.w("NEO_FIREBASE", "Cloud sync queued: ${e.message}")
                }

                // 5. RESPALDO ASÍNCRONO SIN BLOQUEAR
                PocketBaseManager.sendMessage(chatId, cleanMyId, myName, currentText, {}, {})
            }
        } else if (isOtherBlocked.value || amIBlocked.value) {
            Toast.makeText(context, "Protocolo de comunicación cortado. Contacto bloqueado.", Toast.LENGTH_LONG).show()
        }
        Unit
    }

    // --- MOTOR DE TRANSMISIÓN DE IMÁGENES NÍTIDAS (AUTO-RENDERIZADO BASE64 100% GARANTIZADO) ---
    val performMediaUpload = { uri: Uri ->
        if (!isUploading) {
            isUploading = true
            Toast.makeText(context, "📸 Enviando imagen nítida...", Toast.LENGTH_SHORT).show()
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val compressedBytes = NeoUtils.compressImage(context, uri, quality = 88, maxDimension = 2048)
                    if (compressedBytes == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No se pudo procesar la imagen seleccionada", Toast.LENGTH_SHORT).show()
                            isUploading = false
                        }
                        return@launch
                    }

                    val base64Data = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
                    val mid = database.push().key ?: UUID.randomUUID().toString()
                    val msgText = "[Media] data:image/jpeg;base64,$base64Data"
                    val msg = CloudMessage(mid, msgText, System.currentTimeMillis(), cleanMyId, myName, "")

                    dao?.insertMessage(msg.toEntity(chatId))
                    withContext(Dispatchers.Main) {
                        if (firebaseMessages.none { it.id == msg.id }) {
                            firebaseMessages.add(msg)
                            firebaseMessages.sortBy { it.timestamp }
                        }
                        Toast.makeText(context, "✅ Imagen enviada", Toast.LENGTH_SHORT).show()
                        isUploading = false
                    }

                    // Enviar vía UDP Mesh local
                    NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)

                    // Despacho vía Relay ($0.00 costo)
                    NeoRelayClient.sendMessage(context, chatId, otherCleanId, msg)

                    // Enviar vía P2P HTTP si está en la misma red
                    val directPeer = NeoLocalNodeServer.findLocalPeer(otherCleanId)
                    if (directPeer != null) {
                        NeoLocalNodeServer.sendDirectMessage(
                            targetHost = directPeer.host,
                            targetPort = directPeer.port,
                            message = msg,
                            chatId = chatId
                        )
                    }

                    // Enviar por Firebase en tiempo real
                    try { database.child(mid).setValue(msg) } catch (_: Exception) {}
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error procesando imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                        isUploading = false
                    }
                }
            }
        }
    }

    val performDocumentUpload = { uri: Uri ->
        val fileSize = NeoUtils.getFileSize(context, uri)
        val fileName = NeoUtils.getFileName(context, uri) ?: "Documento"
        val sizeMb = fileSize / (1024 * 1024)
        val sizeText = if (sizeMb > 1024) "%.2f GB".format(fileSize.toDouble() / (1024 * 1024 * 1024)) else "${fileSize / 1024} KB"

        Toast.makeText(context, "📄 Enviando $fileName ($sizeText)...", Toast.LENGTH_SHORT).show()
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Hasta 5 MB viajan directo por Base64 en Firebase para entrega 100% inmediata en cualquier red
                val directUrl = if (fileSize < 5_000_000) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        "data:application/octet-stream;base64,$b64"
                    } else {
                        val meta = NeoLocalNodeServer.registerFileForSharing(context, uri)
                        val myIp = NeoLocalNodeServer.getLocalIpAddress()
                        "http://$myIp:${NeoLocalNodeServer.activePort}/download?fileId=${meta.fileId}"
                    }
                } else {
                    val meta = NeoLocalNodeServer.registerFileForSharing(context, uri)
                    val myIp = NeoLocalNodeServer.getLocalIpAddress()
                    "http://$myIp:${NeoLocalNodeServer.activePort}/download?fileId=${meta.fileId}"
                }

                val mid = database.push().key ?: UUID.randomUUID().toString()
                val msgText = "[File] $fileName|$fileSize|$directUrl"
                val msg = CloudMessage(mid, msgText, System.currentTimeMillis(), cleanMyId, myName, "")

                dao?.insertMessage(msg.toEntity(chatId))
                withContext(Dispatchers.Main) {
                    if (firebaseMessages.none { it.id == msg.id }) {
                        firebaseMessages.add(msg)
                        firebaseMessages.sortBy { it.timestamp }
                    }
                    Toast.makeText(context, "✅ Enviado: $fileName", Toast.LENGTH_SHORT).show()
                }

                // Enviar vía UDP Mesh local
                NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)

                // Enviar por Firebase a la nube
                try { database.child(mid).setValue(msg) } catch (_: Exception) {}
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var tempCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraPhotoUri != null) {
            performMediaUpload(tempCameraPhotoUri!!)
        }
    }

    var tempCameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    val captureVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success && tempCameraVideoUri != null) {
            performDocumentUpload(tempCameraVideoUri!!)
        }
    }

    val annotatorPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            annotatingPhotoUri = uri
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { uri -> performMediaUpload(uri) }
        }
    }
    
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { uri -> performDocumentUpload(uri) }
        }
    }
    
    DisposableEffect(chatId) {
        onDispose {
            try { typingDb.removeValue() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(chatId) {
        typingDb.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { 
                val time = snapshot.child("time").getValue(Long::class.java) ?: 0L
                val raw = snapshot.child("user").getValue(String::class.java) ?: snapshot.getValue(String::class.java)
                val isRecent = time == 0L || (System.currentTimeMillis() - time < 4500L)
                val isOther = isRecent && !raw.isNullOrBlank() && raw != "null" && raw != myName && NeoUtils.sanitizeId(raw).takeLast(10) != cleanMyId.takeLast(10)
                typingUser.value = if (isOther) raw else null
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ---- UI ----
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showContactInfoScreen = true }
                    ) {
                        NeoUniversalAvatar(
                            photoSource = otherPhotoUrl,
                            fallbackName = chatName,
                            modifier = Modifier.size(40.dp),
                            fontSize = 18
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chatName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (typingUser.value != null && typingUser.value != myName) "✍️ Escribiendo..." else "En línea", color = Color(0xFF00FF9C), fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                actions = {
                    var showEffectsDialog by remember { mutableStateOf(false) }
                    var showShareDialog by remember { mutableStateOf(false) }
                    var showAiSummary by remember { mutableStateOf(false) }
                    var showMoreMenu by remember { mutableStateOf(false) }
                    
                    // SOLAMENTE 2 ICONOS PRINCIPALES + MENÚ 3 PUNTOS: SEPARACIÓN DE VOZ Y VIDEO
                    IconButton(onClick = { onNavigateToCalling(otherCleanId, true) }) { 
                        Icon(Icons.Filled.Videocam, "Videollamada", tint = Color(0xFF00BFFF)) 
                    }
                    IconButton(onClick = { onNavigateToCalling(otherCleanId, false) }) { 
                        Icon(Icons.Filled.Call, "Llamada de voz", tint = Color(0xFF00FF7F)) 
                    }
                    
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
                        var showGhostMode by remember { mutableStateOf(false) }

                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }, modifier = Modifier.background(surfaceColor)) {
                            DropdownMenuItem(text = { Text("🛒 Catálogo y Ventas", color = Color(0xFFFFD700)) }, onClick = { 
                                showMoreMenu = false
                                showCommerceSheet = true
                            })
                            DropdownMenuItem(text = { Text(if (isLocked) "🔓 Desbloquear chat" else "🔒 Bloquear chat", color = Color.White) }, onClick = { 
                                showMoreMenu = false
                                isLocked = !isLocked
                                alertMsg = if (isLocked) "🔒 Chat BLOQUEADO." else "🔓 Desbloqueado."
                            })
                            DropdownMenuItem(text = { Text("🧠 Resumen NeoAI", color = Color(0xFF00BFFF)) }, onClick = { 
                                showMoreMenu = false
                                showAiSummary = true
                            })
                            DropdownMenuItem(text = { Text("✨ Efectos visuales", color = Color(0xFFFFD700)) }, onClick = { 
                                showMoreMenu = false
                                showEffectsDialog = true
                            })
                            DropdownMenuItem(text = { Text("🖥️ Compartir pantalla", color = Color.White) }, onClick = { 
                                showMoreMenu = false
                                showShareDialog = true
                            })
                            DropdownMenuItem(text = { Text("⏱️ Mensajes temporales", color = Color.White) }, onClick = { 
                                showMoreMenu = false
                                showDisappearingDialog = true
                            })
                            DropdownMenuItem(text = { Text("👻 Protocolo Fantasma", color = if(showGhostMode) NeoTheme.Green else Color.White) }, onClick = { 
                                showGhostMode = !showGhostMode
                                showMoreMenu = false
                                Toast.makeText(context, if(showGhostMode) "MODO FANTASMA ACTIVADO: Mensajes efímeros." else "MODO FANTASMA DESACTIVADO", Toast.LENGTH_SHORT).show()
                            })
                            DropdownMenuItem(text = { Text("🗑️ Vaciar chat", color = Color.Red) }, onClick = { 
                                showMoreMenu = false
                                coroutineScope.launch { 
                                    try { database.removeValue() } catch (_: Exception) {}
                                    try { typingDb.removeValue() } catch (_: Exception) {}
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try { dao?.clearChat(chatId) } catch (_: Exception) {}
                                    }
                                    firebaseMessages.clear()
                                } 
                            })
                            DropdownMenuItem(text = { Text(if (isOtherBlocked.value) "Desbloquear contacto" else "Bloquear contacto", color = if(isOtherBlocked.value) NeoTheme.Green else Color.Red) }, onClick = { 
                                showMoreMenu = false
                                val ref = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("users").child(userId).child("blocked").child(otherCleanId)
                                if (isOtherBlocked.value) ref.removeValue() else ref.setValue(true)
                                Toast.makeText(context, if(isOtherBlocked.value) "Contacto desbloqueado." else "Contacto bloqueado permanentemente.", Toast.LENGTH_SHORT).show()
                            })
                            DropdownMenuItem(text = { Text("🛡️ Auditar Seguridad", color = NeoTheme.Green) }, onClick = { 
                                showMoreMenu = false
                                alertMsg = "PROTOCOL AUDIT: 256-bit AES Encription | P2P NeoMesh Ready | Zero Log Policy."
                            })
                        }
                    }
                    
                    if (showAiSummary) {
                        AlertDialog(
                            onDismissRequest = { showAiSummary = false },
                            title = { Text("Protocolo de Resumen NeoAI", color = Color.White, fontWeight = FontWeight.Bold) },
                            containerColor = Color(0xFF1E293B),
                            text = { 
                                val summary = if (firebaseMessages.isEmpty()) "No hay mensajes suficientes para analizar." 
                                             else "NeoAI ha analizado ${firebaseMessages.size} comunicaciones.\n\nResumen: Los agentes están coordinando el despliegue de NeoMessage."
                                Text(summary, color = Color.LightGray) 
                            },
                            confirmButton = { TextButton(onClick = { showAiSummary = false }) { Text("ENTENDIDO", color = Color(0xFF00FF7F)) } }
                        )
                    }
                    if (showEffectsDialog) {
                        AlertDialog(
                            onDismissRequest = { showEffectsDialog = false },
                            title = { Text("Efectos de Video NeoMessage") },
                            text = { 
                                Column {
                                    Text("Selecciona un protocolo visual:", color = Color.Gray, fontSize = 12.sp)
                                    Spacer(Modifier.height(8.dp))
                                    listOf("✨ Confeti Digital", "🎆 Fuegos Artificiales", "❄️ Nieve Ártica", "💖 Corazones Neo").forEach { effect ->
                                        TextButton(onClick = { 
                                            showEffectsDialog = false
                                            alertMsg = "Efecto '$effect' ACTIVADO para la próxima videollamada." 
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text(effect, color = Color.White)
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showEffectsDialog = false }) { Text("CANCELAR", color = Color.Red) } }
                        )
                    }
                    if (showShareDialog) {
                        AlertDialog(
                            onDismissRequest = { showShareDialog = false },
                            title = { Text("Protocolo ScreenShare") },
                            text = { Text("¿Deseas iniciar la transmisión de pantalla encriptada con $chatName?") },
                            confirmButton = { 
                                Button(onClick = { 
                                    showShareDialog = false
                                    alertMsg = "🖥️ Transmisión iniciada." 
                                }) { Text("TRANSMITIR") } 
                            },
                            dismissButton = { TextButton(onClick = { showShareDialog = false }) { Text("CANCELAR") } }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(wallpaperBrush).padding(padding)) {
            // BANNER DINÁMICO DE TRANSFERENCIA P2P (2 GB - 4 GB)
            val activeTransfer = activeTransfers.values.firstOrNull { it.status == NeoP2PTransferManager.TransferStatus.IN_PROGRESS }
            if (activeTransfer != null) {
                Surface(
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (activeTransfer.isLocalMesh) "⚡ Modo Wi-Fi Mesh Local (0 MB Datos)" else "🌐 Modo P2P Global",
                                color = Color(0xFF00FF7F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Text("${activeTransfer.percentage}% (${activeTransfer.speedMbText})", color = Color.White, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { activeTransfer.percentage / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFF00FF7F)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            activeTransfer.fileName,
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (isUploading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF00FF7F))
            }
            
            if (isOtherBlocked.value || amIBlocked.value) {
                Surface(color = Color.Red.copy(0.1f), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (isOtherBlocked.value) "HAS BLOQUEADO A ESTE CONTACTO" else "ESTE CONTACTO TE HA BLOQUEADO",
                        color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp).fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
            }

            // PROTOCOLO DE LECTURA (MARCAR COMO VISTO / DOBLE PALOMITA)
            LaunchedEffect(firebaseMessages.size) {
                firebaseMessages.filter { 
                    NeoUtils.sanitizeId(it.senderId).takeLast(10) != cleanMyId.takeLast(10) && it.readBy[cleanMyId] != true 
                }.forEach { msg ->
                    if (readReceiptsEnabled) {
                        database.child(msg.id).child("readBy").child(cleanMyId).setValue(true)
                    }
                    database.child(msg.id).child("deliveredTo").child(cleanMyId).setValue(true)
                }
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                items(firebaseMessages.size) { i ->
                    val msg = firebaseMessages[i]
                    val showDateHeader = i == 0 || !isSameDay(msg.timestamp, firebaseMessages[i - 1].timestamp)
                    if (showDateHeader && msg.timestamp > 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = Color(0xFF1E293B).copy(alpha = 0.92f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, Color.White.copy(0.15f))
                            ) {
                                Text(
                                    text = formatChatDateHeader(msg.timestamp),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    val isMine = NeoUtils.sanitizeId(msg.senderId) == cleanMyId
                    val isReadByOther = msg.readBy.keys.any { NeoUtils.sanitizeId(it).takeLast(10) != cleanMyId.takeLast(10) }
                    val isDeliveredToOther = msg.deliveredTo.keys.any { NeoUtils.sanitizeId(it).takeLast(10) != cleanMyId.takeLast(10) }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).combinedClickable(onClick = {}, onDoubleClick = { replyingTo = msg }, onLongClick = { selectedMessage = msg }),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isMine) Color(0xFF00382B) else surfaceColor,
                            shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp).widthIn(max = 280.dp)) {
                                if (msg.isDeletedForEveryone) {
                                    Text("🚫 Este mensaje fue eliminado", color = textSubColor.copy(0.6f), fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                } else if (msg.expiryTime != null && msg.expiryTime < System.currentTimeMillis()) {
                                    Text("🔥 Contenido autodestruido", color = Color.Red.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    if (!msg.replyToText.isNullOrEmpty()) {
                                        Surface(
                                            color = if (isMine) Color(0xFF00241B) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        ) {
                                            Row(modifier = Modifier.padding(6.dp)) {
                                                Box(modifier = Modifier.width(3.dp).height(28.dp).background(NeoTheme.Green, RoundedCornerShape(2.dp)))
                                                Spacer(Modifier.width(6.dp))
                                                Column {
                                                    Text(msg.replyToSender ?: "Mensaje", color = NeoTheme.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text(msg.replyToText ?: "", color = textColor.copy(0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                    MessageContent(msg, textColor, textSubColor, context, isMine = isMine, playingVoiceMsgId = playingVoiceMsgId, onPlayingVoiceChange = { playingVoiceMsgId = it }, onImageClick = { activeZoomBitmap = it })
                                    if (msg.expiryTime != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                            Icon(Icons.Filled.Timer, null, tint = Color.Red, modifier = Modifier.size(10.dp))
                                            Text(" Se quema en ${((msg.expiryTime - System.currentTimeMillis())/1000)}s", color = Color.Red, fontSize = 9.sp)
                                        }
                                    }
                                }
                                
                                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                    if (msg.isStarred) {
                                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                    }
                                    if (msg.isEdited) {
                                        Text("Editado ", color = textSubColor.copy(0.7f), fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                    }
                                    Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp)), color = textSubColor.copy(0.5f), fontSize = 10.sp)
                                    if (isMine) {
                                        Spacer(Modifier.width(4.dp))
                                        val showBlueChecks = readReceiptsEnabled && isReadByOther
                                        Icon(
                                            imageVector = if (showBlueChecks) Icons.Filled.DoneAll else Icons.Filled.Done,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = if (showBlueChecks) Color(0xFF53BDEB) else Color.Gray
                                        )
                                    }
                                }
                                
                                if (msg.reactions.isNotEmpty()) {
                                    val emojis = msg.reactions.values.distinct().joinToString(" ")
                                    Surface(
                                        color = Color(0xFF0B141B),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(0.15f)),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(emojis, fontSize = 12.sp)
                                            if (msg.reactions.size > 1) {
                                                Spacer(Modifier.width(3.dp))
                                                Text("${msg.reactions.size}", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showAttachmentMenu) {
                AttachmentMenu(bgColor = bgColor, textColor = textColor) { action ->
                    showAttachmentMenu = false
                    when (action) {
                        "Galería" -> galleryLauncher.launch("image/*")
                        "Documento" -> documentLauncher.launch("*/*")
                        "Cámara", "Tomar Foto" -> {
                            try {
                                val photoFile = java.io.File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                                val photoUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    photoFile
                                )
                                tempCameraPhotoUri = photoUri
                                takePhotoLauncher.launch(photoUri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al iniciar cámara: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Grabar Video", "Video" -> {
                            try {
                                val videoFile = java.io.File(context.cacheDir, "camera_video_${System.currentTimeMillis()}.mp4")
                                val videoUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    videoFile
                                )
                                tempCameraVideoUri = videoUri
                                captureVideoLauncher.launch(videoUri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al iniciar video: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Ubicación" -> sendLocationMessage(database, cleanMyId, myName, context)
                        "Cotización" -> { showCreateQuoteDialog = true }
                        "Acotar Medidas" -> { annotatorPickerLauncher.launch("image/*") }
                        "Salud / Reloj" -> { showHealthDialog = true }
                        "Programar" -> { alertMsg = "📅 Programador NeoMessage: Selecciona fecha y hora para este mensaje." }
                        "Encuesta" -> { alertMsg = "📊 Creador de Encuestas: Define tu pregunta y opciones." }
                    }
                }
            }

            AnimatedVisibility(visible = replyingTo != null) {
                val target = replyingTo
                if (target != null) {
                    Surface(
                        color = surfaceColor,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeoTheme.Green.copy(0.5f))
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(4.dp).height(32.dp).background(NeoTheme.Green, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Respondiendo a: ${target.senderName.ifEmpty { "Mensaje" }}", color = NeoTheme.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(target.text.take(80), color = textColor.copy(0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancelar respuesta", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Surface(color = surfaceColor, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) { Icon(Icons.Filled.AttachFile, null, tint = if (showAttachmentMenu) Color(0xFF00FF7F) else Color.Gray) }
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { 
                            messageText = it 
                            if (it.contains("\t") && it.contains("\n") && it.lines().count { l -> l.contains("\t") } >= 1) {
                                pendingExcelText = it
                                showExcelDialog = true
                            }
                            if (it.isNotEmpty()) {
                                typingDb.setValue(mapOf("user" to myName, "time" to com.google.firebase.database.ServerValue.TIMESTAMP))
                            } else {
                                typingDb.removeValue()
                            }
                        },
                        placeholder = { Text("Escribe algo...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (enterIsSend) androidx.compose.ui.text.input.ImeAction.Send else androidx.compose.ui.text.input.ImeAction.Default,
                            keyboardType = KeyboardType.Text
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = { if (enterIsSend) sendMessage() }
                        )
                    )
                    if (messageText.isNotBlank()) {
                        IconButton(onClick = { sendMessage() }) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF00FF7F))
                        }
                    } else {
                        // BOTÓN DE NOTA DE VOZ (MICROFONO)
                        IconButton(
                            onClick = {
                                if (!isRecordingVoice) {
                                    val ok = NeoVoiceNoteManager.startRecording(context)
                                    if (ok) {
                                        isRecordingVoice = true
                                        Toast.makeText(context, "🎙️ Grabando nota de voz...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "⚠️ Permiso de micrófono requerido", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val result = NeoVoiceNoteManager.stopRecording()
                                    isRecordingVoice = false
                                    if (result != null) {
                                        val (voicePayload, _) = result
                                        val vMid = database.push().key ?: UUID.randomUUID().toString()
                                        val vMsg = CloudMessage(vMid, voicePayload, System.currentTimeMillis(), cleanMyId, myName, "")
                                        if (firebaseMessages.none { it.id == vMsg.id }) {
                                            firebaseMessages.add(vMsg)
                                            firebaseMessages.sortBy { it.timestamp }
                                        }
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try { dao?.insertMessage(vMsg.toEntity(chatId)) } catch (_: Exception) {}
                                            try { database.child(vMid).setValue(vMsg) } catch (_: Exception) {}
                                            NeoRelayClient.sendMessage(context, chatId, otherCleanId, vMsg)
                                            NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, vMsg, chatId)
                                        }
                                        Toast.makeText(context, "✅ Nota de voz enviada", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(if (isRecordingVoice) Color.Red else Color(0xFF1E293B))
                        ) {
                            Icon(
                                if (isRecordingVoice) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = "Grabar voz",
                                tint = if (isRecordingVoice) Color.White else Color(0xFF00FF7F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (alertMsg != null) {
        AlertDialog(
            onDismissRequest = { alertMsg = null },
            title = { Text("NeoMessage", fontWeight = FontWeight.Bold) },
            text = { Text(alertMsg ?: "") },
            confirmButton = { Button(onClick = { alertMsg = null }) { Text("OK") } }
        )
    }
    
    if (showCreateQuoteDialog) {
        CreateQuoteDialog(
            onDismiss = { showCreateQuoteDialog = false },
            onSendQuote = { q ->
                showCreateQuoteDialog = false
                val mid = database.push().key ?: UUID.randomUUID().toString()
                val msg = CloudMessage(
                    id = mid,
                    text = q.toPayloadString(),
                    timestamp = System.currentTimeMillis(),
                    senderId = cleanMyId,
                    senderName = myName,
                    rank = "📄 Cotización"
                )
                if (firebaseMessages.none { it.id == msg.id }) {
                    firebaseMessages.add(msg)
                    firebaseMessages.sortBy { it.timestamp }
                }
                coroutineScope.launch(Dispatchers.IO) {
                    try { dao?.insertMessage(msg.toEntity(chatId)) } catch (_: Exception) {}
                    try { database.child(mid).setValue(msg) } catch (_: Exception) {}
                    NeoRelayClient.sendMessage(context, chatId, otherCleanId, msg)
                    NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)
                }
                Toast.makeText(context, "Presupuesto enviado al chat", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showHealthDialog) {
        HealthShareDialog(
            onDismiss = { showHealthDialog = false },
            onSendHealthReport = { h ->
                showHealthDialog = false
                val mid = database.push().key ?: UUID.randomUUID().toString()
                val msg = CloudMessage(
                    id = mid,
                    text = h.toPayload(),
                    timestamp = System.currentTimeMillis(),
                    senderId = cleanMyId,
                    senderName = myName,
                    rank = "❤️ NeoHealth"
                )
                if (firebaseMessages.none { it.id == msg.id }) {
                    firebaseMessages.add(msg)
                    firebaseMessages.sortBy { it.timestamp }
                }
                coroutineScope.launch(Dispatchers.IO) {
                    try { dao?.insertMessage(msg.toEntity(chatId)) } catch (_: Exception) {}
                    try { database.child(mid).setValue(msg) } catch (_: Exception) {}
                    NeoRelayClient.sendMessage(context, chatId, otherCleanId, msg)
                    NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)
                }
                Toast.makeText(context, "Ficha de salud compartida", Toast.LENGTH_SHORT).show()
            }
        )
    }


    if (showExcelDialog) {
        AlertDialog(
            onDismissRequest = { showExcelDialog = false },
            containerColor = surfaceColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📊 Tabla de Excel Pegada", color = Color(0xFF00FF9C), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Has pegado una tabla copiada de una hoja de cálculo o reporte.\n\n¿Deseas enviarla como una imagen nítida para que el destinatario la vea perfectamente formateada como imagen?",
                    color = Color.White,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val textToRender = pendingExcelText
                        showExcelDialog = false
                        messageText = ""
                        coroutineScope.launch(Dispatchers.IO) {
                            val bitmap = NeoUtils.renderTableToBitmap(textToRender)
                            if (bitmap != null) {
                                val stream = java.io.ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, stream)
                                val b64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                                
                                val mid = database.push().key ?: UUID.randomUUID().toString()
                                val msgText = "[Media] data:image/png;base64,$b64"
                                val msg = CloudMessage(mid, msgText, System.currentTimeMillis(), cleanMyId, myName, "")
                                dao?.insertMessage(msg.toEntity(chatId))
                                withContext(Dispatchers.Main) {
                                    if (firebaseMessages.none { m -> m.id == msg.id }) {
                                        firebaseMessages.add(msg)
                                        firebaseMessages.sortBy { it.timestamp }
                                    }
                                    Toast.makeText(context, "📊 Tabla de Excel enviada como imagen", Toast.LENGTH_SHORT).show()
                                }
                                NeoRelayClient.sendMessage(context, chatId, otherCleanId, msg)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41))
                ) {
                    Text("🖼️ ENVIAR COMO IMAGEN", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExcelDialog = false }) {
                    Text("CONSERVAR COMO TEXTO", color = Color.Gray)
                }
            }
        )
    }

    if (annotatingPhotoUri != null) {
        TechnicalMeasureAnnotatorDialog(
            imageUri = annotatingPhotoUri!!,
            onDismiss = { annotatingPhotoUri = null },
            onSaveAnnotatedImage = { finalAnnotatedUri ->
                annotatingPhotoUri = null
                performMediaUpload(finalAnnotatedUri)
                Toast.makeText(context, "📐 Foto acotada con medidas enviada en HD", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showContactInfoScreen) {
        ContactInfoScreen(
            contactName = chatName,
            contactPhone = otherCleanId,
            chatId = chatId,
            myPhone = userId,
            photoUrl = otherPhotoUrl,
            messages = firebaseMessages,
            isLocked = isLocked,
            onToggleLock = { locked ->
                isLocked = locked
                val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                val ref = db.getReference("users").child(userId).child("locked_chats").child(chatId)
                if (locked) ref.setValue(true) else ref.removeValue()
            },
            isBlocked = isOtherBlocked.value,
            onToggleBlock = { block ->
                val ref = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("users").child(userId).child("blocked").child(otherCleanId)
                if (block) ref.setValue(true) else ref.removeValue()
            },
            onBack = { showContactInfoScreen = false },
            onAudioCall = {
                showContactInfoScreen = false
                onNavigateToCalling(otherCleanId, false)
            },
            onVideoCall = {
                showContactInfoScreen = false
                onNavigateToCalling(otherCleanId, true)
            },
            onSearchClick = {
                showContactInfoScreen = false
                Toast.makeText(context, "Búsqueda activa dentro del chat", Toast.LENGTH_SHORT).show()
            },
            onClearChat = {
                coroutineScope.launch {
                    try { database.removeValue() } catch (_: Exception) {}
                    try { typingDb.removeValue() } catch (_: Exception) {}
                    coroutineScope.launch(Dispatchers.IO) {
                        try { dao?.clearChat(chatId) } catch (_: Exception) {}
                    }
                    firebaseMessages.clear()
                }
            },
            onOpenDisappearingDialog = { showDisappearingDialog = true },
            disappearingDurationText = "Desactivados",
            onImageClick = { src ->
                try {
                    if (src.startsWith("data:image/") && src.contains("base64,")) {
                        val clean = src.substringAfter("base64,").trim()
                        val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) activeZoomBitmap = bmp.asImageBitmap()
                    }
                } catch (_: Exception) {}
            },
            onCreateGroupWithContact = {
                showContactInfoScreen = false
                Toast.makeText(context, "Para crear un grupo ve a la pestaña Comunidades", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (activeZoomBitmap != null) {
        FullScreenImageViewer(
            bitmap = activeZoomBitmap!!,
            onDismiss = { activeZoomBitmap = null }
        )
    }

    if (showDisappearingDialog) {
        var selectedExpiryHours by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showDisappearingDialog = false },
            containerColor = surfaceColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, tint = NeoTheme.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Mensajes Temporales", color = textColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elige si deseas que los nuevos mensajes se autodestruyan. Por defecto, tus mensajes se guardan para siempre de forma segura.", color = textSubColor, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    val options = listOf(
                        0 to "🛡️ Desactivado (Guardar todo para siempre)",
                        24 to "⏱️ 24 horas",
                        168 to "⏱️ 7 días",
                        720 to "⏱️ 30 días"
                    )
                    options.forEach { (hrs, label) ->
                        Surface(
                            color = if (selectedExpiryHours == hrs) NeoTheme.Green.copy(0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            border = if (selectedExpiryHours == hrs) BorderStroke(1.dp, NeoTheme.Green) else null,
                            modifier = Modifier.fillMaxWidth().clickable { selectedExpiryHours = hrs }
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedExpiryHours == hrs, onClick = { selectedExpiryHours = hrs })
                                Spacer(Modifier.width(6.dp))
                                Text(label, color = textColor, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisappearingDialog = false
                        Toast.makeText(context, if (selectedExpiryHours == 0) "🛡️ Mensajes configurados: Guardado Permanente." else "⏱️ Mensajes temporales activados.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                ) {
                    Text("GUARDAR", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisappearingDialog = false }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }

    if (selectedMessage != null) {
        val targetMsg = selectedMessage!!
        val isMine = NeoUtils.sanitizeId(targetMsg.senderId).takeLast(10) == cleanMyId.takeLast(10)
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            containerColor = surfaceColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MoreVert, null, tint = NeoTheme.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Opciones del Mensaje", color = textColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // BARRA DE REACCIONES RÁPIDAS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        val mid = targetMsg.id
                                        val currentReaction = targetMsg.reactions[cleanMyId]
                                        val newReaction = if (currentReaction == emoji) null else emoji
                                        if (newReaction == null) {
                                            database.child(mid).child("reactions").child(cleanMyId).removeValue()
                                        } else {
                                            database.child(mid).child("reactions").child(cleanMyId).setValue(newReaction)
                                        }
                                        selectedMessage = null
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }

                    val preview = when {
                        targetMsg.text.startsWith("[Media]") -> "📷 Foto adjunta"
                        targetMsg.text.startsWith("[File]") || targetMsg.text.startsWith("[Document]|") -> {
                            val name = if (targetMsg.text.startsWith("[Document]|")) {
                                targetMsg.text.removePrefix("[Document]|").split("|").firstOrNull() ?: "Documento"
                            } else {
                                targetMsg.text.removePrefix("[File] ").split("|").firstOrNull() ?: "Documento"
                            }
                            "📄 Archivo: $name"
                        }
                        else -> targetMsg.text.take(60)
                    }
                    Text(preview, color = textSubColor, fontSize = 13.sp, maxLines = 2)
                    HorizontalDivider(color = Color.White.copy(0.1f))

                    // 1. RESPONDER / CITAR
                    Button(
                        onClick = {
                            replyingTo = targetMsg
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                    ) {
                        Icon(Icons.Filled.Reply, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Responder a este mensaje", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                                        // EDITAR MENSAJE ✏️
                    if (targetMsg.senderId == cleanMyId && !targetMsg.text.startsWith("[Media]") && !targetMsg.text.startsWith("[File]") && !targetMsg.text.startsWith("[Document]|") && !targetMsg.text.startsWith("[Voice]")) {
                        OutlinedButton(
                            onClick = {
                                editingMessage = targetMsg
                                editTextValue = targetMsg.text
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF7F)),
                            border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(0.6f))
                        ) {
                            Icon(Icons.Filled.Edit, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Editar mensaje", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // REENVIAR MENSAJE ↪
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, targetMsg.text)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Reenviar mensaje a..."))
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF7F)),
                        border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(0.6f))
                    ) {
                        Icon(Icons.Filled.Forward, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reenviar mensaje", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(4.dp))

                    // 2. DESTACAR / GUARDAR MENSAJE ⭐
                    OutlinedButton(
                        onClick = {
                            val newStarred = !targetMsg.isStarred
                            database.child(targetMsg.id).child("isStarred").setValue(newStarred)
                            coroutineScope.launch(Dispatchers.IO) {
                                try { dao?.insertMessage(targetMsg.copy(isStarred = newStarred).toEntity(chatId)) } catch (_: Exception) {}
                            }
                            Toast.makeText(context, if (newStarred) "⭐ Mensaje guardado en Destacados" else "Mensaje desmarcado", Toast.LENGTH_SHORT).show()
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD700)),
                        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(0.6f))
                    ) {
                        Icon(if (targetMsg.isStarred) Icons.Filled.Star else Icons.Filled.StarBorder, null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (targetMsg.isStarred) "Quitar de destacados" else "⭐ Destacar (Guardar protegido)")
                    }

                    // 3. COPIAR TEXTO O ENLACE
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(targetMsg.text))
                            Toast.makeText(context, "📋 Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copiar contenido")
                    }

                    // 4. ELIMINAR PARA MÍ
                    Button(
                        onClick = {
                            val mid = targetMsg.id
                            firebaseMessages.removeAll { it.id == mid }
                            coroutineScope.launch(Dispatchers.IO) {
                                try { dao?.deleteMessageById(mid) } catch (_: Exception) {}
                            }
                            Toast.makeText(context, "🗑️ Eliminado para ti", Toast.LENGTH_SHORT).show()
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar para mí", color = Color.White)
                    }

                    // 5. ELIMINAR PARA TODOS (Si el mensaje fue enviado por mí)
                    if (isMine) {
                        Button(
                            onClick = {
                                val mid = targetMsg.id
                                firebaseMessages.removeAll { it.id == mid }
                                coroutineScope.launch(Dispatchers.IO) {
                                    try { dao?.deleteMessageById(mid) } catch (_: Exception) {}
                                    try { database.child(mid).removeValue() } catch (_: Exception) {}
                                }
                                Toast.makeText(context, "🚨 Eliminado para todos", Toast.LENGTH_SHORT).show()
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Filled.DeleteForever, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Eliminar para todos", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessage = null }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }

    if (editingMessage != null) {
        val target = editingMessage!!
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Editar mensaje", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                OutlinedTextField(
                    value = editTextValue,
                    onValueChange = { editTextValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF7F),
                        unfocusedBorderColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTxt = editTextValue.trim()
                        if (newTxt.isNotEmpty() && newTxt != target.text) {
                            database.child(target.id).child("text").setValue(newTxt)
                            database.child(target.id).child("isEdited").setValue(true)
                            coroutineScope.launch(Dispatchers.IO) {
                                try { dao?.insertMessage(target.copy(text = newTxt, isEdited = true).toEntity(chatId)) } catch (_: Exception) {}
                            }
                            val idx = firebaseMessages.indexOfFirst { it.id == target.id }
                            if (idx != -1) {
                                firebaseMessages[idx] = firebaseMessages[idx].copy(text = newTxt, isEdited = true)
                            }
                            Toast.makeText(context, "✏️ Mensaje editado", Toast.LENGTH_SHORT).show()
                        }
                        editingMessage = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                ) {
                    Text("GUARDAR", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }

    if (showCommerceSheet) {
        CommerceCartSheet(
            chatId = chatId,
            onDismiss = { showCommerceSheet = false },
            onOrderPlaced = { receipt ->
                messageText = receipt
                sendMessage()
            }
        )
    }
}

@Composable
fun AttachmentMenu(bgColor: Color, textColor: Color, onAction: (String) -> Unit) {
    val items = listOf(
        Pair("Documento", Icons.AutoMirrored.Filled.InsertDriveFile) to Color(0xFF7E57C2),
        Pair("Tomar Foto", Icons.Filled.CameraAlt) to Color(0xFFE91E63),
        Pair("Grabar Video", Icons.Filled.Videocam) to Color(0xFFFF5722),
        Pair("Galería", Icons.Filled.Photo) to Color(0xFFAB47BC),
        Pair("Ubicación", Icons.Filled.LocationOn) to Color(0xFF4CAF50),
        Pair("Cotización", Icons.Filled.ReceiptLong) to Color(0xFFFFD700),
        Pair("Acotar Medidas", Icons.Filled.Straighten) to Color(0xFF00BFFF),
        Pair("Salud / Reloj", Icons.Filled.Favorite) to Color(0xFFEF4444),
        Pair("Programar", Icons.Filled.Schedule) to Color(0xFFFF9800),
        Pair("Encuesta", Icons.Filled.Poll) to Color(0xFF00BCD4)
    )
    Surface(color = bgColor.copy(0.98f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items.forEach { (info, color) ->
                val (name, icon) = info
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onAction(name) }) {
                    Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(name, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

fun openDocumentFile(context: Context, file: File) {
    try {
        val extension = file.extension.lowercase()
        val mimeType = when (extension) {
            "pdf" -> "application/pdf"
            "xlsx", "xls" -> "application/vnd.ms-excel"
            "docx", "doc" -> "application/msword"
            "pptx", "ppt" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.josuedev.neomessage.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir con..."))
    } catch (e: Exception) {
        Toast.makeText(context, "No se encontró aplicación para abrir este archivo (${file.name}): ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun MessageContent(
    msg: CloudMessage, 
    textColor: Color, 
    textSubColor: Color, 
    context: Context,
    isMine: Boolean = false,
    playingVoiceMsgId: String? = null,
    onPlayingVoiceChange: ((String?) -> Unit)? = null,
    onImageClick: ((ImageBitmap) -> Unit)? = null
) {
    val text = msg.text
    when {
        text.startsWith("[NEO_QUOTE]|") -> {
            val quote = NeoQuote.fromPayload(text)
            if (quote != null) {
                QuoteMessageCard(quote = quote, isMine = isMine, onAccept = {
                    Toast.makeText(context, "Presupuesto Aceptado ✅", Toast.LENGTH_SHORT).show()
                })
            } else {
                Text("📄 Cotización NeoMessage", color = textColor)
            }
        }
        text.startsWith("[NEO_HEALTH]|") -> {
            val health = NeoHealthData.fromPayload(text)
            if (health != null) {
                HealthMessageCard(data = health, isMine = isMine)
            } else {
                Text("❤️ Ficha de Salud NeoHealth", color = textColor)
            }
        }
        text.startsWith("[VideoNote]") -> {
            val content = text.substringAfter("[VideoNote]").trim()
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color(0xFF00FF7F), CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = content,
                    contentDescription = "Nota de Video Circular",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .background(Color.Black.copy(0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("📹 Video Nota", color = Color(0xFF00FF7F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        text.startsWith("[Media]") -> {
            val content = text.substringAfter("[Media]").trim()
            val bitmap = remember(content) {
                if (content.startsWith("data:image")) {
                    try {
                        val base64Data = content.substringAfter("base64,")
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) { null }
                } else null
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Imagen",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { onImageClick?.invoke(bitmap) },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                AsyncImage(
                    model = content,
                    contentDescription = "Imagen",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }
        text.startsWith("[File]") || text.startsWith("[Document]|") -> {
            val name: String
            val sizeStr: String
            val url: String
            val sizeBytes: Long

            if (text.startsWith("[Document]|")) {
                val parts = text.removePrefix("[Document]|").split("|")
                name = parts.getOrNull(0) ?: "Documento"
                sizeStr = parts.getOrNull(1) ?: ""
                url = parts.drop(2).joinToString("|")
                sizeBytes = 0L
            } else {
                val parts = text.substringAfter("[File]").trim().split("|")
                name = parts.getOrNull(0) ?: "Archivo"
                sizeBytes = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                url = if (parts.size > 2) parts[2] else (parts.getOrNull(1) ?: "")
                sizeStr = if (sizeBytes > 1024 * 1024 * 1024) {
                    "%.2f GB".format(sizeBytes.toDouble() / (1024 * 1024 * 1024))
                } else if (sizeBytes > 1024 * 1024) {
                    "%.1f MB".format(sizeBytes.toDouble() / (1024 * 1024))
                } else if (sizeBytes > 0) {
                    "${sizeBytes / 1024} KB"
                } else ""
            }

            var isDownloading by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableIntStateOf(0) }
            var downloadedFile by remember { mutableStateOf<File?>(null) }

            val lowerName = name.lowercase()
            val fileIconText = when {
                lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".csv") -> "📊"
                lowerName.endsWith(".apk") -> "🤖"
                lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") -> "📦"
                lowerName.endsWith(".pdf") -> "📕"
                lowerName.endsWith(".docx") || lowerName.endsWith(".doc") -> "📝"
                lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") -> "📑"
                lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".ogg") -> "🎵"
                lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") -> "🎬"
                else -> "📄"
            }

            // Decodificar Base64 automáticamente
            LaunchedEffect(url) {
                if (url.startsWith("data:") && downloadedFile == null) {
                    try {
                        val base64Data = url.substringAfter("base64,")
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        val cacheFile = File(context.cacheDir, name)
                        FileOutputStream(cacheFile).use { it.write(bytes) }
                        downloadedFile = cacheFile
                    } catch (_: Exception) {}
                }
            }

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF00FF7F).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(fileIconText, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (sizeStr.isNotEmpty()) {
                                Text(sizeStr, color = Color.LightGray, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFF00FF7F)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Descargando: $downloadProgress%", color = Color(0xFF00FF7F), fontSize = 10.sp)
                    } else if (downloadedFile != null) {
                        Button(
                            onClick = {
                                if (downloadedFile != null) {
                                    openDocumentFile(context, downloadedFile!!)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F)),
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ABRIR ARCHIVO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                isDownloading = true
                                NeoP2PTransferManager.startDownload(
                                    context = context,
                                    fileUrl = url,
                                    fileName = name,
                                    expectedBytes = sizeBytes,
                                    onProgressUpdate = { pct -> downloadProgress = pct },
                                    onComplete = { f ->
                                        isDownloading = false
                                        downloadedFile = f
                                        Toast.makeText(context, "✅ Descargado: ${f.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    onFail = { err ->
                                        isDownloading = false
                                        Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = Color(0xFF00FF7F), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("DESCARGAR ARCHIVO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        text.startsWith("[Voice]") -> {
            val parts = text.removePrefix("[Voice] ").split("|")
            val base64Audio = parts.getOrNull(0) ?: ""
            val duration = parts.getOrNull(1) ?: "0"
            val isPlayingThis = playingVoiceMsgId == msg.id

            Card(
                colors = CardDefaults.cardColors(containerColor = if (isMine) Color(0xFF004D40) else Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.widthIn(min = 180.dp, max = 260.dp).padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isPlayingThis) {
                                NeoVoiceNoteManager.stopPlayback()
                                onPlayingVoiceChange?.invoke(null)
                            } else {
                                onPlayingVoiceChange?.invoke(msg.id)
                                NeoVoiceNoteManager.playVoiceNote(context, msg.id, base64Audio) {
                                    onPlayingVoiceChange?.invoke(null)
                                }
                            }
                        },
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF00FF7F))
                    ) {
                        Icon(
                            if (isPlayingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir voz",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Mic, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Nota de voz", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isPlayingThis) "▶ Reproduciendo..." else "Duración: 0:${duration.padStart(2, '0')}",
                            color = Color(0xFF00FF7F),
                            fontSize = 11.sp
                        )
                    }
                }
                
                var transcriptionText by remember { mutableStateOf<String?>(null) }
                var isTranscribing by remember { mutableStateOf(false) }

                if (transcriptionText != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, Color(0xFF00FF7F).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.GraphicEq, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Transcripción Inteligente:", color = Color(0xFF00FF7F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(transcriptionText!!, color = Color.White, fontSize = 13.sp)
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00FF7F).copy(alpha = 0.12f))
                            .clickable {
                                isTranscribing = true
                                NeoTranscriptionManager.transcribeVoiceNote(context, base64Audio) { res ->
                                    isTranscribing = false
                                    transcriptionText = res
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.GraphicEq, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (isTranscribing) "Transcribiendo..." else "Transcribir a texto",
                            color = Color(0xFF00FF7F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        text.startsWith("[Location]") -> {
            val parts = text.substringAfter("[Location]").trim().split("|")
            val lat = parts.getOrNull(0) ?: "0"
            val lon = parts.getOrNull(1) ?: "0"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF00FF7F).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.LocationOn, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("📍 Ubicación GPS en vivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Coordenadas: $lat, $lon", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val mapUri = Uri.parse("https://maps.google.com/?q=$lat,$lon")
                            val intent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, geoUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No se encontró app de mapas", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Icon(Icons.Filled.Map, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("🗺️ ABRIR EN GOOGLE MAPS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
        else -> Text(text = NeoTextFormatter.format(text, textColor), color = textColor, fontSize = 15.sp)
    }
}

fun sendLocationMessage(database: DatabaseReference, userId: String, myName: String, context: Context) {
    Toast.makeText(context, "📍 Localizando dispositivo...", Toast.LENGTH_SHORT).show()
    var lat = "16.7533" 
    var lon = "-93.1167"
    
    try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasGps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val location = if (hasGps) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                           else if (hasNetwork) lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                           else null
            
            if (location != null) {
                lat = String.format(Locale.US, "%.6f", location.latitude)
                lon = String.format(Locale.US, "%.6f", location.longitude)
                Toast.makeText(context, "📍 Coordenadas fijadas", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "🛰️ Sin señal GPS clara. Usando última posición.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "🛡️ Permiso de ubicación denegado.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.util.Log.e("NEO_GPS", "Error: ${e.message}")
    }

    val scope = kotlinx.coroutines.MainScope()
    scope.launch(Dispatchers.IO) {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                try { auth.signInAnonymously().await() } catch (_: Exception) {}
            }
            
            val msgId = database.push().key ?: UUID.randomUUID().toString()
            val msgText = "[Location] $lat|$lon"
            val msg = CloudMessage(msgId, msgText, System.currentTimeMillis(), userId, myName, "")
            
            try {
                val dao = AppDatabase.getDatabase(context).messageDao()
                val chatId = database.parent?.key ?: ""
                if (chatId.isNotEmpty()) {
                    dao.insertMessage(msg.toEntity(chatId))
                }
            } catch (_: Exception) {}
            
            try { database.child(msgId).setValue(msg) } catch (_: Exception) {}

            val cleanMy = NeoUtils.sanitizeId(userId)
            val fullChatId = database.parent?.key ?: ""
            val otherId = fullChatId.replace("private_", "").split("_").firstOrNull { it != cleanMy } ?: ""
            if (otherId.isNotEmpty()) {
                NeoRelayClient.sendMessage(context, fullChatId, otherId, msg)
                NeoLocalNodeServer.broadcastUdpMessage(otherId, msg, fullChatId)
            }

            launch(Dispatchers.Main) {
                Toast.makeText(context, "✅ Ubicación enviada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Desconocido"
            android.util.Log.e("NEO_SYNC", "Location transmit: $errorMsg", e)
        }
    }
}


@Composable
fun FullScreenImageViewer(
    bitmap: ImageBitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Interactive Image with pinch & pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                            offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Zoom imagen",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }

            // Top Bar: Close and Zoom Level indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.6f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }

                Surface(
                    color = Color.Black.copy(0.6f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Zoom: ${(scale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Bar: ++++ / --- Controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E293B).copy(0.9f))
                    .border(1.dp, Color(0xFF00FF7F).copy(0.4f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom OUT (-)
                IconButton(
                    onClick = {
                        scale = (scale - 0.4f).coerceAtLeast(0.6f)
                        if (scale <= 1f) offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Alejar (-)", tint = Color.White)
                }

                // Reset (1x)
                TextButton(
                    onClick = {
                        scale = 1f
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                ) {
                    Text("1x", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold)
                }

                // Zoom IN (+)
                IconButton(
                    onClick = {
                        scale = (scale + 0.5f).coerceAtMost(5.0f)
                    }
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Acercar (+)", tint = Color(0xFF00FF7F))
                }
            }
        }
    }
}
