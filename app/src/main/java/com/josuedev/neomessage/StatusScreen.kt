package com.josuedev.neomessage

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.withContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    userId: String,
    myName: String,
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    val statuses = remember { mutableStateListOf<UserStatus>() }
    val database = remember { FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("status") }
    val storage = remember { com.google.firebase.storage.FirebaseStorage.getInstance("gs://" + NetworkConfig.FIREBASE_STORAGE_BUCKET).reference }
    
    var showMusicDialog by remember { mutableStateOf(false) }
    var selectedMusicName by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingIsVideo by remember { mutableStateOf(false) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var selectedStatusForView by remember { mutableStateOf<UserStatus?>(null) }
    var showTextStatusDialog by remember { mutableStateOf(false) }
    var textStatusInput by remember { mutableStateOf("") }
    var textStatusColor by remember { mutableStateOf("#00FF7F") }

    val toastMain: (String) -> Unit = { msg ->
        coroutineScope.launch(Dispatchers.Main) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    }
    val displayNameOf: (Uri) -> String = { u ->
        try {
            context.contentResolver.query(u, null, null, null, null)?.use { cur ->
                val i = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cur.moveToFirst() && i >= 0) cur.getString(i) else null
            } ?: "Canción"
        } catch (_: Exception) { "Canción" }
    }

    // Subida de estados: FOTO o VIDEO, con MÚSICA opcional (estilo WhatsApp).
    // Lo pesado (video/audio) va a status_media/{id} y solo se descarga al abrir el estado,
    // así la lista de estados se mantiene ligera y no gasta tu cuota de Firebase.
    val uploadStatus = { uri: Uri, isVideo: Boolean ->
        if (!isUploading) {
            isUploading = true
            val audioUri = selectedAudioUri
            val audioName = selectedMusicName
            Toast.makeText(context, "📡 Publicando estado por 24 horas...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val newId = database.push().key ?: UUID.randomUUID().toString()
                    val mediaDb = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("status_media").child(newId)
                    var thumb = ""
                    var hasMedia = false
                    var audioOk = false

                    if (isVideo) {
                        val vbytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (vbytes == null || vbytes.size > 8 * 1024 * 1024) {
                            toastMain("⚠️ El video pesa más de 8 MB. Elige uno más corto (hasta ~30 s).")
                            isUploading = false
                            return@launch
                        }
                        try {
                            val r = android.media.MediaMetadataRetriever()
                            r.setDataSource(context, uri)
                            val frame = r.getFrameAtTime(0)
                            r.release()
                            if (frame != null) {
                                val w = 400
                                val h = (frame.height * (w.toFloat() / frame.width)).toInt().coerceAtLeast(1)
                                val sc = android.graphics.Bitmap.createScaledBitmap(frame, w, h, true)
                                val bos = java.io.ByteArrayOutputStream()
                                sc.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, bos)
                                thumb = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
                            }
                        } catch (_: Exception) {}
                        mediaDb.child("video").setValue("data:video/mp4;base64," + android.util.Base64.encodeToString(vbytes, android.util.Base64.NO_WRAP)).await()
                        hasMedia = true
                    } else {
                        val bytes = NeoUtils.compressImage(context, uri, quality = 60, maxDimension = 600)
                        thumb = if (bytes != null) "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) else ""
                    }

                    if (audioUri != null) {
                        val abytes = try { context.contentResolver.openInputStream(audioUri)?.use { it.readBytes() } } catch (_: Exception) { null }
                        if (abytes != null && abytes.size <= 2 * 1024 * 1024) {
                            val mime = context.contentResolver.getType(audioUri) ?: "audio/mpeg"
                            mediaDb.child("audio").setValue("data:$mime;base64," + android.util.Base64.encodeToString(abytes, android.util.Base64.NO_WRAP)).await()
                            hasMedia = true
                            audioOk = true
                        } else {
                            toastMain("⚠️ La canción pesa más de 2 MB. Se publicó sin música (usa un fragmento de ~1 min).")
                        }
                    }

                    val status = UserStatus(
                        id = newId,
                        text = if (isVideo) "Video de estado" else "Estado NeoMessage",
                        timestamp = System.currentTimeMillis(),
                        senderName = myName.ifEmpty { "Usuario" },
                        senderId = userId,
                        imageUrl = thumb,
                        musicName = if (audioOk) audioName else null,
                        musicUrl = null,
                        mediaType = if (isVideo) "video" else "image",
                        hasMedia = hasMedia
                    )
                    database.child(newId).setValue(status)
                        .addOnSuccessListener {
                            isUploading = false
                            selectedMusicName = null
                            selectedAudioUri = null
                            toastMain("✅ ¡Estado publicado! Durará 24 horas.")
                        }
                        .addOnFailureListener { err ->
                            isUploading = false
                            toastMain("❌ Error al publicar: ${err.message}")
                        }
                } catch (e: Exception) {
                    isUploading = false
                    toastMain("❌ Error al procesar el estado: ${e.message}")
                }
            }
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pendingUri = it
            pendingIsVideo = false
            selectedAudioUri = null
            selectedMusicName = null
            showMusicDialog = true
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pendingUri = it
            pendingIsVideo = true
            selectedAudioUri = null
            selectedMusicName = null
            showMusicDialog = true
        }
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedAudioUri = it
            selectedMusicName = displayNameOf(it)
            showMusicDialog = false
            pendingUri?.let { p -> uploadStatus(p, pendingIsVideo) }
        }
    }

    LaunchedEffect(Unit) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                statuses.clear()
                val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                
                snapshot.children.forEach { snap ->
                    val s = snap.getValue(UserStatus::class.java)
                    if (s != null) {
                        // AQUÍ ESTÁ LA LÓGICA DE 24 HORAS
                        if (s.timestamp > twentyFourHoursAgo) {
                            statuses.add(s)
                        } else {
                            // Borrar estados viejos
                            snap.ref.removeValue()
                            try { FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("status_media").child(s.id).removeValue() } catch (_: Exception) {}
                        }
                    }
                }
                statuses.sortByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novedades", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Filled.Search, "Buscar") }
                    IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, "Opciones") }
                }
            )
        },
        floatingActionButton = {
            if (!isUploading) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = { showTextStatusDialog = true },
                        containerColor = Color(0xFF1F2C34),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(45.dp)
                    ) {
                        Icon(Icons.Default.Edit, "Texto", modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    FloatingActionButton(
                        onClick = { videoLauncher.launch("video/*") },
                        containerColor = Color(0xFF1F2C34),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(45.dp)
                    ) {
                        Icon(Icons.Filled.Videocam, "Video", modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    FloatingActionButton(
                        onClick = { photoLauncher.launch("image/*") },
                        containerColor = Color(0xFF00FF7F),
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, "Subir Estado")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(bgColor).padding(padding)
        ) {
            item {
                if (isUploading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF00FF7F))
                }
            }
            
            // ESTADOS / HISTORIAS
            item {
                Text(
                    "Estados",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp),
                    color = textColor
                )
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón "Añadir estado" (yo)
                    item {
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                                .background(surfaceColor)
                                .clickable { photoLauncher.launch("image/*") }
                        ) {
                            // Avatar placeholder
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF222222)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Person, null, modifier = Modifier.size(50.dp), tint = Color.Gray)
                            }
                            // Botón verde de más (+)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(top = 40.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FF7F).copy(0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                "Añadir estado", 
                                color = Color.White, 
                                fontSize = 13.sp, 
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                            )
                        }
                    }

                    // Lista de estados cargados
                    items(statuses) { status ->
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, if(status.senderId == userId) Color.Gray else Color(0xFF00FF7F), RoundedCornerShape(16.dp))
                                .background(Color.DarkGray)
                                .clickable { selectedStatusForView = status }
                        ) {
                            if (status.imageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = status.imageUrl,
                                    contentDescription = "Estado",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            if (status.mediaType == "video") {
                                Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(0.9f), modifier = Modifier.align(Alignment.Center).size(40.dp))
                            }
                            // Miniatura del autor arriba a la izquierda
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFF00FF7F), CircleShape)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(status.senderName.take(1), color = Color.White, fontSize = 14.sp)
                            }

                            Text(
                                if(status.senderId == userId) "Mi estado" else status.senderName, 
                                color = Color.White, 
                                fontSize = 13.sp, 
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                            )
                            
                            if (status.musicName != null) {
                                Surface(
                                    color = Color.Black.copy(0.6f),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(12.dp))
                                        Text(status.musicName, color = Color.White, fontSize = 8.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(0.05f))
            }
            
            // CANALES (UI TIPO WHATSAPP)
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Canales", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor)
                        Text(
                            "Explorar", 
                            color = Color(0xFF00FF7F), 
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(0.05f))
                                .clickable { Toast.makeText(context, "Buscando nuevos canales en la Red NeoMessage...", Toast.LENGTH_SHORT).show() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Canales Sugeridos Placeholder (Basado en captura)
            val canales = listOf(
                Triple("Stickers ❤️", "213 mil seguidores", "❤️"),
                Triple("Stickers 💞", "88 mil seguidores", "💞"),
                Triple("Becas Bienestar", "2.1 mill. seguidores", "🎓"),
                Triple("Netflix LAT", "Levanten la mano todos a los que...", "📺")
            )
            
            item {
                Text("Busca canales para seguir", color = textSubColor, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            }

            items(canales) { canal ->
                Row(modifier = Modifier.fillMaxWidth().clickable{}.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF222222)), contentAlignment = Alignment.Center) {
                        Text(canal.third, fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(canal.first, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (canal.first.contains("Becas")) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.Verified, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(canal.second, color = textSubColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { Toast.makeText(context, "Siguiendo canal: ${canal.first}", Toast.LENGTH_SHORT).show() }, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF002211)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Seguir", color = Color(0xFF00FF7F), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
            
            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    if (showMusicDialog) {
        AlertDialog(
            onDismissRequest = {
                showMusicDialog = false
                pendingUri = null
            },
            containerColor = surfaceColor,
            title = { Text("Añadir música al estado", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Elige una canción de tu celular (máx. 2 MB, un fragmento de ~1 min) o publica sin música.", color = textSubColor, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { audioLauncher.launch("audio/*") }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = NeoTheme.Green)
                        Spacer(Modifier.width(12.dp))
                        Text("🎵 Elegir canción de mi celular", color = textColor)
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedAudioUri = null
                            selectedMusicName = null
                            showMusicDialog = false
                            pendingUri?.let { uploadStatus(it, pendingIsVideo) }
                            Unit
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MusicOff, null, tint = NeoTheme.Green)
                        Spacer(Modifier.width(12.dp))
                        Text("Publicar sin música", color = textColor)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMusicDialog = false; pendingUri = null }) { Text("CANCELAR", color = Color.Gray) } }
        )
    }

    // DIALOGO CREAR ESTADO DE TEXTO CON COLORES TIPO WHATSAPP
    if (showTextStatusDialog) {
        val colorOptions = listOf("#00FF7F", "#8B5CF6", "#EC4899", "#3B82F6", "#F59E0B", "#10B981")
        AlertDialog(
            onDismissRequest = { showTextStatusDialog = false },
            title = { Text("Nuevo Estado de Texto", fontWeight = FontWeight.Bold, color = Color.White) },
            containerColor = Color(0xFF1E293B),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = textStatusInput,
                        onValueChange = { textStatusInput = it },
                        placeholder = { Text("¿Qué estás pensando?") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Text("Color de fondo:", color = Color.LightGray, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorOptions.forEach { hex ->
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Green }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (textStatusColor == hex) 3.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { textStatusColor = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textStatusInput.isNotBlank()) {
                            val newId = database.push().key ?: UUID.randomUUID().toString()
                            val status = UserStatus(
                                id = newId,
                                text = textStatusInput.trim(),
                                timestamp = System.currentTimeMillis(),
                                senderName = myName,
                                senderId = userId,
                                imageUrl = "",
                                musicUrl = textStatusColor
                            )
                            database.child(newId).setValue(status)
                            textStatusInput = ""
                            showTextStatusDialog = false
                            Toast.makeText(context, "✅ Estado publicado", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                ) {
                    Text("PUBLICAR", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextStatusDialog = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }

    // VISUALIZADOR DE ESTADOS ESTILO WHATSAPP STORIES
    if (selectedStatusForView != null) {
        val status = selectedStatusForView!!
        val isMyStatus = status.senderId == userId
        var progress by remember { mutableFloatStateOf(0f) }

        var videoPath by remember(status.id) { mutableStateOf<String?>(null) }
        var audioPath by remember(status.id) { mutableStateOf<String?>(null) }

        LaunchedEffect(status.id) {
            progress = 0f
            if (status.hasMedia) {
                try {
                    val snap = FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                        .getReference("status_media").child(status.id).get().await()
                    withContext(Dispatchers.IO) {
                        fun save(key: String, ext: String): String? {
                            val raw = snap.child(key).getValue(String::class.java) ?: return null
                            val data = raw.substringAfter("base64,", "")
                            if (data.isEmpty()) return null
                            val f = java.io.File(context.cacheDir, "status_${status.id}_$key.$ext")
                            f.writeBytes(android.util.Base64.decode(data, android.util.Base64.DEFAULT))
                            return f.absolutePath
                        }
                        videoPath = save("video", "mp4")
                        audioPath = save("audio", "mp3")
                    }
                } catch (_: Exception) {}
            }
            if (status.mediaType != "video") {
                val total = if (audioPath != null) 15000f else 5000f
                while (progress < 1f) {
                    kotlinx.coroutines.delay(50)
                    progress += 50f / total
                }
                selectedStatusForView = null
            }
        }

        DisposableEffect(audioPath) {
            var player: android.media.MediaPlayer? = null
            val ap = audioPath
            if (ap != null) {
                try {
                    player = android.media.MediaPlayer().apply {
                        setDataSource(ap)
                        isLooping = true
                        prepare()
                        start()
                    }
                } catch (_: Exception) {}
            }
            onDispose { try { player?.stop(); player?.release() } catch (_: Exception) {} }
        }

        val bgHex = status.musicUrl ?: "#00FF7F"
        val statusBgColor = try { Color(android.graphics.Color.parseColor(bgHex)) } catch (_: Exception) { Color(0xFF0F172A) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (status.imageUrl.isEmpty() && status.mediaType != "video") statusBgColor else Color.Black)
                .clickable { selectedStatusForView = null }
        ) {
            if (status.mediaType == "video" && videoPath != null) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(videoPath)
                            setOnPreparedListener { mp -> mp.isLooping = false; start() }
                            setOnCompletionListener { selectedStatusForView = null }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (status.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = status.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Estado de Texto Centrado
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        status.text,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // UI OVERLAY SUPERIOR
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                // BARRA DE PROGRESO ANIMADA
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(0.3f)
                )

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedStatusForView = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF00FF7F)), contentAlignment = Alignment.Center) {
                        Text(status.senderName.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(status.senderName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(status.timestamp)) + (if (status.musicName != null) "  🎵 ${status.musicName}" else ""),
                            color = Color.LightGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    // Botón para eliminar mi propio estado
                    if (isMyStatus) {
                        IconButton(onClick = {
                            database.child(status.id).removeValue()
                            try { FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("status_media").child(status.id).removeValue() } catch (_: Exception) {}
                            selectedStatusForView = null
                            Toast.makeText(context, "🗑️ Estado eliminado", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar estado", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
