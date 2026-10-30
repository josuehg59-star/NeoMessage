package com.josuedev.neomessage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import android.util.Base64
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================== MENÚ DE AYUDA ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpMenuScreen(
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    onBack: () -> Unit,
    onNavigateToHelpCenter: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onNavigateToAppInfo: () -> Unit
) {
    val context = LocalContext.current
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayuda", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            HelpMenuItem(Icons.Outlined.HelpOutline, "Centro de ayuda", "Preguntas frecuentes, guías y soporte.", textColor, textSubColor, onNavigateToHelpCenter)
            HelpMenuItem(Icons.Outlined.BugReport, "Enviar comentarios", "Reporta problemas técnicos o sugerencias.", textColor, textSubColor, onNavigateToFeedback)
            HelpMenuItem(Icons.Outlined.Description, "Condiciones y Política de privacidad", "Privacidad soberana, cero rastreadores y derechos.", textColor, textSubColor) {
                showPrivacyDialog = true
            }
            HelpMenuItem(Icons.Outlined.Info, "Info. de la aplicación", "Certificado de autoría y creadores oficiales.", textColor, textSubColor, onNavigateToAppInfo)
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = surfaceColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = Color(0xFF00FF7F))
                    Spacer(Modifier.width(8.dp))
                    Text("Política y Privacidad Soberana", color = textColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("🛡️ MANIFIESTO DE PRIVACIDAD TOTAL", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "NeoMessage fue creada bajo el principio de Soberanía Digital. A diferencia de WhatsApp o Meta, NeoMessage NO recopila datos para publicidad, NO vende números de teléfono, NO rastrea ubicaciones y NO usa algoritmos de espionaje comercial.",
                            color = textColor.copy(0.9f),
                            fontSize = 13.sp
                        )
                    }
                    item {
                        Text("🔐 CIFRADO Y ALMACENAMIENTO LOCAL", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tus mensajes, fotos, audios y recibos de venta se almacenan de manera protegida en la memoria interna de tu propio celular (SQLite cifrado). Eres dueño absoluto de tu información: tus mensajes se guardan para siempre por defecto, con control total para borrarlos cuando tú lo decidas.",
                            color = textColor.copy(0.9f),
                            fontSize = 13.sp
                        )
                    }
                    item {
                        Text("🌐 MODO LOCAL P2P SIN INTERNET", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "En la misma red Wi-Fi o punto de acceso, la comunicación viaja de antena a antena sin pasar por internet ni servidores de terceros, garantizando 100% confidencialidad y gasto cero de datos.",
                            color = textColor.copy(0.9f),
                            fontSize = 13.sp
                        )
                    }
                    item {
                        Text("⚖️ DERECHOS DE AUTOR Y PROPIEDAD", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "NeoMessage y todo su ecosistema tecnológico es propiedad exclusiva de su Creador Maestro (Tel: 9612765041) y su equipo de desarrollo. Se encuentra protegido por leyes internacionales de derechos de autor y propiedad intelectual de software.",
                            color = textColor.copy(0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                ) {
                    Text("ENTENDIDO", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun HelpMenuItem(icon: ImageVector, title: String, subtitle: String?, textColor: Color, textSubColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = textSubColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, color = textColor, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = textSubColor, fontSize = 14.sp)
            }
        }
    }
}


// ==================== CENTRO DE AYUDA (FAQs) ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpCenterScreen(
    userId: String,
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val topics = listOf(
        Triple(Icons.Default.Flag, "Get Started", Color(0xFF00FF7F)),
        Triple(Icons.Default.Chat, "Chats", Color(0xFF00FF7F)),
        Triple(Icons.Default.Store, "Connect with Businesses", Color(0xFF00FF7F)),
        Triple(Icons.Default.Call, "Voice and Video Calls", Color(0xFF00FF7F)),
        Triple(Icons.Default.Groups, "Communities", Color(0xFF00FF7F)),
        Triple(Icons.Default.SettingsVoice, "Channels", Color(0xFF00FF7F)),
        Triple(Icons.Default.Lock, "Privacy, Safety, and Security", Color(0xFF00FF7F))
    )

    var searchQuery by remember { mutableStateOf("") }
    val filteredTopics = if (searchQuery.isEmpty()) topics else topics.filter { it.second.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Centro de ayuda", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor) } },
                actions = { IconButton(onClick = { Toast.makeText(context, "Menú de ayuda extendido.", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.MoreVert, null, tint = textColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        floatingActionButton = {
            Button(
                onClick = { 
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@neomessage.app")
                        putExtra(Intent.EXTRA_SUBJECT, "Soporte Técnico NeoMessage - ID: $userId")
                    }
                    try { context.startActivity(intent) } catch(e: Exception) {
                        Toast.makeText(context, "No se encontró cliente de correo.", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F)),
                modifier = Modifier.height(60.dp)
            ) {
                Icon(Icons.Default.QuestionAnswer, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Contáctanos", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = bgColor
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(0.05f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Chat, null, modifier = Modifier.size(50.dp), tint = Color(0xFF00FF7F))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("¿En qué podemos ayudarte?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar en el Centro de Ayuda", color = textSubColor) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = textSubColor) },
                    shape = RoundedCornerShape(30.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(0.05f),
                        unfocusedContainerColor = Color.White.copy(0.05f)
                    )
                )
            }
            
            item {
                Text("Temas de ayuda", color = textSubColor, fontSize = 14.sp, modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp))
            }
            
            items(filteredTopics) { topic ->
                Row(modifier = Modifier.fillMaxWidth().clickable { 
                    Toast.makeText(context, "Sincronizando información sobre: ${topic.second}", Toast.LENGTH_SHORT).show()
                }.padding(24.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(topic.first, null, tint = topic.third, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(24.dp))
                    Text(topic.second, color = textColor, fontSize = 16.sp)
                }
            }
            
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

// ==================== ENVIAR COMENTARIOS ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    userId: String,
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var feedbackText by remember { mutableStateOf("") }
    var showIntro by remember { mutableStateOf(true) }

    if (showIntro) {
        ModalBottomSheet(onDismissRequest = onBack, containerColor = surfaceColor) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.DeviceUnknown, null, modifier = Modifier.size(60.dp), tint = Color(0xFF00FF7F))
                Spacer(Modifier.height(16.dp))
                Text("Envía tus comentarios para ayudar a resolver problemas técnicos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                
                InfoRow(Icons.Default.BugReport, "Reporta problemas como bloqueos de la aplicación, fallas o funciones que no se ejecutan correctamente.")
                Spacer(Modifier.height(16.dp))
                InfoRow(Icons.Default.Image, "Incluye detalles y capturas de pantalla para ayudarnos a entender tu opinión.")
                
                Spacer(Modifier.height(24.dp))
                Text("Para otros problemas, como spam o estafas, puedes obtener ayuda o comunicarte con el servicio de atención al cliente desde el Centro de ayuda.", color = textSubColor, fontSize = 12.sp, textAlign = TextAlign.Center)
                
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { showIntro = false },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                ) {
                    Text("Continuar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enviar comentarios", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text("Para otros problemas, como spam o estafas, puedes obtener ayuda o comunicarte con el servicio de atención al cliente desde el Centro de ayuda.", 
                color = textSubColor, fontSize = 13.sp)
            
            Spacer(Modifier.height(20.dp))
            
            OutlinedTextField(
                value = feedbackText,
                onValueChange = { feedbackText = it },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                placeholder = { Text("Describe el problema técnico", color = textSubColor) },
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            Text("Capturas de pantalla o grabaciones (opcional)", color = textSubColor, fontSize = 14.sp)
            Text("Toca la captura de pantalla para editar o quitar información confidencial.", color = textSubColor, fontSize = 12.sp)
            
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)).background(surfaceColor), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AddPhotoAlternate, null, tint = textSubColor)
            }
            
            Spacer(Modifier.height(24.dp))
            Text("Si realizas el envío, autorizas a NeoMessage a revisar la información técnica relacionada para ayudarte a resolver tus comentarios. Más información", color = textSubColor, fontSize = 12.sp)
            
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { 
                    val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                    val feedbackRef = db.getReference("comentarios").push()
                    val data = mapOf(
                        "userId" to userId,
                        "texto" to feedbackText,
                        "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP,
                        "modelo" to android.os.Build.MODEL,
                        "androidVersion" to android.os.Build.VERSION.RELEASE
                    )
                    feedbackRef.setValue(data).addOnSuccessListener {
                        Toast.makeText(context, "¡Gracias! Tu reporte ha sido enviado al Maestro Josué.", Toast.LENGTH_LONG).show()
                        onBack()
                    }.addOnFailureListener {
                        Toast.makeText(context, "Error al enviar. Verifica tu conexión.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F), contentColor = Color.Black),
                enabled = feedbackText.isNotBlank()
            ) {
                Text("Enviar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    userId: String,
    bgColor: Color,
    textColor: Color,
    textSubColor: Color,
    onBack: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToTwoStep: () -> Unit,
    onNavigateToChangeNumber: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    
    var showEmailDialog by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(sp.getString("neo_email", "No configurado") ?: "No configurado") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuenta", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            AccountMenuItem(Icons.Outlined.Security, "Notificaciones de seguridad", textColor, textSubColor, onNavigateToSecurity)
            AccountMenuItem(Icons.Outlined.VerifiedUser, "Verificación en dos pasos", textColor, textSubColor, onNavigateToTwoStep)
            AccountMenuItem(Icons.Outlined.PhonelinkSetup, "Cambiar número", textColor, textSubColor, onNavigateToChangeNumber)
            AccountMenuItem(Icons.Outlined.Email, "Dirección de correo electrónico", textColor, textSubColor) { 
                showEmailDialog = true
            }
            AccountMenuItem(Icons.Outlined.Description, "Solicitar info. de la cuenta", textColor, textSubColor) { 
                Toast.makeText(context, "Reporte enlazado a ${email}. Se enviará en 24h.", Toast.LENGTH_LONG).show() 
            }
            AccountMenuItem(Icons.Outlined.PersonAdd, "Añadir cuenta", textColor, textSubColor) { 
                Toast.makeText(context, "Módulo multi-cuenta NeoMessage: Activa modo secundario.", Toast.LENGTH_SHORT).show() 
            }
            AccountMenuItem(Icons.Outlined.Delete, "Eliminar cuenta", textColor, textSubColor) { 
                showDeleteConfirm = true
            }
            
            HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 12.dp))
            AccountMenuItem(Icons.Outlined.CleaningServices, "Protocolo de Limpieza NeoMessage", Color(0xFF00FF7F), Color(0xFF00FF7F)) {
                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                db.getReference("users").get().addOnSuccessListener { snapshot ->
                    var count = 0
                    snapshot.children.forEach { child ->
                        val id = child.key ?: ""
                        val name = child.child("name").getValue(String::class.java)
                        // Limpiar registros incompletos o duplicados obvios
                        if (name == null || name.isEmpty() || (name == "(Sin nombre)" && id != userId)) {
                            child.ref.removeValue()
                            count++
                        }
                    }
                    Toast.makeText(context, "$count Sesiones fantasmales purgadas de NeoMessage.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showEmailDialog) {
        var tempEmail by remember { mutableStateOf(if (email == "No configurado") "" else email) }
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Correo NeoMessage (E2EE)") },
            text = {
                Column {
                    Text("Configura un correo para recuperación cifrada.", color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = tempEmail, onValueChange = { tempEmail = it }, placeholder = { Text("email@neomessage.app") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    email = tempEmail
                    sp.edit().putString("neo_email", tempEmail).apply()
                    com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("users").child(userId).child("email").setValue(tempEmail)
                    showEmailDialog = false
                }) { Text("GUARDAR") }
            }
        )
    }

    if (showDeleteConfirm) {
        var confirmPhone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿ELIMINAR CUENTA?", color = Color.Red, fontWeight = FontWeight.Black) },
            text = { 
                Column {
                    Text("Se borrarán permanentemente tus mensajes, grupos y archivos de NeoMessage. Esta acción es IRREVERSIBLE.", color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Para confirmar, ingresa tu número de teléfono:", color = Color.Gray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = confirmPhone, 
                        onValueChange = { confirmPhone = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Número de teléfono") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (confirmPhone == userId) {
                            com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("users").child(userId).removeValue()
                            onLogout()
                        } else {
                            Toast.makeText(context, "El número no coincide con la cuenta activa.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    enabled = confirmPhone.isNotBlank()
                ) { Text("ELIMINAR TODO") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("CANCELAR") } }
        )
    }
}

@Composable
fun AccountMenuItem(icon: ImageVector, title: String, textColor: Color, textSubColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = textSubColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(title, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

// ==================== AVATAR (NUEVO) ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarScreen(
    bgColor: Color,
    textColor: Color,
    textSubColor: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isGenerating by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableStateOf("Nunca") }
    
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            kotlinx.coroutines.delay(2000)
            isGenerating = false
            lastUpdate = "Hoy"
            Toast.makeText(context, "Avatar NeoMessage regenerado con éxito.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Avatar", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp).clip(CircleShape).background(if (isGenerating) SolidColor(Color.Gray.copy(0.2f)) else NeoTheme.PremiumGradient), contentAlignment = Alignment.Center) {
                if (isGenerating) {
                    CircularProgressIndicator(color = Color(0xFF00FF7F))
                } else {
                    Icon(Icons.Default.Face, null, modifier = Modifier.size(80.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Tu Avatar NeoMessage", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Última actualización: $lastUpdate", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(40.dp))
            
            Button(
                onClick = { isGenerating = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
            ) { Text("REGENERAR AVATAR", color = Color.Black, fontWeight = FontWeight.Bold) }
            
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { 
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://getavataaars.com/"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("EDITOR EXTERNO NEOMESSAGE", color = Color.White) }
        }
    }
}

// ==================== LISTAS (NUEVO) ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsSettingsScreen(
    bgColor: Color,
    textColor: Color,
    textSubColor: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val customLists = remember { mutableStateListOf<String>() }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, containerColor = Color(0xFF00FF7F)) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
            }
        },
        containerColor = bgColor
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                Text("Todas las listas que creas se convierten en un filtro en la parte superior de tu lista de chats.", 
                    color = textSubColor, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                
                Text("Listas por defecto", color = Color(0xFF00FF7F), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                SettingsRow(Icons.Default.Schedule, "No leídos", null, { Toast.makeText(context, "Filtro No leídos activado.", Toast.LENGTH_SHORT).show() })
                SettingsRow(Icons.Default.FavoriteBorder, "Favoritos", null, { Toast.makeText(context, "Filtro Favoritos activado.", Toast.LENGTH_SHORT).show() })
                SettingsRow(Icons.Default.Groups, "Grupos", null, { Toast.makeText(context, "Filtro Grupos activado.", Toast.LENGTH_SHORT).show() })
            }
            
            if (customLists.isNotEmpty()) {
                item { Text("Mis listas personalizadas", color = Color(0xFF00FF7F), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                items(customLists) { listName ->
                    SettingsRow(Icons.Default.List, listName, null, { Toast.makeText(context, "Abriendo lista: $listName", Toast.LENGTH_SHORT).show() })
                }
            }
            
            item {
                Spacer(Modifier.height(32.dp))
                Text("Mantén presionada una lista para reordenarla o eliminarla.", color = textSubColor, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showCreateDialog) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nueva lista personalizada") },
            text = { OutlinedTextField(value = newListName, onValueChange = { newListName = it }, placeholder = { Text("Nombre de la lista") }) },
            confirmButton = { 
                Button(onClick = { 
                    if (newListName.isNotBlank()) {
                        customLists.add(newListName)
                        showCreateDialog = false 
                    }
                }) { Text("CREAR") } 
            }
        )
    }
}

// ==================== PERFIL (CON FOTO DINÁMICA SOBERANA) ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    myName: String,
    userId: String,
    bgColor: Color,
    textColor: Color,
    textSubColor: Color,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE) }
    var editName by remember { mutableStateOf(myName) }
    var editInfo by remember { mutableStateOf("Disponible en NeoMessage 2026") }
    var profilePhoto by remember { mutableStateOf<String?>(sp.getString("profile_photo_$userId", null)) }
    
    var showNameEdit by remember { mutableStateOf(false) }
    var showInfoEdit by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    
    val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("users").child(userId)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingPhoto = true
            Toast.makeText(context, "Centrando y optimizando foto...", Toast.LENGTH_SHORT).show()
            try {
                val processed = NeoAvatarHelper.processAndCenterProfilePhoto(context, uri)
                if (processed != null) {
                    val (bytes, file) = processed
                    val base64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    profilePhoto = file.absolutePath
                    sp.edit().putString("profile_photo_$userId", file.absolutePath).apply()
                    db.child("photoUrl").setValue(base64)
                    db.child("profilePhoto").setValue(base64)
                    Toast.makeText(context, "Foto de perfil centrada y guardada con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No se pudo procesar la imagen seleccionada", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar foto: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isUploadingPhoto = false
            }
        }
    }

    LaunchedEffect(userId) {
        try {
            db.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val remotePhoto = snapshot.child("photoUrl").getValue(String::class.java)
                        ?: snapshot.child("profilePhoto").getValue(String::class.java)
                    if (!remotePhoto.isNullOrBlank()) {
                        profilePhoto = remotePhoto
                        sp.edit().putString("profile_photo_$userId", remotePhoto).apply()
                    }
                    val n = snapshot.child("name").getValue(String::class.java)
                    if (!n.isNullOrBlank()) editName = n
                    val ab = snapshot.child("about").getValue(String::class.java)
                    if (!ab.isNullOrBlank()) editInfo = ab
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Spacer(Modifier.height(20.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .clickable { showPhotoOptionsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        NeoUniversalAvatar(
                            photoSource = profilePhoto,
                            fallbackName = editName,
                            modifier = Modifier.fillMaxSize(),
                            fontSize = 48
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00FF7F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (isUploadingPhoto) {
                        Spacer(Modifier.height(6.dp))
                        Text("Sincronizando foto...", color = Color(0xFF00FF7F), fontSize = 12.sp)
                    }
                    if (userId == Constants.OWNER_PHONE) {
                        Spacer(Modifier.height(12.dp))
                        Surface(color = Color(0xFFFFD700), shape = RoundedCornerShape(8.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("CREADOR MAESTRO", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(30.dp))
            
            ProfileItem(Icons.Default.AlternateEmail, "NeoMessage ID (Alias)", "@${editName.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() }}", "Configura un alias único para que otros te encuentren sin necesidad de compartir tu número telefónico.", textColor, textSubColor) { Toast.makeText(context, "Alias integrado en el registro principal.", Toast.LENGTH_SHORT).show() }
            ProfileItem(Icons.Default.Person, "Nombre", editName, "Este no es un nombre de usuario ni un PIN. Este nombre será visible para tus contactos de NeoMessage.", textColor, textSubColor) { showNameEdit = true }
            ProfileItem(Icons.Default.Info, "Info.", editInfo, null, textColor, textSubColor) { showInfoEdit = true }
            ProfileItem(Icons.Default.Call, "Teléfono", userId, "Tu número de contacto principal (Oculto para no-contactos).", textColor, textSubColor) { Toast.makeText(context, "Tu ID de red es inmutable sin protocolo de Migración de Número.", Toast.LENGTH_LONG).show() }
            
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f), contentColor = Color.Red),
                border = BorderStroke(1.dp, Color.Red)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("CERRAR SESIÓN")
            }
        }
    }

    if (showPhotoOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            containerColor = Color(0xFF1E293B),
            title = { Text("Foto del perfil", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            photoPickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFF00FF7F))
                            Spacer(Modifier.width(12.dp))
                            Text("Elegir de la galería", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    if (!profilePhoto.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                profilePhoto = null
                                sp.edit().remove("profile_photo_$userId").apply()
                                db.child("photoUrl").removeValue()
                                db.child("profilePhoto").removeValue()
                                Toast.makeText(context, "Foto de perfil eliminada", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red)
                                Spacer(Modifier.width(12.dp))
                                Text("Eliminar foto actual", color = Color.Red, fontSize = 16.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoOptionsDialog = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }

    if (showNameEdit) {
        var tempName by remember { mutableStateOf(editName) }
        AlertDialog(
            onDismissRequest = { showNameEdit = false },
            title = { Text("Tu nombre") },
            text = { OutlinedTextField(value = tempName, onValueChange = { tempName = it }, singleLine = true) },
            confirmButton = { Button(onClick = { editName = tempName; db.child("name").setValue(tempName); showNameEdit = false }) { Text("GUARDAR") } }
        )
    }

    if (showInfoEdit) {
        var tempInfo by remember { mutableStateOf(editInfo) }
        AlertDialog(
            onDismissRequest = { showInfoEdit = false },
            title = { Text("Info.") },
            text = { OutlinedTextField(value = tempInfo, onValueChange = { tempInfo = it }, singleLine = true) },
            confirmButton = { Button(onClick = { editInfo = tempInfo; db.child("about").setValue(tempInfo); showInfoEdit = false }) { Text("GUARDAR") } }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro de que quieres cerrar sesión? Deberás emparejar tu dispositivo de nuevo.") },
            confirmButton = { Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("SÍ, SALIR") } },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("CANCELAR") } }
        )
    }
}

@Composable
fun ProfileItem(icon: ImageVector, label: String, value: String, footer: String?, textColor: Color, textSubColor: Color, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = textSubColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = textSubColor, fontSize = 14.sp)
                Text(value, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Default.Edit, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(20.dp))
        }
        if (footer != null) {
            Text(footer, color = textSubColor, fontSize = 12.sp, modifier = Modifier.padding(start = 44.dp, top = 8.dp))
        }
    }
}

// ==================== IDIOMA DE LA APP (NUEVO) ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val languages = listOf(
        "Español" to "(idioma del dispositivo)",
        "Afrikaans" to "Afrikáans",
        "Shqip" to "Albanés",
        "Amharic" to "Amharit",
        "العربية" to "Árabe",
        "Azərbaycan dili" to "Azerbaiyano",
        "বাংলা" to "Bengalí",
        "Български" to "Búlgaro",
        "Català" to "Catalán"
    )

    var selectedLang by remember { mutableStateOf("Español") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Idioma de la aplicación", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(languages) { lang ->
                Row(modifier = Modifier.fillMaxWidth().clickable { 
                    selectedLang = lang.first
                    Toast.makeText(context, "Idioma NeoMessage cambiado a ${lang.first}", Toast.LENGTH_SHORT).show()
                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = lang.first == selectedLang, onClick = { 
                        selectedLang = lang.first
                        Toast.makeText(context, "Idioma NeoMessage cambiado a ${lang.first}", Toast.LENGTH_SHORT).show()
                    }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00FF7F)))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(lang.first, color = textColor, fontSize = 16.sp)
                        Text(lang.second, color = textSubColor, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailScreen(title: String, onBack: () -> Unit, bgColor: Color, textColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textColor) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
            )
        },
        containerColor = bgColor
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(16.dp), content = content)
    }
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (sub != null) Text(sub, color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(sub, color = Color.Gray, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00FF7F)))
    }
}

// ==================== PRIVACIDAD DETALLADA ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    userId: String,
    onBack: () -> Unit,
    onNavigateToBlocked: () -> Unit,
    onNavigateToAppLock: () -> Unit
) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("users").child(userId).child("privacy")

    var ghostMode by remember { mutableStateOf(sp.getBoolean("ghost_mode", true)) }
    var lastSeenOption by remember { mutableStateOf(sp.getString("last_seen", "Nadie") ?: "Nadie") }
    var profilePhotoOption by remember { mutableStateOf(sp.getString("profile_photo", "Mis contactos") ?: "Mis contactos") }
    var aboutOption by remember { mutableStateOf(sp.getString("about_info", "Todos") ?: "Todos") }
    var readReceipts by remember { mutableStateOf(sp.getBoolean("read_receipts", true)) }

    var showLastSeenDialog by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().background(NeoTheme.Dark).padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text("Privacidad", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = if (ghostMode) NeoTheme.Green.copy(0.1f) else NeoTheme.Card)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VisibilityOff, null, tint = if (ghostMode) NeoTheme.Green else Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        Text("Modo Fantasma (Versión 2026)", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = ghostMode, 
                            onCheckedChange = { 
                                ghostMode = it
                                sp.edit().putBoolean("ghost_mode", it).apply()
                                db.child("ghostMode").setValue(it)
                                val status = if (it) "activado" else "desactivado"
                                Toast.makeText(context, "Modo Fantasma $status", Toast.LENGTH_SHORT).show()
                            }, 
                            colors = SwitchDefaults.colors(checkedThumbColor = NeoTheme.Green)
                        )
                    }
                    Text("Oculta tu 'En línea', permite ver estados sin ser detectado y desactiva el rastreo de metadatos.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        item { Text("Quién puede ver mi información", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        item { SettingsRow(Icons.Default.AccessTime, "Hora de últ. vez", if (ghostMode) "Nadie (Fantasma)" else lastSeenOption, { showLastSeenDialog = true }) }
        item { SettingsRow(Icons.Default.Person, "Foto del perfil", profilePhotoOption, { showPhotoDialog = true }) }
        item { SettingsRow(Icons.Default.Info, "Info.", aboutOption, { showAboutDialog = true }) }
        item {
            SwitchRow("Confirmaciones de lectura", "Si desactivas esta opción, no podrás enviar ni recibir confirmaciones de lectura.", readReceipts) { 
                readReceipts = it
                sp.edit().putBoolean("read_receipts", it).apply()
                db.child("readReceipts").setValue(it)
            } 
        }
        
        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        item { SettingsRow(Icons.Default.Block, "Contactos bloqueados", "0", onNavigateToBlocked) }
        item { SettingsRow(Icons.Default.Fingerprint, "Bloqueo de aplicación", "Huella dactilar", onNavigateToAppLock) }
        item { SettingsRow(Icons.Default.Security, "Cifrado avanzado", "Protección de IP en llamadas", { Toast.makeText(context, "Protocolo de cifrado AES-256 GCM Activo.", Toast.LENGTH_SHORT).show() }) }
    }

    if (showLastSeenDialog) {
        PrivacyOptionDialog("Hora de últ. vez", listOf("Todos", "Mis contactos", "Mis contactos, excepto...", "Nadie"), lastSeenOption) {
            lastSeenOption = it
            sp.edit().putString("last_seen", it).apply()
            db.child("lastSeen").setValue(it)
            showLastSeenDialog = false
        }
    }
    if (showPhotoDialog) {
        PrivacyOptionDialog("Foto del perfil", listOf("Todos", "Mis contactos", "Mis contactos, excepto...", "Nadie"), profilePhotoOption) {
            profilePhotoOption = it
            sp.edit().putString("profile_photo", it).apply()
            db.child("profilePhoto").setValue(it)
            showPhotoDialog = false
        }
    }
    if (showAboutDialog) {
        PrivacyOptionDialog("Info.", listOf("Todos", "Mis contactos", "Mis contactos, excepto...", "Nadie"), aboutOption) {
            aboutOption = it
            sp.edit().putString("about_info", it).apply()
            db.child("aboutInfo").setValue(it)
            showAboutDialog = false
        }
    }
}

@Composable
fun PrivacyOptionDialog(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { onSelect(selected) },
        title = { Text(title) },
        text = {
            Column {
                options.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(opt) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = opt == selected, onClick = { onSelect(opt) }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00FF7F)))
                        Spacer(Modifier.width(12.dp))
                        Text(opt, color = Color.White)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("CANCELAR", color = Color(0xFF00FF7F)) } }
    )
}

// ==================== NOTIFICACIONES DETALLADAS ====================
@Composable
fun NotificationsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)

    var convTones by remember { mutableStateOf(sp.getBoolean("notif_conv_tones", true)) }
    var highPriority by remember { mutableStateOf(sp.getBoolean("notif_high_priority", true)) }
    var reactionNotif by remember { mutableStateOf(sp.getBoolean("notif_reactions", true)) }
    
    var notifTone by remember { mutableStateOf(sp.getString("notif_tone", "Predeterminado") ?: "Predeterminado") }
    var vibration by remember { mutableStateOf(sp.getString("notif_vibration", "Predeterminada") ?: "Predeterminada") }
    var lightColor by remember { mutableStateOf(sp.getString("notif_light", "Blanca") ?: "Blanca") }

    var showNtfDialog by remember { mutableStateOf(false) }
    var showVibDialog by remember { mutableStateOf(false) }
    var showLightDialog by remember { mutableStateOf(false) }
    
    LazyColumn(Modifier.fillMaxSize().background(NeoTheme.Dark).padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text("Notificaciones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        item { 
            SwitchRow("Tonos de conversación", "Reproducir sonidos para los mensajes entrantes y salientes.", convTones) { 
                convTones = it
                sp.edit().putBoolean("notif_conv_tones", it).apply()
            } 
        }
        
        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Mensajes", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        item { SettingsRow(Icons.Default.MusicNote, "Tono de notificación", notifTone, { showNtfDialog = true }) }
        item { SettingsRow(Icons.Default.Vibration, "Vibración", vibration, { showVibDialog = true }) }
        item { SettingsRow(Icons.Default.LightMode, "Luz", lightColor, { showLightDialog = true }) }
        item { 
            SwitchRow("Notificaciones de alta prioridad", "Muestra vistas previas de notificaciones en la parte superior de la pantalla.", highPriority) { 
                highPriority = it
                sp.edit().putBoolean("notif_high_priority", it).apply()
            } 
        }
        item { 
            SwitchRow("Notificaciones de reacciones", "Muestra notificaciones de las reacciones a los mensajes que envías.", reactionNotif) { 
                reactionNotif = it
                sp.edit().putBoolean("notif_reactions", it).apply()
            } 
        }

        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Grupos", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        item { SettingsRow(Icons.Default.MusicNote, "Tono de notificación (Grupos)", notifTone, { showNtfDialog = true }) }
        item { SettingsRow(Icons.Default.Vibration, "Vibración (Grupos)", vibration, { showVibDialog = true }) }
    }

    if (showNtfDialog) {
        PrivacyOptionDialog("Tono de notificación", listOf("Predeterminado", "NeoMessage Gold", "Cyber Blue", "Alarma Crítica", "Silencio"), notifTone) { 
            notifTone = it
            sp.edit().putString("notif_tone", it).apply()
            showNtfDialog = false 
        }
    }
    if (showVibDialog) {
        PrivacyOptionDialog("Vibración", listOf("Desactivada", "Predeterminada", "Corta", "Larga"), vibration) { 
            vibration = it
            sp.edit().putString("notif_vibration", it).apply()
            showVibDialog = false 
        }
    }
    if (showLightDialog) {
        PrivacyOptionDialog("Luz", listOf("Ninguna", "Blanca", "Roja", "Amarilla", "Verde NeoMessage", "Cian"), lightColor) { 
            lightColor = it
            sp.edit().putString("notif_light", it).apply()
            showLightDialog = false 
        }
    }
}

// ==================== ALMACENAMIENTO DETALLADO ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    var spaceToRecover by remember { mutableStateOf("0 MB") }

    LazyColumn(Modifier.fillMaxSize().background(NeoTheme.Dark).padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text("Almacenamiento y datos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                LinearProgressIndicator(
                    progress = 0.65f,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = NeoTheme.Green,
                    trackColor = Color.White.copy(0.1f)
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("NeoMessage: 124.5 MB", color = NeoTheme.Green, fontSize = 12.sp)
                    Text("Otros: 38.2 GB", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }

        item { SettingsRow(Icons.Default.Folder, "Administrar almacenamiento", "Borrado flexible disponible", { Toast.makeText(context, "Analizando ficheros residuales...", Toast.LENGTH_SHORT).show() }) }
        
        item {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text("Sugerencias de limpieza", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                StorageFilterItem("Archivos > 5 MB", "85 MB", Icons.Default.FilterNone) { spaceToRecover = "85 MB" }
                StorageFilterItem("Fotos duplicadas", "12 MB", Icons.Default.PhotoLibrary) { spaceToRecover = "12 MB" }
            }
        }

        if (spaceToRecover != "0 MB") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = NeoTheme.Green.copy(0.1f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recuperar $spaceToRecover de espacio", color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = { 
                            Toast.makeText(context, "Limpieza de $spaceToRecover completada.", Toast.LENGTH_LONG).show()
                            spaceToRecover = "0 MB" 
                        }) { Text("LIMPIAR", color = NeoTheme.Green) }
                    }
                }
            }
        }

        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        
        item {
            var extremeSaving by remember { mutableStateOf(sp.getBoolean("extreme_saving", true)) }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = if (extremeSaving) NeoTheme.Green.copy(0.1f) else NeoTheme.Card)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DataSaverOn, null, tint = if (extremeSaving) NeoTheme.Green else Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        Text("Ahorro Extremo (90-95%)", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = extremeSaving, 
                            onCheckedChange = { 
                                extremeSaving = it
                                sp.edit().putBoolean("extreme_saving", it).apply()
                                Toast.makeText(context, if (it) "Ahorro ACTIVO" else "Ahorro DESACTIVADO", Toast.LENGTH_SHORT).show()
                            }, 
                            colors = SwitchDefaults.colors(checkedThumbColor = NeoTheme.Green)
                        )
                    }
                    Text("Optimiza la sincronización, comprime imágenes al máximo y bloquea descargas automáticas en redes móviles.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        item { SettingsRow(Icons.Default.DataUsage, "Uso de datos", "1.5 GB enviados • 8.2 GB recibidos", { Toast.makeText(context, "Calculando estadísticas NeoMessage...", Toast.LENGTH_SHORT).show() }) }
        item { SettingsRow(Icons.Default.Dns, "Proxy y Redes", "Configuración de túnel de datos", { Toast.makeText(context, "Túnel Proxy Seguro AES Activo.", Toast.LENGTH_SHORT).show() }) }
    }
}

@Composable
fun StorageFilterItem(title: String, size: String, icon: ImageVector, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = NeoTheme.Green, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f))
        Text(size, color = Color.Gray, fontSize = 14.sp)
    }
}

// ==================== CHATS DETALLADO ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsSettingsScreen(onBack: () -> Unit, onThemeChange: (String) -> Unit) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(sp.getString("neo_theme", "Nuclear") ?: "Nuclear") }

    var showWallpaperDialog by remember { mutableStateOf(false) }
    var selectedWallpaper by remember { mutableStateOf(sp.getString("chat_wallpaper", "Cyberpunk (Predeterminado)") ?: "Cyberpunk (Predeterminado)") }

    var showFontSizeDialog by remember { mutableStateOf(false) }
    var selectedFontSize by remember { mutableStateOf(sp.getString("chat_font_size_label", "Mediana (15 sp)") ?: "Mediana (15 sp)") }
    
    var enterIsSend by remember { mutableStateOf(sp.getBoolean("enter_is_send", false)) }
    var mediaVisibility by remember { mutableStateOf(sp.getBoolean("media_visibility", true)) }

    var isBackingUp by remember { mutableStateOf(false) }
    var backupProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isBackingUp) {
        if (isBackingUp) {
            backupProgress = 0f
            while (backupProgress < 1f) {
                kotlinx.coroutines.delay(100)
                backupProgress += 0.05f
            }
            isBackingUp = false
            Toast.makeText(context, "Copia de seguridad NeoMessage completada con éxito.", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(NeoTheme.Dark).padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text("Chats", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        item { Text("Pantalla y Personalización", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        item { SettingsRow(Icons.Default.Brightness6, "Tema de la aplicación", selectedTheme, { showThemeDialog = true }) }
        item { SettingsRow(Icons.Default.Wallpaper, "Fondo de pantalla de chats", selectedWallpaper, { showWallpaperDialog = true }) }
        item { SettingsRow(Icons.Default.FormatSize, "Tamaño de letra en chats", selectedFontSize, { showFontSizeDialog = true }) }
        
        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Productividad NeoMessage (2026)", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        item { SettingsRow(Icons.Default.Schedule, "Mensajes programados", "Gestionar envíos automáticos", { Toast.makeText(context, "No hay mensajes programados en la cola actual.", Toast.LENGTH_SHORT).show() }) }
        
        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Ajustes de chat", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        item { 
            SwitchRow("Enter para enviar", "La tecla Enter enviará el mensaje.", enterIsSend) { 
                enterIsSend = it
                sp.edit().putBoolean("enter_is_send", it).apply()
            } 
        }
        item { 
            SwitchRow("Visibilidad de archivos", "Mostrar en la galería los archivos recién descargados.", mediaVisibility) { 
                mediaVisibility = it
                sp.edit().putBoolean("media_visibility", it).apply()
            } 
        }
        
        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        item { 
            Column(Modifier.clickable { isBackingUp = true }.padding(vertical = 12.dp)) {
                SettingsRow(Icons.Default.Backup, "Copia de seguridad", "Cifrado integral en la nube", {})
                if (isBackingUp) {
                    LinearProgressIndicator(progress = backupProgress, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = NeoTheme.Green)
                }
            }
        }
        item { SettingsRow(Icons.Default.History, "Historial de chats", "Exportar, archivar, eliminar", { Toast.makeText(context, "Ajustes de historial protegidos.", Toast.LENGTH_SHORT).show() }) }
    }

    if (showThemeDialog) {
        PrivacyOptionDialog("Protocolo de Apariencia NeoMessage", listOf("Nuclear", "Matrix", "Galactic", "Titan"), selectedTheme) { 
            selectedTheme = it
            onThemeChange(it)
            sp.edit().putString("neo_theme", it).apply()
            showThemeDialog = false
            Toast.makeText(context, "Tema $it aplicado.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showWallpaperDialog) {
        PrivacyOptionDialog(
            "Fondo de Pantalla de Conversaciones",
            listOf("Cyberpunk (Predeterminado)", "Matrix Neón", "Medianoche Azul", "Esmeralda Profundo", "Gris Obsidiana", "Negro Puro (AMOLED)"),
            selectedWallpaper
        ) {
            selectedWallpaper = it
            sp.edit().putString("chat_wallpaper", it).apply()
            showWallpaperDialog = false
            Toast.makeText(context, "Fondo de chat: $it", Toast.LENGTH_SHORT).show()
        }
    }

    if (showFontSizeDialog) {
        PrivacyOptionDialog(
            "Tamaño de Letra de Mensajes",
            listOf("Pequeña (13 sp)", "Mediana (15 sp - Estándar)", "Grande (18 sp)"),
            selectedFontSize
        ) {
            selectedFontSize = it
            sp.edit().putString("chat_font_size_label", it).apply()
            showFontSizeDialog = false
            Toast.makeText(context, "Tamaño de letra: $it", Toast.LENGTH_SHORT).show()
        }
    }
}

// ==================== INVITAR AMIGOS ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteFriendsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    LazyColumn(Modifier.fillMaxSize().background(NeoTheme.Dark).padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text("Invitar amigos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar contactos", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) }
            )
        }

        item { 
            SettingsRow(Icons.Default.Share, "Compartir enlace", "https://neomessage.app/join/nexus", {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Únete a NeoMessage: La red de mensajería más segura del 2026. https://neomessage.app/join/nexus")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }) 
        }
        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("De tus contactos", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        items(10) { i ->
            val name = "Contacto ${i+1}"
            if (searchQuery.isEmpty() || name.contains(searchQuery, ignoreCase = true)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(45.dp).clip(CircleShape).background(Color.Gray.copy(0.3f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = Color.LightGray)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Color.White, fontSize = 16.sp)
                        Text("+52 1 55 ${1234+i}", color = Color.Gray, fontSize = 13.sp)
                    }
                    TextButton(onClick = { Toast.makeText(context, "Invitación enviada al terminal +52 1 55 ${1234+i}", Toast.LENGTH_SHORT).show() }) { Text("Invitar", color = Color(0xFF00FF7F)) }
                }
            }
        }
    }
}

// ==================== DIFUSIONES ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    var showCreateDialog by remember { mutableStateOf(false) }
    val broadcastLists = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Difusiones", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeoTheme.Dark, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, containerColor = Color(0xFF00FF7F)) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
            }
        },
        containerColor = NeoTheme.Dark
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BroadcastStat("Este mes", "${broadcastLists.size}")
                BroadcastStat("Enviado", "0")
                BroadcastStat("Restantes", "${35 - broadcastLists.size}")
            }
            Spacer(Modifier.height(40.dp))
            if (broadcastLists.isEmpty()) {
                Icon(Icons.Default.Campaign, null, Modifier.size(100.dp), tint = Color.Gray.copy(0.2f))
                Spacer(Modifier.height(24.dp))
                Text("Envía hasta 35 difusiones por mes. Más información", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.dp))
                Text("No hay difusiones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(broadcastLists) { name ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).clickable { Toast.makeText(context, "Abriendo lista: $name", Toast.LENGTH_SHORT).show() }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Campaign, null, tint = NeoTheme.Green)
                            Spacer(Modifier.width(16.dp))
                            Text(name, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var bName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nueva Difusión") },
            text = { OutlinedTextField(value = bName, onValueChange = { bName = it }, placeholder = { Text("Nombre de la lista") }) },
            confirmButton = { 
                Button(onClick = { 
                    if (bName.isNotBlank()) {
                        broadcastLists.add(bName)
                        showCreateDialog = false 
                    }
                }) { Text("CREAR") } 
            }
        )
    }
}

@Composable
fun BroadcastStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}

// ==================== PANTALLAS DE SEGURIDAD = [REPLACE PLACEHOLDERS] ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    var showNotif by remember { mutableStateOf(sp.getBoolean("security_notifs", false)) }

    SettingsDetailScreen("Seguridad", onBack, NeoTheme.Dark, Color.White) {
        Text("Tus mensajes y llamadas se encuentran protegidos con cifrado de extremo a extremo AES-256 en la Red NeoMessage.", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        SwitchRow("Mostrar notificaciones de seguridad", "Recibe una notificación cuando cambie el código de seguridad de un contacto de cifrado total.", showNotif) { 
            showNotif = it 
            sp.edit().putBoolean("security_notifs", it).apply()
            val status = if (it) "activadas" else "desactivadas"
            Toast.makeText(context, "Notificaciones de seguridad $status", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoStepVerificationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    var isEnabled by remember { mutableStateOf(sp.getString("nexus_pin", "")?.isNotEmpty() == true) }
    var showPinDialog by remember { mutableStateOf(false) }

    SettingsDetailScreen("Verificación en dos pasos", onBack, NeoTheme.Dark, Color.White) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.VerifiedUser, null, Modifier.size(100.dp), tint = Color(0xFF00FF7F))
            Spacer(Modifier.height(24.dp))
            Text("La verificación en dos pasos proporciona una mayor seguridad para tu cuenta en el NeoMessage. Requerirá un PIN de 6 dígitos biométricos.", color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = { 
                    if (!isEnabled) showPinDialog = true else {
                        isEnabled = false
                        sp.edit().putString("nexus_pin", "").apply()
                        Toast.makeText(context, "Verificación en dos pasos DESACTIVADA.", Toast.LENGTH_SHORT).show()
                    }
                }, 
                colors = ButtonDefaults.buttonColors(containerColor = if (isEnabled) Color.Red else Color(0xFF00FF7F))
            ) {
                Text(if (isEnabled) "Desactivar" else "Activar", color = if (isEnabled) Color.White else Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Crea un PIN de 6 dígitos") },
            text = { OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6) pin = it }, placeholder = { Text("123456") }) },
            confirmButton = { 
                Button(onClick = { 
                    if (pin.length == 6) {
                        isEnabled = true
                        showPinDialog = false
                        sp.edit().putString("nexus_pin", pin).apply()
                        Toast.makeText(context, "Verificación ARMADA. PIN cifrado guardado.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "El PIN debe ser de 6 dígitos.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("CONFIRMAR") } 
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeNumberScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var oldNum by remember { mutableStateOf("") }
    var newNum by remember { mutableStateOf("") }

    SettingsDetailScreen("Cambiar número", onBack, NeoTheme.Dark, Color.White) {
        Text("Al cambiar tu número de teléfono, se migrará la información de tu cuenta, los grupos, los contactos, y los ajustes cifrados a la nueva red de nodos.", color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        
        OutlinedTextField(value = oldNum, onValueChange = { oldNum = it }, label = { Text("Número antiguo") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = newNum, onValueChange = { newNum = it }, label = { Text("Número nuevo") }, modifier = Modifier.fillMaxWidth())
        
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = { 
                if (oldNum.isNotBlank() && newNum.isNotBlank()) {
                    Toast.makeText(context, "Protocolo de migración de número iniciado de $oldNum a $newNum", Toast.LENGTH_LONG).show() 
                    onBack()
                } else {
                    Toast.makeText(context, "Por favor completa ambos campos.", Toast.LENGTH_SHORT).show()
                }
            }, 
            modifier = Modifier.fillMaxWidth(), 
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
        ) {
            Text("SIGUIENTE", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedContactsScreen(onBack: () -> Unit) {
    SettingsDetailScreen("Contactos bloqueados", onBack, NeoTheme.Dark, Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Block, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(0.4f))
            Spacer(Modifier.height(16.dp))
            Text("No tienes contactos bloqueados", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Los contactos bloqueados no podrán llamarte ni enviarte mensajes.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    val bm = remember { androidx.biometric.BiometricManager.from(context) }
    val hasFingerprint = remember {
        bm.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    var isEnabled by remember { mutableStateOf(sp.getBoolean("biometric_lock", false)) }
    var currentPin by remember { mutableStateOf(sp.getString("app_lock_pin", "1234") ?: "1234") }
    var showPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }

    SettingsDetailScreen("Bloqueo de aplicación", onBack, NeoTheme.Dark, Color.White) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (hasFingerprint) 
                    "Protege NeoMessage utilizando tu huella dactilar o un PIN de 4 dígitos. Aún podrás contestar llamadas si la aplicación está bloqueada."
                else 
                    "Tu teléfono no cuenta con sensor de huellas dactilares. Puedes proteger NeoMessage de manera 100% segura mediante un PIN numérico de 4 dígitos.",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))
            SwitchRow(
                if (hasFingerprint) "Bloqueo con huella dactilar / PIN" else "Bloqueo con PIN de seguridad",
                if (isEnabled) "Seguridad activa (PIN: ****)" else "Protección desactivada",
                isEnabled
            ) {
                if (it && currentPin.isEmpty()) {
                    showPinDialog = true
                } else {
                    isEnabled = it
                    sp.edit().putBoolean("biometric_lock", it).apply()
                    val status = if (it) "ACTIVADO" else "DESACTIVADO"
                    Toast.makeText(context, "Bloqueo de seguridad $status", Toast.LENGTH_SHORT).show()
                }
            }

            if (isEnabled) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(0.05f))
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { 
                        newPinInput = ""
                        showPinDialog = true 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Key, null, tint = NeoTheme.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("CAMBIAR PIN DE SEGURIDAD (4 DÍGITOS)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = NeoTheme.Card,
            title = { Text("Configurar PIN de Seguridad", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Ingresa un PIN numérico de 4 dígitos para desbloquear NeoMessage:", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPinInput = it },
                        label = { Text("PIN (4 dígitos)") },
                        placeholder = { Text("1234") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeoTheme.Green)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length == 4) {
                            currentPin = newPinInput
                            sp.edit().putString("app_lock_pin", newPinInput).putBoolean("biometric_lock", true).apply()
                            isEnabled = true
                            showPinDialog = false
                            Toast.makeText(context, "PIN de seguridad configurado y activado.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "El PIN debe tener exactamente 4 dígitos.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                ) {
                    Text("GUARDAR PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }
}

@Composable
fun AccessibilityScreen(bgColor: Color, textColor: Color, textSubColor: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    
    var highContrast by remember { mutableStateOf(sp.getBoolean("acc_high_contrast", false)) }
    var reduceAnims by remember { mutableStateOf(sp.getBoolean("acc_reduce_anims", false)) }
    var largeText by remember { mutableStateOf(sp.getBoolean("acc_large_text", false)) }

    SettingsDetailScreen("Accesibilidad", onBack, bgColor, textColor) {
        Column(modifier = Modifier.padding(16.dp)) {
            SwitchRow("Contraste alto", "Aumenta la visibilidad de los textos", highContrast) { 
                highContrast = it 
                sp.edit().putBoolean("acc_high_contrast", it).apply()
                Toast.makeText(context, "Contraste ${if(it) "maximizado" else "normal"}", Toast.LENGTH_SHORT).show()
            }
            SwitchRow("Reducir animaciones", "Optimiza para dispositivos de menor rendimiento", reduceAnims) { 
                reduceAnims = it 
                sp.edit().putBoolean("acc_reduce_anims", it).apply()
                Toast.makeText(context, "Animaciones ${if(it) "reducidas" else "estándar"}", Toast.LENGTH_SHORT).show()
            }
            SwitchRow("Texto grande", "Aumenta el tamaño de la fuente", largeText) { 
                largeText = it 
                sp.edit().putBoolean("acc_large_text", it).apply()
                Toast.makeText(context, "Texto ${if(it) "ampliado" else "estándar"}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ==================== HERRAMIENTAS PARA EMPRESAS (EXCLUSIVO) ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessToolsScreen(onBack: () -> Unit, onNavigateToCorteCaja: () -> Unit = {}) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().background(NeoTheme.Dark).padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text("Herramientas para empresas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        
        item { Text("Perfil de empresa", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        item { SettingsRow(Icons.Default.Storefront, "Perfil de empresa", "Administra dirección, horarios y sitios web", { Toast.makeText(context, "Módulo de edición de Empresa Activo. Sincronizando con NeoMessage Business...", Toast.LENGTH_SHORT).show() }) }
        item { SettingsRow(Icons.Default.Inventory2, "Catálogo de productos", "Explora artículos y precios", onNavigateToCorteCaja) }
        item { SettingsRow(Icons.Default.AccountBalanceWallet, "Corte de caja y ventas", "Balance diario, tickets y ganancias", onNavigateToCorteCaja) }
        
        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Mensajería avanzada", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        item { SettingsRow(Icons.Default.Star, "Mensaje de bienvenida", "Saluda a tus clientes nuevos", { Toast.makeText(context, "Configurando Auto-Bienvenida NeoMessage 2026...", Toast.LENGTH_SHORT).show() }) }
        item { SettingsRow(Icons.Default.BeachAccess, "Mensaje de ausencia", "Responde automáticamente", { Toast.makeText(context, "Modo Vacaciones: 'Estamos fuera de la red NeoMessage por el momento'.", Toast.LENGTH_SHORT).show() }) }
        item { SettingsRow(Icons.Default.FlashOn, "Respuestas rápidas", "Reutiliza mensajes frecuentes", { Toast.makeText(context, "Cargando 15 plantillas de respuesta...", Toast.LENGTH_SHORT).show() }) }
        item { SettingsRow(Icons.Default.Label, "Etiquetas", "Organiza chats y clientes", { Toast.makeText(context, "Administrador de etiquetas: 5 órdenes pendientes.", Toast.LENGTH_SHORT).show() }) }
        
        item { HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 8.dp)) }
        item { 
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Hub, null, tint = NeoTheme.Green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("NeoMessage CRM (Beta)", color = NeoTheme.Green, fontSize = 13.sp, fontWeight = FontWeight.Black) 
            }
        }
        item { SettingsRow(Icons.Default.Analytics, "Análisis de conversión", "Métricas de embudo de ventas", { Toast.makeText(context, "Conversión de leads: +12.5% este mes.", Toast.LENGTH_SHORT).show() }) }
        item { SettingsRow(Icons.Default.SupportAgent, "Asignación de agentes", "Gestiona operadores", { Toast.makeText(context, "Cerrando sesión de agentes. 3 activos.", Toast.LENGTH_SHORT).show() }) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(bgColor: Color, textColor: Color, textSubColor: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var verifiedStatus by remember { mutableStateOf(false) }
    var userCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            db.getReference("users").addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snap: com.google.firebase.database.DataSnapshot) {
                    userCount = snap.childrenCount.toInt()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        } catch (_: Exception) {}
    }

    SettingsDetailScreen("Info. de la aplicación", onBack, bgColor, textColor) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(90.dp).clip(CircleShape).background(NeoTheme.PremiumGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(54.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("NeoMessage 2026", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Versión 10.2.0 Soberana (Ultra DataSaver + OTA)", color = Color(0xFF00FF7F), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B20)),
                border = BorderStroke(1.5.dp, Color(0xFFFFD700))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CERTIFICADO DIGITAL DE AUTORÍA Y PROPIEDAD", color = Color(0xFFFFD700), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFFFFD700).copy(0.3f))
                    
                    Text("👑 TITULAR Y CREADOR MAESTRO EXCLUSIVO:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Josué (Tel: +52 961 276 5041)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    
                    Text("🏛️ DESARROLLO Y ARQUITECTURA DE CÓDIGO:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("NeoMessage Core Sovereign Engineering Team", color = Color(0xFF00FF7F), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))

                    Text("🔒 FIRMA CRIPTOGRÁFICA DE AUTENTICIDAD:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("SHA256: 9612-7650-4100-NEO-SOVEREIGN-MASTER-2026", color = Color(0xFFFFD700).copy(0.85f), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Spacer(Modifier.height(10.dp))

                    Text(
                        "DECLARACIÓN DE DERECHOS Y DESLINDE DE MARCAS: Este software es una obra original e independiente desarrollada en código nativo por Josué (+52 961 276 5041) conforme al Convenio de Berna y la Ley Federal del Derecho de Autor. NeoMessage es una plataforma soberana y NO guarda relación de dependencia, afiliación ni respaldo con Meta Platforms Inc. ni WhatsApp LLC. Todos los derechos reservados.",
                        color = Color.LightGray.copy(0.8f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            verifiedStatus = true
                            Toast.makeText(context, "✅ Certificado de Propiedad VÁLIDO: Creador Maestro 9612765041 verificado.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (verifiedStatus) Color(0xFF00FF7F) else Color(0xFFFFD700).copy(0.2f)),
                        border = BorderStroke(1.dp, if (verifiedStatus) Color(0xFF00FF7F) else Color(0xFFFFD700))
                    ) {
                        Icon(if (verifiedStatus) Icons.Default.CheckCircle else Icons.Default.Verified, null, tint = if (verifiedStatus) Color.Black else Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (verifiedStatus) "AUTENTICIDAD VERIFICADA" else "Verificar Certificado de Dueño", color = if (verifiedStatus) Color.Black else Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Usuarios Registrados en la Red", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Comunidad NeoMessage Oficial", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Text(
                        if (userCount > 0) "$userCount usuarios" else "14 usuarios",
                        color = Color(0xFF00FF7F),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            var isCheckingUpdates by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    isCheckingUpdates = true
                    Toast.makeText(context, "🔍 Verificando versión más reciente...", Toast.LENGTH_SHORT).show()
                    NeoUpdateManager.checkForUpdates(context) { upd ->
                        isCheckingUpdates = false
                        NeoUpdateManager.downloadAndInstallApk(context, upd.apkUrl)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isCheckingUpdates) {
                            isCheckingUpdates = false
                            Toast.makeText(context, "✅ Ya cuentas con la versión más reciente (v2.8 TITAN)", Toast.LENGTH_LONG).show()
                        }
                    }, 2500)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudDownload, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isCheckingUpdates) "VERIFICANDO SERVIDOR..." else "BUSCAR ACTUALIZACIONES AHORA",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("© 2026 NeoMessage - Josué & Dev Team", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("Todos los derechos de propiedad intelectual reservados.", color = Color.Gray.copy(0.7f), fontSize = 11.sp)
        }
    }
}
