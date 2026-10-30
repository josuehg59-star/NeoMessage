with open('app/src/main/java/com/josuedev/neomessage/StatusScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace uploadStatus logic with bulletproof implementation
start_marker = "    val uploadStatus = { uri: Uri ->"
end_marker = "    val galleryLauncher = rememberLauncherForActivityResult"

start_idx = text.find(start_marker)
end_idx = text.find(end_marker)

if start_idx != -1 and end_idx != -1:
    new_upload = """    val uploadStatus = { uri: Uri ->
        if (!isUploading) {
            isUploading = true
            Toast.makeText(context, "📡 Publicando estado por 24 horas...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 1. Compresión optimizada para Base64 ultraligero (40-60 KB)
                    val bytes = NeoUtils.compressImage(context, uri, quality = 60, maxDimension = 600)
                    val imgData = if (bytes != null) "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) else ""

                    val newId = database.push().key ?: UUID.randomUUID().toString()
                    val status = UserStatus(
                        id = newId,
                        text = "Estado NeoMessage",
                        timestamp = System.currentTimeMillis(),
                        senderName = myName.ifEmpty { "Usuario" },
                        senderId = userId,
                        imageUrl = imgData,
                        musicName = selectedMusicName,
                        musicUrl = null
                    )
                    
                    // 2. Guardar en Firebase sin requerir FirebaseAuth anónimo bloqueante
                    database.child(newId).setValue(status)
                        .addOnSuccessListener {
                            isUploading = false
                            selectedMusicName = null
                            Toast.makeText(context, "✅ ¡Estado publicado! Durará 24 horas.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { err ->
                            isUploading = false
                            Toast.makeText(context, "❌ Error al publicar: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                } catch (e: Exception) {
                    coroutineScope.launch(Dispatchers.Main) {
                        isUploading = false
                        Toast.makeText(context, "❌ Error al procesar imagen: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

"""
    text = text[:start_idx] + new_upload + text[end_idx:]
    with open('app/src/main/java/com/josuedev/neomessage/StatusScreen.kt', 'w', encoding='utf-8') as f:
        f.write(text)
    print("Fixed uploadStatus in StatusScreen.kt successfully!")
else:
    print(f"Indices not found: start={start_idx}, end={end_idx}")
