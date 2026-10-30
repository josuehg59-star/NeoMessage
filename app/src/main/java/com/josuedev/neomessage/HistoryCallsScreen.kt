package com.josuedev.neomessage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Close
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
fun HistoryCallsScreen(
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onCallClick: (String) -> Unit
) {
    val context = LocalContext.current
    val sp = context.getSharedPreferences("NeoPrefs", android.content.Context.MODE_PRIVATE)
    val myId = remember { NeoUtils.sanitizeId(sp.getString("user_phone", "") ?: "") }

    val callLogs = remember { mutableStateListOf<CallLog>() }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val dbUrl = NetworkConfig.FIREBASE_DATABASE_URL

    // Load real call logs from Firebase
    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance(dbUrl)
            .getReference("call_logs")
            .orderByChild("timestamp")
            .limitToLast(50)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callLogs.clear()
                    snapshot.children.forEach { snap ->
                        val log = snap.getValue(CallLog::class.java)
                        if (log != null) {
                            val cleanCaller = NeoUtils.sanitizeId(log.callerId)
                            val cleanReceiver = NeoUtils.sanitizeId(log.receiverId)
                            if (cleanCaller == myId || cleanReceiver == myId) {
                                callLogs.add(log)
                            }
                        }
                    }
                    callLogs.sortByDescending { it.timestamp }
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar llamada...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = Color(0xFF00FF7F),
                                focusedTextColor = textColor
                            ),
                            singleLine = true
                        )
                    } else {
                        Text("Llamadas", fontWeight = FontWeight.Bold, color = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surfaceColor),
                actions = {
                    if (isSearching) {
                        IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                            Icon(Icons.Filled.Close, null, tint = textColor)
                        }
                    } else {
                        if (callLogs.isNotEmpty()) {
                            IconButton(onClick = {
                                FirebaseDatabase.getInstance(dbUrl).getReference("call_logs").removeValue()
                                callLogs.clear()
                            }) {
                                Icon(Icons.Filled.DeleteSweep, null, tint = Color.Red)
                            }
                        }
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Outlined.Search, null, tint = textColor)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onCallClick("Nuevo Contacto") },
                containerColor = Color(0xFF00FF7F)
            ) {
                Icon(Icons.Filled.Call, null, tint = Color.Black)
            }
        },
        containerColor = bgColor
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (callLogs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(Modifier.height(80.dp))
                        Icon(Icons.Filled.Call, null, modifier = Modifier.size(72.dp), tint = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        Text("Sin historial de llamadas", color = textSubColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Tus llamadas aparecerán aquí", color = textSubColor, fontSize = 13.sp)
                    }
                }
            } else {
                val filteredLogs = if (searchQuery.isBlank()) callLogs 
                                  else callLogs.filter { (it.callerName.contains(searchQuery, true) || it.receiverName.contains(searchQuery, true)) }

                item {
                    Text(
                        if (isSearching) "Resultados" else "Recientes",
                        color = textSubColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                    )
                }
                items(filteredLogs.size) { i ->
                    val log = filteredLogs[i]
                    val isOutgoing = log.callerId == myId
                    val contactName = if (isOutgoing) log.receiverName else log.callerName
                    val callIcon = when (log.type) {
                        "missed" -> Icons.Filled.CallMissed
                        "incoming" -> Icons.Filled.CallReceived
                        else -> Icons.Filled.CallMade
                    }
                    val iconColor = if (log.type == "missed") Color.Red else Color(0xFF00FF7F)
                    val timeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(log.timestamp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCallClick(contactName) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(50.dp).clip(CircleShape).background(accentGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(contactName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                contactName,
                                color = if (log.type == "missed") Color.Red else textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(callIcon, null, tint = iconColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${if (log.type == "missed") "Perdida" else if (isOutgoing) "Saliente" else "Entrante"} · $timeStr",
                                    color = textSubColor, fontSize = 13.sp
                                )
                            }
                        }
                        IconButton(onClick = { onCallClick(contactName) }) {
                            Icon(Icons.Filled.Call, null, tint = Color(0xFF00FF7F))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(start = 82.dp))
                }
            }
        }
    }
}
