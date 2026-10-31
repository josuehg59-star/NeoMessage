package com.josuedev.neomessage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * ⚡ MOTOR DE IA SOBERANA PARA INVESTIGACIÓN Y TAREAS (ESTILO META AI / CHATGPT)
 * - 100% Gratuito, Cero publicidad, Cero costo por tokens.
 * - Modo 1: Enciclopedia de conocimiento universal abierta y exhaustiva (Wikipedia Full Extracts Engine).
 * - Modo 2: Recetario gastronómico inteligente paso a paso con ingredientes y tiempos (NeoRecipeBook).
 * - Modo 3: Gemini 1.5/2.0 Flash gratuito (Google AI Studio - 1,500 consultas/día gratis).
 */
object NeoAiEngine {
    private const val TAG = "NEO_AI"
    private const val PREFS_NAME = "neo_ai_prefs"
    private const val KEY_GEMINI_KEY = "gemini_api_key"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun getGeminiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GEMINI_KEY, "") ?: ""
    }

    fun setGeminiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GEMINI_KEY, key.trim())
            .apply()
    }

    suspend fun queryAi(context: Context, userPrompt: String): String = withContext(Dispatchers.IO) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isBlank()) return@withContext "Por favor escribe una consulta o tema para investigar."

        // 1. Detectar si el usuario está pidiendo una receta de cocina
        if (NeoRecipeBook.isRecipeQuery(cleanPrompt)) {
            val geminiKey = getGeminiKey(context)
            if (geminiKey.isNotEmpty()) {
                val geminiRecipe = queryGemini(geminiKey, "Dame la receta completa y auténtica de: $cleanPrompt. Incluye: 1. Tiempo y porciones, 2. Lista completa de ingredientes con cantidades exactas, 3. Preparación paso a paso numerada y muy detallada, 4. Consejos y secretos del chef para el mejor sabor.")
                if (!geminiRecipe.isNullOrBlank()) {
                    return@withContext geminiRecipe
                }
            }
            // Si no hay Gemini o falló, usamos el recetario gastronómico soberano
            val recipe = NeoRecipeBook.findRecipe(cleanPrompt)
            return@withContext NeoRecipeBook.formatRecipeResponse(recipe)
        }

        val geminiKey = getGeminiKey(context)

        // 2. Si el usuario configuró su clave gratuita de Gemini, usamos Gemini Flash
        if (geminiKey.isNotEmpty()) {
            val geminiResult = queryGemini(geminiKey, cleanPrompt)
            if (geminiResult != null && geminiResult.isNotBlank()) {
                return@withContext geminiResult
            }
        }

        // 3. Si no hay clave de Gemini o falló, usamos el motor enciclopédico de investigación COMPLETA
        return@withContext queryUniversalKnowledge(cleanPrompt)
    }

    /**
     * Consulta el modelo Gemini 1.5 Flash de Google AI Studio (Capa gratuita oficial)
     */
    private fun queryGemini(apiKey: String, prompt: String): String? {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val jsonPayload = JSONObject().apply {
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray()
                val partObj = JSONObject()
                partObj.put("text", "Eres Neo AI, un asistente de inteligencia artificial avanzado para educación, tareas escolares, ciencia, historia, redacción y resolución de dudas. Responde en español de forma completa, exhaustiva, muy clara y estructurada con viñetas y títulos en negritas cuando sea conveniente.\n\nPregunta: $prompt")
                parts.put(partObj)
                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)
            }

            val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(body).build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val resStr = response.body?.string() ?: return null
            val resObj = JSONObject(resStr)
            val candidates = resObj.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
            val partsArr = content.optJSONArray("parts") ?: return null
            if (partsArr.length() == 0) return null
            partsArr.getJSONObject(0).optString("text", "")
        } catch (e: Exception) {
            Log.e(TAG, "Gemini error: ${e.message}")
            null
        }
    }

    /**
     * Motor de investigación enciclopédica COMPLETA y exhaustiva
     * Extrae el contenido completo, antecedentes, desarrollo y conclusiones.
     */
    private fun queryUniversalKnowledge(prompt: String): String {
        try {
            // Limpiar palabras comunes de pregunta
            var searchTerms = prompt.lowercase()
                .replace("¿", "")
                .replace("?", "")
                .replace("que es", "")
                .replace("qué es", "")
                .replace("quien fue", "")
                .replace("quién fue", "")
                .replace("quien era", "")
                .replace("quién era", "")
                .replace("como funciona", "")
                .replace("cómo funciona", "")
                .replace("explicame", "")
                .replace("explícame", "")
                .replace("dime sobre", "")
                .replace("investiga", "")
                .replace("investigacion de", "")
                .replace("investigación de", "")
                .replace("historia de", "")
                .replace("significado de", "")
                .trim()

            if (searchTerms.isBlank()) searchTerms = prompt.trim()

            // 1. Buscar títulos relevantes en Wikipedia
            val encodedQuery = URLEncoder.encode(searchTerms, "UTF-8")
            val searchUrl = "https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json&utf8=1"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "NeoMessageAI/2.0 (Android; Sovereign)")
                .build()

            val searchRes = httpClient.newCall(searchReq).execute()
            if (!searchRes.isSuccessful) return fallbackKnowledge(prompt)

            val searchJson = JSONObject(searchRes.body?.string() ?: "")
            val queryObj = searchJson.optJSONObject("query")
            val searchArr = queryObj?.optJSONArray("search")

            if (searchArr == null || searchArr.length() == 0) {
                return fallbackKnowledge(prompt)
            }

            // Tomar el mejor resultado
            val topHit = searchArr.getJSONObject(0)
            val articleTitle = topHit.getString("title")

            // 2. OBTENER EXTRACTO COMPLETO Y EXHAUSTIVO (prop=extracts&explaintext=1)
            val fullExtractUrl = "https://es.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&titles=${URLEncoder.encode(articleTitle, "UTF-8")}&format=json&utf8=1"
            val fullExtractReq = Request.Builder()
                .url(fullExtractUrl)
                .header("User-Agent", "NeoMessageAI/2.0 (Android; Sovereign)")
                .build()

            val fullExtractRes = httpClient.newCall(fullExtractReq).execute()
            if (!fullExtractRes.isSuccessful) {
                return fallbackKnowledge(prompt)
            }

            val fullJson = JSONObject(fullExtractRes.body?.string() ?: "")
            val pagesObj = fullJson.optJSONObject("query")?.optJSONObject("pages") ?: return fallbackKnowledge(prompt)

            var rawFullText = ""
            val keys = pagesObj.keys()
            if (keys.hasNext()) {
                val pageKey = keys.next()
                rawFullText = pagesObj.getJSONObject(pageKey).optString("extract", "")
            }

            if (rawFullText.isBlank()) {
                val rawSnippet = topHit.optString("snippet", "").replace("<[^>]*>".toRegex(), "")
                return "📚 *${articleTitle}*\n\n$rawSnippet"
            }

            // 3. PROCESAR Y ESTRUCTURAR EL TEXTO COMPLETO
            val structuredContent = processFullArticle(articleTitle, rawFullText)
            return structuredContent

        } catch (e: Exception) {
            Log.e(TAG, "Knowledge search error: ${e.message}")
            return fallbackKnowledge(prompt)
        }
    }

    /**
     * Procesa y limpia el extracto completo en una investigación estructurada y completa
     */
    private fun processFullArticle(title: String, rawText: String): String {
        var text = rawText

        // Descartar apéndices irrelevantes de referencias y enlaces externos
        val stopSections = listOf(
            "== Véase también ==",
            "== Referencias ==",
            "== Enlaces externos ==",
            "== Bibliografía ==",
            "== Notas =="
        )
        for (stop in stopSections) {
            if (text.contains(stop)) {
                text = text.substringBefore(stop)
            }
        }

        // Formatear encabezados de subsecciones: === Subsección === -> 🔹 **Subsección**
        text = text.replace("===\\s*([^=]+?)\\s*===".toRegex(), "\n🔹 **$1**\n")

        // Formatear encabezados principales: == Sección == -> 📌 **Sección**
        text = text.replace("==\\s*([^=]+?)\\s*==".toRegex(), "\n\n📌 **$1**\n")

        // Reducir saltos de línea excesivos
        text = text.replace("\n{3,}".toRegex(), "\n\n").trim()

        // Si es muy extenso, limitarlo inteligentemente a unos 3,200 caracteres en un corte limpio
        if (text.length > 3200) {
            val cutIndex = text.lastIndexOf('\n', 3200)
            text = if (cutIndex > 2200) {
                text.substring(0, cutIndex) + "\n\n_(Investigación completa estructurada para lectura rápida)_"
            } else {
                text.substring(0, 3200) + "...\n\n_(Investigación completa estructurada para lectura rápida)_"
            }
        }

        val sb = StringBuilder("📚 *INVESTIGACIÓN COMPLETA: ${title.uppercase()}*\n\n")
        sb.append(text)
        sb.append("\n\n💡 *Neo AI Tip:* Puedes preguntarme sobre algún personaje específico, fechas clave, antecedentes o fórmulas relacionadas.")
        return sb.toString()
    }

    private fun fallbackKnowledge(prompt: String): String {
        return "🧠 *Neo AI - Asistente de Investigación*\n\n" +
               "He recibido tu consulta: *\"$prompt\"*.\n\n" +
               "Puedo ayudarte a investigar cualquier tema escolar, científico, histórico, biológico o culinario.\n" +
               "👉 Para investigar, intenta consultar por el tema exacto (ej: *\"Fotosíntesis\"*, *\"Albert Einstein\"*, *\"Revolución Mexicana\"*, *\"Agujeros Negros\"*).\n" +
               "👉 Para recetas, escribe: *\"Receta de pozole\"*, *\"Cómo hacer arroz rojo\"*, *\"Receta de enchiladas verdes\"*, etc.\n\n" +
               "✨ _Opcional: Si deseas respuestas creativas ilimitadas como ChatGPT para redacción libre, puedes ingresar tu clave gratuita de Google Gemini en el ícono de la llave arriba a la derecha._"
    }
}
