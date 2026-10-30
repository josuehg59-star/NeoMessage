package com.josuedev.neomessage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import kotlinx.coroutines.delay

@Composable
fun CallingScreen(
    chatName: String,
    callChannel: String = "",
    myId: String = "",
    otherId: String = "",
    initialIsVideo: Boolean = false,
    onHangUp: () -> Unit
) {
    val context = LocalContext.current
    var callingTime by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var isVideoActive by remember { mutableStateOf(initialIsVideo) }
    var isFrontCamera by remember { mutableStateOf(true) }
    
    // Fotograma remoto recibido y mapa multi-usuario de participantes en grupo
    var remoteBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val remoteParticipants = remember { mutableStateMapOf<String, Bitmap>() }
    var isScreenSharing by remember { mutableStateOf(false) }

    // IDENTIFICADORES 100% BLINDADOS CONTRA CRASHES DE FIREBASE
    val cleanMyId = remember(myId) {
        val sanitized = myId.filter { it.isLetterOrDigit() }
        if (sanitized.isNotEmpty()) sanitized else "local_user"
    }

    val cleanOtherId = remember(otherId, chatName) {
        val o = otherId.filter { it.isLetterOrDigit() }
        if (o.isNotEmpty()) o
        else {
            val c = chatName.filter { it.isLetterOrDigit() }
            if (c.isNotEmpty()) c else "remote_peer"
        }
    }

    val effectiveChannel = remember(callChannel, cleanMyId, cleanOtherId) {
        val raw = if (callChannel.isNotBlank()) callChannel else "call_${cleanMyId}_${cleanOtherId}"
        raw.replace("[^a-zA-Z0-9_]".toRegex(), "_")
    }

    val isGroupCall = chatName.startsWith("group_") || chatName.contains("Grupo") || chatName.contains("Comunidad")

    // CONEXIÓN BIDIRECCIONAL DE AUDIO Y VIDEO EN TIEMPO REAL
    LaunchedEffect(effectiveChannel) {
        try {
            val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            val channelRef = db.getReference("calls_live").child(effectiveChannel)

            // 1. Escuchar video multi-peer (1 a 1 y llamadas grupales)
            channelRef.child("video").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        snapshot.children.forEach { peerSnap ->
                            val pId = peerSnap.key ?: ""
                            if (pId.isNotEmpty() && pId != cleanMyId) {
                                val b64 = peerSnap.getValue(String::class.java)
                                if (!b64.isNullOrEmpty()) {
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bmp != null) {
                                        remoteParticipants[pId] = bmp
                                        remoteBitmap = bmp
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CallingScreen", "Error decodificando video multi-peer: ${e.message}")
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            // 2. Escuchar audio multi-peer mezclado
            channelRef.child("audio").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        snapshot.children.forEach { peerSnap ->
                            val pId = peerSnap.key ?: ""
                            if (pId.isNotEmpty() && pId != cleanMyId) {
                                val b64 = peerSnap.getValue(String::class.java)
                                if (!b64.isNullOrEmpty()) {
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    NeoVoiceCallManager.playAudioChunk(bytes)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CallingScreen", "Error en audio multi-peer: ${e.message}")
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            // 3. Transmitir audio local
            NeoVoiceCallManager.onAudioChunkCaptured = { chunk ->
                if (!isMuted && chunk.isNotEmpty()) {
                    try {
                        val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                        channelRef.child("audio").child(cleanMyId).setValue(b64)
                    } catch (_: Exception) {}
                }
            }

            // Iniciar motor de llamadas VoIP
            NeoVoiceCallManager.startCall(context, isSpeakerphone = isSpeakerOn)

        } catch (e: Exception) {
            Log.e("CallingScreen", "Error inicializando llamada: ${e.message}", e)
        }

        // Contador de tiempo
        while (true) {
            delay(1000)
            callingTime++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                NeoVoiceCallManager.onAudioChunkCaptured = null
                NeoVoiceCallManager.endCall(context)
                val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                db.getReference("calls_live").child(effectiveChannel).removeValue()
            } catch (_: Exception) {}
        }
    }

    // Tono de llamada saliente ("tuuuut... tuuuut...") mientras espera respuesta
    LaunchedEffect(callingTime, remoteParticipants.size) {
        if (callingTime in 1..8 && remoteParticipants.isEmpty()) {
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICE_CALL, 70)
                toneGen.startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE, 1800)
                kotlinx.coroutines.delay(3000)
                toneGen.release()
            } catch (_: Exception) {}
        }
    }

    val timeStr = remember(callingTime) {
        val mins = callingTime / 60
        val secs = callingTime % 60
        "%02d:%02d".format(mins, secs)
    }

    // Animación de pulso
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        // ==================== 1. CAPA PRINCIPAL (GRID GRUPAL O 1-A-1) ====================
        if (isVideoActive && remoteParticipants.size > 1) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 90.dp, bottom = 110.dp, start = 8.dp, end = 8.dp)
            ) {
                items(remoteParticipants.entries.toList()) { (peerId, bmp) ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, Color(0xFF00FF7F), RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = peerId,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(topEnd = 8.dp),
                            modifier = Modifier.align(Alignment.BottomStart)
                        ) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(peerId, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else if (isVideoActive && remoteBitmap != null) {
            Image(
                bitmap = remoteBitmap!!.asImageBitmap(),
                contentDescription = "Video remoto en vivo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF00FF7F), Color(0xFF00BFFF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        chatName.take(1).uppercase(),
                        color = Color.Black,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    chatName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isVideoActive) "📹 Conectando video con $chatName..." else "🔒 Llamada cifrada de extremo a extremo",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }
        }

        // Overlay oscuro suave para legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // ==================== 2. CABECERA SUPERIOR ====================
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                chatName,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF7F))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isGroupCall) "Llamada Grupal • $timeStr" else if (isVideoActive) "📹 Videollamada en Vivo • $timeStr" else "📞 En llamada • $timeStr",
                    color = Color(0xFF00FF7F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ==================== 3. VENTANA PIP: CÁMARA LOCAL ====================
        if (isVideoActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 110.dp)
                    .size(width = 115.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFF00FF7F), RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                RealCameraPreview(
                    isFrontCamera = isFrontCamera,
                    onFrameCaptured = { jpegBytes ->
                        try {
                            val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                            val db = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                            db.getReference("calls_live").child(effectiveChannel).child("video").child(cleanMyId).setValue(b64)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color.Black.copy(0.6f),
                    shape = RoundedCornerShape(topStart = 8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text("Tú", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }

        // ==================== 4. BARRA DE CONTROLES INFERIOR ====================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mute Micrófono
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        NeoVoiceCallManager.setMute(isMuted)
                        Toast.makeText(context, if (isMuted) "🔇 Micrófono Silenciado" else "🎙️ Micrófono Activado", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color.Red else Color.White.copy(0.18f))
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = Color.White
                    )
                }

                // 2. Alternar Video
                IconButton(
                    onClick = {
                        isVideoActive = !isVideoActive
                        val modeText = if (isVideoActive) "📹 Cámara Activada" else "📞 Modo Solo Voz"
                        Toast.makeText(context, modeText, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isVideoActive) Color(0xFF00BFFF) else Color.White.copy(0.18f))
                ) {
                    Icon(
                        if (isVideoActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Video",
                        tint = if (isVideoActive) Color.Black else Color.White
                    )
                }

                // 3. Girar Cámara
                if (isVideoActive) {
                    IconButton(
                        onClick = {
                            isFrontCamera = !isFrontCamera
                            Toast.makeText(context, if (isFrontCamera) "🔄 Cámara Frontal" else "🔄 Cámara Trasera", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.18f))
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Girar cámara", tint = Color.White)
                    }
                }

                // 4. Altavoz
                IconButton(
                    onClick = {
                        isSpeakerOn = !isSpeakerOn
                        NeoVoiceCallManager.setSpeaker(context, isSpeakerOn)
                        val txt = if (isSpeakerOn) "📢 Altavoz Activado" else "🎧 Auricular Activado"
                        Toast.makeText(context, txt, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isSpeakerOn) Color(0xFF00FF7F) else Color.White.copy(0.18f))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Altavoz",
                        tint = if (isSpeakerOn) Color.Black else Color.White
                    )
                }

                // 4.5. COMPARTIR PANTALLA
                IconButton(
                    onClick = {
                        isScreenSharing = !isScreenSharing
                        val txt = if (isScreenSharing) "🖥️ Compartiendo pantalla en la llamada" else "⏹️ Transmisión de pantalla detenida"
                        Toast.makeText(context, txt, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isScreenSharing) Color(0xFF00BFFF) else Color.White.copy(0.18f))
                ) {
                    Icon(
                        Icons.Default.ScreenShare,
                        contentDescription = "Compartir pantalla",
                        tint = if (isScreenSharing) Color.Black else Color.White
                    )
                }

                // 5. COLGAR LLAMADA (Botón Rojo)
                IconButton(
                    onClick = {
                        onHangUp()
                    },
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626))
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Colgar llamada",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}
