path = r'app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Imports
import_target = 'package com.josuedev.neomessage\n'
new_imports = '''package com.josuedev.neomessage

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.webkit.MimeTypeMap
'''

if 'import androidx.compose.ui.window.Dialog' not in c:
    c = c.replace(import_target, new_imports, 1)

# 2. Add activeZoomBitmap state inside ChatRoomScreen composable
state_target = 'var isUploading by remember { mutableStateOf(false) }'
new_state = '''var isUploading by remember { mutableStateOf(false) }
    var activeZoomBitmap by remember { mutableStateOf<ImageBitmap?>(null) }'''

if state_target in c:
    c = c.replace(state_target, new_state, 1)

# 3. Add bot dispatch inside onChildAdded
bot_target = '''                        coroutineScope.launch(Dispatchers.IO) {
                            try { dao?.insertMessage(m.toEntity(chatId)) } catch (_: Exception) {}
                        }
                    }'''

new_bot = '''                        coroutineScope.launch(Dispatchers.IO) {
                            try { dao?.insertMessage(m.toEntity(chatId)) } catch (_: Exception) {}
                        }
                        if (m.senderId != cleanMyId) {
                            NeoBotManager.dispatchAutoReplyIfNeeded(
                                context = context,
                                chatId = chatId,
                                senderId = m.senderId,
                                myId = cleanMyId,
                                incomingText = m.text,
                                msgTimestamp = m.timestamp
                            )
                        }
                    }'''

if bot_target in c:
    c = c.replace(bot_target, new_bot, 1)

# 4. Replace performMediaUpload
media_target = '''    // --- MOTOR DE TRANSMISIÓN DE IMÁGENES NÍTIDAS (AUTO-RENDERIZADO BASE64 + P2P) ---
    val performMediaUpload = { uri: Uri ->
        if (!isUploading) {
            isUploading = true
            Toast.makeText(context, "📸 Procesando imagen...", Toast.LENGTH_SHORT).show()
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val compressedBytes = NeoUtils.compressImage(context, uri, quality = 80)
                    val base64Data = if (compressedBytes != null) {
                        android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
                    } else null

                    val mid = database.push().key ?: UUID.randomUUID().toString()
                    val msgText = if (base64Data != null && base64Data.length < 500_000) {
                        "[Media] data:image/jpeg;base64,$base64Data"
                    } else {
                        val meta = NeoLocalNodeServer.registerFileForSharing(context, uri)
                        val myIp = NeoLocalNodeServer.getLocalIpAddress()
                        "[Media] http://$myIp:${NeoLocalNodeServer.activePort}/download?fileId=${meta.fileId}"
                    }

                    val msg = CloudMessage(mid, msgText, System.currentTimeMillis(), cleanMyId, myName, "")

                    dao?.insertMessage(msg.toEntity(chatId))
                    withContext(Dispatchers.Main) {
                        if (firebaseMessages.none { it.id == msg.id }) {
                            firebaseMessages.add(msg)
                            firebaseMessages.sortBy { it.timestamp }
                        }
                        Toast.makeText(context, "✅ Imagen enviada", Toast.LENGTH_SHORT).show()
                        isUploading = false
                    }

                    // Enviar vía UDP Mesh local
                    NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)

                    // Enviar vía P2P HTTP si está cerca
                    val directPeer = NeoLocalNodeServer.findLocalPeer(otherCleanId)
                    if (directPeer != null) {
                        NeoLocalNodeServer.sendDirectMessage(
                            targetHost = directPeer.host,
                            targetPort = directPeer.port,
                            message = msg,
                            chatId = chatId
                        )
                    }

                    // Enviar por Firebase para larga distancia
                    try { database.child(mid).setValue(msg) } catch (_: Exception) {}
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error procesando imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                        isUploading = false
                    }
                }
            }
        }
    }'''

new_media = '''    // --- MOTOR DE TRANSMISIÓN DE IMÁGENES NÍTIDAS (AUTO-RENDERIZADO BASE64 100% GARANTIZADO) ---
    val performMediaUpload = { uri: Uri ->
        if (!isUploading) {
            isUploading = true
            Toast.makeText(context, "📸 Enviando imagen nítida...", Toast.LENGTH_SHORT).show()
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val compressedBytes = NeoUtils.compressImage(context, uri, quality = 70, maxDimension = 900)
                    if (compressedBytes == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No se pudo procesar la imagen seleccionada", Toast.LENGTH_SHORT).show()
                            isUploading = false
                        }
                        return@launch
                    }

                    val base64Data = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
                    val mid = database.push().key ?: UUID.randomUUID().toString()
                    val msgText = "[Media] data:image/jpeg;base64,$base64Data"
                    val msg = CloudMessage(mid, msgText, System.currentTimeMillis(), cleanMyId, myName, "")

                    dao?.insertMessage(msg.toEntity(chatId))
                    withContext(Dispatchers.Main) {
                        if (firebaseMessages.none { it.id == msg.id }) {
                            firebaseMessages.add(msg)
                            firebaseMessages.sortBy { it.timestamp }
                        }
                        Toast.makeText(context, "✅ Imagen enviada", Toast.LENGTH_SHORT).show()
                        isUploading = false
                    }

                    // Enviar vía UDP Mesh local
                    NeoLocalNodeServer.broadcastUdpMessage(otherCleanId, msg, chatId)

                    // Enviar vía P2P HTTP si está en la misma red
                    val directPeer = NeoLocalNodeServer.findLocalPeer(otherCleanId)
                    if (directPeer != null) {
                        NeoLocalNodeServer.sendDirectMessage(
                            targetHost = directPeer.host,
                            targetPort = directPeer.port,
                            message = msg,
                            chatId = chatId
                        )
                    }

                    // Enviar por Firebase en tiempo real
                    try { database.child(mid).setValue(msg) } catch (_: Exception) {}
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error procesando imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                        isUploading = false
                    }
                }
            }
        }
    }'''

if media_target in c:
    c = c.replace(media_target, new_media, 1)

# 5. Replace performDocumentUpload 600_000 threshold with 2_000_000
c = c.replace('if (fileSize < 600_000) {', 'if (fileSize < 2_000_000) {')

# 6. Pass onImageClick in MessageContent call
msg_content_call = 'MessageContent(msg, textColor, textSubColor, context)'
new_msg_content_call = 'MessageContent(msg, textColor, textSubColor, context, onImageClick = { activeZoomBitmap = it })'
if msg_content_call in c:
    c = c.replace(msg_content_call, new_msg_content_call, 1)

# 7. Render FullScreenImageViewer dialog when activeZoomBitmap != null
# Find selectedMessage != null check
viewer_anchor = 'if (selectedMessage != null) {'
viewer_code = '''if (activeZoomBitmap != null) {
        FullScreenImageViewer(
            bitmap = activeZoomBitmap!!,
            onDismiss = { activeZoomBitmap = null }
        )
    }

    if (selectedMessage != null) {'''
if viewer_anchor in c:
    c = c.replace(viewer_anchor, viewer_code, 1)

# 8. Replace MessageContent definition and add openDocumentFile + FullScreenImageViewer
content_def_target = '''@Composable
fun MessageContent(msg: CloudMessage, textColor: Color, textSubColor: Color, context: Context) {
    val text = msg.text
    when {
        text.startsWith("[Media]") -> {
            val content = text.substringAfter("[Media]").trim()
            val bitmap = remember(content) {
                if (content.startsWith("data:image")) {
                    try {
                        val base64Data = content.substringAfter("base64,")
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) { null }
                } else null
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Imagen",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                AsyncImage(
                    model = content,
                    contentDescription = "Imagen",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }'''

new_content_def = '''fun openDocumentFile(context: Context, file: File) {
    try {
        val extension = file.extension.lowercase()
        val mimeType = when (extension) {
            "pdf" -> "application/pdf"
            "xlsx", "xls" -> "application/vnd.ms-excel"
            "docx", "doc" -> "application/msword"
            "pptx", "ppt" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.josuedev.neomessage.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir con..."))
    } catch (e: Exception) {
        Toast.makeText(context, "No se encontró aplicación para abrir este archivo (${file.name}): ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun MessageContent(
    msg: CloudMessage, 
    textColor: Color, 
    textSubColor: Color, 
    context: Context,
    onImageClick: ((ImageBitmap) -> Unit)? = null
) {
    val text = msg.text
    when {
        text.startsWith("[Media]") -> {
            val content = text.substringAfter("[Media]").trim()
            val bitmap = remember(content) {
                if (content.startsWith("data:image")) {
                    try {
                        val base64Data = content.substringAfter("base64,")
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) { null }
                } else null
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Imagen",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { onImageClick?.invoke(bitmap) },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                AsyncImage(
                    model = content,
                    contentDescription = "Imagen",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }'''

if content_def_target in c:
    c = c.replace(content_def_target, new_content_def, 1)

# 9. Update the open button in MessageContent to use openDocumentFile
old_open_button = '''                        Button(
                            onClick = {
                                try {
                                    val openUri = androidx.core.content.FileProvider.getUriForFile(context, "com.josuedev.neomessage.fileprovider", downloadedFile!!)
                                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(openUri, context.contentResolver.getType(openUri) ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(openIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No hay aplicación para abrir este archivo", Toast.LENGTH_SHORT).show()
                                }
                            },'''

new_open_button = '''                        Button(
                            onClick = {
                                if (downloadedFile != null) {
                                    openDocumentFile(context, downloadedFile!!)
                                }
                            },'''

if old_open_button in c:
    c = c.replace(old_open_button, new_open_button, 1)

# 10. Append FullScreenImageViewer at the end of the file
viewer_composable = '''

@Composable
fun FullScreenImageViewer(
    bitmap: ImageBitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Interactive Image with pinch & pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                            offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Zoom imagen",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }

            // Top Bar: Close and Zoom Level indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.6f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }

                Surface(
                    color = Color.Black.copy(0.6f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Zoom: ${(scale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Bar: ++++ / --- Controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E293B).copy(0.9f))
                    .border(1.dp, Color(0xFF00FF7F).copy(0.4f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom OUT (-)
                IconButton(
                    onClick = {
                        scale = (scale - 0.4f).coerceAtLeast(0.6f)
                        if (scale <= 1f) offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Alejar (-)", tint = Color.White)
                }

                // Reset (1x)
                TextButton(
                    onClick = {
                        scale = 1f
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                ) {
                    Text("1x", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold)
                }

                // Zoom IN (+)
                IconButton(
                    onClick = {
                        scale = (scale + 0.5f).coerceAtMost(5.0f)
                    }
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Acercar (+)", tint = Color(0xFF00FF7F))
                }
            }
        }
    }
}
'''

c = c + viewer_composable

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print("SUCCESS: Updated ChatRoomScreen.kt with Image Zoom, Document Opener, and Instant Delivery")
