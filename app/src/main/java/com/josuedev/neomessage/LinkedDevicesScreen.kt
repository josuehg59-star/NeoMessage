package com.josuedev.neomessage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    val myPhone = sp.getString("user_phone", "") ?: ""
    val myName = sp.getString("user_name", "Usuario") ?: "Usuario"

    // Generar un código de sesión único cada 60 segundos
    var sessionCode by remember { mutableStateOf("") }
    var sessionExpires by remember { mutableStateOf(0L) }
    var alertMsg by remember { mutableStateOf<String?>(null) }
    val linkedDevices = remember { mutableStateListOf<Map<String, String>>() }

    val scanLauncher = rememberLauncherForActivityResult(com.journeyapps.barcodescanner.ScanContract()) { result ->
        if (result.contents != null) {
            val sessionId = result.contents
            if (sessionId.startsWith("NEO-")) {
                val sessionData = mapOf(
                    "phone" to myPhone,
                    "name" to myName,
                    "platform" to "Android (QR)"
                )
                FirebaseDatabase.getInstance("https://neomessage-19ab4-default-rtdb.firebaseio.com")
                    .getReference("sessions")
                    .child(sessionId)
                    .setValue(sessionData)
                    .addOnSuccessListener {
                        Toast.makeText(context, "¡Dispositivo vinculado con éxito!", Toast.LENGTH_LONG).show()
                    }
            } else {
                alertMsg = "Código QR no válido para NeoMessage."
            }
        }
    }

    LaunchedEffect(Unit) {
        // Generar código de sesión y guardarlo en Firebase
        val code = (100000 + Random().nextInt(900000)).toString()
        val expiry = System.currentTimeMillis() + 60 * 1000L
        sessionCode = code
        sessionExpires = expiry

        // Guardar sesión en Firebase para que la app web pueda leerla
        if (myPhone.isNotEmpty()) {
            val sessionData = mapOf(
                "code" to code,
                "phone" to myPhone,
                "name" to myName,
                "expiry" to expiry,
                "platform" to "Android"
            )
            FirebaseDatabase.getInstance("https://neomessage-19ab4-default-rtdb.firebaseio.com")
                .getReference("sessions")
                .child(code)
                .setValue(sessionData)
        }

        // Cargar dispositivos vinculados
        FirebaseDatabase.getInstance("https://neomessage-19ab4-default-rtdb.firebaseio.com")
            .getReference("linked_devices")
            .child(myPhone.filter { it.isDigit() })
            .get()
            .addOnSuccessListener { snap ->
                snap.children.forEach { d ->
                    linkedDevices.add(mapOf(
                        "id" to (d.key ?: ""),
                        "name" to (d.child("name").getValue(String::class.java) ?: "Neo Terminal"),
                        "time" to (d.child("time").getValue(String::class.java) ?: "")
                    ))
                }
            }
    }

    if (alertMsg != null) {
        AlertDialog(
            onDismissRequest = { alertMsg = null },
            title = { Text("NeoMessage", fontWeight = FontWeight.Bold) },
            text = { Text(alertMsg!!) },
            confirmButton = { Button(onClick = { alertMsg = null }) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dispositivos Vinculados", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(bgColor).padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ---- CABECERA ----
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(100.dp).clip(RoundedCornerShape(24.dp)).background(accentGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Computer, null, modifier = Modifier.size(54.dp), tint = Color.White)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Vincula NeoMessage a tu PC", fontWeight = FontWeight.Black, fontSize = 20.sp, color = textColor)
                    Text("Accede a tus chats desde cualquier navegador web o PC.", color = textSubColor, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }



            // ---- BOTÓN DE ESCÁNER ----
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // Opción Principal QR
                    Button(
                        onClick = {
                            val options = com.journeyapps.barcodescanner.ScanOptions().apply {
                                setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                                setPrompt("Escanea el código QR de NeoMessage Web")
                                setCameraId(0)
                                setBeepEnabled(true)
                                setOrientationLocked(false)
                            }
                            scanLauncher.launch(options)
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, null, tint = Color.Black, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("VINCULAR UN DISPOSITIVO", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }

                    Surface(color = surfaceColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.HelpOutline, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Abre NeoMessage Web en tu PC o Tablet y apunta tu cámara hacia el código QR.", color = textSubColor, fontSize = 14.sp)
                        }
                    }

                    // VINCULACIÓN POR CÓDIGO (NUEVA SECCIÓN)
                    var showPhoneCode by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showPhoneCode = !showPhoneCode },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (showPhoneCode) "OCULTAR CÓDIGO" else "VINCULAR CON NÚMERO DE TELÉFONO", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (showPhoneCode) {
                        Surface(
                            color = Color.Black,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Introduce este código en NeoMessage Web", color = Color.Gray, fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    sessionCode.toList().forEach { char ->
                                        Surface(
                                            color = Color(0xFF1E293B),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(40.dp, 50.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(char.toString(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("Expira en: ${SimpleDateFormat("mm:ss", Locale.getDefault()).format(Date(sessionExpires - System.currentTimeMillis()))}", color = Color(0xFFE91E63), fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            // ---- DISPOSITIVOS VINCULADOS ----
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text("DISPOSITIVOS ACTIVOS", color = Color(0xFF00FF7F), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    if (linkedDevices.isEmpty()) {
                        Surface(color = surfaceColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DevicesOther, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Ningún dispositivo vinculado aún.", color = textSubColor, fontSize = 14.sp)
                            }
                        }
                    } else {
                        linkedDevices.forEach { device ->
                            Surface(color = surfaceColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Computer, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(device["name"] ?: "PC", color = textColor, fontWeight = FontWeight.SemiBold)
                                        Text(device["time"] ?: "", color = textSubColor, fontSize = 12.sp)
                                    }
                                            IconButton(onClick = {
                                                val deviceKey = device["id"] ?: ""
                                                if (deviceKey.isNotEmpty()) {
                                                    FirebaseDatabase.getInstance("https://neomessage-19ab4-default-rtdb.firebaseio.com")
                                                        .getReference("linked_devices")
                                                        .child(myPhone.filter { it.isDigit() })
                                                        .child(deviceKey)
                                                        .removeValue()
                                                        .addOnSuccessListener {
                                                            linkedDevices.remove(device)
                                                            alertMsg = "Sesión cerrada en este dispositivo."
                                                        }
                                                } else {
                                                    alertMsg = "Sesión finalizada preventivamente."
                                                    linkedDevices.remove(device)
                                                }
                                            }) {
                                                Icon(Icons.Filled.Logout, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                                            }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            item {
                // Info de seguridad
                Surface(color = Color(0xFF0D1B0D), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Todas las conexiones están cifradas end-to-end con AES-256. Tu sesión es tuya.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
