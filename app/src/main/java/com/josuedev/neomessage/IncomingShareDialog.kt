package com.josuedev.neomessage

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShareTargetItem(
    val chatId: String,
    val otherId: String,
    val name: String,
    val isGroup: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingShareDialog(
    myUserId: String,
    incomingContent: IncomingSharedContent,
    onSelectTarget: (name: String, chatId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val targets = remember { mutableStateListOf<ShareTargetItem>() }
    var searchQuery by remember { mutableStateOf("") }
    val myClean = remember(myUserId) { NeoUtils.sanitizeId(myUserId).takeLast(10) }

    LaunchedEffect(myUserId) {
        val spNames = context.getSharedPreferences("NeoUserNames", Context.MODE_PRIVATE)
        val spCont = context.getSharedPreferences("NeoContacts", Context.MODE_PRIVATE)
        val seen = mutableSetOf<String>()

        fun nameFor(p10: String): String {
            val n = spNames.getString(p10, null) ?: spCont.getString(p10, null)
            return if (!n.isNullOrBlank() && n.any { it.isLetter() }) n else p10
        }

        // 1. Mis Notas Personales
        val selfCid = "private_${myClean}_${myClean}"
        if (seen.add(selfCid)) {
            targets.add(ShareTargetItem(selfCid, myClean, "Mis Notas Personales (Tú)", false))
        }

        // 2. Contactos guardados
        spCont.all.keys.forEach { k ->
            val p10 = NeoUtils.sanitizeId(k).takeLast(10)
            if (p10.length == 10 && p10 != myClean) {
                val cid = "private_" + listOf(myClean, p10).sorted().joinToString("_")
                if (seen.add(cid)) {
                    targets.add(ShareTargetItem(cid, p10, nameFor(p10), false))
                }
            }
        }

        // 3. Nombres conocidos de usuarios
        spNames.all.keys.forEach { k ->
            val p10 = NeoUtils.sanitizeId(k).takeLast(10)
            if (p10.length == 10 && p10 != myClean) {
                val cid = "private_" + listOf(myClean, p10).sorted().joinToString("_")
                if (seen.add(cid)) {
                    targets.add(ShareTargetItem(cid, p10, nameFor(p10), false))
                }
            }
        }

        // 4. Grupos activos
        try {
            FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL).getReference("groups")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        snapshot.children.forEach { g ->
                            val gid = g.child("id").getValue(String::class.java) ?: g.key ?: return@forEach
                            val gname = g.child("name").getValue(String::class.java) ?: "Grupo"
                            val creator = NeoUtils.sanitizeId(g.child("createdBy").getValue(String::class.java) ?: "").takeLast(10)
                            val members = g.child("members").children.mapNotNull { m -> m.getValue(String::class.java) }
                                .map { NeoUtils.sanitizeId(it).takeLast(10) }
                            val isMember = creator == myClean || members.contains(myClean) || members.isEmpty()
                            if (isMember && seen.add(gid)) {
                                targets.add(ShareTargetItem(gid, gid, gname, true))
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        } catch (_: Exception) {}
    }

    val filtered = remember(searchQuery, targets.size) {
        if (searchQuery.isBlank()) targets
        else targets.filter { it.name.contains(searchQuery, ignoreCase = true) || it.otherId.contains(searchQuery) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📤 Compartir con...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Preview banner
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                incomingContent.uris.isNotEmpty() -> "📁 ${incomingContent.uris.size} archivo(s) para enviar"
                                !incomingContent.text.isNullOrBlank() -> "💬 \"${incomingContent.text.take(40)}\""
                                else -> "📄 Contenido listo para enviar"
                            },
                            color = Color(0xFF00FF7F),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar contacto o grupo...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF7F),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No se encontraron contactos", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered, key = { it.chatId }) { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTarget(t.name, t.chatId) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (t.isGroup) Color(0xFF3B82F6).copy(0.2f) else Color(0xFF00FF7F).copy(0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (t.isGroup) "👥" else if (t.chatId.startsWith("private_${myClean}_${myClean}")) "⭐" else "👤",
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!t.isGroup && t.otherId != myClean) {
                                        Text(t.otherId, color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                                Text("ENVIAR ➔", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    )
}
