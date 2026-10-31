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
import java.util.Calendar

data class BotProduct(
    val nombre: String = "",
    val precio: String = "",
    val imagenBase64: String = "",
    val codigo: Int = 0
)

data class BotConfig(
    val isEnabled: Boolean = false,
    val nombreNegocio: String = "miscelanea brittany",
    val telefono: String = "9612765041",
    val horario: String = "Lunes a Sábado de 8:30 AM a 7:00 PM",
    val direccion: String = "Av. 16 de septiembre 903, B.S., Tuxtla Gutiérrez, Chiapas",
    val mensajeBienvenida: String = "",
    val catalogo: List<BotProduct> = emptyList()
)

object NeoBotManager {
    private const val PREF_NAME = "neobot_prefs"
    private const val KEY_CONFIG = "bot_config_json"

    // Guardar último mensaje procesado con timestamp para evitar eco y duplicados
    private val lastProcessedMsg = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isBotEnabled(context: Context): Boolean {
        return try {
            val sp = context.getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
            sp.getBoolean("bot_sales_active", false)
        } catch (_: Exception) { false }
    }

    fun setBotEnabled(context: Context, enabled: Boolean) {
        try {
            val sp = context.getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
            sp.edit().putBoolean("bot_sales_active", enabled).apply()
            getPrefs(context).edit().putBoolean("bot_active_switch", enabled).apply()
            
            val userPhone = sp.getString("user_phone", "") ?: ""
            val cfg = loadConfig(context).copy(
                isEnabled = enabled,
                telefono = if (userPhone.isNotEmpty()) userPhone else loadConfig(context).telefono
            )
            saveConfig(context, cfg)

            // Sincronizar estado en Firebase de inmediato para el número real del usuario
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val myClean = NeoUtils.sanitizeId(userPhone).takeLast(10)
                    val cfgClean = NeoUtils.sanitizeId(cfg.telefono).takeLast(10)
                    val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                    
                    if (myClean.isNotEmpty()) {
                        db.getReference("bot_configs").child(myClean).child("isEnabled").setValue(enabled)
                        db.getReference("users").child(myClean).child("bot_sales_active").setValue(enabled)
                    }
                    if (cfgClean.isNotEmpty() && cfgClean != myClean) {
                        db.getReference("bot_configs").child(cfgClean).child("isEnabled").setValue(enabled)
                        db.getReference("users").child(cfgClean).child("bot_sales_active").setValue(enabled)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun loadConfig(context: Context): BotConfig {
        val sp = getPrefs(context)
        val jsonStr = sp.getString(KEY_CONFIG, null)
        val defaultCatalog = getDefaultCatalog()

        val neoPrefs = context.getSharedPreferences("NeoPrefs", Context.MODE_PRIVATE)
        val myPhone = neoPrefs.getString("user_phone", "") ?: ""
        val myName = neoPrefs.getString("user_name", "") ?: ""

        if (jsonStr.isNullOrEmpty()) {
            val defaultConfig = BotConfig(
                isEnabled = false,
                nombreNegocio = if (myName.isNotEmpty()) myName else "Mi Negocio",
                telefono = myPhone,
                horario = "Lunes a Sábado de 8:30 AM a 7:00 PM",
                direccion = "Tuxtla Gutiérrez, Chiapas",
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
                        imagenBase64 = p.optString("imagenBase64", ""),
                        codigo = p.optInt("codigo", i + 1)
                    )
                )
            }

            val finalCatalog = if (catList.isEmpty()) defaultCatalog else catList

            val currentPhone = if (myPhone.isNotEmpty()) myPhone else obj.optString("telefono", "")
            BotConfig(
                isEnabled = obj.optBoolean("isEnabled", false),
                nombreNegocio = obj.optString("nombreNegocio", if (myName.isNotEmpty()) myName else "Mi Negocio"),
                telefono = currentPhone,
                horario = obj.optString("horario", "Lunes a Sábado de 8:30 AM a 7:00 PM").ifEmpty { "Lunes a Sábado de 8:30 AM a 7:00 PM" },
                direccion = obj.optString("direccion", "Tuxtla Gutiérrez, Chiapas").ifEmpty { "Tuxtla Gutiérrez, Chiapas" },
                mensajeBienvenida = obj.optString("mensajeBienvenida", ""),
                catalogo = finalCatalog
            )
        } catch (e: Exception) {
            BotConfig(isEnabled = false, catalogo = defaultCatalog)
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
            config.catalogo.forEachIndexed { idx, it ->
                val p = JSONObject()
                p.put("nombre", it.nombre)
                p.put("precio", it.precio)
                p.put("imagenBase64", it.imagenBase64)
                p.put("codigo", if (it.codigo > 0) it.codigo else (idx + 1))
                catArray.put(p)
            }
            obj.put("catalogo", catArray)

            getPrefs(context).edit()
                .putString(KEY_CONFIG, obj.toString())
                .putBoolean("bot_active_switch", config.isEnabled)
                .apply()

            // Sincronizar en tiempo real con Firebase RTDB para que el guardián 24/7 lo reconozca
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val cleanPhone = NeoUtils.sanitizeId(config.telefono).takeLast(10)
                    if (cleanPhone.isNotEmpty()) {
                        val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                        val map = HashMap<String, Any>()
                        map["isEnabled"] = config.isEnabled
                        map["nombreNegocio"] = config.nombreNegocio
                        map["telefono"] = cleanPhone
                        map["horario"] = config.horario
                        map["direccion"] = config.direccion
                        map["mensajeBienvenida"] = config.mensajeBienvenida

                        val catList = config.catalogo.mapIndexed { idx, p ->
                            mapOf(
                                "codigo" to if (p.codigo > 0) p.codigo else (idx + 1),
                                "nombre" to p.nombre,
                                "precio" to p.precio,
                                "imagenBase64" to p.imagenBase64
                            )
                        }
                        map["catalogo"] = catList

                        db.getReference("bot_configs").child(cleanPhone).setValue(map)
                        db.getReference("users").child(cleanPhone).child("bot_sales_active").setValue(config.isEnabled)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NEO_BOT", "Error syncing bot config to Firebase: ${e.message}")
                }
            }
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

    private fun getGreetingPrefix(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..11 -> "¡Buenos días! ☀️"
            hour in 12..18 -> "¡Buenas tardes! 🌤️"
            else -> "¡Buenas noches! 🌙"
        }
    }

    // Procesa el mensaje entrante y genera respuesta de texto y opcionalmente imagen
    fun processMessageWithMedia(context: Context, incomingText: String, chatId: String = "default_cart"): Pair<String, String?> {
        val cfg = loadConfig(context)
        val text = cleanText(incomingText)
        val negocio = cfg.nombreNegocio.ifEmpty { "miscelanea brittany" }
        val rawCatalog = if (cfg.catalogo.isEmpty()) getDefaultCatalog() else cfg.catalogo

        // Asegurar que cada producto tenga su código visible
        val catalog = rawCatalog.mapIndexed { idx, prod ->
            if (prod.codigo > 0) prod else prod.copy(codigo = idx + 1)
        }

        // ==================== 0. FOTO DE PRODUCTO ====================
        if (text.startsWith("foto ") || text.startsWith("ver foto ") || text.startsWith("imagen ") ||
            text.startsWith("fotos ") || text.startsWith("foto de ") || text.startsWith("foto del ")) {
            val prodQuery = text.replace("ver foto ", "")
                .replace("foto de ", "")
                .replace("foto del ", "")
                .replace("foto ", "")
                .replace("fotos ", "")
                .replace("imagen ", "")
                .trim()

            if (prodQuery.isNotEmpty()) {
                val codeNum = prodQuery.toIntOrNull()
                val matched = if (codeNum != null) {
                    catalog.firstOrNull { it.codigo == codeNum }
                } else {
                    catalog.firstOrNull { cleanText(it.nombre).contains(prodQuery) }
                        ?: catalog.firstOrNull { prodQuery.contains(cleanText(it.nombre)) }
                }

                if (matched != null) {
                    if (matched.imagenBase64.isNotEmpty()) {
                        return "📸 Aquí tienes la foto de *[${matched.codigo}] ${matched.nombre}* ($${matched.precio} MXN):" to matched.imagenBase64
                    } else {
                        return "ℹ️ El producto *[${matched.codigo}] ${matched.nombre}* ($${matched.precio} MXN) no tiene foto asignada en el catálogo.\n(Escribe *${matched.codigo}* para agregarlo a tu carrito)." to null
                    }
                } else {
                    return "🔍 No encontré fotos para \"$prodQuery\".\nEscribe *Catalogo* para ver los productos disponibles." to null
                }
            }
        }

        // ==================== 🛒 CORTE DE CAJA ====================
        if (text == "corte de caja" || text == "corte" || text == "corte caja" || text == "ventas de hoy" || text == "caja") {
            return NeoCommerceManager.formatCorteDeCajaReport(context, negocio) to null
        }

        // ==================== 🛒 CARRITO ACTUAL ====================
        if (text == "carrito" || text == "ver carrito" || text == "mi carrito" || text == "total" || text == "cuenta" || text == "mi pedido" || text == "compras") {
            return NeoCommerceManager.formatCartReceipt(context, chatId, negocio) to null
        }

        if (text == "vaciar carrito" || text == "borrar carrito" || text == "limpiar carrito" || text == "cancelar pedido") {
            NeoCommerceManager.clearCart(context, chatId)
            return "🗑️ Tu carrito de compras ha sido vaciado correctamente.\nEscribe *Catalogo* para ver los productos y volver a agregar." to null
        }

        // ==================== 🧾 CONFIRMAR PEDIDO / TICKET ====================
        if (text == "confirmar" || text == "confirmar pedido" || text == "confirmar compra" || text == "comprar" || text == "pagar" || text == "pedir ya" || text == "finalizar") {
            val (ok, res) = NeoCommerceManager.checkoutCart(context, chatId, "Cliente", "Cliente Chat", negocio)
            return res to null
        }

        // ==================== 🔢 PARSER INTELIGENTE DE PEDIDOS POR CÓDIGO Y CANTIDAD ====================
        // Casos que maneja:
        // - "2 del 276"
        // - "del 1 requiero 2" o "del 3 requierp 1"
        // - "1 del 276 y 2 del 725"
        // - "276 2" (código y cantidad)
        // - "276" (sólo código -> cantidad 1)
        // - "agregar 26 2" o "pedir 725"
        val orderItems = parseOrderItems(text)
        if (orderItems.isNotEmpty()) {
            val sb = java.lang.StringBuilder("🛒 *¡Agregado al Carrito!*\n\n")
            var addedAny = false
            for ((code, qty) in orderItems) {
                val matched = catalog.firstOrNull { it.codigo == code }
                if (matched != null) {
                    NeoCommerceManager.addToCart(context, chatId, matched, qty)
                    val unitPrice = matched.precio.replace("[^0-9.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                    val sub = unitPrice * qty
                    sb.append("✅ ${qty}x *[${matched.codigo}] ${matched.nombre}* ➔ *$${"%.2f".format(sub)} MXN*\n")
                    addedAny = true
                }
            }
            if (addedAny) {
                val currentTotal = NeoCommerceManager.getCartTotal(context, chatId)
                sb.append("───────────────────────\n")
                sb.append("💰 *Total actual en carrito:* $${"%.2f".format(currentTotal)} MXN\n\n")
                sb.append("👉 Escribe *carrito* para ver tus compras actuales.\n")
                sb.append("👉 Escribe *confirmar pedido* para generar tu ticket y total.")
                return sb.toString() to null
            }
        }

        // Si escribió "agregar [nombre producto]"
        if (text.startsWith("agregar ") || text.startsWith("agrega ") || text.startsWith("pedir ") || text.startsWith("comprar ")) {
            val q = text.replace("agregar ", "").replace("agrega ", "").replace("pedir ", "").replace("comprar ", "").trim()
            val matched = catalog.firstOrNull { cleanText(it.nombre).contains(q) } ?: catalog.firstOrNull { q.contains(cleanText(it.nombre)) }
            if (matched != null) {
                val item = NeoCommerceManager.addToCart(context, chatId, matched, 1)
                val currentTotal = NeoCommerceManager.getCartTotal(context, chatId)
                val sb = java.lang.StringBuilder("🛒 *¡Agregado al Carrito!*\n\n")
                sb.append("✅ *[${matched.codigo}] ${matched.nombre}*\n")
                sb.append("💵 Cantidad: ${item.cantidad} | Precio: $${matched.precio} MXN\n")
                sb.append("───────────────────────\n")
                sb.append("💰 *Total actual en carrito:* $${"%.2f".format(currentTotal)} MXN\n\n")
                sb.append("👉 Escribe *carrito* para ver tu cuenta completa.\n")
                sb.append("👉 Escribe *confirmar pedido* para confirmar tu orden.")
                return sb.toString() to (if (matched.imagenBase64.isNotEmpty()) matched.imagenBase64 else null)
            }
        }

        // ==================== 1. CATÁLOGO COMPLETO ====================
        if (text == "catalogo" || text == "catalago" || text == "catallogo" || text == "productos" || 
            text == "articulos" || text == "lista" || text == "precios" || text == "que venden" || text == "menu") {
            
            val sb = java.lang.StringBuilder("🔍 *CATÁLOGO DE PRODUCTOS (${catalog.size}):*\n")
            sb.append("🏪 _${negocio}_\n\n")
            
            val sample = catalog.take(20)
            sample.forEach {
                val hasImg = if (it.imagenBase64.isNotEmpty()) " 📸" else ""
                sb.append("*[${it.codigo}]* ${it.nombre} ➔ *$${it.precio} MXN*$hasImg\n")
            }
            if (catalog.size > 20) {
                sb.append("\n_...y ${catalog.size - 20} productos más disponibles._\n")
            }
            sb.append("\n🛒 *¿Cómo agregarlos a tu carrito?*\n")
            sb.append("• Escribe el número del producto (ej: *${sample.firstOrNull()?.codigo ?: 1}*).\n")
            sb.append("• O directo con cantidad: (ej: *2 del ${sample.firstOrNull()?.codigo ?: 1}*).\n")
            sb.append("• O varios juntos: (ej: *1 del 276 y 2 del 725*).\n")
            sb.append("• Para finalizar escribe: *confirmar pedido*")
            return sb.toString() to null
        }

        // ==================== 2. UBICACIÓN Y HORARIOS ====================
        if (text == "ubicacion" || text == "ubicasion" || text.contains("ubic") || 
            text.contains("direc") || text.contains("donde") || text.contains("mapa") || 
            text.contains("lugar") || text.contains("como llegar")) {
            
            val encodedDir = try { URLEncoder.encode(cfg.direccion, "UTF-8") } catch (_: Exception) { "" }
            val mapsUrl = if (encodedDir.isNotEmpty()) "https://maps.google.com/?q=$encodedDir" else ""

            val resp = java.lang.StringBuilder("📍 *Ubicación de $negocio:*\n${cfg.direccion}\n\n")
            resp.append("🕒 *Horario:*\n${cfg.horario}\n\n")
            resp.append("📞 *Teléfono de contacto:*\n${cfg.telefono}")
            if (mapsUrl.isNotEmpty()) {
                resp.append("\n\n🗺️ *Ver en Google Maps:*\n$mapsUrl")
            }
            return resp.toString() to null
        }

        // ==================== 3. HORARIO ====================
        if (text.contains("horari") || text.contains("hora") || text.contains("abre") || 
            text.contains("cierra") || text.contains("abierto")) {
            return "🕒 *Horario de Atención en $negocio:*\n${cfg.horario}\n\n📍 Ubicación: ${cfg.direccion}" to null
        }

        // ==================== 4. ASESOR HUMANO ====================
        if (text.contains("asesor") || text.contains("human") || text.contains("encargad") || 
            text.contains("person") || text.contains("ayuda")) {
            return "👤 *Atención Personalizada*\nUn momento por favor, le estoy avisando al encargado de *$negocio* para que te atienda personalmente." to null
        }

        // ==================== 5. SALUDO Y MENÚ PRINCIPAL ====================
        val isGreeting = text == "hola" || text == "ola" || text == "buenas" || text == "buenos dias" || 
                         text == "buenas tardes" || text == "buenas noches" || text == "inicio" || 
                         text == "start" || text == "que tal" || text == "saludos" ||
                         text.startsWith("hola ") || text.startsWith("ola ") || text.startsWith("buenas ")
        if (isGreeting) {
            val greeting = getGreetingPrefix()
            val welcome = "$greeting Gracias por comunicarte con *$negocio* 🤖.\n\n" +
                          "Escribe:\n" +
                          "📦 *Catalogo* ➔ Ver productos y precios\n" +
                          "📍 *Ubicacion* ➔ Horarios y dirección\n" +
                          "🛒 *Carrito* ➔ Ver tus compras actuales\n\n" +
                          "🔍 _O escribe lo que buscas (ej: \"coca\", \"aceite\", \"sabritas\") para buscarlo al instante._"
            return welcome to null
        }

        // ==================== 6. BÚSQUEDA DIRECTA POR PALABRA CLAVE ====================
        val ignoredWords = setOf(
            "tienen", "precio", "precios", "cuanto", "cuesta", "cuestan", "venden", 
            "vende", "hay", "para", "quiero", "dame", "me", "das", "un", "una", 
            "de", "el", "la", "los", "las", "por", "favor", "requiero", "saludos"
        )
        val searchTokens = text.split(" ").filter { it.length >= 2 && !ignoredWords.contains(it) }

        val matches = catalog.filter { prod ->
            val pNorm = cleanText(prod.nombre)
            searchTokens.any { token -> pNorm.contains(token) } || pNorm.contains(text) || text.contains(pNorm)
        }

        if (matches.isNotEmpty()) {
            val sb = java.lang.StringBuilder("🔍 *PRODUCTOS ENCONTRADOS (${matches.size}):*\n")
            sb.append("🏪 _${negocio}_\n\n")

            matches.take(10).forEach {
                sb.append("*[${it.codigo}]* ${it.nombre} ➔ *$${it.precio} MXN*\n")
            }
            if (matches.size > 10) {
                sb.append("\n_Hay ${matches.size - 10} artículos más coincidentes._\n")
            }

            val exampleCode1 = matches.first().codigo
            val exampleCode2 = if (matches.size > 1) matches[1].codigo else (exampleCode1 + 1)
            sb.append("\n🛒 *¿Cómo agregarlos a tu carrito?*\n")
            sb.append("• Escribe el número del producto (ej: *$exampleCode1* o *$exampleCode2*).\n")
            sb.append("• O directo con cantidad: (ej: *2 del $exampleCode1*).\n")
            sb.append("• O varios juntos: (ej: *1 del $exampleCode1 y 2 del $exampleCode2*).")
            return sb.toString() to null
        }

        // ==================== 7. RESPUESTA POR DEFECTO ====================
        val defaultResp = "$negocio 🤖\n\n" +
               "¿En qué puedo ayudarte? Puedes escribir:\n" +
               "📦 *Catalogo* ➔ Ver productos y precios\n" +
               "📍 *Ubicacion* ➔ Horarios y dirección\n" +
               "🛒 *Carrito* ➔ Ver tus compras actuales\n" +
               "👤 *Asesor* ➔ Hablar con el encargado\n\n" +
               "🔍 _O escribe el producto que buscas (ej: \"coca\", \"aceite\", \"sabritas\")._"
        return defaultResp to null
    }

    private fun parseOrderItems(rawText: String): List<Pair<Int, Int>> {
        val text = rawText.lowercase().trim()
        val results = mutableListOf<Pair<Int, Int>>()

        try {
            // Patrón A: "1 del 276 y 2 del 725" o "2 del 276" o "2 de 276"
            val r1 = "(\\d+)\\s*(?:del?|de)\\s*(\\d+)".toRegex()
            val m1 = r1.findAll(text).toList()
            if (m1.isNotEmpty()) {
                for (m in m1) {
                    val qty = m.groupValues[1].toIntOrNull() ?: 1
                    val code = m.groupValues[2].toIntOrNull() ?: 0
                    if (code > 0) results.add(code to qty)
                }
                return results
            }

            // Patrón B: "del 1 requiero 2" o "del 3 requierp 1" o "del 1 quiero 2" o "del 1 pido 2"
            val r2 = "(?:del?|de)\\s*(\\d+)\\s*(?:requier[op]|quier[op]|pid[op]|necesit[op]|dame|ponme|agreg[ao])\\s*(\\d+)".toRegex()
            val m2 = r2.findAll(text).toList()
            if (m2.isNotEmpty()) {
                for (m in m2) {
                    val code = m.groupValues[1].toIntOrNull() ?: 0
                    val qty = m.groupValues[2].toIntOrNull() ?: 1
                    if (code > 0) results.add(code to qty)
                }
                return results
            }

            // Patrón C: "del 26" o "del 26 quiero" (sin cantidad explícita -> cantidad 1)
            val r3 = "^(?:del?|de)\\s*(\\d+)".toRegex()
            val m3 = r3.find(text)
            if (m3 != null) {
                val code = m3.groupValues[1].toIntOrNull() ?: 0
                if (code > 0) return listOf(code to 1)
            }

            // Patrón D: "276 2" (código y luego cantidad) o "agregar 276 2"
            val r4 = "^(?:agregar\\s+|pedir\\s+|comprar\\s+)?(\\d+)\\s+(\\d+)$".toRegex()
            val m4 = r4.find(text)
            if (m4 != null) {
                val code = m4.groupValues[1].toIntOrNull() ?: 0
                val qty = m4.groupValues[2].toIntOrNull() ?: 1
                if (code > 0) return listOf(code to qty)
            }

            // Patrón E: Sólo un número: "276" o "agregar 276" -> cantidad 1
            val r5 = "^(?:agregar\\s+|pedir\\s+|comprar\\s+)?(\\d+)$".toRegex()
            val m5 = r5.find(text)
            if (m5 != null) {
                val code = m5.groupValues[1].toIntOrNull() ?: 0
                if (code > 0) return listOf(code to 1)
            }
        } catch (_: Exception) {}

        return results
    }

    fun processMessage(context: Context, incomingText: String, chatId: String = "default_cart"): String {
        return processMessageWithMedia(context, incomingText, chatId).first
    }

    fun isStoreNumber(phone: String): Boolean {
        val clean = NeoUtils.sanitizeId(phone).takeLast(10)
        return clean == "9612765041" || clean == "9614524879"
    }

    // ==================== AUTO-RESPUESTA INMEDIATA DESDE EL LADO CLIENTE ====================
    fun dispatchClientBotReply(
        context: Context,
        chatId: String,
        storePhone: String,
        userMsg: CloudMessage
    ) {
        val cleanStore = NeoUtils.sanitizeId(storePhone)
        val text = userMsg.text.trim()
        if (text.startsWith("🤖") || text.startsWith("🔍 *CATÁLOGO") || text.startsWith("🧾 *TICKET") || text.startsWith("🛒 *¡Agregado")) return

        val replyKey = "bot_${userMsg.id}"
        val (replyText, replyMedia) = processMessageWithMedia(context, text, chatId)
        val cfg = loadConfig(context)
        val storeName = cfg.nombreNegocio.ifEmpty { "miscelanea brittany" }

        val botMsg = CloudMessage(
            id = replyKey,
            text = replyText,
            timestamp = System.currentTimeMillis() + 200,
            senderId = cleanStore,
            senderName = "$storeName 🤖",
            rank = "Operador Bot",
            replyToId = userMsg.id,
            replyToText = userMsg.text.take(80),
            replyToSender = userMsg.senderName
        )

        CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(400)
            try {
                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                db.getReference("chats").child(chatId).child("messages").child(replyKey).setValue(botMsg)
            } catch (e: Exception) {
                android.util.Log.e("NEO_BOT", "Error dispatching client bot reply: ${e.message}")
            }
        }
    }

    // ==================== 🚀 AUTO-RESPUESTA DEL BOT DESDE EL TELÉFONO DEL DUEÑO ====================
    fun dispatchAutoReplyIfNeeded(
        context: Context,
        chatId: String,
        senderId: String,
        myId: String,
        incomingText: String,
        msgTimestamp: Long,
        msgId: String = ""
    ) {
        val cleanSender = NeoUtils.sanitizeId(senderId)
        val cleanMe = NeoUtils.sanitizeId(myId)

        // 1. Evitar auto-responder a uno mismo (el dueño no se responde a sí mismo)
        if (cleanSender.takeLast(10) == cleanMe.takeLast(10) && cleanMe.isNotEmpty()) return
        if (cleanSender.isEmpty()) return

        // 2. Solo responde si el teléfono del dueño tiene encendido el interruptor de Ventas
        if (!isBotEnabled(context)) return

        // 3. Anti-loop: no responder a mensajes generados por un bot ni comandos internos
        if (incomingText.startsWith("🤖") || incomingText.contains("(Bot)") || incomingText.startsWith("🧾") || incomingText.startsWith("📦") || incomingText.startsWith("[NEO_") || incomingText.startsWith("[SYSTEM]") || msgId.startsWith("bot_")) return
        val now = System.currentTimeMillis()

        // 5. Anti-repetición inmediata del mismo mensaje (4 segundos de debounce por cliente)
        val key = "$chatId-$cleanSender"
        val last = lastProcessedMsg[key]
        if (last != null && last.first == incomingText && now - last.second < 4000) return
        lastProcessedMsg[key] = incomingText to now

        // 6. Procesa el mensaje con el catálogo del negocio
        val (replyText, replyMedia) = processMessageWithMedia(context, incomingText, chatId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                val messagesRef = db.getReference("chats").child(chatId).child("messages")
                val localDb = AppDatabase.getDatabase(context)
                val cfg = loadConfig(context)
                val senderTitle = "${cfg.nombreNegocio} 🤖"

                // Deterministic reply ID para sincronizar con guardián 24/7 y evitar duplicados
                val textKey = if (msgId.isNotEmpty()) "bot_$msgId" else "bot_${System.currentTimeMillis()}" 
                val textMsg = CloudMessage(
                    id = textKey,
                    text = replyText,
                    timestamp = System.currentTimeMillis(),
                    senderId = myId,
                    senderName = senderTitle,
                    rank = "Operador Bot"
                )
                android.util.Log.i("NEO_BOT", "🚀 Despachando respuesta a $chatId: $replyText")
                messagesRef.child(textKey).setValue(textMsg).addOnSuccessListener {
                    android.util.Log.i("NEO_BOT", "✅ [BOT ENVIADO EXITOSAMENTE] a $chatId: $replyText")
                }.addOnFailureListener { err ->
                    android.util.Log.e("NEO_BOT", "❌ [ERROR AL ENVIAR BOT]: ${err.message}")
                }
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

                // Si hay foto solicitada, enviarla inmediatamente
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
            BotProduct("IMPRESIONES B/N", "1", "", 1),
            BotProduct("IMPRESIONES A COLOR LETRAS", "2", "", 2),
            BotProduct("IMPRESIONES A COLOR IMÁGENES GDES", "5", "", 3),
            BotProduct("CARGA DE BATERIA COCHE O MOTO", "50", "", 4),
            BotProduct("ACEITE NUTRIOLI COMESTIBLE PURO DE SOYA 400 ML", "24", "", 5),
            BotProduct("ACEITE NUTRIOLI COMESTIBLE PURO DE SOYA 850 ML", "44", "", 6),
            BotProduct("AGUA CIEL PURIFICADA BOTTLE 1 LTS NR", "16", "", 7),
            BotProduct("AGUA CRISTAL PURIFICADA 20 LTS", "50", "", 8),
            BotProduct("AZUCAR KILO", "22", "", 9),
            BotProduct("COCA COLA 600 ML", "18", "", 10),
            BotProduct("COCA COLA 2.5 LTS", "38", "", 11),
            BotProduct("HUEVO CONO 30 PZAS", "85", "", 12),
            BotProduct("LECHE LALA ENTERA 1 LTS", "26", "", 13),
            BotProduct("ALIMENTO PEDIGREE RAZA PEQUEÑA RES Y VEGETALES 420 GR", "30.00", "", 26),
            BotProduct("BARCEL BIG MIX QUESO 185 GR", "40.00", "", 45),
            BotProduct("BARCEL BIG MIX QUESO 75 GR", "22.00", "", 46),
            BotProduct("BARCEL POP QUESO CHEDDAR 45 GR", "17.00", "", 67),
            BotProduct("BARCEL TAKIS PIX QUESO 65 GR", "16.00", "", 79),
            BotProduct("BARCEL WAPAS QUESO 52 GR", "17.00", "", 86),
            BotProduct("ELASTICO CHICO POR METRO $ 6 PESOS", "70.00", "", 276),
            BotProduct("BIMBO GALLETAS GABI BESOS DE NUEZ 60 GR", "10.00", "", 725)
        )
    }
}
