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
    var selectedStatusForView by remember { mutableStateOf<UserStatus?>(null) }
    var showTextStatusDialog by remember { mutableStateOf(false) }
    var textStatusInput by remember { mutableStateOf("") }
    var textStatusColor by remember { mutableStateOf("#00FF7F") }

    // Función para manejar la subida (NUEVO: SOPORTE VIDEO Y MÚSICA MAESTRA)
    val uploadStatus = { uri: Uri ->
        if (!isUploading) {
            isUploading = true
            Toast.makeText(context, "📡 Publicando estado por 24 horas...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 1. Compresión optimizada para Base64 ultraligero (40-60 KB)
                    val bytes = NeoUtils.compressImage(context, uri, quality = 60, maxDimension = 600)
                    val imgData = if (bytes != null) "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) else ""

                    val newId = database.push().key ?: UUID.randomUUID().toString()
                    val status = UserStatus(
                        id = newId,
                        text = "Estado NeoMessage",
                        timestamp = System.currentTimeMillis(),
                        senderName = myName.ifEmpty { "Usuario" },
                        senderId = userId,
                        imageUrl = imgData,
                        musicName = selectedMusicName,
                        musicUrl = null
                    )
                    
                    // 2. Guardar en Firebase sin requerir FirebaseAuth anónimo bloqueante
                    database.child(newId).setValue(status)
                        .addOnSuccessListener {
                            isUploading = false
                            selectedMusicName = null
                            Toast.makeText(context, "✅ ¡Estado publicado! Durará 24 horas.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { err ->
                            isUploading = false
                            Toast.makeText(context, "❌ Error al publicar: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                } catch (e: Exception) {
                    coroutineScope.launch(Dispatchers.Main) {
                        isUploading = false
                        Toast.makeText(context, "❌ Error al procesar imagen: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            pendingUri = it
            showMusicDialog = true 
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
                        onClick = { galleryLauncher.launch("image/*") },
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
                                .clickable { galleryLauncher.launch("image/*") }
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
                pendingUri?.let { uploadStatus(it) }
            },
            containerColor = surfaceColor,
            title = { Text("Añadir banda sonora", color = textColor, fontWeight = FontWeight.Bold) },
            text = {
                val musicList = listOf("NeoPhonk 2026", "Cyber Stream", "Emerald Night", "Bass Protocol", "Sin Música")
                Column {
                    musicList.forEach { music ->
                        Row(
                            Modifier.fillMaxWidth().clickable { 
                                selectedMusicName = if(music == "Sin Música") null else music
                                showMusicDialog = false
                                pendingUri?.let { uploadStatus(it) }
                                Unit
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if(music == "Sin Música") Icons.Filled.MusicOff else Icons.Filled.MusicNote, null, tint = NeoTheme.Green)
                            Spacer(Modifier.width(12.dp))
                            Text(music, color = textColor)
                        }
                    }
                }
            },
            confirmButton = {}
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

        LaunchedEffect(status.id) {
            progress = 0f
            while (progress < 1f) {
                kotlinx.coroutines.delay(50)
                progress += 0.01f
            }
            selectedStatusForView = null
        }

        val bgHex = status.musicUrl ?: "#00FF7F"
        val statusBgColor = try { Color(android.graphics.Color.parseColor(bgHex)) } catch (_: Exception) { Color(0xFF0F172A) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (status.imageUrl.isEmpty()) statusBgColor else Color.Black)
                .clickable { selectedStatusForView = null }
        ) {
            if (status.imageUrl.isNotEmpty()) {
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
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(status.timestamp)), color = Color.LightGray, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))

                    // Botón para eliminar mi propio estado
                    if (isMyStatus) {
                        IconButton(onClick = {
                            database.child(status.id).removeValue()
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
