package com.josuedev.neomessage

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoBotConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(NeoBotManager.loadConfig(context)) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Negocio, 1: Catálogo, 2: POS Venta, 3: Simulador
    var isPosModuleEnabled by remember { mutableStateOf(NeoPosManager.isPosEnabled(context)) }

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
                            Text("NeoBot & POS Tienda", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (isEnabled) "🟢 Bot Activo • POS Local ($0 costo)" else "⚪ Bot Inactivo • POS Local",
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF00FF7F),
                edgePadding = 4.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Negocio", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Storefront, null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Catálogo (${catalogList.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(18.dp)) }
                )
                if (isPosModuleEnabled) {
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("🛒 POS Venta", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.PointOfSale, null, modifier = Modifier.size(18.dp)) }
                    )
                }
                Tab(
                    selected = selectedTab == if (isPosModuleEnabled) 3 else 2,
                    onClick = { selectedTab = if (isPosModuleEnabled) 3 else 2 },
                    text = { Text("Simulador", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(18.dp)) }
                )
            }

            val effectiveTab = if (!isPosModuleEnabled && selectedTab == 2) 3 else selectedTab

            when (effectiveTab) {
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
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("🛒 Punto de Venta (POS Básico Opcional)", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Activa la pestaña 'POS Venta' para marcar productos de tu catálogo, crear clientes y llevar el control básico de tu tienda sin gastar megas.", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = isPosModuleEnabled,
                                        onCheckedChange = {
                                            isPosModuleEnabled = it
                                            NeoPosManager.setPosEnabled(context, it)
                                            if (!it && selectedTab == 2) selectedTab = 0
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFD700))
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

                                            IconButton(
                                                onClick = {
                                                    editingProductIndex = pIndex
                                                    editPhotoLauncher.launch("image/*")
                                                }
                                            ) {
                                                Icon(Icons.Default.CameraAlt, contentDescription = "Foto", tint = if (prod.imagenBase64.isNotEmpty()) Color(0xFF00FF7F) else Color.Gray)
                                            }

                                            IconButton(
                                                onClick = {
                                                    catalogList.remove(prod)
                                                    saveAll()
                                                }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFF5252))
                                            }
                                        }

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
                    // TAB 2: PUNTO DE VENTA (POS BÁSICO OPCIONAL) CON CATÁLOGO Y CLIENTES
                    NeoPosStoreView(
                        storeName = nombreNegocio.ifBlank { "Mi Tienda" },
                        catalogList = catalogList
                    )
                }

                3 -> {
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
                                        imagenBase64 = newProdImageB64,
                                        codigo = catalogList.size + 1
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

@Composable
fun NeoPosStoreView(
    storeName: String,
    catalogList: List<BotProduct>
) {
    val context = LocalContext.current
    var posSubTab by remember { mutableIntStateOf(0) } // 0: Vender, 1: Clientes, 2: Historial/Corte
    var customers by remember { mutableStateOf(NeoPosManager.loadCustomers(context)) }
    var sales by remember { mutableStateOf(NeoPosManager.loadSales(context)) }

    // Cart state: product index -> quantity
    val cartQuantities = remember { mutableStateMapOf<Int, Int>() }
    var selectedCustomer by remember { mutableStateOf<PosCustomer?>(null) }
    var isSalePaid by remember { mutableStateOf(true) }
    var posSearchQuery by remember { mutableStateOf("") }

    // New customer dialog
    var showNewCustomerDialog by remember { mutableStateOf(false) }
    var custNameInput by remember { mutableStateOf("") }
    var custPhoneInput by remember { mutableStateOf("") }
    var custNotesInput by remember { mutableStateOf("") }

    // Customer detail history
    var expandedCustomerId by remember { mutableStateOf<String?>(null) }

    val cartItemsList = catalogList.mapIndexedNotNull { index, prod ->
        val qty = cartQuantities[index] ?: 0
        if (qty > 0) {
            val price = prod.precio.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
            PosSaleItem(
                codigo = if (prod.codigo > 0) prod.codigo else (index + 1),
                nombre = prod.nombre,
                precioUnitario = price,
                cantidad = qty
            )
        } else null
    }
    val currentTotal = cartItemsList.sumOf { it.subtotal }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Sub-selector: Vender | Clientes | Corte
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                0 to "🛍️ Nueva Venta",
                1 to "👥 Clientes (${customers.size})",
                2 to "📊 Corte (${sales.size})"
            ).forEach { (idx, title) ->
                val active = posSubTab == idx
                Surface(
                    modifier = Modifier.weight(1f).clickable { posSubTab = idx },
                    color = if (active) Color(0xFF00FF7F) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (active) Color(0xFF00FF7F) else Color(0xFF334155))
                ) {
                    Text(
                        text = title,
                        color = if (active) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        when (posSubTab) {
            0 -> {
                // 1. SELECTOR DE CLIENTE + BOTÓN CREAR CLIENTE
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "👤 Cliente: ${selectedCustomer?.name ?: "Público en General (Mostrador)"}",
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    custNameInput = ""
                                    custPhoneInput = ""
                                    custNotesInput = ""
                                    showNewCustomerDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("➕ Nuevo Cliente", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedCustomer == null,
                                onClick = { selectedCustomer = null },
                                label = { Text("Mostrador", fontSize = 11.sp) }
                            )
                            customers.forEach { c ->
                                FilterChip(
                                    selected = selectedCustomer?.id == c.id,
                                    onClick = { selectedCustomer = c },
                                    label = {
                                        Text(
                                            if (c.phone.isNotEmpty()) "${c.name} (${c.phone})" else c.name,
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 2. BUSCADOR DEL CATÁLOGO PARA MARCAR PRODUCTOS
                OutlinedTextField(
                    value = posSearchQuery,
                    onValueChange = { posSearchQuery = it },
                    placeholder = { Text("🔍 Buscar producto del catálogo para marcar...", color = Color.Gray, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(Modifier.height(8.dp))

                // 3. LISTA DE PRODUCTOS CON BOTONES [-] [+]
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val indexedFiltered = catalogList.mapIndexed { idx, p -> idx to p }.filter { (_, p) ->
                        posSearchQuery.isBlank() || p.nombre.contains(posSearchQuery, ignoreCase = true) || p.codigo.toString().contains(posSearchQuery)
                    }
                    items(indexedFiltered) { (idx, prod) ->
                        val qty = cartQuantities[idx] ?: 0
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (qty > 0) Color(0xFF064E3B) else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = if (qty > 0) BorderStroke(1.dp, Color(0xFF00FF7F)) else null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(prod.nombre, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("$${prod.precio} MXN", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledIconButton(
                                        onClick = {
                                            if (qty > 1) cartQuantities[idx] = qty - 1
                                            else cartQuantities.remove(idx)
                                        },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155))
                                    ) {
                                        Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = qty.toString(),
                                        color = if (qty > 0) Color(0xFF00FF7F) else Color.Gray,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.widthIn(min = 24.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    FilledIconButton(
                                        onClick = { cartQuantities[idx] = qty + 1 },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF00FF7F))
                                    ) {
                                        Icon(Icons.Default.Add, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. BARRA INFERIOR DE COBRO / REGISTRO DE VENTA
                if (cartItemsList.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF00FF7F)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("${cartItemsList.sumOf { it.cantidad }} artículo(s) marcados", color = Color.LightGray, fontSize = 12.sp)
                                    Text("TOTAL: $${String.format(Locale.US, "%.2f", currentTotal)} MXN", color = Color(0xFF00FF7F), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = isSalePaid,
                                        onClick = { isSalePaid = true },
                                        label = { Text("✅ Pagado", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = !isSalePaid,
                                        onClick = { isSalePaid = false },
                                        label = { Text("⏳ Fiado", fontSize = 11.sp) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val sale = NeoPosManager.recordSale(context, selectedCustomer, cartItemsList, isSalePaid)
                                        sales = NeoPosManager.loadSales(context)
                                        cartQuantities.clear()
                                        Toast.makeText(context, "✅ Venta ${sale.folio} registrada ($${String.format(Locale.US, "%.2f", sale.total)})", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                                ) {
                                    Text("💾 Registrar Venta", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                if (!selectedCustomer?.phone.isNullOrBlank()) {
                                    Button(
                                        onClick = {
                                            val targetCustomer = selectedCustomer!!
                                            val sale = NeoPosManager.recordSale(context, targetCustomer, cartItemsList, isSalePaid)
                                            sales = NeoPosManager.loadSales(context)
                                            cartQuantities.clear()
                                            val ticketText = NeoPosManager.buildTicketText(storeName, sale)
                                            val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
                                            val myPhone = NeoUtils.sanitizeId(sp.getString("user_phone", "") ?: "").takeLast(10)
                                            val myName = sp.getString("user_name", storeName) ?: storeName
                                            val otherPhone = NeoUtils.sanitizeId(targetCustomer.phone).takeLast(10)
                                            if (myPhone.isNotEmpty() && otherPhone.isNotEmpty()) {
                                                val chatId = NeoUtils.getChatId(myPhone, otherPhone)
                                                val mid = "pos_" + System.currentTimeMillis()
                                                val msg = CloudMessage(mid, ticketText, System.currentTimeMillis(), myPhone, myName, "POS Tienda")
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    try {
                                                        AppDatabase.getDatabase(context).messageDao().insertMessage(msg.toEntity(chatId))
                                                        val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                                                        db.getReference("chats").child(chatId).child("messages").child(mid).setValue(msg)
                                                        db.getReference("inbox").child(otherPhone).child(mid).setValue(
                                                            mapOf("id" to mid, "chatId" to chatId, "senderId" to myPhone, "senderName" to myName, "text" to "🧾 Ticket de compra ${sale.folio}", "timestamp" to msg.timestamp)
                                                        )
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                            Toast.makeText(context, "📲 Venta guardada y Ticket enviado a ${targetCustomer.name}", Toast.LENGTH_LONG).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                                    ) {
                                        Text("📲 Guardar + Ticket", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // PESTAÑA CLIENTES
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cartera de Clientes (${customers.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Button(
                        onClick = {
                            custNameInput = ""
                            custPhoneInput = ""
                            custNotesInput = ""
                            showNewCustomerDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                    ) {
                        Icon(Icons.Default.PersonAdd, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Crear Cliente", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (customers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aún no has creado clientes.\nToca 'Crear Cliente' para llevar el control de quién te compra.", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(customers) { c ->
                            val customerSales = sales.filter { it.customerId == c.id }
                            val totalBought = customerSales.sumOf { it.total }
                            val pendingDebt = customerSales.filter { !it.isPaid }.sumOf { it.total }
                            val isExpanded = expandedCustomerId == c.id

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    expandedCustomerId = if (isExpanded) null else c.id
                                }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(c.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            if (c.phone.isNotEmpty()) {
                                                Text("📱 ${c.phone}", color = Color(0xFF00FF7F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            if (c.notes.isNotEmpty()) {
                                                Text("📝 ${c.notes}", color = Color.LightGray, fontSize = 12.sp)
                                            }
                                            Text(
                                                "🛍️ ${customerSales.size} compra(s) • Total: $${String.format(Locale.US, "%.2f", totalBought)}",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                            if (pendingDebt > 0) {
                                                Text(
                                                    "⏳ Saldo pendiente (Fiado): $${String.format(Locale.US, "%.2f", pendingDebt)} MXN",
                                                    color = Color(0xFFF59E0B),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                selectedCustomer = c
                                                posSubTab = 0
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Venderle", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                        IconButton(onClick = {
                                            NeoPosManager.deleteCustomer(context, c.id)
                                            customers = NeoPosManager.loadCustomers(context)
                                        }) {
                                            Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252))
                                        }
                                    }

                                    if (isExpanded && customerSales.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        HorizontalDivider(color = Color(0xFF334155))
                                        Spacer(Modifier.height(6.dp))
                                        Text("Historial de productos comprados:", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        val sdf = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault())
                                        customerSales.forEach { s ->
                                            val itemsSummary = s.items.joinToString(", ") { "${it.cantidad}x ${it.nombre}" }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("${s.folio} • ${sdf.format(Date(s.timestamp))}", color = Color.LightGray, fontSize = 11.sp)
                                                    Text(itemsSummary, color = Color.White, fontSize = 12.sp)
                                                }
                                                TextButton(onClick = {
                                                    NeoPosManager.toggleSalePaid(context, s.id)
                                                    sales = NeoPosManager.loadSales(context)
                                                }) {
                                                    Text(
                                                        if (s.isPaid) "✅ $${String.format(Locale.US, "%.0f", s.total)}" else "⏳ Cobrar $${String.format(Locale.US, "%.0f", s.total)}",
                                                        color = if (s.isPaid) Color(0xFF00FF7F) else Color(0xFFF59E0B),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // PESTAÑA CORTE DEL DÍA / HISTORIAL DE VENTAS
                // SOLO LO MARCADO / APLICADO (isPaid == true) ENTRA AL CORTE DEL DÍA
                val todayAppliedSales = sales.filter { NeoPosManager.isToday(it.timestamp) && it.isPaid }
                val todayTotal = todayAppliedSales.sumOf { it.total }
                val paidTotal = sales.filter { it.isPaid }.sumOf { it.total }
                val pendingTotal = sales.filter { !it.isPaid }.sumOf { it.total }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Corte Hoy (Aplicado)", color = Color.Gray, fontSize = 11.sp)
                            Text("$${String.format(Locale.US, "%.2f", todayTotal)}", color = Color(0xFF00FF7F), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Total Aplicado", color = Color.Gray, fontSize = 11.sp)
                            Text("$${String.format(Locale.US, "%.2f", paidTotal)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Sin Aplicar / Bot", color = Color.Gray, fontSize = 11.sp)
                            Text("$${String.format(Locale.US, "%.2f", pendingTotal)}", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                if (sales.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay ventas ni tickets registrados aún.", color = Color.Gray)
                    }
                } else {
                    val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sales) { s ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = if (!s.isPaid) BorderStroke(1.dp, Color(0xFFF59E0B).copy(0.5f)) else BorderStroke(1.dp, Color(0xFF00FF7F).copy(0.3f))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${s.folio} ${if (s.source == "CHATBOT") "🤖" else "🏪"} • 👤 ${s.customerName}",
                                            color = Color(0xFFFFD700),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(sdf.format(Date(s.timestamp)), color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    s.items.forEach { item ->
                                        Text(
                                            "• ${item.cantidad}x ${item.nombre} ($${String.format(Locale.US, "%.2f", item.subtotal)})",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "TOTAL: $${String.format(Locale.US, "%.2f", s.total)} MXN",
                                            color = if (s.isPaid) Color(0xFF00FF7F) else Color(0xFFF59E0B),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Button(
                                                onClick = {
                                                    NeoPosManager.toggleSalePaid(context, s.id)
                                                    sales = NeoPosManager.loadSales(context)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (s.isPaid) Color(0xFF065F46) else Color(0xFF00FF7F)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text(
                                                    if (s.isPaid) "✅ En Corte" else "✅ Aplicar al Corte",
                                                    color = if (s.isPaid) Color.White else Color.Black,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    NeoPosManager.deleteSale(context, s.id)
                                                    sales = NeoPosManager.loadSales(context)
                                                    Toast.makeText(context, "🗑️ Ticket eliminado (no entra al corte)", Toast.LENGTH_SHORT).show()
                                                },
                                                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252), modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(2.dp))
                                                Text("Eliminar", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showNewCustomerDialog = false },
            containerColor = Color(0xFF1E293B),
            title = { Text("👤 Nuevo Cliente de Tienda", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = custNameInput,
                        onValueChange = { custNameInput = it },
                        label = { Text("Nombre del Cliente *") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = custPhoneInput,
                        onValueChange = { custPhoneInput = it },
                        label = { Text("Teléfono (10 dígitos, opcional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = custNotesInput,
                        onValueChange = { custNotesInput = it },
                        label = { Text("Dirección / Nota (opcional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (custNameInput.isNotBlank()) {
                            val newC = NeoPosManager.addCustomer(context, custNameInput, custPhoneInput, custNotesInput)
                            customers = NeoPosManager.loadCustomers(context)
                            selectedCustomer = newC
                            showNewCustomerDialog = false
                            Toast.makeText(context, "✅ Cliente '${newC.name}' creado", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Escribe el nombre del cliente", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                ) {
                    Text("Guardar Cliente", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCustomerDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
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
