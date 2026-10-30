package com.josuedev.neomessage

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoBotConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(NeoBotManager.loadConfig(context)) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Negocio, 1: Catálogo, 2: Simulador

    // Form states
    var nombreNegocio by remember { mutableStateOf(config.nombreNegocio) }
    var telefono by remember { mutableStateOf(config.telefono) }
    var horario by remember { mutableStateOf(config.horario) }
    var direccion by remember { mutableStateOf(config.direccion) }
    var mensajeBienvenida by remember { mutableStateOf(config.mensajeBienvenida) }
    var isEnabled by remember { mutableStateOf(config.isEnabled) }
    var catalogList by remember { mutableStateOf(config.catalogo.toMutableList()) }

    // Dialog state for adding product
    var showAddProductDialog by remember { mutableStateOf(false) }
    var newProdName by remember { mutableStateOf("") }
    var newProdPrice by remember { mutableStateOf("") }
    var newProdImageB64 by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    // Image picker launcher for new product
    val productPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bytes = NeoUtils.compressImage(context, uri, quality = 65, maxDimension = 500)
            if (bytes != null) {
                newProdImageB64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                Toast.makeText(context, "📸 Foto de producto lista", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Image picker launcher for existing product
    var editingProductIndex by remember { mutableIntStateOf(-1) }
    val editPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && editingProductIndex in catalogList.indices) {
            val bytes = NeoUtils.compressImage(context, uri, quality = 65, maxDimension = 500)
            if (bytes != null) {
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val current = catalogList[editingProductIndex]
                catalogList[editingProductIndex] = current.copy(imagenBase64 = b64)
                saveAllNow(
                    context, isEnabled, nombreNegocio, telefono, horario, direccion, mensajeBienvenida, catalogList
                )
                Toast.makeText(context, "📸 Foto actualizada para ${current.nombre}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Simulator states
    var simInput by remember { mutableStateOf("") }
    val simMessages = remember { mutableStateListOf<Pair<String, Boolean>>(
        "¡Hola! Escribe *Catalogo*, *Ubicacion*, *Horario*, o un producto como *coca* o *impresiones* para probar tu bot en vivo." to false
    ) }

    fun saveAll() {
        saveAllNow(context, isEnabled, nombreNegocio, telefono, horario, direccion, mensajeBienvenida, catalogList)
        config = NeoBotManager.loadConfig(context)
        Toast.makeText(context, "✅ Configuración guardada ($0.00 costo)", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF00FF7F), Color(0xFF00BFFF)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SmartToy, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("NeoBot Ventas & IA", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (isEnabled) "🟢 Auto-Respuesta ACTIVA" else "⚪ Inactivo",
                                color = if (isEnabled) Color(0xFF00FF7F) else Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { saveAll() }) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar", tint = Color(0xFF00FF7F))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A), titleContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // TABS SELECTOR
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF00FF7F)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Configuración", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Storefront, null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Catálogo (${catalogList.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Simulador", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // CONFIGURACIÓN GENERAL
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Auto-Respuesta Automática", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Responde a tus clientes en milisegundos con bienvenida, catálogo, fotos y precios.", color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = {
                                            isEnabled = it
                                            NeoBotManager.setBotEnabled(context, it)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00FF7F))
                                    )
                                }
                            }
                        }

                        item {
                            Text("DATOS DEL NEGOCIO", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        item {
                            OutlinedTextField(
                                value = nombreNegocio,
                                onValueChange = { nombreNegocio = it },
                                label = { Text("Nombre del Negocio") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = telefono,
                                onValueChange = { telefono = it },
                                label = { Text("Teléfono de Contacto") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = horario,
                                onValueChange = { horario = it },
                                label = { Text("Horario de Atención") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = direccion,
                                onValueChange = { direccion = it },
                                label = { Text("Dirección / Ubicación") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = mensajeBienvenida,
                                onValueChange = { mensajeBienvenida = it },
                                label = { Text("Mensaje de Bienvenida") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                        }

                        item {
                            Button(
                                onClick = { saveAll() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                            ) {
                                Text("GUARDAR CAMBIOS", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                1 -> {
                    // TAB CATÁLOGO CON FOTOS
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Productos (${catalogList.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Button(
                                onClick = { 
                                    newProdName = ""
                                    newProdPrice = ""
                                    newProdImageB64 = ""
                                    showAddProductDialog = true 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.Black)
                                Spacer(Modifier.width(4.dp))
                                Text("Añadir", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("🔍 Buscar producto o precio...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )

                        Spacer(Modifier.height(8.dp))

                        val filtered = catalogList.filter {
                            searchQuery.isEmpty() || it.nombre.contains(searchQuery, ignoreCase = true)
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered) { prod ->
                                val pIndex = catalogList.indexOf(prod)
                                var showImagePreview by remember { mutableStateOf(false) }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Thumbnail o botón de agregar foto
                                            if (prod.imagenBase64.isNotEmpty()) {
                                                val bmp = remember(prod.imagenBase64) {
                                                    try {
                                                        val bytes = android.util.Base64.decode(prod.imagenBase64, android.util.Base64.DEFAULT)
                                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                                    } catch (_: Exception) { null }
                                                }
                                                if (bmp != null) {
                                                    Image(
                                                        bitmap = bmp,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { showImagePreview = !showImagePreview },
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Image, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(36.dp))
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = {
                                                        editingProductIndex = pIndex
                                                        editPhotoLauncher.launch("image/*")
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Añadir foto", tint = Color.Gray)
                                                }
                                            }

                                            Spacer(Modifier.width(10.dp))

                                            Column(Modifier.weight(1f)) {
                                                Text(prod.nombre, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("$${prod.precio} MXN", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    if (prod.imagenBase64.isNotEmpty()) {
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            if (showImagePreview) "Ocultar foto" else "Ver foto",
                                                            color = Color(0xFF00BFFF),
                                                            fontSize = 11.sp,
                                                            modifier = Modifier.clickable { showImagePreview = !showImagePreview }
                                                        )
                                                    }
                                                }
                                            }

                                            // Cambiar foto
                                            IconButton(
                                                onClick = {
                                                    editingProductIndex = pIndex
                                                    editPhotoLauncher.launch("image/*")
                                                }
                                            ) {
                                                Icon(Icons.Default.CameraAlt, contentDescription = "Foto", tint = if (prod.imagenBase64.isNotEmpty()) Color(0xFF00FF7F) else Color.Gray)
                                            }

                                            // Eliminar
                                            IconButton(
                                                onClick = {
                                                    catalogList.remove(prod)
                                                    saveAll()
                                                }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFF5252))
                                            }
                                        }

                                        // Foto desplegable
                                        AnimatedVisibility(visible = showImagePreview && prod.imagenBase64.isNotEmpty()) {
                                            val bmp = remember(prod.imagenBase64) {
                                                try {
                                                    val bytes = android.util.Base64.decode(prod.imagenBase64, android.util.Base64.DEFAULT)
                                                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                                } catch (_: Exception) { null }
                                            }
                                            if (bmp != null) {
                                                Spacer(Modifier.height(8.dp))
                                                Image(
                                                    bitmap = bmp,
                                                    contentDescription = "Foto ampliada",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(180.dp)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB SIMULADOR EN VIVO
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                "⚡ Prueba aquí tus respuestas. Escribe: catalogo, ubicacion, horario, o foto [producto].",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(simMessages) { msg ->
                                val isUser = msg.second
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        color = if (isUser) Color(0xFF2E7D32) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.widthIn(max = 300.dp)
                                    ) {
                                        Text(msg.first, color = Color.White, modifier = Modifier.padding(10.dp), fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = simInput,
                                onValueChange = { simInput = it },
                                placeholder = { Text("Escribe (catalogo, ubicacion)...", color = Color.Gray) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (simInput.isNotBlank()) {
                                        val q = simInput.trim()
                                        simMessages.add(q to true)
                                        simInput = ""
                                        val reply = NeoBotManager.processMessage(context, q)
                                        simMessages.add(reply to false)
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF00FF7F))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Black)
                            }
                        }
                    }
                }
            }
        }

        // DIÁLOGO AÑADIR PRODUCTO CON FOTO
        if (showAddProductDialog) {
            AlertDialog(
                onDismissRequest = { showAddProductDialog = false },
                title = { Text("Nuevo Producto o Servicio", color = Color.White, fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFF1E293B),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newProdName,
                            onValueChange = { newProdName = it },
                            label = { Text("Nombre del artículo") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = newProdPrice,
                            onValueChange = { newProdPrice = it },
                            label = { Text("Precio en MXN (ej: 25)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )

                        // Selector de foto opcional
                        Button(
                            onClick = { productPhotoLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = Color(0xFF00FF7F))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (newProdImageB64.isNotEmpty()) "✅ Foto seleccionada" else "📷 Añadir Foto (Opcional)",
                                color = Color.White
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newProdName.isNotBlank() && newProdPrice.isNotBlank()) {
                                catalogList.add(
                                    BotProduct(
                                        nombre = newProdName.trim().uppercase(),
                                        precio = newProdPrice.trim(),
                                        imagenBase64 = newProdImageB64
                                    )
                                )
                                saveAll()
                                newProdName = ""
                                newProdPrice = ""
                                newProdImageB64 = ""
                                showAddProductDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                    ) {
                        Text("Añadir", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddProductDialog = false }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }
    }
}

private fun saveAllNow(
    context: android.content.Context,
    isEnabled: Boolean,
    nombreNegocio: String,
    telefono: String,
    horario: String,
    direccion: String,
    mensajeBienvenida: String,
    catalogList: List<BotProduct>
) {
    val updated = BotConfig(
        isEnabled = isEnabled,
        nombreNegocio = nombreNegocio.trim(),
        telefono = telefono.trim(),
        horario = horario.trim(),
        direccion = direccion.trim(),
        mensajeBienvenida = mensajeBienvenida.trim(),
        catalogo = catalogList.toList()
    )
    NeoBotManager.saveConfig(context, updated)
}
