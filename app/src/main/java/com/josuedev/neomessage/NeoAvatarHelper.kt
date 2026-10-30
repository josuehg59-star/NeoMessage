package com.josuedev.neomessage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object NeoAvatarHelper {

    /**
     * Procesa la imagen seleccionada:
     * - La lee en un solo paso seguro (sin agotar el flujo del ContentResolver)
     * - Recorta el centro exacto en forma cuadrada (sin deformar ni cortar la cara)
     * - Escala a 400x400 con nitidez y comprime a JPEG ~25 KB
     * - Guarda copia local en archivos internos de la app
     */
    fun processAndCenterProfilePhoto(context: Context, uri: Uri): Pair<ByteArray, File>? {
        return try {
            val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (inputBytes.isEmpty()) return null

            // 1. Obtener dimensiones
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, bounds)

            var inSampleSize = 1
            var w = bounds.outWidth
            var h = bounds.outHeight
            while (w > 800 || h > 800) {
                w /= 2
                h /= 2
                inSampleSize *= 2
            }

            // 2. Decodificar imagen
            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val fullBitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, decodeOpts) ?: return null

            // 3. RECORTAR AL CENTRO EXACTO (CUADRADO PERFECTO)
            val squareDim = minOf(fullBitmap.width, fullBitmap.height)
            val startX = ((fullBitmap.width - squareDim) / 2).coerceAtLeast(0)
            val startY = ((fullBitmap.height - squareDim) / 2).coerceAtLeast(0)
            val croppedSquare = Bitmap.createBitmap(fullBitmap, startX, startY, squareDim, squareDim)

            // 4. ESCALADO A 400x400
            val finalAvatar = Bitmap.createScaledBitmap(croppedSquare, 400, 400, true)

            // 5. COMPRESIÓN A JPEG
            val out = ByteArrayOutputStream()
            finalAvatar.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val compressedBytes = out.toByteArray()

            // 6. GUARDAR COPIA LOCAL
            val avatarFile = File(context.filesDir, "my_avatar_centered.jpg")
            FileOutputStream(avatarFile).use { it.write(compressedBytes) }

            Pair(compressedBytes, avatarFile)
        } catch (e: Exception) {
            android.util.Log.e("NEO_AVATAR", "Error procesando avatar centrado: ${e.message}")
            null
        }
    }

    /**
     * Decodifica un string de foto (Base64, archivo local o URL) de forma infalible a Bitmap
     */
    fun decodeAvatarBitmap(photoSource: String?): Bitmap? {
        if (photoSource.isNullOrBlank()) return null
        return try {
            if (photoSource.startsWith("/")) {
                val f = File(photoSource)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            } else if (photoSource.startsWith("data:") || photoSource.length > 200) {
                val b64 = if (photoSource.contains("base64,")) photoSource.substringAfter("base64,") else photoSource
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Composable universal infalible para renderizar avatares centrados sin quedar en blanco
 */
@Composable
fun NeoUniversalAvatar(
    photoSource: String?,
    fallbackName: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 18
) {
    val bitmap = remember(photoSource) {
        NeoAvatarHelper.decodeAvatarBitmap(photoSource)
    }

    Box(
        modifier = modifier.clip(CircleShape).background(Color(0xFF1E293B)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = fallbackName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!photoSource.isNullOrBlank() && (photoSource.startsWith("http://") || photoSource.startsWith("https://"))) {
            AsyncImage(
                model = photoSource,
                contentDescription = fallbackName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = fallbackName.trim().take(1).uppercase()
            if (initial.isNotEmpty() && initial != "@") {
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.fillMaxSize(0.6f)
                )
            }
        }
    }
}
