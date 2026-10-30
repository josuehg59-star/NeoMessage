package com.josuedev.neomessage

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    userId: String,
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val savedMessages = remember { mutableStateListOf<CloudMessage>() }
    var isUnlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val dbUrl = "https://neomessage-19ab4-default-rtdb.firebaseio.com"

    // Fun de autenticación biométrica
    fun authenticate() {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(context as FragmentActivity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isUnlocked = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authError = errString.toString()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Bóveda de Seguridad Neo")
            .setSubtitle("Autentícate para acceder a tus mensajes archivados")
            .setNegativeButtonText("Cancelar")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        authenticate()
    }

    if (!isUnlocked) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bóveda Segura", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().background(bgColor).padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(80.dp), tint = Color(0xFF00FF7F))
                Spacer(Modifier.height(24.dp))
                Text("Bóveda Bloqueada", fontWeight = FontWeight.Black, fontSize = 22.sp, color = textColor)
                Spacer(Modifier.height(8.dp))
                Text(authError ?: "Se requiere identificación biométrica para entrar.", color = textSubColor, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(Modifier.height(32.dp))
                Button(onClick = { authenticate() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))) {
                    Text("REINTENTAR ACCESO", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // Carga de datos una vez desbloqueado
    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            FirebaseDatabase.getInstance(dbUrl)
                .getReference("saved_messages")
                .child(userId)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        savedMessages.clear()
                        snapshot.children.forEach { snap ->
                            val m = snap.getValue(CloudMessage::class.java)
                            if (m != null) savedMessages.add(m)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔐 Bóveda de Mensajes", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(bgColor).padding(padding)) {
            // Header de seguridad
            Surface(color = Color(0xFF0D1B0D), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Los mensajes en esta bóveda están cifrados y solo tú puedes verlos.", color = Color.Gray, fontSize = 11.sp)
                }
            }

            if (savedMessages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bóveda vacía.\nNo hay mensajes archivados.", color = textSubColor, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    items(savedMessages.size) { i ->
                        val msg = savedMessages[i]
                        Surface(
                            color = surfaceColor,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(msg.senderName, color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(msg.timestamp)), color = textSubColor, fontSize = 10.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                MessageContent(msg, textColor, textSubColor, context)
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        FirebaseDatabase.getInstance(dbUrl).getReference("saved_messages").child(userId).child(msg.id).removeValue()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("ELIMINAR DE LA BÓVEDA", color = Color.Red.copy(0.7f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
