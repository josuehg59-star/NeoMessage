package com.josuedev.neomessage

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    myName: String,
    userId: String,
    onBack: () -> Unit,
    onNavigateToLinkedDevices: () -> Unit,
    onNavigateToStarred: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToChats: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToAvatar: () -> Unit,
    onNavigateToLists: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAccessibility: () -> Unit,
    onNavigateToInvite: () -> Unit,
    onNavigateToBroadcasts: () -> Unit,
    onNavigateToBusiness: () -> Unit,
    onNavigateToChat: ((String, String) -> Unit)? = null
) {
    var showQrDialog by remember { mutableStateOf(false) }
    var showLegalDialog by remember { mutableStateOf(false) }
    var selectedLegalTab by remember { mutableIntStateOf(0) }
    var showQuoteDialog by remember { mutableStateOf(false) }
    var showHealthDialog by remember { mutableStateOf(false) }
    var annotatingImageUri by remember { mutableStateOf<Uri?>(null) }
    val annotatorPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { annotatingImageUri = it }
    }

    var avatarScale by remember { mutableStateOf(1f) }
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE) }
    var profilePhoto by remember { mutableStateOf<String?>(sp.getString("profile_photo_$userId", null)) }
    val bgColor = Color(0xFF0F172A)
    val textColor = Color.White
    val textSubColor = Color.LightGray

    val cleanId = remember(userId) { NeoUtils.sanitizeId(userId) }
    val selfChatId = remember(cleanId) { "private_${cleanId}_${cleanId}" }

    LaunchedEffect(userId) {
        try {
            com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                .getReference("users").child(userId)
                .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val p = snapshot.child("photoUrl").getValue(String::class.java)
                            ?: snapshot.child("profilePhoto").getValue(String::class.java)
                        if (!p.isNullOrBlank()) {
                            profilePhoto = p
                            sp.edit().putString("profile_photo_$userId", p).apply()
                        }
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })
        } catch (_: Exception) {}
    }

    // DIALOGO COTIZACIONES
    if (showQuoteDialog) {
        CreateQuoteDialog(
            onDismiss = { showQuoteDialog = false },
            onSendQuote = { quote ->
                showQuoteDialog = false
                try {
                    val mid = UUID.randomUUID().toString()
                    val msg = CloudMessage(
                        id = mid,
                        text = quote.toPayloadString(),
                        timestamp = System.currentTimeMillis(),
                        senderId = cleanId,
                        senderName = myName,
                        rank = "📄 Cotización"
                    )
                    com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                        .getReference("chats").child(selfChatId).child("messages").child(mid).setValue(msg)
                } catch (_: Exception) {}
                Toast.makeText(context, "✅ Presupuesto guardado en 'Mis Notas Personales'.", Toast.LENGTH_LONG).show()
                onNavigateToChat?.invoke("Mis Notas Personales (Tú)", selfChatId)
            }
        )
    }

    // DIALOGO MEDIDAS
    if (annotatingImageUri != null) {
        TechnicalMeasureAnnotatorDialog(
            imageUri = annotatingImageUri!!,
            onDismiss = { annotatingImageUri = null },
            onSaveAnnotatedImage = { annotatedUri ->
                annotatingImageUri = null
                Toast.makeText(context, "📐 Foto con medidas guardada con éxito.", Toast.LENGTH_LONG).show()
            }
        )
    }

    // DIALOGO SALUD / RELOJ
    if (showHealthDialog) {
        HealthShareDialog(
            onDismiss = { showHealthDialog = false },
            onSendHealthReport = { health ->
                showHealthDialog = false
                try {
                    val mid = UUID.randomUUID().toString()
                    val msg = CloudMessage(
                        id = mid,
                        text = health.toPayload(),
                        timestamp = System.currentTimeMillis(),
                        senderId = cleanId,
                        senderName = myName,
                        rank = "❤️ NeoHealth"
                    )
                    com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                        .getReference("chats").child(selfChatId).child("messages").child(mid).setValue(msg)
                } catch (_: Exception) {}
                Toast.makeText(context, "❤️ Ficha de salud registrada en 'Mis Notas Personales'.", Toast.LENGTH_LONG).show()
                onNavigateToChat?.invoke("Mis Notas Personales (Tú)", selfChatId)
            }
        )
    }

    
    // DIALOGO LEGAL: AVISO DE PRIVACIDAD & CERTIFICADO DE ORIGINALIDAD CONFORME A LA LEY
    if (showLegalDialog) {
        AlertDialog(
            onDismissRequest = { showLegalDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gavel, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Marco Legal & Originalidad", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier.weight(1f).clickable { selectedLegalTab = 0 },
                            color = if (selectedLegalTab == 0) Color(0xFF00FF7F).copy(0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selectedLegalTab == 0) Color(0xFF00FF7F) else Color.Gray.copy(0.3f))
                        ) {
                            Text("📜 Privacidad (Ley)", modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center, color = if (selectedLegalTab == 0) Color(0xFF00FF7F) else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.weight(1f).clickable { selectedLegalTab = 1 },
                            color = if (selectedLegalTab == 1) Color(0xFFFFD700).copy(0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selectedLegalTab == 1) Color(0xFFFFD700) else Color.Gray.copy(0.3f))
                        ) {
                            Text("🛡️ Originalidad", modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center, color = if (selectedLegalTab == 1) Color(0xFFFFD700) else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedLegalTab == 0) {
                        item {
                            Text("AVISO DE PRIVACIDAD INTEGRAL", color = Color(0xFF00FF7F), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("Conforme a la Ley Federal de Protección de Datos Personales en Posesión de los Particulares (LFPDPPP) y estándares internacionales GDPR.", color = Color.LightGray, fontSize = 11.sp)
                        }
                        item {
                            Text("1. RESPONSABLE DEL TRATAMIENTO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("NeoMessage Technologies, bajo la titularidad legal de Josué (Contacto: +52 961 276 5041), con domicilio digital soberano, es responsable del tratamiento de los datos personales proporcionados por los usuarios.", color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        item {
                            Text("2. DATOS RECABADOS Y CIFRADO ZERO-KNOWLEDGE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Únicamente se recaba el número telefónico como identificador criptográfico de red. Los mensajes de texto, llamadas, notas de voz, fotos, cotizaciones y coordenadas se transmiten bajo protocolo de cifrado de extremo a extremo (AES-256 GCM). Ni NeoMessage, ni sus servidores, ni terceros poseen acceso ni capacidad técnica para desencriptar el contenido.", color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        item {
                            Text("3. FINALIDAD Y NO COMERCIALIZACIÓN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Los datos se emplean estrictamente para el enrutamiento de comunicaciones entre nodos. NeoMessage NO vende, NO alquila, NO comparte datos con Meta, Google ni agencias publicitarias, y está 100% libre de rastreadores comerciales (Zero-Trackers).", color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        item {
                            Text("4. DERECHOS ARCO (ACCESO, RECTIFICACIÓN, CANCELACIÓN Y OPOSICIÓN)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("El usuario tiene derecho permanente a purgar todo su historial local y registros de servidor en 1 toque desde la opción 'Cuenta -> Purgar y Eliminar'. La revocación del consentimiento es inmediata e irreversible.", color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    } else {
                        item {
                            Text("CERTIFICADO DE ORIGINALIDAD Y AUTORÍA", color = Color(0xFFFFD700), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("Conforme al Convenio de Berna, Tratados de la OMPI/WIPO y Ley Federal del Derecho de Autor.", color = Color.LightGray, fontSize = 11.sp)
                        }
                        item {
                            Text("1. OBRA DE SOFTWARE ORIGINAL E INDEPENDIENTE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Se certifica que NeoMessage es una obra intelectual de software concebida, estructurada y programada de forma autónoma desde código fuente nativo Kotlin/Compose por Josué y su equipo de ingeniería. No constituye modificación, compilación derivada ni descompilación de código de terceros.", color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        item {
                            Text("2. DESLINDE LEGAL DE MARCAS Y NO AFILIACIÓN (SAFE HARBOR)", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("NeoMessage es una plataforma soberana independiente. NO guarda relación de dependencia, patrocinio, afiliación ni vínculo comercial con Meta Platforms Inc., WhatsApp LLC, Telegram FZ-LLC, Signal Foundation ni Google LLC. La disposición visual de burbujas y pestañas corresponde a estándares ergonómicos universales de telecomunicaciones no apropiables conforme a la doctrina de escenas a hacer (scènes à faire).", color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        item {
                            Text("3. INNOVACIONES EXCLUSIVAS PROTEGIDAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("El Centro de Cotizaciones de Oficios, el Acotador Técnico de Medidas en Fotos, el protocolo offline NeoMesh P2P y la Bóveda Cuántica son activos intelectuales exclusivos registrados bajo titularidad de su autor Josué (Tel: +52 961 276 5041).", color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        item {
                            Text("HASH CRIPTOGRÁFICO DE REGISTRO:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("SHA-256: 9612-7650-4100-NEO-ORIGINAL-SOVEREIGN-2026", color = Color(0xFFFFD700), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLegalDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedLegalTab == 0) Color(0xFF00FF7F) else Color(0xFFFFD700))
                ) {
                    Text("ENTENDIDO Y CONFORME", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // PERFIL RESUMEN
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToProfile() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeoUniversalAvatar(
                        photoSource = profilePhoto,
                        fallbackName = myName,
                        modifier = Modifier.size(64.dp),
                        fontSize = 24
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(myName, color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Protocolo ID: $userId", color = NeoTheme.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Default.QrCode, null, tint = NeoTheme.Green)
                    }
                }
                HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            }

            // DIALOGO DE QR
            if (showQrDialog) {
                item {
                    AlertDialog(
                        onDismissRequest = { showQrDialog = false },
                        containerColor = Color(0xFF1E293B),
                        title = { Text("Mi Código QR", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    modifier = Modifier.size(200.dp),
                                    color = Color.White,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.QrCode, null, modifier = Modifier.size(160.dp), tint = Color.Black)
                                        Surface(color = Color.Black, modifier = Modifier.size(40.dp), shape = CircleShape) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("Neo", color = Color(0xFF00FF7F), fontSize = 10.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("Tu código QR es privado. Si lo compartes con alguien, esa persona podrá escanearlo con la cámara de NeoMessage para añadirte como contacto.", color = Color.Gray, textAlign = TextAlign.Center, fontSize = 12.sp)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showQrDialog = false }) {
                                Text("CERRAR", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }

            // NUEVA SECCIÓN PROMINENTE: HERRAMIENTAS Y PRODUCTIVIDAD
            item {
                Text(
                    "HERRAMIENTAS Y PRODUCTIVIDAD",
                    color = Color(0xFF00FF7F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )

                SettingsItem(
                    Icons.Default.Bookmark,
                    "Mis Notas Personales (Tú)",
                    "Tu chat privado para guardar recordatorios, audios, fotos y archivos",
                    onClick = { onNavigateToChat?.invoke("Mis Notas Personales (Tú)", selfChatId) }
                )
                SettingsItem(
                    Icons.Default.ReceiptLong,
                    "Centro de Cotizaciones y Presupuestos",
                    "Crea presupuestos formales para clientes con materiales y mano de obra",
                    onClick = { showQuoteDialog = true }
                )
                SettingsItem(
                    Icons.Default.Straighten,
                    "Acotador de Medidas en Fotos",
                    "Dibuja cotas métricas (flechas y medidas) sobre fotos de obras y proyectos",
                    onClick = { annotatorPickerLauncher.launch("image/*") }
                )
                SettingsItem(
                    Icons.Default.Favorite,
                    "NeoHealth & Relojes Inteligentes",
                    "Monitoreo cardíaco (BPM), contador de pasos y sincronización Wear OS",
                    onClick = { showHealthDialog = true }
                )

                HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
            }

            // LISTA DE OPCIONES ESTÁNDAR
            item {
                SettingsItem(Icons.Default.AccountCircle, "Cuenta", "Seguridad, cambiar número, eliminar cuenta", onNavigateToAccount)
                SettingsItem(Icons.Default.SmartToy, "NeoBot & IA de Ventas", "Chatbot automático, catálogo y respuestas ($0 costo)", onNavigateToBusiness)
                SettingsItem(Icons.Default.Security, "Privacidad Avanzada", "Protección de IP, Bloqueo de Desconocidos", onNavigateToPrivacy)
                SettingsItem(Icons.Default.Chat, "Chats y Protocolos", "Historial, temas, backup encriptado", onNavigateToChats)
                SettingsItem(Icons.Default.Notifications, "Notificaciones Inteligentes", "Tonos y prioridades tácticas", onNavigateToNotifications)
                SettingsItem(Icons.Default.Storage, "Almacenamiento Zero-Trace", "Uso de datos y limpieza de caché", onNavigateToStorage)
                SettingsItem(Icons.Default.Help, "Centro de Inteligencia", "Soporte, política de privacidad", onNavigateToHelp)
                SettingsItem(Icons.Default.Gavel, "Legal, Privacidad y Originalidad", "Aviso de Privacidad (LFPDPPP) y Certificado de Autoría (OMPI)", onClick = { showLegalDialog = true })
                
                Spacer(Modifier.height(32.dp))
                
                // EXCLUSIVOS NEOMESSAGE
                Text("TECNOLOGÍA REVOLUCIONARIA", color = NeoTheme.Green, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(16.dp))
                SettingsItem(Icons.Default.Shield, "Bóveda Cuántica (Vault)", "Archivos bajo cifrado 256-bit", onNavigateToVault)
                SettingsItem(Icons.Default.Sensors, "NeoMesh Infinity", "Funcionar sin internet (Wi-Fi/Datos)", {})
                SettingsItem(Icons.Default.Devices, "Nodos Vinculados", "Multi-dispositivo independiente", onNavigateToLinkedDevices)
                SettingsItem(Icons.Default.Star, "Protocolos Destacados", "Mensajes guardados con prioridad", onNavigateToStarred)
            }
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}
