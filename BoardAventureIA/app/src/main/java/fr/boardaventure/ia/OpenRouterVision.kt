package fr.boardaventure.ia
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GameAnalysis(
    val recognized: Boolean, val confidence: Double, val turns: String?, val bonusTurns: String?,
    val coins: String?, val gems: String?, val stars: String?, val multiplier: String?,
    val screen: String, val recommendation: String, val action: String,
    val x: Int?, val y: Int?, val reason: String
)
class OpenRouterVision {
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    suspend fun analyze(apiKey: String, model: String, bitmap: Bitmap): GameAnalysis = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Clé OpenRouter manquante." }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        val image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        val systemPrompt = "Tu es l'agent de vision de Board Aventure (package com.moonactive.cmboard). " +
            "Analyse uniquement ce qui est visible, sans inventer. Réponds en JSON strict avec recognized(boolean), " +
            "confidence(number 0..1), screen(string), turns(string|null), bonus_turns(string|null), coins(string|null), " +
            "gems(string|null), stars(string|null), multiplier(string|null), recommendation(string), " +
            "action('none'|'tap'), x(integer|null), y(integer|null), reason(string). " +
            "Si le jeu n'est pas clairement reconnu: recognized=false et action=none. Valeur illisible=null. " +
            "Tours, bonus, pièces et gemmes peuvent monter ou baisser ; étoiles seulement augmenter. " +
            "Ne propose jamais d'achat en argent réel. Ignore la date et l'heure."
        val userPrompt = "Règles: réserve de 5 tours, limite de lancers=all, arrêt=both. " +
            "Gemmes gagnées utilisables si cela améliore clairement la progression. " +
            "Ne pas arrêter seulement parce qu'une progression liée est terminée. Retourne uniquement le JSON demandé."
        val content = org.json.JSONArray()
            .put(JSONObject().put("type", "text").put("text", userPrompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image")))
        val payload = JSONObject().put("model", model).put("temperature", 0.1).put("max_tokens", 900)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", content)))
        val req = Request.Builder().url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey").header("Content-Type", "application/json")
            .header("X-Title", "Board Aventure IA")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("OpenRouter HTTP ${res.code}: ${body.take(350)}")
            val raw = JSONObject(body).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            val json = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val j = JSONObject(json)
            fun str(k: String) = if (j.isNull(k) || !j.has(k)) null else j.optString(k).takeIf { it.isNotBlank() }
            fun int(k: String) = if (j.isNull(k) || !j.has(k)) null else j.optInt(k)
            GameAnalysis(j.optBoolean("recognized", false), j.optDouble("confidence", 0.0),
                str("turns"), str("bonus_turns"), str("coins"), str("gems"), str("stars"), str("multiplier"),
                j.optString("screen", "inconnu"), j.optString("recommendation", ""), j.optString("action", "none"),
                int("x"), int("y"), j.optString("reason", ""))
        }
    }
}
