package com.josuedev.neomessage

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onBack: () -> Unit,
    onNavigateToChat: (String, String) -> Unit = { _, _ -> },
    onNavigateToCall: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    val myName = sp.getString("user_name", "yo") ?: "yo"
    val myPhone = sp.getString("user_phone", "") ?: ""

    val db = remember {
        FirebaseDatabase.getInstance("https://neomessage-19ab4-default-rtdb.firebaseio.com")
    }

    // Estado de tabs
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Grupos", "Comunidades", "Difusión", "Destacados")

    // Datos en tiempo real
    val groups = remember { mutableStateListOf<NeoGroup>() }
    val communities = remember { mutableStateListOf<NeoGroup>() }
    val broadcasts = remember { mutableStateListOf<NeoGroup>() }
    val starredMessages = remember { mutableStateListOf<CloudMessage>() }
    val firebaseUsers = remember { mutableStateListOf<Map<String, String>>() }

    // Dialogs
    var alertMsg by remember { mutableStateOf<String?>(null) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showCreateCommunity by remember { mutableStateOf(false) }
    var showCreateBroadcast by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var createDesc by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var showMemberPicker by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf("") }

    // Cargar datos desde Firebase
    LaunchedEffect(Unit) {
        // Grupos
        db.getReference("groups").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                groups.clear()
                s.children.forEach { snap ->
                    val g = snap.getValue(NeoGroup::class.java) ?: return@forEach
                    if (g.type == "group" && (g.createdBy == myPhone || g.members.contains(myPhone))) {
                        groups.add(g)
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
        // Comunidades
        db.getReference("groups").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                communities.clear()
                s.children.forEach { snap ->
                    val g = snap.getValue(NeoGroup::class.java) ?: return@forEach
                    if (g.type == "community" && (g.createdBy == myPhone || g.members.contains(myPhone))) {
                        communities.add(g)
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
        // Difusión
        db.getReference("groups").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                broadcasts.clear()
                s.children.forEach { snap ->
                    val g = snap.getValue(NeoGroup::class.java) ?: return@forEach
                    if (g.type == "broadcast" && g.createdBy == myPhone) {
                        broadcasts.add(g)
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
        // Mensajes destacados
        db.getReference("starred").child(myPhone.filter { it.isDigit() })
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    starredMessages.clear()
                    s.children.forEach { snap ->
                        val m = snap.getValue(CloudMessage::class.java) ?: return@forEach
                        starredMessages.add(m)
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            })
        // Usuarios de la app
        db.getReference("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                firebaseUsers.clear()
                s.children.forEach { snap ->
                    val id = snap.child("id").getValue(String::class.java) ?: ""
                    val name = snap.child("name").getValue(String::class.java) ?: ""
                    val phone = snap.child("phone").getValue(String::class.java) ?: ""
                    if (id.isNotEmpty() && id != myPhone && id != myPhone.filter { it.isDigit() }) {
                        firebaseUsers.add(mapOf("id" to id, "name" to name, "phone" to phone))
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    fun createEntity(type: String) {
        if (createName.isBlank()) { alertMsg = "Ingresa un nombre."; return }
        val id = db.getReference("groups").push().key ?: UUID.randomUUID().toString()
        val entity = NeoGroup(
            id = id,
            name = createName.trim(),
            description = createDesc.trim(),
            createdBy = myPhone,
            createdAt = System.currentTimeMillis(),
            members = selectedMembers.toList() + listOf(myPhone),
            type = type
        )
        db.getReference("groups").child(id).setValue(entity)
            .addOnSuccessListener {
                createName = ""; createDesc = ""; selectedMembers = emptySet()
                alertMsg = when (type) {
                    "group" -> "✅ Grupo '${entity.name}' creado con ${entity.members.size} miembro(s)."
                    "community" -> "✅ Comunidad '${entity.name}' creada."
                    "broadcast" -> "✅ Lista de difusión '${entity.name}' creada con ${entity.members.size} destinatario(s)."
                    else -> "✅ Elemento creado."
                }
            }
            .addOnFailureListener { e ->
                alertMsg = "❌ Error al crear protocolo: ${e.message}"
            }
    }

    // ---- DIALOG ALERTAS ----
    if (alertMsg != null) {
        AlertDialog(
            onDismissRequest = { alertMsg = null },
            title = { Text("NeoMessage", fontWeight = FontWeight.Bold) },
            text = { Text(alertMsg!!) },
            confirmButton = { Button(onClick = { alertMsg = null }) { Text("OK") } }
        )
    }

    // ---- DIALOG SELECTOR DE MIEMBROS ----
    if (showMemberPicker) {
        AlertDialog(
            onDismissRequest = { showMemberPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, null, tint = Color(0xFF00FF7F))
                    Spacer(Modifier.width(8.dp))
                    Text("Seleccionar participantes", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("${selectedMembers.size} seleccionado(s) de ${firebaseUsers.size} usuarios en la app", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    if (firebaseUsers.isEmpty()) {
                        Text("No hay otros usuarios registrados aún. Invita a tus contactos a instalar NeoMessage.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(firebaseUsers) { user ->
                                val uid = user["id"] ?: ""
                                val uname = user["name"] ?: "Usuario"
                                val isSelected = selectedMembers.contains(uid)
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedMembers = if (isSelected) selectedMembers - uid else selectedMembers + uid
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = isSelected, onCheckedChange = {
                                        selectedMembers = if (it) selectedMembers + uid else selectedMembers - uid
                                    })
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF00FF7F)),
                                        contentAlignment = Alignment.Center
                                    ) { Text(uname.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold) }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(uname, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text(user["phone"] ?: uid, color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showMemberPicker = false
                    when (pendingType) {
                        "group" -> showCreateGroup = true
                        "community" -> showCreateCommunity = true
                        "broadcast" -> showCreateBroadcast = true
                    }
                }) { Text("CONFIRMAR (${selectedMembers.size})") }
            },
            dismissButton = { TextButton(onClick = { showMemberPicker = false }) { Text("CANCELAR") } }
        )
    }

    // ---- DIALOG CREAR GRUPO ----
    if (showCreateGroup) {
        AlertDialog(
            onDismissRequest = { showCreateGroup = false },
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Group, null, tint = Color(0xFF00FF7F)); Spacer(Modifier.width(8.dp))
                Text("Nuevo Grupo", fontWeight = FontWeight.Bold)
            } },
            text = { Column {
                OutlinedTextField(value = createName, onValueChange = { createName = it }, label = { Text("Nombre del grupo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = createDesc, onValueChange = { createDesc = it }, label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Participantes: ${selectedMembers.size} seleccionados", color = Color.Gray, fontSize = 12.sp)
                TextButton(onClick = { showCreateGroup = false; pendingType = "group"; showMemberPicker = true }) {
                    Icon(Icons.Filled.PersonAdd, null); Spacer(Modifier.width(4.dp)); Text("AGREGAR PERSONAS")
                }
            } },
            confirmButton = { Button(onClick = { showCreateGroup = false; createEntity("group") }) { Text("CREAR GRUPO") } },
            dismissButton = { TextButton(onClick = { showCreateGroup = false }) { Text("CANCELAR") } }
        )
    }

    // ---- DIALOG CREAR COMUNIDAD ----
    if (showCreateCommunity) {
        AlertDialog(
            onDismissRequest = { showCreateCommunity = false },
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, null, tint = Color(0xFF00FF7F)); Spacer(Modifier.width(8.dp))
                Text("Nueva Comunidad", fontWeight = FontWeight.Bold)
            } },
            text = { Column {
                OutlinedTextField(value = createName, onValueChange = { createName = it }, label = { Text("Nombre de la comunidad") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = createDesc, onValueChange = { createDesc = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Miembros: ${selectedMembers.size} seleccionados", color = Color.Gray, fontSize = 12.sp)
                TextButton(onClick = { showCreateCommunity = false; pendingType = "community"; showMemberPicker = true }) {
                    Icon(Icons.Filled.PersonAdd, null); Spacer(Modifier.width(4.dp)); Text("AGREGAR MIEMBROS")
                }
            } },
            confirmButton = { Button(onClick = { showCreateCommunity = false; createEntity("community") }) { Text("CREAR COMUNIDAD") } },
            dismissButton = { TextButton(onClick = { showCreateCommunity = false }) { Text("CANCELAR") } }
        )
    }

    // ---- DIALOG CREAR DIFUSIÓN ----
    if (showCreateBroadcast) {
        AlertDialog(
            onDismissRequest = { showCreateBroadcast = false },
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Campaign, null, tint = Color(0xFF00FF7F)); Spacer(Modifier.width(8.dp))
                Text("Nueva Lista de Difusión", fontWeight = FontWeight.Bold)
            } },
            text = { Column {
                OutlinedTextField(value = createName, onValueChange = { createName = it }, label = { Text("Nombre de la lista") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Los mensajes de difusión llegan a todos los destinatarios individualmente.", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Destinatarios: ${selectedMembers.size}", color = Color.Gray, fontSize = 12.sp)
                TextButton(onClick = { showCreateBroadcast = false; pendingType = "broadcast"; showMemberPicker = true }) {
                    Icon(Icons.Filled.PersonAdd, null); Spacer(Modifier.width(4.dp)); Text("SELECCIONAR DESTINATARIOS")
                }
            } },
            confirmButton = { Button(onClick = { showCreateBroadcast = false; createEntity("broadcast") }) { Text("CREAR LISTA") } },
            dismissButton = { TextButton(onClick = { showCreateBroadcast = false }) { Text("CANCELAR") } }
        )
    }

    // ---- SCAFFOLD PRINCIPAL ----
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grupos y Comunidades", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        createName = ""; createDesc = ""; selectedMembers = emptySet()
                        when (selectedTab) {
                            0 -> showCreateGroup = true
                            1 -> showCreateCommunity = true
                            2 -> showCreateBroadcast = true
                        }
                    }) {
                        Icon(Icons.Filled.Add, null, tint = Color(0xFF00FF7F))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(bgColor).padding(padding)) {

            // ---- TABS ----
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = surfaceColor,
                contentColor = Color(0xFF00FF7F),
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            // ---- CONTENIDO POR TAB ----
            when (selectedTab) {
                0 -> GroupsTab(groups, accentGradient, textColor, textSubColor, bgColor,
                    onCreateGroup = { createName = ""; createDesc = ""; selectedMembers = emptySet(); showCreateGroup = true },
                    onDeleteGroup = { id -> db.getReference("groups").child(id).removeValue() },
                    onOpenGroup = { g -> onNavigateToChat(g.name, "group_${g.id}") },
                    onCallGroup = { g -> onNavigateToCall(g.name, "group_${g.id}") }
                )
                1 -> CommunitiesTab(communities, accentGradient, textColor, textSubColor, bgColor,
                    onCreateCommunity = { createName = ""; createDesc = ""; selectedMembers = emptySet(); showCreateCommunity = true },
                    onDeleteCommunity = { id -> db.getReference("groups").child(id).removeValue() },
                    onOpenCommunity = { g -> onNavigateToChat(g.name, "comm_${g.id}") },
                    onCallCommunity = { g -> onNavigateToCall(g.name, "comm_${g.id}") }
                )
                2 -> BroadcastTab(broadcasts, accentGradient, textColor, textSubColor, bgColor,
                    onCreateBroadcast = { createName = ""; createDesc = ""; selectedMembers = emptySet(); showCreateBroadcast = true },
                    onDeleteBroadcast = { id -> db.getReference("groups").child(id).removeValue() },
                    onOpenBroadcast = { g -> onNavigateToChat(g.name, "broad_${g.id}") }
                )
                3 -> StarredTab(starredMessages, textColor, textSubColor, bgColor, surfaceColor,
                    onUnstar = { msg -> db.getReference("starred").child(myPhone.filter { it.isDigit() }).child(msg.id).removeValue() }
                )
            }
        }
    }
}

// ==================== GRUPOS TAB ====================
@Composable
fun GroupsTab(
    groups: List<NeoGroup>, accentGradient: Brush,
    textColor: Color, textSubColor: Color, bgColor: Color,
    onCreateGroup: () -> Unit, onDeleteGroup: (String) -> Unit,
    onOpenGroup: (NeoGroup) -> Unit = {},
    onCallGroup: (NeoGroup) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (groups.isEmpty()) {
            item { EmptyState("Crea tu primer Grupo", "Habla simultáneamente con varios contactos.", Icons.Filled.Group, onCreateGroup) }
        } else {
            items(groups) { g ->
                GroupItem(g, accentGradient, textColor, textSubColor, "group",
                    onOpen = { onOpenGroup(g) },
                    onCall = { onCallGroup(g) },
                    onDelete = { onDeleteGroup(g.id) }
                )
            }
        }
        item {
            Button(onClick = onCreateGroup, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))) {
                Icon(Icons.Filled.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("NUEVO GRUPO", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

// ==================== COMUNIDADES TAB ====================
@Composable
fun CommunitiesTab(
    communities: List<NeoGroup>, accentGradient: Brush,
    textColor: Color, textSubColor: Color, bgColor: Color,
    onCreateCommunity: () -> Unit, onDeleteCommunity: (String) -> Unit,
    onOpenCommunity: (NeoGroup) -> Unit = {},
    onCallCommunity: (NeoGroup) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (communities.isEmpty()) {
            item { EmptyState("Crea una Comunidad", "Organiza múltiples grupos bajo una sola comunidad.", Icons.Filled.Groups, onCreateCommunity) }
        } else {
            items(communities) { g ->
                GroupItem(g, accentGradient, textColor, textSubColor, "community",
                    onOpen = { onOpenCommunity(g) },
                    onCall = { onCallCommunity(g) },
                    onDelete = { onDeleteCommunity(g.id) }
                )
            }
        }
        item {
            Button(onClick = onCreateCommunity, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                Icon(Icons.Filled.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("NUEVA COMUNIDAD", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

// ==================== DIFUSIÓN TAB ====================
@Composable
fun BroadcastTab(
    broadcasts: List<NeoGroup>, accentGradient: Brush,
    textColor: Color, textSubColor: Color, bgColor: Color,
    onCreateBroadcast: () -> Unit, onDeleteBroadcast: (String) -> Unit,
    onOpenBroadcast: (NeoGroup) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        item {
            Surface(color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Los mensajes de difusión llegan individualmente. Solo ven que lo enviaste tú.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        if (broadcasts.isEmpty()) {
            item { EmptyState("Sin listas de difusión", "Envía mensajes a múltiples contactos a la vez.", Icons.Filled.Campaign, onCreateBroadcast) }
        } else {
            items(broadcasts) { g ->
                GroupItem(g, accentGradient, textColor, textSubColor, "broadcast",
                    onOpen = { onOpenBroadcast(g) },
                    onDelete = { onDeleteBroadcast(g.id) }
                )
            }
        }
        item {
            Button(onClick = onCreateBroadcast, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))) {
                Icon(Icons.Filled.Campaign, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("NUEVA LISTA DE DIFUSIÓN", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

// ==================== DESTACADOS TAB ====================
@Composable
fun StarredTab(
    starred: List<CloudMessage>,
    textColor: Color, textSubColor: Color, bgColor: Color, surfaceColor: Color,
    onUnstar: (CloudMessage) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (starred.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Star, null, modifier = Modifier.size(64.dp), tint = Color(0xFFFFD700))
                    Spacer(Modifier.height(16.dp))
                    Text("Sin mensajes destacados", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Mantén presionado un mensaje y toca ⭐ para guardarlo aquí.", color = textSubColor, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(starred) { msg ->
                Surface(color = surfaceColor, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(msg.senderName, color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(msg.text, color = textColor, fontSize = 14.sp)
                            Text(java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp)), color = textSubColor, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onUnstar(msg) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.StarBorder, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

// ==================== COMPONENTES COMUNES ====================
@Composable
fun GroupItem(
    g: NeoGroup, 
    grad: Brush, 
    textColor: Color, 
    textSubColor: Color, 
    type: String, 
    onOpen: () -> Unit = {},
    onCall: () -> Unit = {},
    onDelete: () -> Unit
) {
    val typeLabel = when (type) { "community" -> "Comunidad"; "broadcast" -> "Difusión"; else -> "Grupo" }
    val typeColor = when (type) { "community" -> Color(0xFFFF9800); "broadcast" -> Color(0xFFE91E63); else -> Color(0xFF00FF7F) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(grad), contentAlignment = Alignment.Center) {
            Text(g.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Surface(color = typeColor.copy(0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(typeLabel, color = typeColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
            Text("${g.members.size} participante(s)${if (g.description.isNotEmpty()) " · ${g.description}" else ""}", color = textSubColor, fontSize = 12.sp, maxLines = 1)
        }
        if (type != "broadcast") {
            IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Call, contentDescription = "Llamada grupal", tint = Color(0xFF00FF7F), modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, null, tint = Color.Gray.copy(0.6f), modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = Color.White.copy(0.05f))
}

@Composable
fun EmptyState(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onCreate: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = Color(0xFF00FF7F).copy(0.5f))
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
