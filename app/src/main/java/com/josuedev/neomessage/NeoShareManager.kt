package com.josuedev.neomessage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

data class IncomingSharedContent(
    val text: String? = null,
    val uris: List<Uri> = emptyList(),
    val mimeType: String? = null
)

object NeoShareManager {
    val pendingIncomingContent = mutableStateOf<IncomingSharedContent?>(null)

    fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            val uris = if (streamUri != null) listOf(streamUri) else emptyList()
            if (!text.isNullOrBlank() || uris.isNotEmpty()) {
                pendingIncomingContent.value = IncomingSharedContent(
                    text = text,
                    uris = uris,
                    mimeType = type
                )
            }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            val streamUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            } ?: emptyList()
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (streamUris.isNotEmpty() || !text.isNullOrBlank()) {
                pendingIncomingContent.value = IncomingSharedContent(
                    text = text,
                    uris = streamUris,
                    mimeType = type
                )
            }
        }
    }

    /**
     * Comparte hacia afuera a WhatsApp, Gmail, Facebook, Drive, Bluetooth, etc.
     */
    fun shareExternally(context: Context, text: String? = null, file: File? = null, mimeType: String? = null) {
        try {
            if (file != null && file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val resolvedMime = mimeType ?: when (file.extension.lowercase()) {
                    "pdf" -> "application/pdf"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "doc" -> "application/msword"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "xls" -> "application/vnd.ms-excel"
                    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    "ppt" -> "application/vnd.ms-powerpoint"
                    "txt" -> "text/plain"
                    "mp3" -> "audio/mpeg"
                    "mp4" -> "video/mp4"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "zip" -> "application/zip"
                    else -> "*/*"
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = resolvedMime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (!text.isNullOrBlank()) {
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir archivo con..."))
            } else if (!text.isNullOrBlank()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir mensaje con..."))
            } else {
                Toast.makeText(context, "No hay contenido para compartir", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error al abrir menú de compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
