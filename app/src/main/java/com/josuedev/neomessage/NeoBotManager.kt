package com.josuedev.neomessage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.net.URLEncoder

data class BotProduct(
    val nombre: String = "",
    val precio: String = "",
    val imagenBase64: String = "" // Foto del producto en Base64 o URL
)

data class BotConfig(
    val isEnabled: Boolean = false,
    val nombreNegocio: String = "Miscelánea Brittany",
    val telefono: String = "9612765041",
    val horario: String = "Lunes a Sábado de 8:30 AM a 7:00 PM",
    val direccion: String = "Av. 16 de septiembre 903, B.S., Tuxtla Gutiérrez, Chiapas",
    val mensajeBienvenida: String = "¡Hola! 👋 Bienvenido a *{negocio}*.\n\nSoy tu asistente virtual 🤖.\n¿En qué podemos ayudarte hoy?\n\n📦 Escribe *Catalogo* para ver productos y precios\n🛒 Escribe *Carrito* para ver tu total de compras\n📊 Escribe *Corte* para ver el corte de caja del día\n📍 Escribe *Ubicacion* para horarios y dirección\n👤 Escribe *Asesor* para hablar con el encargado\n\n_🔍 O escribe directamente: \"agregar takis\", \"coca\", \"foto aceite\"..._",
    val catalogo: List<BotProduct> = emptyList()
)

object NeoBotManager {
    private const val PREF_NAME = "neobot_prefs"
    private const val KEY_CONFIG = "bot_config_json"

    // Guardar último mensaje procesado con timestamp para evitar eco inmediato
    private val lastProcessedMsg = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isBotEnabled(context: Context): Boolean {
        val sp = getPrefs(context)
        if (sp.contains("bot_active_switch")) {
            return sp.getBoolean("bot_active_switch", false)
        }
        return false
    }

    fun setBotEnabled(context: Context, enabled: Boolean) {
        val sp = getPrefs(context)
        sp.edit().putBoolean("bot_active_switch", enabled).apply()
        val cfg = loadConfig(context).copy(isEnabled = enabled)
        saveConfig(context, cfg)
    }

    fun loadConfig(context: Context): BotConfig {
        val sp = getPrefs(context)
        val jsonStr = sp.getString(KEY_CONFIG, null)
        val defaultCatalog = getDefaultCatalog()

        if (jsonStr.isNullOrEmpty()) {
            val defaultConfig = BotConfig(
                isEnabled = false,
                nombreNegocio = "Miscelánea Brittany",
                telefono = "9612765041",
                horario = "Lunes a Sábado de 8:30 AM a 7:00 PM",
                direccion = "Av. 16 de septiembre 903, B.S., Tuxtla Gutiérrez, Chiapas",
                catalogo = defaultCatalog
            )
            saveConfig(context, defaultConfig)
            return defaultConfig
        }

        return try {
            val obj = JSONObject(jsonStr)
            val catArray = obj.optJSONArray("catalogo") ?: JSONArray()
            val catList = mutableListOf<BotProduct>()
            for (i in 0 until catArray.length()) {
                val p = catArray.getJSONObject(i)
                catList.add(
                    BotProduct(
                        nombre = p.optString("nombre"),
                        precio = p.optString("precio"),
                        imagenBase64 = p.optString("imagenBase64", "")
                    )
                )
            }

            val finalCatalog = if (catList.isEmpty()) defaultCatalog else catList

            BotConfig(
                isEnabled = obj.optBoolean("isEnabled", true),
                nombreNegocio = obj.optString("nombreNegocio", "Miscelánea Brittany").ifEmpty { "Miscelánea Brittany" },
                telefono = obj.optString("telefono", "9612765041").ifEmpty { "9612765041" },
                horario = obj.optString("horario", "Lunes a Sábado de 8:30 AM a 7:00 PM").ifEmpty { "Lunes a Sábado de 8:30 AM a 7:00 PM" },
                direccion = obj.optString("direccion", "Av. 16 de septiembre 903, B.S., Tuxtla Gutiérrez, Chiapas").ifEmpty { "Av. 16 de septiembre 903, B.S., Tuxtla Gutiérrez, Chiapas" },
                mensajeBienvenida = obj.optString("mensajeBienvenida", "").ifEmpty { BotConfig().mensajeBienvenida },
                catalogo = finalCatalog
            )
        } catch (e: Exception) {
            BotConfig(isEnabled = true, catalogo = defaultCatalog)
        }
    }

    fun saveConfig(context: Context, config: BotConfig) {
        try {
            val obj = JSONObject()
            obj.put("isEnabled", config.isEnabled)
            obj.put("nombreNegocio", config.nombreNegocio)
            obj.put("telefono", config.telefono)
            obj.put("horario", config.horario)
            obj.put("direccion", config.direccion)
            obj.put("mensajeBienvenida", config.mensajeBienvenida)

            val catArray = JSONArray()
            config.catalogo.forEach {
                val p = JSONObject()
                p.put("nombre", it.nombre)
                p.put("precio", it.precio)
                p.put("imagenBase64", it.imagenBase64)
                catArray.put(p)
            }
            obj.put("catalogo", catArray)

            getPrefs(context).edit().putString(KEY_CONFIG, obj.toString()).apply()
        } catch (ignored: Exception) {}
    }

    // Normalizador de texto: quita acentos, signos y espacios dobles
    fun cleanText(raw: String): String {
        val nfd = Normalizer.normalize(raw.lowercase().trim(), Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    // Procesa el mensaje entrante y genera respuesta de texto y opcionalmente imagen
    fun processMessageWithMedia(context: Context, incomingText: String): Pair<String, String?> {
        val cfg = loadConfig(context)
        val text = cleanText(incomingText)
        val negocio = cfg.nombreNegocio.ifEmpty { "Miscelánea Brittany" }
        val catalog = if (cfg.catalogo.isEmpty()) getDefaultCatalog() else cfg.catalogo

        // 0. Si el usuario pide explícitamente ver la foto de un producto (ej: "foto coca", "ver foto takis", "imagen de chips")
        if (text.startsWith("foto ") || text.startsWith("ver foto ") || text.startsWith("imagen ") ||
            text.startsWith("fotos ") || text.startsWith("foto de ") || text.startsWith("foto del ") ||
            text.startsWith("imagen de ") || text.startsWith("imagen del ")) {
            
            val prodQuery = text.replace("ver foto ", "")
                .replace("foto de ", "")
                .replace("foto del ", "")
                .replace("foto ", "")
                .replace("fotos ", "")
                .replace("imagen de ", "")
                .replace("imagen del ", "")
                .replace("imagen ", "")
                .trim()

            if (prodQuery.isNotEmpty()) {
                val matched = catalog.firstOrNull { cleanText(it.nombre).contains(prodQuery) }
                    ?: catalog.firstOrNull { prodQuery.contains(cleanText(it.nombre)) }

                if (matched != null) {
                    if (matched.imagenBase64.isNotEmpty()) {
                        return "📸 Aquí tienes la foto de *${matched.nombre}* ($${matched.precio} MXN):" to matched.imagenBase64
                    } else {
                        return "ℹ️ El producto *${matched.nombre}* ($${matched.precio} MXN) no tiene foto asignada en el catálogo.\n(Puedes solicitar más detalles con un *Asesor*)." to null
                    }
                } else {
                    return "🔍 No encontré fotos para \"$prodQuery\".\nEscribe *Catalogo* para ver los productos disponibles con fotos 📸." to null
                }
            }
        }

                // --- 🛒 COMANDOS DE CARRITO Y COMPRAS ---
        if (text == "corte de caja" || text == "corte" || text == "corte caja" || text == "ventas de hoy" || text == "caja" || text == "ventas") {
            return NeoCommerceManager.formatCorteDeCajaReport(context, negocio) to null
        }

        if (text == "carrito" || text == "ver carrito" || text == "mi carrito" || text == "total" || text == "cuenta" || text == "mi pedido" || text == "compras") {
            return NeoCommerceManager.formatCartReceipt(context, "default_cart", negocio) to null
        }

        if (text == "vaciar carrito" || text == "borrar carrito" || text == "limpiar carrito" || text == "cancelar pedido") {
            NeoCommerceManager.clearCart(context, "default_cart")
            return "🗑️ Tu carrito de compras ha sido vaciado correctamente.\nEscribe *Catalogo* para ver los productos." to null
        }

        if (text == "comprar" || text == "confirmar" || text == "confirmar compra" || text == "confirmar pedido" || text == "pagar" || text == "pedir ya" || text == "finalizar") {
            val (ok, res) = NeoCommerceManager.checkoutCart(context, "default_cart", "Cliente", "Cliente Chat", negocio)
            return res to null
        }

        if (text.startsWith("agregar ") || text.startsWith("agrega ") || text.startsWith("pedir ") || text.startsWith("comprar ")) {
            val q = text.replace("agregar ", "").replace("agrega ", "").replace("pedir ", "").replace("comprar ", "").trim()
            val matched = catalog.firstOrNull { cleanText(it.nombre).contains(q) } ?: catalog.firstOrNull { q.contains(cleanText(it.nombre)) }
            if (matched != null) {
                val item = NeoCommerceManager.addToCart(context, "default_cart", matched, 1)
                val currentTotal = NeoCommerceManager.getCartTotal(context, "default_cart")
                val sb = StringBuilder("🛒 *¡Agregado al Carrito!*\n\n")
                sb.append("✅ *${matched.nombre}*\n")
                sb.append("💵 Cantidad: ${item.cantidad} | Precio: $${matched.precio} MXN\n")
                sb.append("───────────────────────\n")
                sb.append("💰 *Total actual en carrito:* $${"%.2f".format(currentTotal)} MXN\n\n")
                sb.append("👉 Escribe *carrito* para ver tu cuenta completa.\n")
                sb.append("👉 Escribe *comprar* para confirmar tu orden.")
                return sb.toString() to (if (matched.imagenBase64.isNotEmpty()) matched.imagenBase64 else null)
            } else {
                return "🔍 No encontré el producto \"$q\" en el catálogo.\nEscribe *Catalogo* para ver la lista completa de artículos disponibles." to null
            }
        }

        // 1. Catálogo completo ("catalogo", "catalago", "catallogo", "productos", "articulos", "lista", "precios", "que venden")
        if (text == "catalogo" || text == "catalago" || text == "catallogo" || text.contains("catal") || 
            text.contains("prod") || text.contains("articu") || text.contains("lista") || 
            text.contains("que vend") || text.contains("que tien") || text == "1") {
            
            val sb = StringBuilder("📦 *Catálogo de $negocio:*\n\n")
            val sample = catalog.take(25)
            sample.forEach {
                val hasImg = if (it.imagenBase64.isNotEmpty()) " 📸" else ""
                sb.append("• *${it.nombre}*: $${it.precio} MXN$hasImg\n")
            }
            if (catalog.size > 25) {
                sb.append("\n_...y ${catalog.size - 25} productos más disponibles._")
            }
            sb.append("\n\n🛒 *¿Cómo comprar y ver total?*\n")
            sb.append("• Escribe: *agregar [producto]* (ej: *agregar takis fuego*)\n")
            sb.append("• Escribe: *carrito* para ver los artículos y el total\n")
            sb.append("• Escribe: *comprar* para confirmar tu pedido y caja\n")
            sb.append("• Escribe: *corte de caja* para ver el resumen de ventas del día\n")
            sb.append("• Para ver fotos escribe: *foto [producto]*")
            return sb.toString() to null
        }

        // 2. Ubicación ("ubicacion", "ubicasion", "direccion", "donde", "mapa", "como llegar", "2")
        if (text == "ubicacion" || text == "ubicasion" || text.contains("ubic") || 
            text.contains("direc") || text.contains("donde") || text.contains("mapa") || 
            text.contains("lugar") || text.contains("como llegar") || text == "2") {
            
            val encodedDir = try { URLEncoder.encode(cfg.direccion, "UTF-8") } catch (_: Exception) { "" }
            val mapsUrl = if (encodedDir.isNotEmpty()) "https://maps.google.com/?q=$encodedDir" else ""

            val resp = StringBuilder("📍 *Ubicación de $negocio:*\n${cfg.direccion}\n\n")
            resp.append("🕒 *Horario:*\n${cfg.horario}\n\n")
            resp.append("📞 *Teléfono de contacto:*\n${cfg.telefono}")
            if (mapsUrl.isNotEmpty()) {
                resp.append("\n\n🗺️ *Ver en Google Maps:*\n$mapsUrl")
            }
            return resp.toString() to null
        }

        // 3. Horario ("horario", "hora", "abren", "cierran", "abierto")
        if (text.contains("horari") || text.contains("hora") || text.contains("abre") || 
            text.contains("cierra") || text.contains("abierto")) {
            return "🕒 *Horario de Atención en $negocio:*\n${cfg.horario}\n\n📍 Ubicación: ${cfg.direccion}" to null
        }

        // 4. Asesor humano ("asesor", "humano", "persona", "encargado", "ayuda", "3")
        if (text.contains("asesor") || text.contains("human") || text.contains("encargad") || 
            text.contains("person") || text.contains("ayuda") || text == "3") {
            return "👤 *Atención Personalizada*\nUn momento por favor, le estoy avisando al encargado de *$negocio* para que te atienda personalmente." to null
        }

        // 5. Mensaje de Bienvenida o Menú (Palabras exactas o de inicio para no chocar con "coca cola")
        val isGreeting = text == "hola" || text == "ola" || text == "buenas" || text == "buenos dias" || 
                         text == "buenas tardes" || text == "buenas noches" || text == "menu" || 
                         text == "inicio" || text == "start" || text == "que tal" || text == "saludos" ||
                         text.startsWith("hola ") || text.startsWith("ola ") || text.startsWith("buenas ")
        if (isGreeting) {
            return cfg.mensajeBienvenida.replace("{negocio}", negocio) to null
        }

        // 6. Búsqueda directa de productos en el catálogo (ej: "coca", "aceite", "bateria", "impresiones")
        val ignoredWords = setOf(
            "tienen", "precio", "precios", "cuanto", "cuesta", "cuestan", "venden", 
            "vende", "hay", "para", "quiero", "dame", "me", "das", "un", "una", 
            "de", "el", "la", "los", "las", "por", "favor"
        )
        val searchTokens = text.split(" ").filter { it.length >= 2 && !ignoredWords.contains(it) }

        val matches = catalog.filter { prod ->
            val pNorm = cleanText(prod.nombre)
            searchTokens.any { token -> pNorm.contains(token) } || pNorm.contains(text) || text.contains(pNorm)
        }

        if (matches.isNotEmpty()) {
            val sb = StringBuilder("🔍 *Resultados en $negocio:*\n\n")
            var firstProductWithImage: BotProduct? = null

            matches.take(8).forEach {
                val imgTag = if (it.imagenBase64.isNotEmpty()) " [📸 Foto disponible]" else ""
                sb.append("✅ *${it.nombre}* ➔ *$${it.precio} MXN*$imgTag\n")
                if (firstProductWithImage == null && it.imagenBase64.isNotEmpty()) {
                    firstProductWithImage = it
                }
            }
            if (matches.size > 8) {
                sb.append("\n_Hay ${matches.size - 8} artículos más coincidentes._")
            }

            if (firstProductWithImage != null) {
                sb.append("\n\n💡 Para ver la foto escribe: *foto ${firstProductWithImage!!.nombre.take(15)}*")
            }
            return sb.toString() to null
        }

        // 7. Respuesta por defecto con menú claro
        return ("🤖 *Asistente Virtual $negocio*\n\n" +
               "¿En qué puedo ayudarte? Puedes escribir:\n" +
               "📦 *Catalogo* - Ver lista de productos y precios\n" +
               "📍 *Ubicacion* - Ver dirección y mapa\n" +
               "🕒 *Horario* - Ver horario de atención\n" +
               "👤 *Asesor* - Hablar con el encargado\n\n" +
               "_O escribe lo que buscas (ej: \"coca\", \"impresiones\", \"aceite\")._") to null
    }

    fun processMessage(context: Context, incomingText: String): String {
        return processMessageWithMedia(context, incomingText).first
    }

    fun dispatchAutoReplyIfNeeded(
        context: Context,
        chatId: String,
        senderId: String,
        myId: String,
        incomingText: String,
        msgTimestamp: Long
    ) {
        val cleanSender = NeoUtils.sanitizeId(senderId)
        val cleanMe = NeoUtils.sanitizeId(myId)

        // Evitar auto-responder a uno mismo
        if (cleanSender.takeLast(10) == cleanMe.takeLast(10) && cleanMe.isNotEmpty()) return
        if (cleanSender.isEmpty()) return
        if (!isBotEnabled(context)) return

        // Anti-loop: no responder si el mensaje ya fue generado por un bot
        if (incomingText.startsWith("🤖") || incomingText.contains("(Bot)")) return

        // Anti-repetición inmediata del mismo mensaje (2 segundos de debounce)
        val key = "$chatId-$cleanSender"
        val last = lastProcessedMsg[key]
        val now = System.currentTimeMillis()
        if (last != null && last.first == incomingText && now - last.second < 2000) return
        lastProcessedMsg[key] = incomingText to now

        // Respuesta en milisegundos
        val (replyText, replyMedia) = processMessageWithMedia(context, incomingText)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                val messagesRef = db.getReference("chats").child(chatId).child("messages")
                val localDb = AppDatabase.getDatabase(context)
                val cfg = loadConfig(context)
                val senderTitle = "${cfg.nombreNegocio} (Bot)"

                // 1. Mensaje de texto
                val textKey = messagesRef.push().key ?: java.util.UUID.randomUUID().toString()
                val textMsg = CloudMessage(
                    id = textKey,
                    text = replyText,
                    timestamp = System.currentTimeMillis(),
                    senderId = myId,
                    senderName = senderTitle,
                    rank = "Operador Bot"
                )
                messagesRef.child(textKey).setValue(textMsg)
                localDb.messageDao().insertMessage(
                    MessageEntity(
                        id = textKey,
                        chatId = chatId,
                        senderId = myId,
                        senderName = senderTitle,
                        text = replyText,
                        timestamp = textMsg.timestamp
                    )
                )

                // 2. Si hay foto solicitada, enviarla inmediatamente en Base64
                if (!replyMedia.isNullOrEmpty()) {
                    val mediaKey = messagesRef.push().key ?: java.util.UUID.randomUUID().toString()
                    val mediaPayload = if (replyMedia.startsWith("data:") || replyMedia.startsWith("http")) {
                        "[Media] $replyMedia"
                    } else {
                        "[Media] data:image/jpeg;base64,$replyMedia"
                    }
                    val mediaMsg = CloudMessage(
                        id = mediaKey,
                        text = mediaPayload,
                        timestamp = System.currentTimeMillis() + 1,
                        senderId = myId,
                        senderName = senderTitle,
                        rank = "Operador Bot"
                    )
                    messagesRef.child(mediaKey).setValue(mediaMsg)
                    localDb.messageDao().insertMessage(
                        MessageEntity(
                            id = mediaKey,
                            chatId = chatId,
                            senderId = myId,
                            senderName = senderTitle,
                            text = mediaPayload,
                            timestamp = mediaMsg.timestamp
                        )
                    )
                    NeoRelayClient.sendMessage(context, chatId, cleanSender, mediaMsg)
                }

                // Difusión por Mesh Local UDP y Relay Soberano
                NeoLocalNodeServer.broadcastUdpMessage(cleanSender, textMsg, chatId)
                NeoRelayClient.sendMessage(context, chatId, cleanSender, textMsg)
            } catch (ignored: Exception) {}
        }
    }

    fun getDefaultCatalog(): List<BotProduct> {
        return listOf(
            BotProduct("IMPRESIONES B/N", "1"),
            BotProduct("IMPRESIONES A COLOR LETRAS", "2"),
            BotProduct("IMPRESIONES A COLOR IMÁGENES GDES", "5"),
            BotProduct("CARGA DE BATERIA COCHE O MOTO", "50"),
            BotProduct("ACEITE NUTRIOLI COMESTIBLE PURO DE SOYA 400 ML", "24"),
            BotProduct("ACEITE NUTRIOLI COMESTIBLE PURO DE SOYA 850 ML", "44"),
            BotProduct("AGUA CIEL PURIFICADA BOTTLE 1 LTS NR", "16"),
            BotProduct("AGUA CRISTAL PURIFICADA 20 LTS", "50"),
            BotProduct("AZUCAR KILO", "22"),
            BotProduct("BARCEL CHIPS FUEGO 170 GR", "55"),
            BotProduct("BARCEL TAKIS FUEGO 70 GR", "20"),
            BotProduct("BIMBO PAN BLANCO GDE 640 GR", "48"),
            BotProduct("BIMBO PAN TOSTADO 210 GR", "33"),
            BotProduct("BIMBO NITO 62 GR", "17"),
            BotProduct("CAFE DOLCA SOLUBLE 80 GR", "75"),
            BotProduct("COCA COLA 600 ML", "18"),
            BotProduct("COCA COLA 2.5 LTS", "38"),
            BotProduct("HUEVO CONO 30 PZAS", "85"),
            BotProduct("LECHE LALA ENTERA 1 LTS", "26")
        )
    }
}
