import re

chat_path = 'app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt'
with open(chat_path, 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Add replyingTo state
target_state = 'var selectedMessage by remember { mutableStateOf<CloudMessage?>(null) }'
replacement_state = """var selectedMessage by remember { mutableStateOf<CloudMessage?>(null) }
    var replyingTo by remember { mutableStateOf<CloudMessage?>(null) }
    var showDisappearingDialog by remember { mutableStateOf(false) }"""

if target_state in code:
    code = code.replace(target_state, replacement_state, 1)
    print("Added replyingTo state")
else:
    print("Warning: target_state not found")

# 2. Update sendMessage to attach reply
old_send_msg = """            val msgId = database.push().key ?: UUID.randomUUID().toString()
            val msg = CloudMessage(msgId, currentText, System.currentTimeMillis(), cleanMyId, myName, "")"""

new_send_msg = """            val currentReply = replyingTo
            replyingTo = null
            val msgId = database.push().key ?: UUID.randomUUID().toString()
            val msg = CloudMessage(
                id = msgId,
                text = currentText,
                timestamp = System.currentTimeMillis(),
                senderId = cleanMyId,
                senderName = myName,
                rank = "",
                replyToId = currentReply?.id,
                replyToText = currentReply?.text?.take(80),
                replyToSender = currentReply?.senderName
            )"""

if old_send_msg in code:
    code = code.replace(old_send_msg, new_send_msg, 1)
    print("Updated sendMessage with reply attachments")
else:
    print("Warning: old_send_msg not found")

# 3. Update DropdownMenu to include "Mensajes Temporales"
old_dropdown_item = """                            DropdownMenuItem(text = { Text("Protocolo Fantasma (Off)", color = if(showGhostMode) NeoTheme.Green else Color.White) }, onClick = { 
                                showGhostMode = !showGhostMode
                                showMoreMenu = false
                                Toast.makeText(context, if(showGhostMode) "MODO FANTASMA ACTIVADO: Mensajes efímeros." else "MODO FANTASMA DESACTIVADO", Toast.LENGTH_SHORT).show()
                            })"""

new_dropdown_item = """                            DropdownMenuItem(text = { Text("⏱️ Mensajes temporales", color = Color.White) }, onClick = { 
                                showMoreMenu = false
                                showDisappearingDialog = true
                            })
                            DropdownMenuItem(text = { Text("Protocolo Fantasma (Off)", color = if(showGhostMode) NeoTheme.Green else Color.White) }, onClick = { 
                                showGhostMode = !showGhostMode
                                showMoreMenu = false
                                Toast.makeText(context, if(showGhostMode) "MODO FANTASMA ACTIVADO: Mensajes efímeros." else "MODO FANTASMA DESACTIVADO", Toast.LENGTH_SHORT).show()
                            })"""

if old_dropdown_item in code:
    code = code.replace(old_dropdown_item, new_dropdown_item, 1)
    print("Added Mensajes temporales to DropdownMenu")
else:
    print("Warning: old_dropdown_item not found")

# 4. Enhance message bubble with reply preview, reactions, and star
old_bubble = """                                if (msg.isDeletedForEveryone) {
                                    Text("🚫 Este mensaje fue eliminado", color = textSubColor.copy(0.6f), fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                } else if (msg.expiryTime != null && msg.expiryTime < System.currentTimeMillis()) {
                                    Text("🔥 Contenido autodestruido", color = Color.Red.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    MessageContent(msg, textColor, textSubColor, context, isMine = isMine, playingVoiceMsgId = playingVoiceMsgId, onPlayingVoiceChange = { playingVoiceMsgId = it }, onImageClick = { activeZoomBitmap = it })
                                    if (msg.expiryTime != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                            Icon(Icons.Filled.Timer, null, tint = Color.Red, modifier = Modifier.size(10.dp))
                                            Text(" Se quema en ${((msg.expiryTime - System.currentTimeMillis())/1000)}s", color = Color.Red, fontSize = 9.sp)
                                        }
                                    }
                                }
                                
                                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)), color = textSubColor.copy(0.5f), fontSize = 10.sp)
                                    if (isMine) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isReadByOther) Icons.Filled.DoneAll else Icons.Filled.Done,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isReadByOther) Color(0xFF00FF7F) else Color.Gray
                                        )
                                    }
                                }"""

new_bubble = """                                if (msg.isDeletedForEveryone) {
                                    Text("🚫 Este mensaje fue eliminado", color = textSubColor.copy(0.6f), fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                } else if (msg.expiryTime != null && msg.expiryTime < System.currentTimeMillis()) {
                                    Text("🔥 Contenido autodestruido", color = Color.Red.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    if (!msg.replyToText.isNullOrEmpty()) {
                                        Surface(
                                            color = if (isMine) Color(0xFF00241B) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        ) {
                                            Row(modifier = Modifier.padding(6.dp)) {
                                                Box(modifier = Modifier.width(3.dp).height(28.dp).background(NeoTheme.Green, RoundedCornerShape(2.dp)))
                                                Spacer(Modifier.width(6.dp))
                                                Column {
                                                    Text(msg.replyToSender ?: "Mensaje", color = NeoTheme.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text(msg.replyToText ?: "", color = textColor.copy(0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                    MessageContent(msg, textColor, textSubColor, context, isMine = isMine, playingVoiceMsgId = playingVoiceMsgId, onPlayingVoiceChange = { playingVoiceMsgId = it }, onImageClick = { activeZoomBitmap = it })
                                    if (msg.expiryTime != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                            Icon(Icons.Filled.Timer, null, tint = Color.Red, modifier = Modifier.size(10.dp))
                                            Text(" Se quema en ${((msg.expiryTime - System.currentTimeMillis())/1000)}s", color = Color.Red, fontSize = 9.sp)
                                        }
                                    }
                                }
                                
                                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                    if (msg.isStarred) {
                                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                    }
                                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)), color = textSubColor.copy(0.5f), fontSize = 10.sp)
                                    if (isMine) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isReadByOther) Icons.Filled.DoneAll else Icons.Filled.Done,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isReadByOther) Color(0xFF00FF7F) else Color.Gray
                                        )
                                    }
                                }
                                
                                if (msg.reactions.isNotEmpty()) {
                                    val emojis = msg.reactions.values.distinct().joinToString(" ")
                                    Surface(
                                        color = Color(0xFF0B141B),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(0.15f)),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(emojis, fontSize = 12.sp)
                                            if (msg.reactions.size > 1) {
                                                Spacer(Modifier.width(3.dp))
                                                Text("${msg.reactions.size}", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }"""

if old_bubble in code:
    code = code.replace(old_bubble, new_bubble, 1)
    print("Enhanced message bubble with replies, stars and reactions")
else:
    print("Warning: old_bubble not found")

# 5. Add Quoted reply box above input bar
old_input_surface = """            Surface(color = surfaceColor, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) { Icon(Icons.Filled.AttachFile, null, tint = if (showAttachmentMenu) Color(0xFF00FF7F) else Color.Gray) }"""

new_input_surface = """            AnimatedVisibility(visible = replyingTo != null) {
                val target = replyingTo
                if (target != null) {
                    Surface(
                        color = surfaceColor,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeoTheme.Green.copy(0.5f))
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(4.dp).height(32.dp).background(NeoTheme.Green, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Respondiendo a: ${target.senderName.ifEmpty { "Mensaje" }}", color = NeoTheme.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(target.text.take(80), color = textColor.copy(0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancelar respuesta", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Surface(color = surfaceColor, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) { Icon(Icons.Filled.AttachFile, null, tint = if (showAttachmentMenu) Color(0xFF00FF7F) else Color.Gray) }"""

if old_input_surface in code:
    code = code.replace(old_input_surface, new_input_surface, 1)
    print("Added quoted reply box above message input bar")
else:
    print("Warning: old_input_surface not found")

# 6. Upgrade selectedMessage dialog with Emoji reaction bar, Reply button and Star button
old_dialog = """    if (selectedMessage != null) {
        val targetMsg = selectedMessage!!
        val isMine = NeoUtils.sanitizeId(targetMsg.senderId).takeLast(10) == cleanMyId.takeLast(10)
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            containerColor = surfaceColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MoreVert, null, tint = NeoTheme.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Opciones del Mensaje", color = textColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val preview = when {
                        targetMsg.text.startsWith("[Media]") -> "📷 Foto adjunta"
                        targetMsg.text.startsWith("[File]") -> {
                            val parts = targetMsg.text.removePrefix("[File] ").split("|")
                            "📄 Archivo: " + (parts.firstOrNull() ?: "Documento")
                        }
                        else -> targetMsg.text.take(60)
                    }
                    Text(preview, color = textSubColor, fontSize = 13.sp, maxLines = 2)
                    HorizontalDivider(color = Color.White.copy(0.1f))

                    // 1. COPIAR TEXTO O ENLACE
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(targetMsg.text))
                            Toast.makeText(context, "📋 Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copiar contenido")
                    }

                    // 2. ELIMINAR PARA MÍ (Local Room SQLite + lista en pantalla)
                    Button(
                        onClick = {
                            val mid = targetMsg.id
                            firebaseMessages.removeAll { it.id == mid }
                            coroutineScope.launch(Dispatchers.IO) {
                                try { dao?.deleteMessageById(mid) } catch (_: Exception) {}
                            }
                            Toast.makeText(context, "🗑️ Eliminado para ti", Toast.LENGTH_SHORT).show()
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar para mí", color = Color.White)
                    }

                    // 3. ELIMINAR PARA TODOS (Si el mensaje fue enviado por mí)
                    if (isMine) {
                        Button(
                            onClick = {
                                val mid = targetMsg.id
                                firebaseMessages.removeAll { it.id == mid }
                                coroutineScope.launch(Dispatchers.IO) {
                                    try { dao?.deleteMessageById(mid) } catch (_: Exception) {}
                                    try { database.child(mid).removeValue() } catch (_: Exception) {}
                                }
                                Toast.makeText(context, "🚨 Eliminado para todos", Toast.LENGTH_SHORT).show()
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Filled.DeleteForever, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Eliminar para todos", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessage = null }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }"""

new_dialog = """    if (showDisappearingDialog) {
        var selectedExpiryHours by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showDisappearingDialog = false },
            containerColor = surfaceColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, tint = NeoTheme.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Mensajes Temporales", color = textColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elige si deseas que los nuevos mensajes se autodestruyan. Por defecto, tus mensajes se guardan para siempre de forma segura.", color = textSubColor, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    val options = listOf(
                        0 to "🛡️ Desactivado (Guardar todo para siempre)",
                        24 to "⏱️ 24 horas",
                        168 to "⏱️ 7 días",
                        720 to "⏱️ 30 días"
                    )
                    options.forEach { (hrs, label) ->
                        Surface(
                            color = if (selectedExpiryHours == hrs) NeoTheme.Green.copy(0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            border = if (selectedExpiryHours == hrs) BorderStroke(1.dp, NeoTheme.Green) else null,
                            modifier = Modifier.fillMaxWidth().clickable { selectedExpiryHours = hrs }
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedExpiryHours == hrs, onClick = { selectedExpiryHours = hrs })
                                Spacer(Modifier.width(6.dp))
                                Text(label, color = textColor, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisappearingDialog = false
                        Toast.makeText(context, if (selectedExpiryHours == 0) "🛡️ Mensajes configurados: Guardado Permanente." else "⏱️ Mensajes temporales activados.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                ) {
                    Text("GUARDAR", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisappearingDialog = false }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }

    if (selectedMessage != null) {
        val targetMsg = selectedMessage!!
        val isMine = NeoUtils.sanitizeId(targetMsg.senderId).takeLast(10) == cleanMyId.takeLast(10)
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            containerColor = surfaceColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MoreVert, null, tint = NeoTheme.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Opciones del Mensaje", color = textColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // BARRA DE REACCIONES RÁPIDAS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        val mid = targetMsg.id
                                        val currentReaction = targetMsg.reactions[cleanMyId]
                                        val newReaction = if (currentReaction == emoji) null else emoji
                                        if (newReaction == null) {
                                            database.child(mid).child("reactions").child(cleanMyId).removeValue()
                                        } else {
                                            database.child(mid).child("reactions").child(cleanMyId).setValue(newReaction)
                                        }
                                        selectedMessage = null
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }

                    val preview = when {
                        targetMsg.text.startsWith("[Media]") -> "📷 Foto adjunta"
                        targetMsg.text.startsWith("[File]") -> {
                            val parts = targetMsg.text.removePrefix("[File] ").split("|")
                            "📄 Archivo: " + (parts.firstOrNull() ?: "Documento")
                        }
                        else -> targetMsg.text.take(60)
                    }
                    Text(preview, color = textSubColor, fontSize = 13.sp, maxLines = 2)
                    HorizontalDivider(color = Color.White.copy(0.1f))

                    // 1. RESPONDER / CITAR
                    Button(
                        onClick = {
                            replyingTo = targetMsg
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                    ) {
                        Icon(Icons.Filled.Reply, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Responder a este mensaje", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    // 2. DESTACAR / GUARDAR MENSAJE ⭐
                    OutlinedButton(
                        onClick = {
                            val newStarred = !targetMsg.isStarred
                            database.child(targetMsg.id).child("isStarred").setValue(newStarred)
                            coroutineScope.launch(Dispatchers.IO) {
                                try { dao?.insertMessage(targetMsg.copy(isStarred = newStarred).toEntity(chatId)) } catch (_: Exception) {}
                            }
                            Toast.makeText(context, if (newStarred) "⭐ Mensaje guardado en Destacados" else "Mensaje desmarcado", Toast.LENGTH_SHORT).show()
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD700)),
                        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(0.6f))
                    ) {
                        Icon(if (targetMsg.isStarred) Icons.Filled.Star else Icons.Filled.StarBorder, null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (targetMsg.isStarred) "Quitar de destacados" else "⭐ Destacar (Guardar protegido)")
                    }

                    // 3. COPIAR TEXTO O ENLACE
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(targetMsg.text))
                            Toast.makeText(context, "📋 Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copiar contenido")
                    }

                    // 4. ELIMINAR PARA MÍ
                    Button(
                        onClick = {
                            val mid = targetMsg.id
                            firebaseMessages.removeAll { it.id == mid }
                            coroutineScope.launch(Dispatchers.IO) {
                                try { dao?.deleteMessageById(mid) } catch (_: Exception) {}
                            }
                            Toast.makeText(context, "🗑️ Eliminado para ti", Toast.LENGTH_SHORT).show()
                            selectedMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar para mí", color = Color.White)
                    }

                    // 5. ELIMINAR PARA TODOS (Si el mensaje fue enviado por mí)
                    if (isMine) {
                        Button(
                            onClick = {
                                val mid = targetMsg.id
                                firebaseMessages.removeAll { it.id == mid }
                                coroutineScope.launch(Dispatchers.IO) {
                                    try { dao?.deleteMessageById(mid) } catch (_: Exception) {}
                                    try { database.child(mid).removeValue() } catch (_: Exception) {}
                                }
                                Toast.makeText(context, "🚨 Eliminado para todos", Toast.LENGTH_SHORT).show()
                                selectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Filled.DeleteForever, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Eliminar para todos", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessage = null }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }"""

if old_dialog in code:
    code = code.replace(old_dialog, new_dialog, 1)
    print("Upgraded selectedMessage dialog successfully")
else:
    print("Warning: old_dialog not found")

with open(chat_path, 'w', encoding='utf-8') as f:
    f.write(code)
