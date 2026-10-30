package com.josuedev.neomessage

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    myId: String,
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onBack: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToCall: (String, String) -> Unit
) {
    val context = LocalContext.current
    val spContacts = remember { context.getSharedPreferences("NeoContacts", Context.MODE_PRIVATE) }
    
    val users = remember { mutableStateListOf<Map<String, String>>() }
    val savedContacts = remember { mutableStateListOf<Map<String, String>>() }
    val localContacts = remember { mutableStateListOf<Map<String, String>>() }
    
    var searchQuery by remember { mutableStateOf("") }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var inputContactName by remember { mutableStateOf("") }
    var inputContactPhone by remember { mutableStateOf("") }
    var isCheckingContact by remember { mutableStateOf(false) }
    var alertMsg by remember { mutableStateOf<String?>(null) }
    var contactToPurge by remember { mutableStateOf<Map<String, String>?>(null) }

    val cleanMyId = NeoUtils.sanitizeId(myId)
    val db = remember { FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL) }
    val usersRef = remember { db.getReference("users") }
    val myContactsRef = remember { db.getReference("users").child(cleanMyId).child("contacts") }

    // Permiso de lectura de contactos telefónicos
    val contactPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            syncLocalContactsAction(context, localContacts)
            Toast.makeText(context, "Sincronizados ${localContacts.size} contactos de tu agenda", Toast.LENGTH_SHORT).show()
        } else {
            alertMsg = "Acceso denegado a contactos del teléfono."
        }
    }

    LaunchedEffect(cleanMyId) {
        // 1. CARGA INMEDIATA LOCAL (Garantiza que siempre aparezcan sin importar la red o Firebase)
        val localList = mutableListOf<Map<String, String>>()
        spContacts.all.forEach { (phone, nameObj) ->
            val clean = NeoUtils.sanitizeId(phone)
            if (clean.isNotEmpty() && clean != cleanMyId) {
                localList.add(mapOf("id" to clean, "name" to (nameObj?.toString() ?: "Contacto")))
            }
        }
        savedContacts.clear()
        savedContacts.addAll(localList.distinctBy { it["id"] })

        // 2. Cargar Contactos Guardados de Firebase (si las reglas lo permiten)
        myContactsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tmpSaved = mutableListOf<Map<String, String>>()
                snapshot.children.forEach { snap ->
                    val cId = snap.child("id").getValue(String::class.java) ?: snap.key ?: ""
                    val cName = snap.child("name").getValue(String::class.java) ?: "Contacto"
                    val cleanId = NeoUtils.sanitizeId(cId)
                    if (cleanId.isNotEmpty() && cleanId != cleanMyId) {
                        tmpSaved.add(mapOf("id" to cleanId, "name" to cName))
                        // Respaldar también localmente
                        spContacts.edit().putString(cleanId, cName).apply()
                    }
                }
                if (tmpSaved.isNotEmpty()) {
                    savedContacts.clear()
                    savedContacts.addAll(tmpSaved.distinctBy { it["id"] })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 3. Cargar Usuarios Globales de la red
        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tmpUsers = mutableListOf<Map<String, String>>()
                snapshot.children.forEach { snap ->
                    val serverKey = snap.key ?: ""
                    val serverId = snap.child("id").getValue(String::class.java) ?: serverKey
                    val name = snap.child("name").getValue(String::class.java) ?: "Usuario NeoMessage"
                    val cleanId = NeoUtils.sanitizeId(serverId)
                    
                    if (cleanId.isNotEmpty() && cleanId != cleanMyId) {
                        tmpUsers.add(mapOf(
                            "id" to cleanId,
                            "name" to name,
                            "status" to (snap.child("status").getValue(String::class.java) ?: "Online")
                        ))
                    }
                }
                users.clear()
                users.addAll(tmpUsers.distinctBy { it["id"] })
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Sincronizar agenda telefónica automáticamente si tiene permiso, o solicitarlo
        val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            syncLocalContactsAction(context, localContacts)
        } else {
            contactPermLauncher.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }

    // --- MODAL: AGREGAR CONTACTO Y VALIDAR EN NEOMESSAGE ---
    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCheckingContact) showAddContactDialog = false },
            containerColor = surfaceColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PersonAdd, null, tint = Color(0xFF00FF7F))
                    Spacer(Modifier.width(8.dp))
                    Text("Añadir Contacto", color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Ingresa el número y nombre del contacto para añadirlo y comenzar a chatear:",
                        color = textSubColor,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = inputContactName,
                        onValueChange = { inputContactName = it },
                        label = { Text("Nombre o Alias") },
                        placeholder = { Text("Ej: Carlos López", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00FF7F),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    OutlinedTextField(
                        value = inputContactPhone,
                        onValueChange = { inputContactPhone = it },
                        label = { Text("Número de Teléfono") },
                        placeholder = { Text("Ej: 9611234567", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00FF7F),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    if (isCheckingContact) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color(0xFF00FF7F), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Guardando contacto...", color = Color(0xFF00FF7F), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rawPhone = inputContactPhone.filter { it.isDigit() }
                        val cleanPhone = NeoUtils.sanitizeId(rawPhone)
                        val nameToSave = inputContactName.trim().ifEmpty { "Contacto ($cleanPhone)" }

                        if (cleanPhone.length < 7) {
                            alertMsg = "Por favor ingresa un número de teléfono válido (mínimo 7 a 10 dígitos)."
                            return@Button
                        }

                        if (cleanPhone == cleanMyId) {
                            alertMsg = "No puedes agregarte a ti mismo como contacto."
                            return@Button
                        }

                        isCheckingContact = true

                        // 1. GUARDADO LOCAL INMEDIATO (Garantizado que NUNCA falle)
                        spContacts.edit().putString(cleanPhone, nameToSave).apply()
                        if (savedContacts.none { it["id"] == cleanPhone }) {
                            savedContacts.add(0, mapOf("id" to cleanPhone, "name" to nameToSave))
                        }

                        // 2. Consulta directa del usuario por nodo específico en Firebase
                        // (Consultar users/{cleanPhone} directamente evita el error de permisos en la raíz)
                        usersRef.child(cleanPhone).get().addOnSuccessListener { snap ->
                            isCheckingContact = false
                            val registeredName = snap.child("name").getValue(String::class.java)
                            val finalName = if (inputContactName.isNotBlank()) inputContactName.trim() else (registeredName ?: nameToSave)

                            // Actualizar con el nombre definitivo
                            spContacts.edit().putString(cleanPhone, finalName).apply()

                            // Respaldar en Firebase en su lista de contactos
                            try {
                                myContactsRef.child(cleanPhone).setValue(mapOf(
                                    "id" to cleanPhone,
                                    "name" to finalName,
                                    "savedAt" to System.currentTimeMillis()
                                ))
                            } catch (_: Exception) {}

                            showAddContactDialog = false
                            inputContactName = ""
                            inputContactPhone = ""

                            val chatKey = NeoUtils.getChatId(cleanMyId, cleanPhone)
                            Toast.makeText(context, "✅ Contacto guardado con éxito.", Toast.LENGTH_SHORT).show()
                            onNavigateToChat(finalName, chatKey)
                        }.addOnFailureListener {
                            // Si Firebase falla por reglas de seguridad o modo offline,
                            // ¡EL CONTACTO YA ESTÁ GUARDADO LOCALMENTE Y EL CHAT SE ABRE SIN BLOQUEOS!
                            isCheckingContact = false
                            try {
                                myContactsRef.child(cleanPhone).setValue(mapOf(
                                    "id" to cleanPhone,
                                    "name" to nameToSave,
                                    "savedAt" to System.currentTimeMillis()
                                ))
                            } catch (_: Exception) {}

                            showAddContactDialog = false
                            inputContactName = ""
                            inputContactPhone = ""

                            val chatKey = NeoUtils.getChatId(cleanMyId, cleanPhone)
                            Toast.makeText(context, "✅ Contacto '$nameToSave' guardado. Abriendo chat...", Toast.LENGTH_SHORT).show()
                            onNavigateToChat(nameToSave, chatKey)
                        }
                    },
                    enabled = !isCheckingContact && inputContactPhone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                ) {
                    Text("GUARDAR Y ABRIR CHAT", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddContactDialog = false },
                    enabled = !isCheckingContact
                ) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }

    // MODAL DE ALERTA GENERAL
    if (alertMsg != null) {
        AlertDialog(
            onDismissRequest = { alertMsg = null },
            containerColor = surfaceColor,
            title = { Text("NeoMessage", color = textColor, fontWeight = FontWeight.Bold) },
            text = { Text(alertMsg!!, color = textSubColor) },
            confirmButton = { Button(onClick = { alertMsg = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))) { Text("OK", color = Color.Black) } }
        )
    }

    // MODAL DE ELIMINAR CONTACTO
    if (contactToPurge != null) {
        AlertDialog(
            onDismissRequest = { contactToPurge = null },
            containerColor = surfaceColor,
            title = { Text("ELIMINAR CONTACTO", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas eliminar a '${contactToPurge!!["name"]}' (${contactToPurge!!["id"]}) de tus contactos guardados?", color = textColor) },
            confirmButton = {
                Button(onClick = {
                    val otherId = contactToPurge!!["id"] ?: ""
                    if (otherId.isNotEmpty()) {
                        spContacts.edit().remove(otherId).apply()
                        savedContacts.removeAll { it["id"] == otherId }
                        try { myContactsRef.child(otherId).removeValue() } catch (_: Exception) {}
                        contactToPurge = null
                        Toast.makeText(context, "Contacto eliminado.", Toast.LENGTH_SHORT).show()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("ELIMINAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToPurge = null }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Seleccionar contacto", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { 
                        inputContactName = ""
                        inputContactPhone = ""
                        showAddContactDialog = true 
                    }) { Icon(Icons.Filled.PersonAdd, "Nuevo Contacto", tint = Color(0xFF00FF7F)) }
                    IconButton(onClick = { 
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            syncLocalContactsAction(context, localContacts)
                            Toast.makeText(context, "Sincronizados ${localContacts.size} contactos de tu agenda", Toast.LENGTH_SHORT).show()
                        } else {
                            contactPermLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                        }
                    }) { Icon(Icons.Filled.Sync, "Sincronizar", tint = Color(0xFF00FF7F)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // BUSCADOR INTELIGENTE (Busca por Nombre Y por Número de Teléfono)
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por nombre o número...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Color(0xFF00FF7F)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, null, tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }

            // ACCIONES PRINCIPALES
            item {
                ContactActionRow(Icons.Filled.PersonAddAlt1, "Añadir Nuevo Contacto (Por Teléfono)", Color(0xFF00FF7F)) {
                    inputContactName = ""
                    inputContactPhone = ""
                    showAddContactDialog = true
                }
                ContactActionRow(Icons.Filled.ImportContacts, "Importar de Agenda Telefónica", textColor) {
                    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        syncLocalContactsAction(context, localContacts)
                        Toast.makeText(context, "Sincronizados ${localContacts.size} contactos de tu agenda", Toast.LENGTH_SHORT).show()
                    } else {
                        contactPermLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                    }
                }
            }

            // 1. REUNIR EXCLUSIVAMENTE LOS CONTACTOS DE ESTE CELULAR (Agenda local + Guardados por el usuario)
            val myDeviceContacts = mutableMapOf<String, Map<String, String>>()
            localContacts.forEach {
                val phone10 = NeoUtils.sanitizeId(it["id"] ?: "").takeLast(10)
                if (phone10.isNotEmpty() && phone10 != cleanMyId.takeLast(10)) {
                    myDeviceContacts[phone10] = it
                }
            }
            savedContacts.forEach {
                val phone10 = NeoUtils.sanitizeId(it["id"] ?: "").takeLast(10)
                if (phone10.isNotEmpty() && phone10 != cleanMyId.takeLast(10)) {
                    val existing = myDeviceContacts[phone10]
                    if (existing != null) {
                        myDeviceContacts[phone10] = existing + it
                    } else {
                        myDeviceContacts[phone10] = it
                    }
                }
            }

            // 2. USUARIOS REGISTRADOS EN LA RED
            val registeredMap = mutableMapOf<String, Map<String, String>>()
            users.forEach {
                val phone10 = NeoUtils.sanitizeId(it["id"] ?: "").takeLast(10)
                if (phone10.isNotEmpty()) {
                    registeredMap[phone10] = it
                }
            }

            // 3. PRIVACIDAD TOTAL ESTILO WHATSAPP:
            // Solo los contactos guardados en ESTE celular que tengan la app instalada
            val neoContacts = myDeviceContacts.filter { (phone10, _) ->
                registeredMap.containsKey(phone10)
            }.map { (phone10, localInfo) ->
                val regInfo = registeredMap[phone10]
                mapOf(
                    "id" to phone10,
                    "name" to (localInfo["name"] ?: regInfo?.get("name") ?: "Contacto"),
                    "photoUrl" to (regInfo?.get("photoUrl") ?: ""),
                    "status" to (regInfo?.get("status") ?: "En línea")
                )
            }
            val allNeoUsers = neoContacts

            val filtered = if (searchQuery.isEmpty()) {
                allNeoUsers
            } else {
                allNeoUsers.filter {
                    (it["name"] ?: "").contains(searchQuery, ignoreCase = true) ||
                    (it["id"] ?: "").contains(searchQuery)
                }
            }

            // SECCIÓN: CONTACTOS EN NEOMESSAGE
            if (filtered.isNotEmpty()) {
                item {
                    Text(
                        "MIS CONTACTOS NEOMESSAGE (${filtered.size})",
                        color = Color(0xFF00FF7F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }
                items(filtered) { user ->
                    val otherId = user["id"] ?: ""
                    val isSaved = savedContacts.any { it["id"] == otherId }
                    
                    ContactItem(
                        name = user["name"] ?: "Neo",
                        subtitle = "Tel: $otherId ${if (isSaved) "• Guardado" else "• En Red"}",
                        photoUrl = user["photoUrl"],
                        gradient = accentGradient,
                        textColor = textColor, 
                        textSubColor = textSubColor,
                        onCallClick = { onNavigateToCall(user["name"] ?: "Neo", otherId) },
                        onDeleteClick = { contactToPurge = user }
                    ) {
                        val chatKey = NeoUtils.getChatId(cleanMyId, otherId)
                        onNavigateToChat(user["name"] ?: "Neo", chatKey)
                    }
                }
            } else if (searchQuery.isNotEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No se encontró ningún contacto con '$searchQuery'.", color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                inputContactName = ""
                                inputContactPhone = searchQuery
                                showAddContactDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                        ) {
                            Text("Añadir '$searchQuery' como nuevo contacto", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // SECCIÓN: CONTACTOS LOCALES DE LA AGENDA DEL TELÉFONO
            if (localContacts.isNotEmpty()) {
                val filteredLocal = if (searchQuery.isEmpty()) {
                    localContacts
                } else {
                    localContacts.filter {
                        (it["name"] ?: "").contains(searchQuery, ignoreCase = true) ||
                        (it["id"] ?: "").contains(searchQuery)
                    }
                }

                if (filteredLocal.isNotEmpty()) {
                    item {
                        Text(
                            "DE TU AGENDA TELEFÓNICA (${filteredLocal.size})",
                            color = Color(0xFF64B5F6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                        )
                    }
                    items(filteredLocal.take(50)) { loc ->
                        val locId = loc["id"] ?: ""
                        val locName = loc["name"] ?: "Contacto"
                        
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                inputContactName = locName
                                inputContactPhone = locId
                                showAddContactDialog = true
                            }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF334155)), contentAlignment = Alignment.Center) {
                                Text(locName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(locName, color = textColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(locId, color = textSubColor, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    inputContactName = locName
                                    inputContactPhone = locId
                                    showAddContactDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Añadir", color = Color(0xFF00FF7F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItem(
    name: String, 
    subtitle: String, 
    photoUrl: String? = null,
    gradient: Brush, 
    textColor: Color, 
    textSubColor: Color, 
    onCallClick: () -> Unit, 
    onDeleteClick: () -> Unit, 
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(gradient), contentAlignment = Alignment.Center) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = textColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = textSubColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCallClick) { Icon(Icons.Filled.Call, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(20.dp)) }
        IconButton(onClick = onDeleteClick) { Icon(Icons.Filled.Delete, null, tint = Color.Red.copy(0.7f), modifier = Modifier.size(20.dp)) }
    }
}

@Composable
fun ContactActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    label: String, 
    textColor: Color, 
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1E293B)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

fun syncLocalContactsAction(context: Context, list: MutableList<Map<String, String>>) {
    try {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, 
            null, 
            null, 
            null, 
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val tmp = mutableListOf<Map<String, String>>()
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: "Contacto"
                val phone = it.getString(phoneIdx) ?: ""
                val clean = NeoUtils.sanitizeId(phone)
                if (clean.isNotBlank()) tmp.add(mapOf("name" to name, "id" to clean))
            }
            list.clear()
            list.addAll(tmp.distinctBy { it["id"] })
        }
    } catch (_: Exception) {}
}
