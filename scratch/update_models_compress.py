path = r'app/src/main/java/com/josuedev/neomessage/Models.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

old_compress = '''    // --- PROTOCOLO DE AHORRO DE DATOS NEO (DATASAVER) ---
    fun compressImage(context: Context, uri: Uri, quality: Int = 70): ByteArray? {
        return try {
            val input = context.contentResolver.openInputStream(uri)
            val original = android.graphics.BitmapFactory.decodeStream(input)
            input?.close()

            val scale = 1200f / Math.max(original.width, original.height).coerceAtLeast(1)
            val targetWidth = if (scale < 1) (original.width * scale).toInt() else original.width
            val targetHeight = if (scale < 1) (original.height * scale).toInt() else original.height
            
            val resized = android.graphics.Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
            val out = java.io.ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (ignored: Exception) { null }
    }'''

new_compress = '''    // --- PROTOCOLO DE AHORRO DE DATOS NEO (DATASAVER) ---
    fun compressImage(context: Context, uri: Uri, quality: Int = 70, maxDimension: Int = 900): ByteArray? {
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
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (ignored: Exception) { null }
    }'''

if old_compress in c:
    c = c.replace(old_compress, new_compress)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)
    print("SUCCESS: Updated compressImage in Models.kt")
else:
    print("ERROR: old_compress not found in Models.kt")
