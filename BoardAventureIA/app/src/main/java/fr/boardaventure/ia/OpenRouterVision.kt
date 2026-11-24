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
    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(50, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(apiKey: String, model: String, bitmap: Bitmap): GameAnalysis = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Clé OpenRouter manquante." }
        require(model.isNotBlank()) { "Identifiant du modèle vision manquant." }
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "Capture d’écran invalide ou déjà libérée." }
        val stream = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)) { "Compression de la capture impossible." }
        val bytes = stream.toByteArray()
        check(bytes.isNotEmpty()) { "Capture compressée vide." }
        val image = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val systemPrompt = "Tu es l'agent de vision du jeu Board Aventure (package com.moonactive.cmboard). " +
            "Analyse uniquement ce qui est visible, sans inventer. Réponds en JSON strict avec recognized(boolean), " +
            "confidence(number 0..1), screen(string), turns(string|null), bonus_turns(string|null), coins(string|null), " +
            "gems(string|null), stars(string|null), multiplier(string|null), recommendation(string), " +
            "action('none'|'spin'|'collect_free_reward'|'upgrade_village'), x(integer|null), y(integer|null), reason(string). " +
            "Si le jeu n'est pas clairement reconnu: recognized=false et action=none. Valeur illisible=null. " +
            "Tours, bonus, pièces et gemmes peuvent monter ou baisser ; étoiles seulement augmenter. " +
            "Repères visuels issus des captures réelles du téléphone en portrait (684x1536, coordonnées proportionnelles): " +
            "pièces en haut à gauche, environ x=8-36% et y=7-12%; gemmes dans la barre supérieure juste à droite des pièces, " +
            "environ x=36-60% et y=7-12%; énergie en bas au centre, x=32-68% et y=92-97%; bonus d'énergie sous cette barre, " +
            "x=38-63% et y=97-100%; multiplicateur de lancer en bas à droite près du gros bouton rouge, x=68-85% et y=84-90%; " +
            "compteur bonus visible près de la progression supérieure, x=65-78% et y=22-28%. " +
            "Les étoiles et les tours principaux peuvent être hors champ ou masqués: retourne null si leur nombre n'est pas lisible. " +
            "Un panneau flottant peut cacher la barre supérieure: ne devine jamais les chiffres cachés. " +
            "Ne propose jamais d'achat en argent réel. Ignore la date et l'heure. Ne fournis aucune coordonnée non vérifiable."
        val userPrompt = "Règles: réserve de 5 tours, limite de lancers=all, arrêt=both. " +
            "Gemmes gagnées utilisables uniquement si cela améliore clairement la progression. " +
            "Ne pas arrêter seulement parce qu'une progression liée est terminée. Retourne uniquement le JSON demandé."
        val content = org.json.JSONArray()
            .put(JSONObject().put("type", "text").put("text", userPrompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image")))
        val payload = JSONObject().put("model", model).put("temperature", 0.1).put("max_tokens", 1600)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", content)))
        val request = Request.Builder().url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey").header("Content-Type", "application/json")
            .header("X-Title", "Board Aventure IA")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("OpenRouter HTTP ${response.code}: ${body.take(350)}")
            val root = try { JSONObject(body) } catch (e: Exception) {
                error("Réponse OpenRouter non JSON : ${body.take(300)}")
            }
            root.optJSONObject("error")?.let { apiError ->
                error("OpenRouter : ${apiError.optString("message", "erreur API")} (code ${apiError.opt("code") ?: "inconnu"})")
            }
            val choices = root.optJSONArray("choices")
                ?: error("Réponse OpenRouter sans choices. Modèle demandé : $model. Réponse : ${body.take(300)}")
            if (choices.length() == 0) error("OpenRouter n'a renvoyé aucun résultat.")
            val choice = choices.optJSONObject(0) ?: error("Premier choix OpenRouter vide.")
            val message = choice.optJSONObject("message") ?: error("Message OpenRouter absent.")
            val value = message.opt("content")
            val raw = when (value) {
                is String -> value.trim()
                is org.json.JSONArray -> buildString {
                    for (i in 0 until value.length()) {
                        val part = value.optJSONObject(i)
                        if (part != null && part.opt("text") is String) append(part.optString("text"))
                    }
                }.trim()
                else -> ""
            }
            if (raw.isBlank()) {
                val finish = choice.optString("finish_reason", "inconnu")
                val nativeFinish = choice.optString("native_finish_reason", "")
                val refusalValue = message.opt("refusal")
                val refusal = if (refusalValue is String) refusalValue else ""
                val returnedModel = root.optString("model", model)
                val diagnostic = when {
                    refusal.isNotBlank() -> "Refus du modèle : ${refusal.take(180)}"
                    finish == "length" -> "Réponse coupée par la limite de tokens."
                    else -> "Le fournisseur n'a renvoyé aucun texte final."
                }
                error("Réponse IA vide. Modèle servi : $returnedModel. Fin : $finish${if (nativeFinish.isNotBlank()) " ($nativeFinish)" else ""}. $diagnostic")
            }
            val cleaned = raw.replace("```json", "").replace("```", "").trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start < 0 || end <= start) error("Réponse IA non JSON : ${cleaned.take(180)}")
            val json = try { JSONObject(cleaned.substring(start, end + 1)) }
                catch (e: Exception) { error("JSON IA illisible : ${e.message}") }

            fun textField(key: String): String? {
                if (!json.has(key) || json.isNull(key)) return null
                return when (val field = json.opt(key)) {
                    is String -> field.trim().takeIf { it.isNotEmpty() }
                    is Number -> field.toString()
                    else -> error("Champ $key invalide : texte ou nombre attendu.")
                }
            }
            fun intField(key: String): Int? {
                if (!json.has(key) || json.isNull(key)) return null
                val field = json.opt(key)
                if (field !is Number) error("Champ $key invalide : entier attendu.")
                val number = field.toDouble()
                if (!number.isFinite() || number % 1.0 != 0.0 || number < Int.MIN_VALUE || number > Int.MAX_VALUE) {
                    error("Champ $key invalide : entier hors limites.")
                }
                return number.toInt()
            }
            val recognized = json.opt("recognized")
            if (recognized !is Boolean) error("Champ recognized absent ou invalide.")
            val confidenceValue = json.opt("confidence")
            if (confidenceValue !is Number) error("Champ confidence absent ou invalide.")
            val confidence = confidenceValue.toDouble()
            if (!confidence.isFinite() || confidence !in 0.0..1.0) error("Score de confiance hors limites.")
            val action = textField("action") ?: "none"
            if (action !in setOf("none", "spin", "collect_free_reward", "upgrade_village")) {
                error("Action IA inconnue : $action")
            }
            GameAnalysis(
                recognized, confidence, textField("turns"), textField("bonus_turns"),
                textField("coins"), textField("gems"), textField("stars"), textField("multiplier"),
                textField("screen") ?: "inconnu", textField("recommendation") ?: "", action,
                intField("x"), intField("y"), textField("reason") ?: ""
            )
        }
    }
}
