package com.josuedev.neomessage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestor soberano de mensajes eliminados "Para mí".
 * Mantiene un registro permanente en SharedPreferences para garantizar
 * que los mensajes eliminados localmente nunca vuelvan a mostrarse,
 * incluso tras reconexiones de Firebase o reinicios de la aplicación.
 */
object NeoDeletedMessagesManager {
    private const val PREF_NAME = "neo_deleted_messages_pref"
    private const val KEY_DELETED_SET = "deleted_msg_ids"

    @Volatile
    private var memorySet: MutableSet<String>? = null

    private fun getSet(context: Context): MutableSet<String> {
        if (memorySet == null) {
            synchronized(this) {
                if (memorySet == null) {
                    val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    val rawSet = sp.getStringSet(KEY_DELETED_SET, emptySet()) ?: emptySet()
                    memorySet = HashSet(rawSet)
                }
            }
        }
        return memorySet!!
    }

    fun markDeletedForMe(context: Context, messageId: String) {
        if (messageId.isBlank()) return
        val set = getSet(context)
        synchronized(this) {
            set.add(messageId)
            val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            sp.edit().putStringSet(KEY_DELETED_SET, HashSet(set)).apply()
        }
    }

    fun isDeletedForMe(context: Context, messageId: String): Boolean {
        if (messageId.isBlank()) return false
        val set = getSet(context)
        return set.contains(messageId)
    }
}

/**
 * Caché de alta velocidad en memoria (LRU) para imágenes y medios en Base64.
 * Evita la decodificación repetitiva en el hilo principal durante el scroll
 * de Jetpack Compose, eliminando el lag o congelamiento en dispositivos móviles.
 */
object NeoMediaBitmapCache {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 6).coerceIn(16 * 1024, 64 * 1024)

    private val lruCache = object : LruCache<String, ImageBitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 2) / 1024 // Estimación para RGB_565 (2 bytes por pixel)
        }
    }

    fun get(key: String): ImageBitmap? {
        return lruCache.get(key)
    }

    fun put(key: String, bitmap: ImageBitmap) {
        lruCache.put(key, bitmap)
    }

    suspend fun decodeBase64Safe(key: String, rawBase64: String, maxDim: Int = 1024): ImageBitmap? = withContext(Dispatchers.IO) {
        val existing = get(key)
        if (existing != null) return@withContext existing

        try {
            val cleanB64 = if (rawBase64.contains("base64,")) {
                rawBase64.substringAfter("base64,")
            } else {
                rawBase64
            }
            val bytes = Base64.decode(cleanB64, Base64.DEFAULT)

            // 1. Obtener dimensiones sin alojar mapa de bits completo
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sampleSize = 1
            val maxOriginal = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxOriginal / sampleSize > maxDim) {
                sampleSize *= 2
            }

            // 2. Decodificar en formato RGB_565 (50% menos consumo de RAM que ARGB_8888)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (bmp != null) {
                val imageBitmap = bmp.asImageBitmap()
                put(key, imageBitmap)
                return@withContext imageBitmap
            }
        } catch (_: Throwable) {}
        return@withContext null
    }
}
