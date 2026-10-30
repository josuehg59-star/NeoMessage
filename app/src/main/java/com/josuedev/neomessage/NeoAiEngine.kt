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

/**
 * ⚡ MOTOR DE IA SOBERANA PARA INVESTIGACIÓN Y TAREAS (ESTILO META AI / CHATGPT)
 * - 100% Gratuito, Cero publicidad, Cero costo por tokens.
 * - Modo 1: Enciclopedia de conocimiento universal abierta (Wikipedia Knowledge Engine).
 * - Modo 2: Gemini 1.5/2.0 Flash gratuito (Google AI Studio - 1,500 consultas/día gratis).
 */
object NeoAiEngine {
    private const val TAG = "NEO_AI"
    private const val PREFS_NAME = "neo_ai_prefs"
    private const val KEY_GEMINI_KEY = "gemini_api_key"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

        val geminiKey = getGeminiKey(context)

        // 1. Si el usuario configuró su clave gratuita de Gemini, usamos Gemini Flash
        if (geminiKey.isNotEmpty()) {
            val geminiResult = queryGemini(geminiKey, cleanPrompt)
            if (geminiResult != null && geminiResult.isNotBlank()) {
                return@withContext geminiResult
            }
        }

        // 2. Si no hay clave de Gemini o falló, usamos el motor enciclopédico libre universal
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
                partObj.put("text", "Eres Neo AI, un asistente inteligente experto en educación, ciencia, historia, tareas escolares, redacción y consejos útiles. Responde en español de forma amigable, estructurada y muy clara con viñetas y negritas cuando sea conveniente.\n\nPregunta: $prompt")
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
     * Motor de búsqueda e investigación enciclopédica mundial (Wikipedia REST en español)
     * Cero tokens, Cero publicidad, Cero costo para siempre.
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
                .replace("historia de", "")
                .replace("significado de", "")
                .trim()

            if (searchTerms.isBlank()) searchTerms = prompt.trim()

            // 1. Buscar títulos relevantes en Wikipedia
            val encodedQuery = URLEncoder.encode(searchTerms, "UTF-8")
            val searchUrl = "https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json&utf8=1"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "NeoMessageAI/1.0 (Android; Sovereign)")
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

            // 2. Obtener el extracto completo y pulido del artículo
            val summaryUrl = "https://es.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(articleTitle, "UTF-8")}"
            val summaryReq = Request.Builder()
                .url(summaryUrl)
                .header("User-Agent", "NeoMessageAI/1.0 (Android; Sovereign)")
                .build()

            val summaryRes = httpClient.newCall(summaryReq).execute()
            if (!summaryRes.isSuccessful) {
                val rawSnippet = topHit.optString("snippet", "")
                    .replace("<[^>]*>".toRegex(), "")
                return "📚 *${articleTitle}*\n\n$rawSnippet..."
            }

            val summaryJson = JSONObject(summaryRes.body?.string() ?: "")
            val title = summaryJson.optString("title", articleTitle)
            val extract = summaryJson.optString("extract", "")
            val desc = summaryJson.optString("description", "")

            val sb = StringBuilder("📚 *Investigación: $title*\n")
            if (desc.isNotBlank()) {
                sb.append("_($desc)_\n\n")
            } else {
                sb.append("\n")
            }
            sb.append(extract)
            sb.append("\n\n💡 *Tip:* Puedes hacer más preguntas sobre este tema, fechas, autores o ciencia.")
            return sb.toString()

        } catch (e: Exception) {
            Log.e(TAG, "Knowledge search error: ${e.message}")
            return fallbackKnowledge(prompt)
        }
    }

    private fun fallbackKnowledge(prompt: String): String {
        return "🧠 *Neo AI - Asistente de Investigación*\n\n" +
               "He procesado tu consulta: *\"$prompt\"*.\n\n" +
               "Puedo ayudarte a investigar cualquier tema escolar, científico, histórico o general.\n" +
               "👉 Intenta consultar por el nombre exacto del tema (ej: *\"Albert Einstein\"*, *\"Fotosíntesis\"*, *\"Revolución Mexicana\"*, *\"Sistema Solar\"*).\n\n" +
               "✨ _Opcional: Si deseas respuestas redactadas como ChatGPT para tareas largas o matemáticas, puedes vincular una clave gratuita de Google Gemini en los ajustes de Neo AI._"
    }
}
