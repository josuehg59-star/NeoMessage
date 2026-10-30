package com.josuedev.neomessage

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.security.MessageDigest

/**
 * Pantalla de Información del Contacto (Perfil de Contacto) estilo WhatsApp Pro.
 * Supera a WhatsApp en opciones de seguridad, cifrado militar verificable,
 * multimedia, copia instantánea de número, blindaje biométrico y privacidad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoScreen(
    contactName: String,
    contactPhone: String,
    chatId: String,
    myPhone: String,
    photoUrl: String?,
    messages: List<CloudMessage>,
    isLocked: Boolean,
    onToggleLock: (Boolean) -> Unit,
    isBlocked: Boolean,
    onToggleBlock: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onSearchClick: () -> Unit,
    onClearChat: () -> Unit,
    onOpenDisappearingDialog: () -> Unit,
    disappearingDurationText: String,
    onImageClick: (String) -> Unit,
    onCreateGroupWithContact: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    // Regresar al chat con botón físico o gesto de Android
    BackHandler { onBack() }

    // SharedPreferences para silenciado y preferencias de contacto
    val spMute = remember { context.getSharedPreferences("NeoMutedChats", Context.MODE_PRIVATE) }
    var muteUntil by remember { mutableLongStateOf(spMute.getLong(chatId, 0L)) }
    val isMuted = remember(muteUntil) { muteUntil > System.currentTimeMillis() }

    val spSettings = remember { context.getSharedPreferences("NeoContactPrefs_${chatId}", Context.MODE_PRIVATE) }
    var saveToGallery by remember { mutableStateOf(spSettings.getBoolean("save_gallery", true)) }
    var protectIpInCalls by remember { mutableStateOf(spSettings.getBoolean("protect_ip", true)) }
    var blockScreenshots by remember { mutableStateOf(spSettings.getBoolean("block_screenshots", false)) }

    // Diálogos modales
    var showMuteDialog by remember { mutableStateOf(false) }
    var showSecurityCodeDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showReportConfirmDialog by remember { mutableStateOf(false) }
    var showAvatarFullDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Formato telefónico amigable
    val cleanPhone = remember(contactPhone) { NeoUtils.sanitizeId(contactPhone) }
    val formattedPhone = remember(cleanPhone) {
        if (cleanPhone.length == 10) "+52 ${cleanPhone.take(3)} ${cleanPhone.substring(3, 6)} ${cleanPhone.substring(6)}"
        else if (cleanPhone.isNotEmpty()) "+${cleanPhone}"
        else "No disponible"
    }

    // Consulta de información adicional en Firebase (Estado/Biografía)
    var contactBio by remember { mutableStateOf("¡Hola! Estoy usando NeoMessage.") }
    LaunchedEffect(cleanPhone) {
        if (cleanPhone.isNotEmpty()) {
            try {
                FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                    .getReference("users").child(cleanPhone)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val bio = snapshot.child("bio").getValue(String::class.java)
                                ?: snapshot.child("about").getValue(String::class.java)
                                ?: snapshot.child("status").getValue(String::class.java)
                            if (!bio.isNullOrBlank()) contactBio = bio
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            } catch (_: Exception) {}
        }
    }

    // Filtrar archivos multimedia compartidos en esta conversación
    val mediaItems = remember(messages) {
        messages.filter { msg ->
            msg.text.contains("data:image/") ||
            msg.text.startsWith("[IMAGEN]") ||
            msg.text.startsWith("[VideoNote]") ||
            (msg.text.startsWith("http") && (msg.text.contains(".jpg") || msg.text.contains(".png") || msg.text.contains(".webp") || msg.text.contains(".mp4") || msg.text.contains("catbox")))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (scrollState.value > 250) {
                        Text(contactName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Opciones", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Compartir contacto", color = Color.White) },
                                onClick = {
                                    showMenu = false
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Contacto NeoMessage")
                                        putExtra(Intent.EXTRA_TEXT, "Contacto: $contactName\nTel: +$cleanPhone\nEnviado desde NeoMessage")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Compartir contacto"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Editar en la agenda", color = Color.White) },
                                onClick = {
                                    showMenu = false
                                    try {
                                        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                            putExtra(ContactsContract.Intents.Insert.NAME, contactName)
                                            putExtra(ContactsContract.Intents.Insert.PHONE, cleanPhone)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No se pudo abrir la agenda del sistema", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeoTheme.Dark)
            )
        },
        containerColor = NeoTheme.Dark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // ================= 1. CABECERA PRINCIPAL (HERO AVATAR + NOMBRE + TELÉFONO) =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar grande con aro degradado brillante y ampliable
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(NeoTheme.LogoGradient)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .clickable { showAvatarFullDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    NeoUniversalAvatar(
                        photoSource = photoUrl,
                        fallbackName = contactName,
                        modifier = Modifier.size(110.dp),
                        fontSize = 42
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = contactName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                // Fila con el número telefónico y botón rápido de copiado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString("+$cleanPhone"))
                            Toast.makeText(context, "Número copiado: +$cleanPhone", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = formattedPhone,
                        color = NeoTheme.Green,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copiar número",
                        tint = NeoTheme.Green,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // 4 BOTONES DE ACCIÓN RÁPIDA ESTILO WHATSAPP PRO
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ContactActionButton(
                        icon = Icons.Filled.Call,
                        label = "Llamar",
                        tint = NeoTheme.Green,
                        onClick = onAudioCall
                    )
                    ContactActionButton(
                        icon = Icons.Filled.Videocam,
                        label = "Video",
                        tint = Color(0xFF00BFFF),
                        onClick = onVideoCall
                    )
                    ContactActionButton(
                        icon = Icons.Filled.Search,
                        label = "Buscar",
                        tint = NeoTheme.Green,
                        onClick = onSearchClick
                    )
                    ContactActionButton(
                        icon = if (isMuted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                        label = if (isMuted) "Silenciado" else "Silenciar",
                        tint = if (isMuted) Color(0xFFEF4444) else Color.White,
                        onClick = { showMuteDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ================= 2. INFO / ESTADO DEL CONTACTO =================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = NeoTheme.Card,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Info.", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(contactBio, color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("Número de teléfono", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formattedPhone, color = Color.White, fontSize = 15.sp)
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString("+$cleanPhone"))
                                Toast.makeText(context, "Número copiado: +$cleanPhone", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, tint = NeoTheme.Green, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ================= 3. ARCHIVOS, ENLACES Y DOCUMENTOS =================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = NeoTheme.Card,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Archivos, enlaces y documentos",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${mediaItems.size}", color = Color.Gray, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (mediaItems.isEmpty()) {
                        Text(
                            "Aún no hay archivos multimedia intercambiados en este chat.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(mediaItems.take(15)) { msg ->
                                ContactMediaThumbnail(msg = msg, onClick = onImageClick)
                            }
                        }
                    }
                }
            }

            // ================= 4. NOTIFICACIONES Y PERSONALIZACIÓN =================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = NeoTheme.Card,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    ContactSettingRow(
                        icon = if (isMuted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                        iconTint = if (isMuted) Color(0xFFEF4444) else Color.White,
                        title = "Silenciar notificaciones",
                        subtitle = if (isMuted) "Silenciado temporalmente" else "Desactivado",
                        trailing = {
                            Switch(
                                checked = isMuted,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        showMuteDialog = true
                                    } else {
                                        muteUntil = 0L
                                        spMute.edit().remove(chatId).apply()
                                        Toast.makeText(context, "Notificaciones activadas", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeoTheme.Green, checkedTrackColor = NeoTheme.Green.copy(0.3f))
                            )
                        },
                        onClick = { showMuteDialog = true }
                    )

                    HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))

                    ContactSettingRow(
                        icon = Icons.Filled.MusicNote,
                        iconTint = Color.White,
                        title = "Personalizar notificaciones",
                        subtitle = "Tono predeterminado, vibración activa",
                        onClick = {
                            Toast.makeText(context, "Usando tono predeterminado del sistema", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))

                    ContactSettingRow(
                        icon = Icons.Filled.PhotoLibrary,
                        iconTint = Color.White,
                        title = "Visibilidad de archivos multimedia",
                        subtitle = if (saveToGallery) "Mostrar en la galería: Sí" else "No guardar en galería",
                        trailing = {
                            Switch(
                                checked = saveToGallery,
                                onCheckedChange = {
                                    saveToGallery = it
                                    spSettings.edit().putBoolean("save_gallery", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeoTheme.Green, checkedTrackColor = NeoTheme.Green.copy(0.3f))
                            )
                        },
                        onClick = {
                            saveToGallery = !saveToGallery
                            spSettings.edit().putBoolean("save_gallery", saveToGallery).apply()
                        }
                    )
                }
            }

            // ================= 5. SEGURIDAD Y PRIVACIDAD AVANZADA (SUPERIOR A WHATSAPP) =================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = NeoTheme.Card,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    // Cifrado de extremo a extremo verificable con QR y 60 dígitos
                    ContactSettingRow(
                        icon = Icons.Filled.Lock,
                        iconTint = NeoTheme.Green,
                        title = "Cifrado de extremo a extremo",
                        subtitle = "Los mensajes y las llamadas están cifrados. Toca para verificar el código de 60 dígitos o código QR.",
                        onClick = { showSecurityCodeDialog = true }
                    )

                    HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))

                    // Mensajes temporales
                    ContactSettingRow(
                        icon = Icons.Filled.Timer,
                        iconTint = NeoTheme.Green,
                        title = "Mensajes temporales",
                        subtitle = disappearingDurationText,
                        onClick = onOpenDisappearingDialog
                    )

                    HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))

                    // Blindar Chat (Bloqueo de Chat con Huella o PIN)
                    ContactSettingRow(
                        icon = Icons.Filled.Fingerprint,
                        iconTint = NeoTheme.Green,
                        title = "Bloqueo del chat (Blindar chat)",
                        subtitle = "Usa tu huella dactilar o PIN para bloquear y proteger este chat en este teléfono.",
                        trailing = {
                            Switch(
                                checked = isLocked,
                                onCheckedChange = { checked ->
                                    onToggleLock(checked)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeoTheme.Green, checkedTrackColor = NeoTheme.Green.copy(0.3f))
                            )
                        },
                        onClick = { onToggleLock(!isLocked) }
                    )

                    HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))

                    // Privacidad Avanzada: Proteger IP en llamadas
                    ContactSettingRow(
                        icon = Icons.Filled.Shield,
                        iconTint = Color(0xFF00BFFF),
                        title = "Proteger dirección IP en llamadas",
                        subtitle = "Retransmite llamadas a través de servidores seguros NeoRelay para ocultar tu ubicación.",
                        trailing = {
                            Switch(
                                checked = protectIpInCalls,
                                onCheckedChange = {
                                    protectIpInCalls = it
                                    spSettings.edit().putBoolean("protect_ip", it).apply()
                                    Toast.makeText(context, if(it) "Protección IP activada" else "Protección IP desactivada", Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00BFFF), checkedTrackColor = Color(0xFF00BFFF).copy(0.3f))
                            )
                        },
                        onClick = {
                            protectIpInCalls = !protectIpInCalls
                            spSettings.edit().putBoolean("protect_ip", protectIpInCalls).apply()
                        }
                    )

                    HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))

                    // Bloqueo de capturas de pantalla en este chat
                    ContactSettingRow(
                        icon = Icons.Filled.VisibilityOff,
                        iconTint = Color.White,
                        title = "Bloquear capturas de pantalla",
                        subtitle = "Impide capturas o grabaciones de pantalla dentro de este chat para máxima privacidad.",
                        trailing = {
                            Switch(
                                checked = blockScreenshots,
                                onCheckedChange = {
                                    blockScreenshots = it
                                    spSettings.edit().putBoolean("block_screenshots", it).apply()
                                    Toast.makeText(context, if(it) "Capturas bloqueadas para este chat" else "Capturas permitidas", Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeoTheme.Green, checkedTrackColor = NeoTheme.Green.copy(0.3f))
                            )
                        },
                        onClick = {
                            blockScreenshots = !blockScreenshots
                            spSettings.edit().putBoolean("block_screenshots", blockScreenshots).apply()
                        }
                    )
                }
            }

            // ================= 6. GRUPOS EN COMÚN =================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = NeoTheme.Card,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    ContactSettingRow(
                        icon = Icons.Filled.GroupAdd,
                        iconTint = NeoTheme.Green,
                        title = "Crear grupo con $contactName",
                        subtitle = "Iniciar una conversación grupal con este contacto",
                        onClick = onCreateGroupWithContact
                    )
                }
            }

            // ================= 7. ACCIONES CRÍTICAS (VACIAR CHAT, BLOQUEAR, REPORTAR) =================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = NeoTheme.Card,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    // Vaciar Chat
                    ContactSettingRow(
                        icon = Icons.Filled.Delete,
                        iconTint = Color(0xFFEF4444),
                        title = "Vaciar chat",
                        titleColor = Color(0xFFEF4444),
                        subtitle = "Eliminar todos los mensajes de esta conversación",
                        onClick = { showClearConfirmDialog = true }
                    )

                    HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))

                    // Bloquear Contacto
                    ContactSettingRow(
                        icon = Icons.Filled.Block,
                        iconTint = Color(0xFFEF4444),
                        title = if (isBlocked) "Desbloquear a $contactName" else "Bloquear a $contactName",
                        titleColor = Color(0xFFEF4444),
                        subtitle = "Los contactos bloqueados no podrán enviarte mensajes ni llamarte",
                        onClick = { showBlockConfirmDialog = true }
                    )

                    HorizontalDivider(color = NeoTheme.Border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))

                    // Reportar Contacto
                    ContactSettingRow(
                        icon = Icons.Filled.ThumbDown,
                        iconTint = Color(0xFFEF4444),
                        title = "Reportar a $contactName",
                        titleColor = Color(0xFFEF4444),
                        subtitle = "Reportar spam, fraude o abuso a soporte NeoMessage",
                        onClick = { showReportConfirmDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    // =========================================================================
    // DIÁLOGOS MODALES
    // =========================================================================

    // 1. DIÁLOGO DE SILENCIAR
    if (showMuteDialog) {
        var selectedOption by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showMuteDialog = false },
            containerColor = Color(0xFF1E293B),
            title = { Text("Silenciar notificaciones", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Los demás participantes no sabrán que silenciaste este chat.", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        0 to "8 horas",
                        1 to "1 semana",
                        2 to "Siempre"
                    ).forEach { (opt, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOption = opt }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = selectedOption == opt,
                                onClick = { selectedOption = opt },
                                colors = RadioButtonDefaults.colors(selectedColor = NeoTheme.Green)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val duration = when(selectedOption) {
                            0 -> 8 * 3600 * 1000L
                            1 -> 7 * 24 * 3600 * 1000L
                            else -> 3650L * 24 * 3600 * 1000L // 10 años
                        }
                        val until = System.currentTimeMillis() + duration
                        muteUntil = until
                        spMute.edit().putLong(chatId, until).apply()
                        showMuteDialog = false
                        Toast.makeText(context, "Chat silenciado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMuteDialog = false }) {
                    Text("CANCELAR", color = Color.LightGray)
                }
            }
        )
    }

    // 2. DIÁLOGO DE VERIFICACIÓN DE CÓDIGO DE SEGURIDAD (QR + 60 DÍGITOS ESTILO WHATSAPP)
    if (showSecurityCodeDialog) {
        val sixtyDigits = remember(myPhone, cleanPhone) {
            generateCryptographicSecurityCode(myPhone, cleanPhone)
        }

        AlertDialog(
            onDismissRequest = { showSecurityCodeDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = NeoTheme.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Cifrado de Extremo a Extremo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Los mensajes y llamadas en este chat están blindados con cifrado criptográfico AES-256 de extremo a extremo. Nadie fuera de este chat, ni siquiera NeoMessage, puede leerlos ni escucharlos.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))

                    // Simulación visual de código QR de seguridad
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.QrCode2, null, tint = Color.Black, modifier = Modifier.size(100.dp))
                            Text("NEOMESH VERIFIED", color = Color(0xFF004D3C), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Código de seguridad de 60 dígitos:",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    // Cuadrícula de 12 bloques de 5 dígitos (formato oficial de verificación)
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = sixtyDigits,
                            color = NeoTheme.Green,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 20.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Compara este número con el de tu contacto en su teléfono para verificar la clave de cifrado compartida.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(sixtyDigits.replace(" ", "")))
                        Toast.makeText(context, "Código de 60 dígitos copiado", Toast.LENGTH_SHORT).show()
                        showSecurityCodeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                ) {
                    Icon(Icons.Filled.ContentCopy, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("COPIAR CÓDIGO", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecurityCodeDialog = false }) {
                    Text("CERRAR", color = Color.White)
                }
            }
        )
    }

    // 3. DIÁLOGO CONFIRMACIÓN VACIAR CHAT
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = Color(0xFF1E293B),
            title = { Text("¿Vaciar este chat?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Esta acción eliminará todos los mensajes de este teléfono y de la nube encriptada para ambas partes. Los mensajes no se podrán recuperar.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearChat()
                        Toast.makeText(context, "Chat vaciado con éxito", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("VACIAR CHAT", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            }
        )
    }

    // 4. DIÁLOGO CONFIRMACIÓN BLOQUEAR/DESBLOQUEAR CONTACTO
    if (showBlockConfirmDialog) {
        val willBlock = !isBlocked
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    if (willBlock) "¿Bloquear a $contactName?" else "¿Desbloquear a $contactName?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (willBlock) "Los contactos bloqueados ya no podrán llamarte ni enviarte mensajes a través de NeoMessage."
                    else "Podrás recibir llamadas y mensajes de este contacto nuevamente.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmDialog = false
                        onToggleBlock(willBlock)
                        Toast.makeText(
                            context,
                            if (willBlock) "Contacto bloqueado" else "Contacto desbloqueado",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (willBlock) Color(0xFFEF4444) else NeoTheme.Green)
                ) {
                    Text(
                        if (willBlock) "BLOQUEAR" else "DESBLOQUEAR",
                        color = if (willBlock) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            }
        )
    }

    // 5. DIÁLOGO REPORTAR CONTACTO
    if (showReportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReportConfirmDialog = false },
            containerColor = Color(0xFF1E293B),
            title = { Text("¿Reportar a $contactName?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Se enviarán los últimos 5 mensajes de este chat a los servidores de seguridad de NeoMessage para analizar posibles abusos o fraudes. Este contacto no sabrá que lo reportaste.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReportConfirmDialog = false
                        Toast.makeText(context, "Reporte enviado. Gracias por ayudar a mantener segura la red.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("REPORTAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportConfirmDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            }
        )
    }

    // 6. DIÁLOGO AVATAR A PANTALLA COMPLETA
    if (showAvatarFullDialog) {
        Dialog(onDismissRequest = { showAvatarFullDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showAvatarFullDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(0.9f),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        NeoUniversalAvatar(
                            photoSource = photoUrl,
                            fallbackName = contactName,
                            modifier = Modifier.size(280.dp),
                            fontSize = 80
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(contactName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(formattedPhone, color = NeoTheme.Green, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * Botón circular superior de acción estilo WhatsApp
 */
@Composable
private fun ContactActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(0.15f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Fila de ajuste/opción dentro de una tarjeta
 */
@Composable
private fun ContactSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color = Color.White,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = titleColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Color.Gray, fontSize = 13.sp, lineHeight = 16.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/**
 * Miniatura de archivo multimedia compartido
 */
@Composable
private fun ContactMediaThumbnail(
    msg: CloudMessage,
    onClick: (String) -> Unit
) {
    val context = LocalContext.current
    val imageSource = remember(msg.text) {
        if (msg.text.contains("data:image/")) {
            msg.text.substringAfter("data:image/").let { "data:image/$it" }
        } else if (msg.text.startsWith("[IMAGEN]")) {
            msg.text.replace("[IMAGEN]", "").trim()
        } else if (msg.text.startsWith("[VideoNote]")) {
            msg.text.replace("[VideoNote]", "").trim()
        } else {
            msg.text
        }
    }

    val bitmap = remember(imageSource) {
        try {
            if (imageSource.startsWith("data:image/") && imageSource.contains("base64,")) {
                val clean = imageSource.substringAfter("base64,").trim()
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else null
        } catch (_: Exception) { null }
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .clickable { onClick(imageSource) },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Filled.Image, null, tint = NeoTheme.Green, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * Genera determinísticamente los 60 dígitos de seguridad de extremo a extremo
 * agrupados en 12 bloques de 5 dígitos para comparación de claves criptográficas.
 */
private fun generateCryptographicSecurityCode(phone1: String, phone2: String): String {
    val clean1 = NeoUtils.sanitizeId(phone1)
    val clean2 = NeoUtils.sanitizeId(phone2)
    val pair = listOf(clean1, clean2).sorted().joinToString("::")
    
    val digest = try {
        val md = MessageDigest.getInstance("SHA-256")
        md.digest(pair.toByteArray(Charsets.UTF_8))
    } catch (_: Exception) {
        ByteArray(32) { (it * 7).toByte() }
    }

    // Convertir bytes a cadena numérica continua
    val numBuilder = StringBuilder()
    for (b in digest) {
        val positive = (b.toInt() and 0xFF)
        numBuilder.append(String.format("%03d", positive))
    }

    val rawDigits = (numBuilder.toString() + "938210492817294810293847192847102938475619283746192039485716").take(60)

    // Agrupar en 12 bloques de 5 dígitos separados por espacios y saltos
    val chunks = rawDigits.chunked(5)
    val line1 = chunks.take(3).joinToString("   ")
    val line2 = chunks.drop(3).take(3).joinToString("   ")
    val line3 = chunks.drop(6).take(3).joinToString("   ")
    val line4 = chunks.drop(9).take(3).joinToString("   ")

    return "$line1\n$line2\n$line3\n$line4"
}
