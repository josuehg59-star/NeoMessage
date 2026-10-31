package com.josuedev.neomessage

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

// ==================== 🛒 HOJA INTERACTIVA: CATÁLOGO Y CARRITO ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommerceCartSheet(
    chatId: String,
    onDismiss: () -> Unit,
    onOrderPlaced: (String) -> Unit
) {
    val context = LocalContext.current
    val cfg = remember { NeoBotManager.loadConfig(context) }
    val catalog = remember { if (cfg.catalogo.isEmpty()) NeoBotManager.getDefaultCatalog() else cfg.catalogo }
    
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Catálogo, 1 = Carrito
    var searchQuery by remember { mutableStateOf("") }
    
    // Lista reactiva del carrito
    var cartItems by remember { mutableStateOf(NeoCommerceManager.getCart(context, chatId)) }
    val totalCart = remember(cartItems) { cartItems.sumOf { it.subtotal } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Cabecera
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(cfg.nombreNegocio, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Catálogo & Carrito de Compras", color = Color.Gray, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.LightGray)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Selector de Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF00FF7F),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("📦 Catálogo (${catalog.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🛒 Carrito (${cartItems.size})", fontWeight = FontWeight.Bold)
                            if (cartItems.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    color = Color(0xFF00FF7F),
                                    shape = CircleShape,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${cartItems.sumOf { it.cantidad }}", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            if (selectedTab == 0) {
                // TAB 0: CATÁLOGO DE PRODUCTOS
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar producto (ej: takis, coca, aceite)...", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF7F),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                val filteredCatalog = remember(searchQuery, catalog) {
                    if (searchQuery.isBlank()) catalog
                    else {
                        val q = NeoBotManager.cleanText(searchQuery)
                        catalog.filter { NeoBotManager.cleanText(it.nombre).contains(q) }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCatalog) { prod ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF334155)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.ShoppingBag, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prod.nombre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                                    Text("$${prod.precio} MXN", color = Color(0xFF00FF7F), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = {
                                        NeoCommerceManager.addToCart(context, chatId, prod, 1)
                                        cartItems = NeoCommerceManager.getCart(context, chatId)
                                        Toast.makeText(context, "✅ Agregado: ${prod.nombre}", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("+ Agregar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (cartItems.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { selectedTab = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Ver Carrito (${cartItems.size} productos) • $${"%.2f".format(totalCart)} MXN", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // TAB 1: MI CARRITO
                if (cartItems.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.RemoveShoppingCart, null, tint = Color.Gray, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("El carrito está vacío", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Explora el catálogo para agregar artículos", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { selectedTab = 0 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                        ) {
                            Text("Explorar Catálogo", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cartItems) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.nombre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("$${"%.2f".format(item.precio)} c/u • Subtotal: $${"%.2f".format(item.subtotal)}", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    // Selector de cantidad - y +
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                NeoCommerceManager.updateQuantity(context, chatId, item.nombre, item.cantidad - 1)
                                                cartItems = NeoCommerceManager.getCart(context, chatId)
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Filled.Remove, null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                        }
                                        Text("${item.cantidad}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))
                                        IconButton(
                                            onClick = {
                                                NeoCommerceManager.updateQuantity(context, chatId, item.nombre, item.cantidad + 1)
                                                cartItems = NeoCommerceManager.getCart(context, chatId)
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Tarjeta de Total y Confirmación
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTAL A PAGAR:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("$${"%.2f".format(totalCart)} MXN", color = Color(0xFF00FF7F), fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        NeoCommerceManager.clearCart(context, chatId)
                                        cartItems = mutableListOf<CartItem>()
                                        Toast.makeText(context, "🗑️ Carrito vaciado", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(0.35f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                                ) {
                                    Text("Vaciar", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val (ok, receipt) = NeoCommerceManager.checkoutCart(context, chatId, "Cliente", "Cliente Móvil", cfg.nombreNegocio)
                                        if (ok) {
                                            cartItems = mutableListOf<CartItem>()
                                            onOrderPlaced(receipt)
                                            onDismiss()
                                            Toast.makeText(context, "🎉 ¡Venta registrada en caja!", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.weight(0.65f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cobrar / Confirmar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 📊 PANTALLA DE CORTE DE CAJA ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorteDeCajaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cfg = remember { NeoBotManager.loadConfig(context) }
    var todaySales by remember { mutableStateOf(NeoCommerceManager.getTodaySales(context)) }
    
    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Todos, 1: Por recoger (Pendientes), 2: Entregados

    val deliveredSales = remember(todaySales) { todaySales.filter { it.status == "ENTREGADO" } }
    val pendingSales = remember(todaySales) { todaySales.filter { it.status != "ENTREGADO" } }

    val filteredSales = remember(todaySales, selectedFilter) {
        when (selectedFilter) {
            1 -> pendingSales
            2 -> deliveredSales
            else -> todaySales
        }
    }

    val deliveredAmount = remember(deliveredSales) { deliveredSales.sumOf { it.total } }
    val pendingAmount = remember(pendingSales) { pendingSales.sumOf { it.total } }
    val count = remember(deliveredSales) { deliveredSales.size }
    val avgTicket = remember(deliveredSales) { if (count > 0) deliveredAmount / count else 0.0 }
    
    var showManualSaleDialog by remember { mutableStateOf(false) }
    var saleToDelete by remember { mutableStateOf<SaleRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💰 Corte de Caja y Pedidos", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val report = NeoCommerceManager.formatCorteDeCajaReport(context, cfg.nombreNegocio)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Corte de Caja", report))
                        Toast.makeText(context, "📋 Reporte copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar reporte", tint = Color(0xFF00FF7F))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF070B14))
            )
        },
        containerColor = Color(0xFF070B14)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tarjetas de Métricas de Caja
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF00FF7F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountBalanceWallet, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("TOTAL COBRADO EN CAJA HOY", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "$${"%.2f".format(deliveredAmount)} MXN",
                            color = Color(0xFF00FF7F),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("⏳ Por Recoger", color = Color(0xFFFFA500), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("$${"%.2f".format(pendingAmount)} MXN (${pendingSales.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("✅ Cobrados", color = Color(0xFF00FF7F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("$count tickets", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Botones de acción rápida
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showManualSaleDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("Venta Manual", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val report = NeoCommerceManager.formatCorteDeCajaReport(context, cfg.nombreNegocio)
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir Corte de Caja"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Filled.Share, null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Compartir Corte", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // FILTROS DE ÓRDENES (Todos / Por Recoger / Entregados)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == 0,
                        onClick = { selectedFilter = 0 },
                        label = { Text("Todos (${todaySales.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00FF7F),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color.LightGray
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == 1,
                        onClick = { selectedFilter = 1 },
                        label = { Text("⏳ Por recoger (${pendingSales.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFA500),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color.LightGray
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == 2,
                        onClick = { selectedFilter = 2 },
                        label = { Text("✅ Entregados (${deliveredSales.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00FF7F),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color.LightGray
                        )
                    )
                }
            }

            if (filteredSales.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.ReceiptLong, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (selectedFilter == 1) "No hay pedidos pendientes por recoger"
                                else if (selectedFilter == 2) "No hay pedidos entregados aún"
                                else "No hay ventas registradas el día de hoy",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredSales, key = { it.id }) { sale ->
                    val isPending = sale.status != "ENTREGADO"
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isPending) BorderStroke(1.dp, Color(0xFFFFA500).copy(0.4f)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(if (isPending) Color(0xFFFFA500).copy(0.15f) else Color(0xFF00FF7F).copy(0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (isPending) "⏳" else "🧾", fontSize = 16.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("#${sale.id}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sale.timestamp)), color = Color.Gray, fontSize = 11.sp)
                                    }
                                    val summary = sale.items.joinToString(", ") { "${it.cantidad}x ${it.nombre.take(15)}" }
                                    Text(summary.ifEmpty { "Venta directa" }, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "$${"%.2f".format(sale.total)} MXN",
                                        color = if (isPending) Color(0xFFFFA500) else Color(0xFF00FF7F),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        if (isPending) "Por recoger" else "Entregado",
                                        color = if (isPending) Color(0xFFFFA500) else Color(0xFF00FF7F),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = Color.White.copy(0.06f))
                            Spacer(Modifier.height(8.dp))

                            // Botones de acción de la orden (Marcar Entregado / Eliminar si no fueron a traer)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                if (isPending) {
                                    Button(
                                        onClick = {
                                            NeoCommerceManager.updateSaleStatus(context, sale.id, "ENTREGADO")
                                            todaySales = NeoCommerceManager.getTodaySales(context)
                                            Toast.makeText(context, "✅ Pedido #${sale.id} marcado como Entregado y Cobrado", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("✅ Entregar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }

                                OutlinedButton(
                                    onClick = { saleToDelete = sale },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isPending) "No vino / Eliminar" else "Eliminar", color = Color(0xFFFF5252), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para eliminar orden que no fueron a traer
    saleToDelete?.let { targetSale ->
        AlertDialog(
            onDismissRequest = { saleToDelete = null },
            containerColor = Color(0xFF0F172A),
            title = { Text("¿Eliminar Pedido #${targetSale.id}?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Si el cliente no fue a traer este pedido, al eliminarlo se descontará de la lista y no afectará tu corte de caja final.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        NeoCommerceManager.deleteSale(context, targetSale.id)
                        todaySales = NeoCommerceManager.getTodaySales(context)
                        saleToDelete = null
                        Toast.makeText(context, "🗑️ Orden eliminada correctamente", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { saleToDelete = null }) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
    }

    // Diálogo de venta manual rápida
    if (showManualSaleDialog) {
        var prodName by remember { mutableStateOf("") }
        var prodPrice by remember { mutableStateOf("") }
        var prodQty by remember { mutableStateOf("1") }

        AlertDialog(
            onDismissRequest = { showManualSaleDialog = false },
            containerColor = Color(0xFF0F172A),
            title = { Text("Registrar Venta Manual", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prodName,
                        onValueChange = { prodName = it },
                        label = { Text("Nombre o concepto") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = prodPrice,
                        onValueChange = { prodPrice = it },
                        label = { Text("Precio en $ MXN") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = prodQty,
                        onValueChange = { prodQty = it },
                        label = { Text("Cantidad") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pr = prodPrice.toDoubleOrNull() ?: 0.0
                        val qty = prodQty.toIntOrNull() ?: 1
                        if (pr > 0 && prodName.isNotBlank()) {
                            val item = CartItem(prodName.trim(), pr, qty)
                            val sale = SaleRecord(
                                customerName = "Mostrador",
                                items = listOf(item),
                                total = item.subtotal,
                                metodoPago = "Efectivo",
                                status = "ENTREGADO" // La venta manual en mostrador es cobrada y entregada de inmediato
                            )
                            NeoCommerceManager.recordSale(context, sale)
                            todaySales = NeoCommerceManager.getTodaySales(context)
                            showManualSaleDialog = false
                            Toast.makeText(context, "✅ Venta manual registrada en caja", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                ) {
                    Text("Registrar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualSaleDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
    }
}
