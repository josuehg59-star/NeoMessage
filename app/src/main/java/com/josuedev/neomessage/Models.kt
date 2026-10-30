
package com.josuedev.neomessage

import android.content.Context
import android.net.Uri

enum class Screen { 
    Login, ChatsList, ChatRoom, Status, Settings, Contacts, Calling, 
    LinkedDevices, Communities, HistoryCalls, StarredMessages,
    HelpMenu, Feedback, AppInfo, HelpCenter, Vault, Account,
    Privacy, ChatsSettings, NotificationsSettings, StorageSettings,
    Avatar, ListsSettings, Profile, Language, Accessibility,
    InviteFriends, BroadcastsSettings, Security, TwoStepVerification,
    ChangeNumber, BlockedContacts, AppLock, BusinessTools, NeoAI, CorteCaja
}

object Constants {
    const val OWNER_PHONE = "9612765041" // EL CREADOR MAESTRO
}

object NeoUtils {
    fun sanitizeId(id: String): String {
        val digits = id.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }
    
    fun getChatId(id1: String, id2: String): String {
        val s1 = sanitizeId(id1)
        val s2 = sanitizeId(id2)
        return if (s1 < s2) "private_${s1}_$s2" else "private_${s2}_$s1"
    }

    // --- PROTOCOLO DE AHORRO DE DATOS NEO (DATASAVER) ---
    fun compressImage(context: Context, uri: Uri, quality: Int = 85, maxDimension: Int = 2048): ByteArray? {
        return try {
            val getStream = {
                if (uri.scheme == "file") {
                    java.io.FileInputStream(java.io.File(uri.path ?: ""))
                } else {
                    context.contentResolver.openInputStream(uri)
                }
            }

            // 1. Obtener dimensiones sin cargar a memoria completa
            val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            getStream()?.use { android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts) }

            // 2. Calcular sampleSize óptimo para no saturar memoria
            var inSampleSize = 1
            var w = boundsOpts.outWidth
            var h = boundsOpts.outHeight
            while (w > maxDimension * 2 || h > maxDimension * 2) {
                w /= 2
                h /= 2
                inSampleSize *= 2
            }

            // 3. Decodificar imagen redimensionada
            val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val original = getStream()?.use { android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: return null

            val maxSide = Math.max(original.width, original.height).coerceAtLeast(1)
            val scale = maxDimension.toFloat() / maxSide
            val targetWidth = if (scale < 1) (original.width * scale).toInt().coerceAtLeast(1) else original.width
            val targetHeight = if (scale < 1) (original.height * scale).toInt().coerceAtLeast(1) else original.height

            val resized = if (targetWidth != original.width || targetHeight != original.height) {
                android.graphics.Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
            } else {
                original
            }

            val out = java.io.ByteArrayOutputStream()
            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                android.graphics.Bitmap.CompressFormat.JPEG
            }
            resized.compress(format, quality, out)
            out.toByteArray()
        } catch (ignored: Exception) { null }
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) it.getLong(it.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE))
                else 0L
            } ?: 0L
        } catch (ignored: Exception) { 0L }
    }

    fun getFileName(context: Context, uri: Uri): String {
        return try {
            var name = "Documento"
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && it.moveToFirst()) {
                    name = it.getString(nameIndex) ?: "Documento"
                }
            }
            name
        } catch (ignored: Exception) { "Documento" }
    }
}

data class Quintuple<out A, out B, out C, out D, out E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
) : java.io.Serializable

data class Sextuple<out A, out B, out C, out D, out E, out F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
) : java.io.Serializable

data class CloudMessage(
    val id: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val senderId: String = "",
    val senderName: String = "",
    val rank: String = "",
    val isEdited: Boolean = false,
    val expiryTime: Long? = null,
    val isStarred: Boolean = false,
    val deliveredTo: Map<String, Boolean> = emptyMap(),
    val readBy: Map<String, Boolean> = emptyMap(),
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val reactions: Map<String, String> = emptyMap(),
    val isDeletedForEveryone: Boolean = false
)

data class UserStatus(
    val id: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val senderName: String = "",
    val senderId: String = "",
    val imageUrl: String = "",
    val musicName: String? = null,
    val musicUrl: String? = null
)

data class NeoGroup(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val members: List<String> = emptyList(),
    val type: String = "group" // "group" | "community" | "broadcast"
)

data class CallLog(
    val id: String = "",
    val callerName: String = "",
    val callerId: String = "",
    val receiverName: String = "",
    val receiverId: String = "",
    val timestamp: Long = 0L,
    val type: String = "outgoing" // "outgoing" | "incoming" | "missed"
)
