package com.josuedev.neomessage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    userId: String,
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onBack: () -> Unit
) {
    val starredMessages = remember { mutableStateListOf<Pair<String, CloudMessage>>() } // chatId to message
    val dbUrl = "https://neomessage-19ab4-default-rtdb.firebaseio.com"

    LaunchedEffect(Unit) {
        // Listen to starred messages stored per user in Firebase
        FirebaseDatabase.getInstance(dbUrl)
            .getReference("starred_messages")
            .child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    starredMessages.clear()
                    snapshot.children.forEach { snap ->
                        val chatId = snap.child("chatId").getValue(String::class.java) ?: ""
                        val msg = snap.getValue(CloudMessage::class.java)
                        if (msg != null) starredMessages.add(chatId to msg)
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⭐ Mensajes Destacados", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surfaceColor)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(bgColor).padding(padding)) {
            if (starredMessages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.StarBorder, null, modifier = Modifier.size(72.dp), tint = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Text("Sin mensajes destacados", color = textSubColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Mantén presionado un mensaje en el chat\ny toca ⭐ para destacarlo aquí.",
                        color = textSubColor, fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    items(starredMessages.size) { i ->
                        val (chatId, msg) = starredMessages[i]
                        Surface(
                            color = surfaceColor,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            msg.senderName,
                                            color = Color(0xFF00FF7F),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                                            color = textSubColor, fontSize = 11.sp
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        msg.text.let {
                                            when {
                                                it.startsWith("[Media]") -> "📷 Imagen"
                                                it.startsWith("[Audio]") -> "🎤 Nota de voz"
                                                it.startsWith("[File]") -> "📎 Archivo"
                                                it.startsWith("[Video]") -> "🎬 Vídeo"
                                                it.startsWith("📍") -> "📍 Ubicación"
                                                else -> it
                                            }
                                        },
                                        color = textColor, fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    // Unstar button
                                    TextButton(
                                        onClick = {
                                            FirebaseDatabase.getInstance(dbUrl)
                                                .getReference("starred_messages")
                                                .child(userId)
                                                .child(msg.id)
                                                .removeValue()
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Quitar de destacados", color = Color.Gray, fontSize = 11.sp)
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
