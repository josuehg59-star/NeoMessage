package com.josuedev.neomessage

import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import android.widget.Toast
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.josuedev.neomessage.NetworkConfig
import android.media.RingtoneManager
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas

object NeoTheme {
    val Green = Color(0xFF00FF9C) // Neon Emerald
    val GreenDark = Color(0xFF004D3C)
    val Dark = Color(0xFF020617) // Deep Navy/Black
    val Card = Color(0xFF0F172A)
    val Border = Color(0xFF1E293B)
    val PremiumGradient = Brush.linearGradient(listOf(Color(0xFF00FF9C), Color(0xFF00C9FF)))
    val WarningGradient = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))
    val LogoGradient = Brush.verticalGradient(listOf(Color(0xFF00FF9C), Color(0xFF00A550)))
}

@Composable
fun NeoLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // El Logo Real Sci-Fi con Blindaje Reforzado (NeoSync-v4)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent,
            border = BorderStroke(3.dp, NeoTheme.Green)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_neomessage_scifi),
                contentDescription = "NeoMessage Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
            )
        }
        
        // El Candado de Blindaje Maestro
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 12.dp)
                .size(42.dp),
            shape = CircleShape,
            color = NeoTheme.Dark,
            border = BorderStroke(2.dp, NeoTheme.Green),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Lock, 
                    null, 
                    modifier = Modifier.size(24.dp),
                    tint = NeoTheme.Green
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginView(
    onLogin: (String, String, String) -> Unit, 
    bgColor: Color, 
    textColor: Color, 
    isLoading: Boolean = false
) {
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) }
    var isCheckingPhone by remember { mutableStateOf(false) }
    
    var registeredOwnerName by remember { mutableStateOf<String?>(null) }
    var boundDeviceId by remember { mutableStateOf<String?>(null) }
    var boundDeviceModel by remember { mutableStateOf<String?>(null) }
    var savedSecurityPin by remember { mutableStateOf<String?>(null) }
    var isDifferentDevice by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val currentDeviceId = remember {
        try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Exception) { "" }
    }
    val currentDeviceModel = remember {
        "${android.os.Build.MANUFACTURER.uppercase()} ${android.os.Build.MODEL}".trim()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NeoTheme.Dark)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NeoLogo(Modifier.size(120.dp))
        Spacer(Modifier.height(12.dp))
        Text("NeoMessage Auth", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Protocolo de Vinculación de Dispositivo Físico", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        
        Spacer(Modifier.height(32.dp))
        
        if (isLoading || isCheckingPhone) {
            CircularProgressIndicator(color = NeoTheme.Green)
            Spacer(Modifier.height(16.dp))
            Text(if (isCheckingPhone) "Verificando hardware e identidad en red..." else "Estableciendo enlace seguro...", color = Color.Gray, fontSize = 12.sp)
        } else {
            if (step == 1) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PhoneAndroid, null, tint = NeoTheme.Green, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Dispositivo Local Detectado", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(currentDeviceModel, color = NeoTheme.Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("El número quedará enlazado a este teléfono físico para prevenir suplantación o delitos.", color = Color.LightGray, fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = phone, 
                    onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) phone = it }, 
                    label = { Text("Número de Teléfono (10 dígitos)") }, 
                    placeholder = { Text("Ej: 9612765041", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedLabelColor = NeoTheme.Green,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = NeoTheme.Green,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val clean = NeoUtils.sanitizeId(phone)
                        if (clean.length == 10) {
                            isCheckingPhone = true
                            try {
                                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                                db.getReference("users").child(clean).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                                        isCheckingPhone = false
                                        val existing = snapshot.child("name").getValue(String::class.java)
                                        val devId = snapshot.child("deviceId").getValue(String::class.java) ?: snapshot.child("deviceBinding/deviceId").getValue(String::class.java)
                                        val devMod = snapshot.child("deviceModel").getValue(String::class.java) ?: snapshot.child("deviceBinding/deviceModel").getValue(String::class.java)
                                        val secPin = snapshot.child("securityPin").getValue(String::class.java) ?: snapshot.child("nexus_pin").getValue(String::class.java)

                                        registeredOwnerName = existing
                                        boundDeviceId = devId
                                        boundDeviceModel = devMod
                                        savedSecurityPin = secPin
                                        if (!existing.isNullOrBlank()) {
                                            name = existing
                                        }

                                        // Si ya tiene un deviceId registrado y es distinto al actual: ES OTRO TELÉFONO FÍSICO
                                        isDifferentDevice = !devId.isNullOrBlank() && currentDeviceId.isNotBlank() && devId != currentDeviceId
                                        step = 2
                                    }
                                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                                        isCheckingPhone = false
                                        step = 2
                                    }
                                })
                            } catch (_: Exception) {
                                isCheckingPhone = false
                                step = 2
                            }
                        } else {
                            android.widget.Toast.makeText(context, "El número debe tener exactamente 10 dígitos (ej: 9612765041)", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }, 
                    Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                ) { 
                    Text("VALIDAR NÚMERO Y VINCULACIÓN", color = Color.Black, fontWeight = FontWeight.Bold) 
                }
            } else {
                // PASO 2: COMPROBACIÓN DE HARDWARE FÍSICO Y PIN
                if (isDifferentDevice) {
                    // ALERTA DE SEGURIDAD: INTENTO DE USAR EL NÚMERO EN OTRO TELÉFONO
                    Surface(
                        color = Color(0xFFFF453A).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF453A)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Security, null, tint = Color(0xFFFF453A), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("DISPOSITIVO NO AUTORIZADO", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Este número pertenece a otro celular físico registrado:", color = Color.White, fontSize = 12.sp)
                            Text("📱 ${boundDeviceModel ?: "Teléfono original protegido"}", color = Color(0xFFFFD60A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Por seguridad y prevención contra delitos de suplantación, ingresa el PIN de Seguridad de 6 dígitos establecido por el propietario para autorizar la transferencia a este teléfono.", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                        label = { Text("PIN de Seguridad (6 dígitos)") },
                        placeholder = { Text("••••••", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = Color(0xFFFFD60A),
                            unfocusedLabelColor = Color.Gray,
                            focusedBorderColor = Color(0xFFFFD60A),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (pin.length != 6) {
                                android.widget.Toast.makeText(context, "Ingresa el PIN de 6 dígitos completo.", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (savedSecurityPin != null && pin != savedSecurityPin) {
                                android.widget.Toast.makeText(context, "❌ PIN INCORRECTO. Acceso denegado: Este número pertenece a otro teléfono físico.", android.widget.Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            android.widget.Toast.makeText(context, "✅ PIN verificado. Vinculando número a este nuevo teléfono físico...", android.widget.Toast.LENGTH_SHORT).show()
                            val clean = NeoUtils.sanitizeId(phone)
                            onLogin(if (name.isNotBlank()) name.trim() else (registeredOwnerName ?: "Usuario Neo"), clean, pin)
                        },
                        Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A))
                    ) {
                        Text("VERIFICAR IDENTIDAD Y TRANSFERIR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else if (registeredOwnerName != null) {
                    // MISMO TELÉFONO O CUENTA EXISTENTE VINCULÁNDOSE
                    Surface(
                        color = Color(0xFF00FF7F).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF7F).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.VerifiedUser, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("DISPOSITIVO FÍSICO AUTORIZADO", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Equipo: $currentDeviceModel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Registrado como: $registeredOwnerName", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre de Usuario") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = NeoTheme.Green,
                            unfocusedLabelColor = Color.Gray,
                            focusedBorderColor = NeoTheme.Green,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    if (savedSecurityPin != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                            label = { Text("PIN de Seguridad (6 dígitos)") },
                            placeholder = { Text("••••••", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedLabelColor = NeoTheme.Green,
                                unfocusedLabelColor = Color.Gray,
                                focusedBorderColor = NeoTheme.Green,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("🔒 Si deseas proteger tu cuenta con PIN, créalo aquí (Opcional):", color = Color.LightGray, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                            label = { Text("PIN de Seguridad (Opcional)") },
                            placeholder = { Text("Opcional (déjalo vacío si no deseas PIN)", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedLabelColor = NeoTheme.Green,
                                unfocusedLabelColor = Color.Gray,
                                focusedBorderColor = NeoTheme.Green,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                android.widget.Toast.makeText(context, "El nombre no puede estar vacío.", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (savedSecurityPin != null) {
                                if (pin.length != 6) {
                                    android.widget.Toast.makeText(context, "Ingresa tu PIN de 6 dígitos.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (pin != savedSecurityPin) {
                                    android.widget.Toast.makeText(context, "❌ PIN incorrecto.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            } else {
                                if (pin.isNotEmpty() && pin.length != 6) {
                                    android.widget.Toast.makeText(context, "El PIN debe tener exactamente 6 dígitos.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }
                            val clean = NeoUtils.sanitizeId(phone)
                            onLogin(name.trim(), clean, pin)
                        },
                        Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                    ) {
                        Text("CONTINUAR A MIS CHATS", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // CASO 3: NÚMERO NUEVO
                    Surface(
                        color = Color(0xFF00BFFF).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00BFFF).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PhoneAndroid, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("VINCULACIÓN DE HARDWARE FÍSICO", color = Color(0xFF00BFFF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Este número quedará enlazado exclusivamente a este teléfono ($currentDeviceModel).", color = Color.White, fontSize = 11.sp)
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tu Nombre Completo") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = NeoTheme.Green,
                            unfocusedLabelColor = Color.Gray,
                            focusedBorderColor = NeoTheme.Green,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("🔒 Si deseas proteger tu cuenta, puedes crear un PIN (Opcional):", color = Color.LightGray, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                        label = { Text("PIN de Seguridad (Opcional - 6 dígitos)") },
                        placeholder = { Text("Opcional (déjalo vacío si no deseas PIN)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedLabelColor = NeoTheme.Green,
                            unfocusedLabelColor = Color.Gray,
                            focusedBorderColor = NeoTheme.Green,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    if (pin.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                            label = { Text("Confirmar PIN de Seguridad") },
                            placeholder = { Text("••••••", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedLabelColor = NeoTheme.Green,
                                unfocusedLabelColor = Color.Gray,
                                focusedBorderColor = NeoTheme.Green,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                android.widget.Toast.makeText(context, "Ingresa tu nombre completo.", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (pin.isNotBlank()) {
                                if (pin.length != 6) {
                                    android.widget.Toast.makeText(context, "Si deseas configurar un PIN, debe tener 6 dígitos (o déjalo vacío).", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (confirmPin.isNotBlank() && pin != confirmPin) {
                                    android.widget.Toast.makeText(context, "Los PIN ingresados no coinciden.", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }
                            val clean = NeoUtils.sanitizeId(phone)
                            onLogin(name.trim(), clean, pin)
                        },
                        Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                    ) {
                        Text("REGISTRAR Y VINCULAR A ESTE CELULAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = { 
                    step = 1
                    registeredOwnerName = null
                    boundDeviceId = null
                    boundDeviceModel = null
                    savedSecurityPin = null
                    isDifferentDevice = false
                    pin = ""
                    confirmPin = ""
                }, Modifier.padding(top = 8.dp)) {
                    Text("← Usar otro número", color = Color.Gray)
                }
            }
        }
    }
}

data class NeoChatEntry(
    val name: String,
    val chatId: String,
    val lastMsg: String,
    val timestamp: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    userId: String,
    onNavigateToSettings: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToStatus: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToCommunities: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToLinkedDevices: () -> Unit,
    onNavigateToStarred: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToBroadcasts: () -> Unit,
    onNavigateToAi: () -> Unit,
    textColor: Color,
    accentGradient: Brush,
    onNavigateToCorteCaja: () -> Unit = {}
) {
    val context = LocalContext.current
    val spContacts = remember { context.getSharedPreferences("NeoContacts", Context.MODE_PRIVATE) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("Todos") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    val found = remember { mutableStateListOf<NeoChatEntry>() }
    val archivedIds = remember { mutableStateListOf<String>() }
    val lockedIds = remember { mutableStateListOf<String>() }
    val blockedIds = remember { mutableStateListOf<String>() }
    val userPhotos = remember { mutableStateMapOf<String, String>() }
    val myCleanId = remember(userId) { NeoUtils.sanitizeId(userId) }
    val selfChatId = remember(myCleanId) { "private_${myCleanId}_${myCleanId}" }
    var selfLastMsgText by remember { mutableStateOf("") }
    var showMainQuoteDialog by remember { mutableStateOf(false) }
    var showMainHealthDialog by remember { mutableStateOf(false) }
    var mainAnnotatingUri by remember { mutableStateOf<Uri?>(null) }
    val mainMeasurePickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { mainAnnotatingUri = it }
    }
    val spPrefs = remember { context.getSharedPreferences("NeoMessagePrefs", Context.MODE_PRIVATE) }
    val mySavedName = remember { spPrefs.getString("user_name", "") ?: "" }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showVaultPinDialog by remember { mutableStateOf(false) }
    var pendingHiddenChatId by remember { mutableStateOf<String?>(null) }
    var isVaultUnlocked by remember { mutableStateOf(false) }
    var vaultPinInput by remember { mutableStateOf("") }
    var hiddenPinInput by remember { mutableStateOf("") }
    var hiddenChatUpdateTrigger by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(userId) {
        val spContacts = context.getSharedPreferences("NeoContacts", Context.MODE_PRIVATE)
        val dao = try { AppDatabase.getDatabase(context).messageDao() } catch (_: Exception) { null }

        fun updateEntry(entry: NeoChatEntry) {
            val idx = found.indexOfFirst { it.chatId == entry.chatId }
            if (idx != -1) {
                val existing = found[idx]
                if (existing.lastMsg == entry.lastMsg && existing.timestamp == entry.timestamp && existing.name == entry.name) {
                    return
                }
                if (existing.timestamp == entry.timestamp) {
                    found[idx] = entry
                    return
                }
                found.removeAt(idx)
            }
            found.add(0, entry)
        }

        // 1. CARGA INMEDIATA LOCAL (Cero dependencias de Firebase/Internet)
        // A) Carga de chats archivados guardados localmente
        // Escuchar mensajes de Mis Notas Personales
        try {
            val dbLocal = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            dbLocal.getReference("chats").child(selfChatId).child("messages").limitToLast(1)
                .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snap: com.google.firebase.database.DataSnapshot) {
                        val firstChild = snap.children.firstOrNull()
                        val text = firstChild?.child("text")?.getValue(String::class.java)
                        val timestamp = firstChild?.child("timestamp")?.getValue(Long::class.java) ?: System.currentTimeMillis()
                        if (!text.isNullOrBlank()) {
                            val display = if (text.contains("NEO_QUOTE") || text.contains("COTIZACI") || text.contains("Cotización")) "📄 Presupuesto guardado"
                            else if (text.contains("NEO_HEALTH") || text.contains("NEOHEALTH") || text.contains("BPM")) "❤️ Ficha de salud guardada"
                            else text
                            selfLastMsgText = display
                            updateEntry(NeoChatEntry("Mis Notas Personales (Tú)", selfChatId, display, timestamp))
                        }
}
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })
        } catch (_: Exception) {}

        val spArchived = context.getSharedPreferences("NeoArchived", Context.MODE_PRIVATE)
        val spUserNames = context.getSharedPreferences("NeoUserNames", Context.MODE_PRIVATE)
        spArchived.all.keys.forEach { if (!archivedIds.contains(it)) archivedIds.add(it) }

        fun isValidPersonName(n: String?): Boolean {
            val t = n?.trim() ?: return false
            if (t.isBlank()) return false
            val low = t.lowercase()
            if (low == "neo" || low == "contacto" || low == "chat" || low == "usuario" || low == "usuario neomessage" || low.startsWith("contacto (") || low.startsWith("usuario ")) return false
            return t.any { it.isLetter() }
        }

        fun sanitizePreview(raw: String): String = when {
            raw.startsWith("[Media]") -> "📷 Foto"
            raw.startsWith("[Document]|") || raw.startsWith("[File]") -> "📄 Archivo"
            raw.startsWith("[Voice]") || raw.startsWith("[Audio]") -> "🎤 Mensaje de voz"
            raw.contains("NEO_QUOTE") || raw.contains("COTIZACI") || raw.contains("Cotización") -> "📄 Presupuesto guardado"
            raw.contains("NEO_HEALTH") || raw.contains("NEOHEALTH") || raw.contains("BPM") -> "❤️ Ficha de salud guardada"
            raw.length > 60 -> raw.take(60) + "..."
            else -> raw
        }

        // B) Mensajes locales guardados en SQLite Room
        launch(Dispatchers.IO) {
            try {
                val allEntities = dao?.getAllMessagesDirect() ?: emptyList()
                val grouped = allEntities.groupBy { it.chatId }
                withContext(Dispatchers.Main) {
                    grouped.forEach { (cId, msgs) ->
                        val lastMsg = msgs.maxByOrNull { it.timestamp }
                        if (lastMsg != null) {
                            val parts = cId.replace("private_", "").split("_")
                            val rawOther = if (parts.size == 2) {
                                if (NeoUtils.sanitizeId(parts[0]).takeLast(10) == myCleanId.takeLast(10)) parts[1] else parts[0]
                            } else ""
                            val otherPhone = NeoUtils.sanitizeId(rawOther).takeLast(10)
                            val peerMsgName = msgs.lastOrNull {
                                NeoUtils.sanitizeId(it.senderId).takeLast(10) != myCleanId.takeLast(10) && isValidPersonName(it.senderName)
                            }?.senderName?.trim()
                            
                            val name = spUserNames.getString(otherPhone, null)?.takeIf { isValidPersonName(it) }
                                ?: spContacts.getString(otherPhone, null)?.takeIf { isValidPersonName(it) }
                                ?: peerMsgName
                                ?: if (otherPhone.isNotEmpty()) otherPhone else "Chat"
                            
                            updateEntry(NeoChatEntry(name, cId, sanitizePreview(lastMsg.text), lastMsg.timestamp))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. ESCUCHADORES REMOTOS (Si Firebase está activo y con permisos)
        try {
            val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            val userIdsRef = db.getReference("users")
            val archivedRef = db.getReference("users").child(myCleanId).child("archived")
            val lockedRef = db.getReference("users").child(myCleanId).child("locked_chats")
            
            archivedRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val spArch = context.getSharedPreferences("NeoArchived", Context.MODE_PRIVATE)
                    val editor = spArch.edit()
                    snapshot.children.forEach { child ->
                        child.key?.let { key ->
                            if (!archivedIds.contains(key)) archivedIds.add(key)
                            editor.putBoolean(key, true)
                        }
                    }
                    editor.apply()
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })

            lockedRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    snapshot.children.forEach { child ->
                        child.key?.let { key -> if (!lockedIds.contains(key)) lockedIds.add(key) }
                    }
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })

            db.getReference("users").child(myCleanId).child("blocked").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    snapshot.children.forEach { child ->
                        child.key?.let { key -> if (!blockedIds.contains(key)) blockedIds.add(key) }
                    }
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
            
            // A) SINCRONIZACIÓN DE GRUPOS EN TIEMPO REAL (APARECEN DE INMEDIATO EN LA LISTA PRINCIPAL)
            val listenedChatIds = mutableSetOf<String>()
            val groupsRef = db.getReference("groups")
            groupsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(groupSnap: com.google.firebase.database.DataSnapshot) {
                    val myCanonical = NeoUtils.sanitizeId(myCleanId).takeLast(10)
                    groupSnap.children.forEach { child ->
                        val g = child.getValue(NeoGroup::class.java) ?: return@forEach
                        val isMember = g.createdBy.takeLast(10) == myCanonical || g.members.any { it.takeLast(10) == myCanonical }
                        if (isMember) {
                            if (!listenedChatIds.contains(g.id)) {
                                listenedChatIds.add(g.id)
                                db.getReference("chats").child(g.id).child("messages").limitToLast(1)
                                    .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                                        override fun onDataChange(mSnap: com.google.firebase.database.DataSnapshot) {
                                            val lastMsgObj = mSnap.children.firstOrNull()?.getValue(CloudMessage::class.java)
                                            val display = lastMsgObj?.text ?: "Grupo creado"
                                            val time = lastMsgObj?.timestamp ?: g.createdAt
                                            updateEntry(NeoChatEntry(g.name, g.id, display, time))
                                        }
                                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                                    })
                            }
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })

            // B) CHATS INDIVIDUALES CON NOMBRE REGISTRADO EN LA APP Y AGENDA
            launch(Dispatchers.IO) {
                val agenda = mutableMapOf<String, String>()
                try {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val tmp = mutableListOf<Map<String, String>>()
                        syncLocalContactsAction(context, tmp)
                        tmp.forEach { m ->
                            val p10 = NeoUtils.sanitizeId(m["id"] ?: "").takeLast(10)
                            val n = m["name"]?.trim() ?: ""
                            if (p10.length == 10 && isValidPersonName(n)) agenda[p10] = n
                        }
                    }
                } catch (_: Exception) {}
                val saved = mutableMapOf<String, String>()
                spContacts.all.forEach { (k, v) ->
                    val p10 = NeoUtils.sanitizeId(k).takeLast(10)
                    val n = v?.toString()?.trim() ?: ""
                    if (p10.length == 10 && isValidPersonName(n)) saved[p10] = n
                }
                val regNames = mutableMapOf<String, String>()

                fun resolveName(p10: String, profileName: String?, msgSenderName: String? = null): String {
                    val cleanProfile = profileName?.trim()?.takeIf { isValidPersonName(it) } ?: regNames[p10]
                    val cleanSaved = saved[p10]?.takeIf { isValidPersonName(it) }
                    val cleanAgenda = agenda[p10]?.takeIf { isValidPersonName(it) }
                    val cleanMsg = msgSenderName?.trim()?.takeIf { isValidPersonName(it) }
                    val cleanCached = spUserNames.getString(p10, null)?.takeIf { isValidPersonName(it) }
                    val finalName = cleanProfile ?: cleanSaved ?: cleanAgenda ?: cleanMsg ?: cleanCached ?: p10
                    if (isValidPersonName(finalName)) {
                        spUserNames.edit().putString(p10, finalName).apply()
                    }
                    return finalName
                }

                withContext(Dispatchers.Main) {
                    // Corregir nombres de chats ya cargados desde la base local
                    found.toList().forEach { e ->
                        if (e.chatId.startsWith("private_")) {
                            val parts = e.chatId.removePrefix("private_").split("_")
                            val other = parts.firstOrNull { NeoUtils.sanitizeId(it).takeLast(10) != myCleanId.takeLast(10) } ?: return@forEach
                            val p10 = NeoUtils.sanitizeId(other).takeLast(10)
                            val better = resolveName(p10, null, e.name)
                            if (better != e.name) {
                                val idx = found.indexOfFirst { it.chatId == e.chatId }
                                if (idx != -1) found[idx] = e.copy(name = better)
                            }
                        }
                    }

                    userIdsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                            snapshot.children.forEach { child ->
                                val rawKey = child.child("id").getValue(String::class.java) ?: child.key ?: ""
                                val canonicalPhone = NeoUtils.sanitizeId(rawKey).takeLast(10)
                                if (canonicalPhone.length != 10 || canonicalPhone == myCleanId.takeLast(10)) return@forEach
                                val profileName = child.child("name").getValue(String::class.java)?.trim()
                                if (isValidPersonName(profileName)) {
                                    regNames[canonicalPhone] = profileName!!
                                }
                                val photo = child.child("photoUrl").getValue(String::class.java)
                                    ?: child.child("profilePhoto").getValue(String::class.java)
                                if (!photo.isNullOrBlank()) {
                                    userPhotos[canonicalPhone] = photo
                                }
                                val name = resolveName(canonicalPhone, profileName)
                                // Si está en la agenda del celular, sincronizar a Firebase contacts para que en PC Web también aparezca con su nombre
                                if (agenda.containsKey(canonicalPhone) && isValidPersonName(name)) {
                                    try {
                                        db.getReference("users").child(myCleanId).child("contacts").child(canonicalPhone).setValue(
                                            mapOf("id" to canonicalPhone, "name" to name)
                                        )
                                    } catch (_: Exception) {}
                                }
                                val chatId = NeoUtils.getChatId(myCleanId, canonicalPhone)
                                val existingIdx = found.indexOfFirst { it.chatId == chatId }
                                if (existingIdx != -1 && found[existingIdx].name != name && isValidPersonName(name)) {
                                    found[existingIdx] = found[existingIdx].copy(name = name)
                                }
                                if (listenedChatIds.contains(chatId)) return@forEach
                                listenedChatIds.add(chatId)
                                // Solo aparece en la lista si realmente existe una conversación con mensajes
                                db.getReference("chats").child(chatId).child("messages").limitToLast(1)
                                    .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                                        override fun onDataChange(msgSnap: com.google.firebase.database.DataSnapshot) {
                                            val lastMsgObj = msgSnap.children.firstOrNull()?.getValue(CloudMessage::class.java)
                                            if (lastMsgObj != null && !NeoDeletedMessagesManager.isMessageHiddenForMe(context, chatId, lastMsgObj.id, lastMsgObj.timestamp)) {
                                                val cleanSender = NeoUtils.sanitizeId(lastMsgObj.senderId)
                                                val cleanMe = NeoUtils.sanitizeId(myCleanId)
                                                val peerSenderName = if (cleanSender.takeLast(10) != cleanMe.takeLast(10)) lastMsgObj.senderName else null
                                                val dynamicName = resolveName(canonicalPhone, regNames[canonicalPhone], peerSenderName)
                                                if (cleanSender.takeLast(10) != cleanMe.takeLast(10) && NeoBotManager.isBotEnabled(context)) {
                                                    NeoBotManager.dispatchAutoReplyIfNeeded(
                                                        context = context,
                                                        chatId = chatId,
                                                        senderId = lastMsgObj.senderId,
                                                        myId = cleanMe,
                                                        incomingText = lastMsgObj.text,
                                                        msgTimestamp = lastMsgObj.timestamp
                                                    )
                                                }
                                                val previewText = sanitizePreview(lastMsgObj.text)
                                                updateEntry(NeoChatEntry(dynamicName, chatId, previewText, lastMsgObj.timestamp))
                                            }
                                        }
                                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                                    })
                            }
                        }
                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                    })
                }
            }
        } catch (_: Exception) {}
    }

    val hiddenChatIds = remember(hiddenChatUpdateTrigger) {
        NeoHiddenChatsManager.getHiddenChatIds(context)
    }

    val expandedChats by remember {
        derivedStateOf {
            val list = found.toMutableList()
            archivedIds.forEach { aId ->
                if (list.none { it.chatId == aId }) {
                    val parts = aId.replace("private_", "").split("_")
                    val otherPhone = if (parts.size == 2) {
                        if (parts[0] == myCleanId) parts[1] else parts[0]
                    } else aId
                    val name = spContacts.getString(otherPhone, otherPhone) ?: otherPhone
                    list.add(NeoChatEntry(name, aId, "Chat archivado", System.currentTimeMillis()))
                }
            }
            list.sortedByDescending { it.timestamp }.map { entry ->
                val (name, id, lastMsg, _) = entry
                val isFav = name.contains("Neo") || id.contains("owner")
                val isUnread = lastMsg.length % 2 == 0 
                val isArchived = archivedIds.contains(id)
                val isLocked = lockedIds.contains(id)
                Sextuple(name, id, lastMsg, isFav, isUnread, Quintuple(isArchived, isLocked, false, false, false))
            }
        }
    }

    val filteredChats by remember(selectedFilter, searchQuery, blockedIds.size, isVaultUnlocked, hiddenChatUpdateTrigger) {
        derivedStateOf {
            val isPinMatch = searchQuery.isNotBlank() && NeoHiddenChatsManager.checkPin(context, searchQuery)
            expandedChats.filter { (name, id, lastMsg, isFav, isUnread, meta) ->
                val (isArchived, isLocked, _, _, _) = meta as Quintuple<Boolean, Boolean, Boolean, Boolean, Boolean>
                val isHidden = hiddenChatIds.contains(id)
                if (isVaultUnlocked || isPinMatch) return@filter isHidden
                if (isHidden) return@filter false
                
                // EXTRAER ID DEL OTRO USUARIO DEL CHATID
                val otherId = id.replace("private_", "").split("_").firstOrNull { it != NeoUtils.sanitizeId(userId) } ?: ""
                val isBlocked = blockedIds.contains(otherId)

                val matchesFilter = when(selectedFilter) {
                    "Grupos" -> id.startsWith("group_") && !isArchived && !isBlocked
                    "No leídos" -> isUnread && !isArchived && !isBlocked
                    "Favoritos" -> isFav && !isArchived && !isBlocked
                    "Archivados" -> isArchived
                    "Todos" -> !isArchived && !isBlocked
                    else -> true
                }
                val matchesSearch = name.contains(searchQuery, ignoreCase = true) || id.contains(searchQuery, ignoreCase = true)
                matchesFilter && matchesSearch
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar protocolo...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = NeoTheme.Green,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NeoMessage", fontWeight = FontWeight.Black, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Surface(color = NeoTheme.Green.copy(0.1f), shape = RoundedCornerShape(4.dp)) {
                                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Sensors, null, tint = NeoTheme.Green, modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("MESH ACTIVE", color = NeoTheme.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(onClick = { isSearching = false; searchQuery = "" }) { Icon(Icons.Filled.Close, null, tint = Color.White) }
                    } else {
                        IconButton(onClick = { isSearching = true }) { Icon(Icons.Filled.Search, null, tint = Color.White) }
                        IconButton(onClick = { Toast.makeText(context, "Módulo de captura biométrica rápida.", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Filled.PhotoCamera, null, tint = Color.White) }
                        IconButton(onClick = onNavigateToAi) { Icon(Icons.Filled.AutoAwesome, null, tint = NeoTheme.Green) }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Color.White) }
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(NeoTheme.Card)
                    ) {
                        DropdownMenuItem(
    text = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.GroupAdd, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("👥 Crear Grupo Escolar / Comunidad", color = Color.White, fontWeight = FontWeight.Bold)
        }
    },
    onClick = {
        showMenu = false
        onNavigateToCommunities()
    }
)
                        DropdownMenuItem(text = { Text("Nueva comunidad", color = Color.White) }, onClick = { showMenu = false; onNavigateToCommunities() })
                        DropdownMenuItem(text = { Text("Listas de difusión", color = Color.White) }, onClick = { showMenu = false; onNavigateToBroadcasts() })
                        DropdownMenuItem(text = { Text("Dispositivos vinculados", color = Color.White) }, onClick = { showMenu = false; onNavigateToLinkedDevices() })
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Bookmark, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("📝 Mis Notas Personales (Tú)", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            onClick = {
                                showMenu = false
                                onNavigateToChat("Mis Notas Personales (Tú)", selfChatId)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.ReceiptLong, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("📋 Cotizaciones y Presupuestos", color = Color.White)
                                }
                            },
                            onClick = {
                                showMenu = false
                                showMainQuoteDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Straighten, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("📐 Acotar Medidas en Fotos", color = Color.White)
                                }
                            },
                            onClick = {
                                showMenu = false
                                mainMeasurePickerLauncher.launch("image/*")
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("❤️ Salud y Relojes (Biometría)", color = Color.White)
                                }
                            },
                            onClick = {
                                showMenu = false
                                showMainHealthDialog = true
                            }
                        )
                        HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp)
                        DropdownMenuItem(text = { Text("💰 Corte de caja y ventas", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold) }, onClick = { showMenu = false; onNavigateToCorteCaja() })
                        val isBotOn = NeoBotManager.isBotEnabled(context)
                        DropdownMenuItem(
                            text = { Text(if (isBotOn) "🤖 Bot Ventas: ACTIVO" else "🤖 Bot Ventas: DESACTIVADO (Activar)", color = if (isBotOn) Color(0xFF00FF7F) else Color.LightGray) },
                            onClick = {
                                showMenu = false
                                val next = !isBotOn
                                NeoBotManager.setBotEnabled(context, next)
                                Toast.makeText(context, if (next) "🤖 Bot de Ventas ACTIVADO para clientes." else "🤖 Bot de Ventas DESACTIVADO.", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isVaultUnlocked) "🔓 Cerrar Bóveda de Chats" else "🔒 Abrir Chats Ocultos con PIN", color = Color(0xFFFFD700)) },
                            onClick = {
                                showMenu = false
                                if (isVaultUnlocked) {
                                    isVaultUnlocked = false
                                    Toast.makeText(context, "🔒 Bóveda cerrada. Chats ocultos protegidos.", Toast.LENGTH_SHORT).show()
                                } else {
                                    showVaultPinDialog = true
                                }
                            }
                        )
                        DropdownMenuItem(text = { Text("Mensajes destacados", color = Color.White) }, onClick = { showMenu = false; onNavigateToStarred() })
                        DropdownMenuItem(text = { Text("Ayuda", color = Color.White) }, onClick = { showMenu = false; onNavigateToHelp() })
                        DropdownMenuItem(text = { Text("Ajustes", color = Color.White) }, onClick = { showMenu = false; onNavigateToSettings() })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeoTheme.Dark)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = NeoTheme.Dark, contentColor = NeoTheme.Green) {
                NavigationBarItem(
                    selected = (selectedFilter == "Todos" && !isSearching), 
                    icon = { Icon(Icons.Filled.Forum, null) }, 
                    label = { Text("Chats") }, 
                    onClick = { 
                        selectedFilter = "Todos"
                        searchQuery = ""
                        isSearching = false
                    },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Black, indicatorColor = NeoTheme.Green)
                )
                NavigationBarItem(
                    selected = false, 
                    icon = { Icon(Icons.Filled.Campaign, null) }, 
                    label = { Text("Novedades") }, 
                    onClick = onNavigateToStatus,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = false, 
                    icon = { Icon(Icons.Filled.Groups, null) }, 
                    label = { Text("Comunidades") }, 
                    onClick = onNavigateToCommunities,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray)
                )
                NavigationBarItem(
                    selected = false, 
                    icon = { Icon(Icons.Filled.Call, null) }, 
                    label = { Text("Llamadas") }, 
                    onClick = onNavigateToCalls,
                    colors = NavigationBarItemDefaults.colors(unselectedIconColor = Color.Gray)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToContacts, 
                containerColor = NeoTheme.Green,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo Chat", tint = Color.Black)
            }
        },
        containerColor = NeoTheme.Dark
    ) { padding ->
        var selectedChatAction by remember { mutableStateOf<Triple<String, String, String>?>(null) }

        if (showMainQuoteDialog) {
            CreateQuoteDialog(
                onDismiss = { showMainQuoteDialog = false },
                onSendQuote = { quote ->
                    showMainQuoteDialog = false
                    try {
                        val mid = java.util.UUID.randomUUID().toString()
                        val msg = CloudMessage(
                            id = mid,
                            text = quote.toPayloadString(),
                            timestamp = System.currentTimeMillis(),
                            senderId = myCleanId,
                            senderName = mySavedName.ifEmpty { "Yo" },
                            rank = "📄 Cotización"
                        )
                        val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                        db.getReference("chats").child(selfChatId).child("messages").child(mid).setValue(msg)
                    } catch (_: Exception) {}
                    Toast.makeText(context, "✅ Presupuesto guardado en 'Mis Notas Personales'.", Toast.LENGTH_LONG).show()
                    onNavigateToChat("Mis Notas Personales (Tú)", selfChatId)
                }
            )
        }

        if (mainAnnotatingUri != null) {
            TechnicalMeasureAnnotatorDialog(
                imageUri = mainAnnotatingUri!!,
                onDismiss = { mainAnnotatingUri = null },
                onSaveAnnotatedImage = { annotatedUri ->
                    mainAnnotatingUri = null
                    Toast.makeText(context, "📐 Foto con medidas técnicas guardada con éxito.", Toast.LENGTH_LONG).show()
                }
            )
        }

        if (showMainHealthDialog) {
            HealthShareDialog(
                onDismiss = { showMainHealthDialog = false },
                onSendHealthReport = { health ->
                    showMainHealthDialog = false
                    try {
                        val mid = java.util.UUID.randomUUID().toString()
                        val msg = CloudMessage(
                            id = mid,
                            text = health.toPayload(),
                            timestamp = System.currentTimeMillis(),
                            senderId = myCleanId,
                            senderName = mySavedName.ifEmpty { "Yo" },
                            rank = "❤️ NeoHealth"
                        )
                        val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                        db.getReference("chats").child(selfChatId).child("messages").child(mid).setValue(msg)
                    } catch (_: Exception) {}
                    Toast.makeText(context, "❤️ Ficha de salud registrada en 'Mis Notas Personales'.", Toast.LENGTH_LONG).show()
                    onNavigateToChat("Mis Notas Personales (Tú)", selfChatId)
                }
            )
        }
        var showDeleteDialog by remember { mutableStateOf(false) }
        
        if (showDeleteDialog && selectedChatAction != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = NeoTheme.Card,
                title = { Text("Vaciar Conversación (Solo en mi cel)", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("¿Deseas vaciar los mensajes con ${selectedChatAction!!.first} en tu celular? Se limpiará únicamente de tu teléfono sin borrar el historial del otro usuario, y el contacto se mantendrá guardado.", color = Color.LightGray) },
                confirmButton = {
                    Button(onClick = {
                        val targetChatId = selectedChatAction!!.second
                        NeoDeletedMessagesManager.markChatClearedNow(context, targetChatId)
                        Thread {
                            try {
                                kotlinx.coroutines.runBlocking {
                                    AppDatabase.getDatabase(context).messageDao().clearChat(targetChatId)
                                }
                            } catch (_: Exception) {}
                        }.start()
                        val existingIdx = found.indexOfFirst { it.chatId == targetChatId }
                        if (existingIdx != -1) {
                            found[existingIdx] = found[existingIdx].copy(lastMsg = "Chat vaciado")
                        }
                        showDeleteDialog = false
                        selectedChatAction = null
                        Toast.makeText(context, "🧹 Historial vaciado solo en tu celular.", Toast.LENGTH_SHORT).show()
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Text("VACIAR EN MI CEL")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("CANCELAR", color = Color.Gray) }
                }
            )
        }

        if (selectedChatAction != null && !showDeleteDialog) {
            val isArchived = archivedIds.contains(selectedChatAction!!.second)
            val isLocked = lockedIds.contains(selectedChatAction!!.second)
            AlertDialog(
                onDismissRequest = { selectedChatAction = null },
                containerColor = NeoTheme.Card,
                title = { Text("Opciones de Protocolo", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("Selecciona la acción para ${selectedChatAction!!.first}", color = Color.LightGray) },
                confirmButton = {
                    Column(Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                                val aRef = db.getReference("users").child(myCleanId).child("archived").child(selectedChatAction!!.second)
                                val lRef = db.getReference("users").child(myCleanId).child("locked_chats").child(selectedChatAction!!.second)
                                
                                val spArch = context.getSharedPreferences("NeoArchived", Context.MODE_PRIVATE)
                                if (isArchived) {
                                    aRef.removeValue()
                                    spArch.edit().remove(selectedChatAction!!.second).apply()
                                    archivedIds.remove(selectedChatAction!!.second)
                                } else {
                                    aRef.setValue(true)
                                    spArch.edit().putBoolean(selectedChatAction!!.second, true).apply()
                                    if (!archivedIds.contains(selectedChatAction!!.second)) archivedIds.add(selectedChatAction!!.second)
                                }
                                selectedChatAction = null
                                Toast.makeText(context, if (isArchived) "Chat desarchivado." else "Chat archivado 📁", Toast.LENGTH_SHORT).show()
                            },
                            Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                        ) {
                            Text(if (isArchived) "SACAR DE ARCHIVOS" else "ARCHIVAR CHAT", color = Color.Black)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                                val lRef = db.getReference("users").child(userId).child("locked_chats").child(selectedChatAction!!.second)
                                if (isLocked) lRef.removeValue() else lRef.setValue(true)
                                selectedChatAction = null
                                val isH = NeoHiddenChatsManager.isChatHidden(context, selectedChatAction!!.second)
                                if (isH) {
                                    NeoHiddenChatsManager.setChatHidden(context, selectedChatAction!!.second, false)
                                    hiddenChatUpdateTrigger++
                                    Toast.makeText(context, "🔓 Chat visible nuevamente.", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (!NeoHiddenChatsManager.isPinConfigured(context)) {
                                        pendingHiddenChatId = selectedChatAction!!.second
                                        showSetPinDialog = true
                                    } else {
                                        NeoHiddenChatsManager.setChatHidden(context, selectedChatAction!!.second, true)
                                        hiddenChatUpdateTrigger++
                                        Toast.makeText(context, "🔒 Chat OCULTO con PIN.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f))
                        ) {
                            val isH = NeoHiddenChatsManager.isChatHidden(context, selectedChatAction!!.second)
                            Text(if (isH) "🔓 HACER VISIBLE EL CHAT" else "🔒 OCULTAR CHAT CON PIN", color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showDeleteDialog = true },
                            Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.7f))
                        ) {
                            Text("VACIAR CHAT (SOLO EN MI CEL)", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedChatAction = null }) { Text("CANCELAR", color = Color.Gray) }
                }
            )
        }

        if (showSetPinDialog) {
            AlertDialog(
                onDismissRequest = { showSetPinDialog = false },
                containerColor = NeoTheme.Card,
                title = { Text("🔒 Crear PIN de Chats Ocultos", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Elige un PIN secreto de 4 dígitos para proteger tus chats privados. Solo tú podrás verlos.", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = hiddenPinInput,
                            onValueChange = { if (it.length <= 6) hiddenPinInput = it },
                            label = { Text("PIN Secreto (ej. 1234)") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeoTheme.Green)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (hiddenPinInput.length >= 4) {
                                NeoHiddenChatsManager.setPin(context, hiddenPinInput)
                                if (pendingHiddenChatId != null) {
                                    NeoHiddenChatsManager.setChatHidden(context, pendingHiddenChatId!!, true)
                                    pendingHiddenChatId = null
                                }
                                hiddenChatUpdateTrigger++
                                showSetPinDialog = false
                                hiddenPinInput = ""
                                Toast.makeText(context, "🔒 PIN configurado y chat ocultado con éxito.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "El PIN debe tener al menos 4 dígitos.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                    ) {
                        Text("GUARDAR PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSetPinDialog = false; pendingHiddenChatId = null }) { Text("CANCELAR", color = Color.Gray) }
                }
            )
        }

        if (showVaultPinDialog) {
            AlertDialog(
                onDismissRequest = { showVaultPinDialog = false },
                containerColor = NeoTheme.Card,
                title = { Text("🔒 Bóveda de Chats Ocultos", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Ingresa tu PIN secreto para desbloquear la lista de chats ocultos.", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = vaultPinInput,
                            onValueChange = { if (it.length <= 6) vaultPinInput = it },
                            label = { Text("PIN Secreto") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeoTheme.Green)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (NeoHiddenChatsManager.checkPin(context, vaultPinInput)) {
                                isVaultUnlocked = true
                                showVaultPinDialog = false
                                vaultPinInput = ""
                                Toast.makeText(context, "🔓 Bóveda desbloqueada.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ PIN incorrecto.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                    ) {
                        Text("DESBLOQUEAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVaultPinDialog = false; vaultPinInput = "" }) { Text("CANCELAR", color = Color.Gray) }
                }
            )
        }
        
        Column(Modifier.padding(padding).fillMaxSize()) {

            val archivedCount = maxOf(archivedIds.size, found.count { archivedIds.contains(it.chatId) })
            BackHandler(enabled = selectedFilter == "Archivados") {
                selectedFilter = "Todos"
            }
            if (selectedFilter == "Archivados") {
                Row(
                    Modifier.fillMaxWidth().background(NeoTheme.Card).padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedFilter = "Todos" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Chats Archivados ($archivedCount)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp)
            } else if (archivedCount > 0 && selectedFilter == "Todos") {
                Row(
                    Modifier.fillMaxWidth().clickable { selectedFilter = "Archivados" }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Archive, null, tint = NeoTheme.Green, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Archivados", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("$archivedCount", color = NeoTheme.Green, fontSize = 12.sp)
                }
                HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp)
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(filteredChats, key = { it.second }) { chat ->
                    val (name, id, lastMsg, _, _, meta) = chat
                    val isLocked = (meta as Quintuple<*, *, *, *, *>).second as Boolean
                    val otherPhone = if (id.startsWith("private_")) {
                        val parts = id.replace("private_", "").split("_")
                        val rawOther = if (parts.size >= 2) (if (NeoUtils.sanitizeId(parts[0]).takeLast(10) == myCleanId.takeLast(10)) parts[1] else parts[0]) else ""
                        NeoUtils.sanitizeId(rawOther).takeLast(10)
                    } else ""
                    val photo = userPhotos[otherPhone]
                    ChatRow(name, lastMsg, id, phone = otherPhone, isLocked = isLocked, photoUrl = photo,
                        onClick = { onNavigateToChat(name, id) },
                        onLongClick = { selectedChatAction = Triple(name, id, lastMsg) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRow(
    name: String,
    lastMsg: String,
    id: String,
    phone: String = "",
    isLocked: Boolean = false,
    photoUrl: String? = null,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val isGroup = id.startsWith("group_")
    val isSelf = name.contains("Mis Notas Personales")
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelf) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(NeoTheme.PremiumGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Bookmark, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(24.dp))
            }
        } else {
            NeoUniversalAvatar(
                photoSource = photoUrl,
                fallbackName = name,
                modifier = Modifier.size(50.dp),
                fontSize = 18
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    if (isLocked) Icons.Filled.Lock else (if (isSelf) Icons.Filled.Bookmark else if (isGroup) Icons.Filled.Groups else Icons.Filled.Person), 
                    null, 
                    tint = if (isLocked) NeoTheme.Green else (if (isGroup) NeoTheme.Green else Color.Gray), 
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!isSelf && !isGroup && phone.isNotBlank() && !name.contains(phone)) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "📱 $phone",
                        color = Color(0xFF00FF7F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            Text(lastMsg, color = Color.Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isLocked) {
             Icon(Icons.Filled.Fingerprint, null, tint = NeoTheme.Green.copy(0.4f), modifier = Modifier.size(18.dp))
        }
    }
}
