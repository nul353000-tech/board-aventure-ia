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
    val recognized: Boolean, val confidence: Double, 
    val coins: String?, val gems: String?, val stars: String?, val energy: String?,
    val energyBonus: String?, val multiplier: String?, val spinState: String?, val villageUpgradeCosts: String?, val screen: String, val recommendation: String, val action: String,
    val x: Int?, val y: Int?, val reason: String,
    val damagedBuildings: Boolean? = null, val freeRewardAvailable: Boolean? = null,
    val villageManiaActive: Boolean? = null, val villageMasterActive: Boolean? = null,
    val buildRushActive: Boolean? = null, val shieldCount: String? = null,
    val activeEvents: String? = null, val progression: String? = null
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
        // The free router may select a reasoning-heavy provider that exhausts the
        // completion budget before returning the JSON. Pin the generic free alias
        // to a known free image+text model instead of allowing random routing.
        // Strict free-only policy: never send a model without the :free suffix.
        // Generic aliases and previously saved paid model IDs fall back to Gemma Free.
        val requestedModel = model.trim()
        val effectiveModel = when {
            requestedModel == "openrouter/free" || requestedModel.startsWith("google/") -> "nvidia/nemotron-3.5-super-vl:free"
            requestedModel.endsWith(":free") -> requestedModel
            else -> "nvidia/nemotron-3.5-super-vl:free"
        }
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "Capture d’écran invalide ou déjà libérée." }
        val stream = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)) { "Compression de la capture impossible." }
        val bytes = stream.toByteArray()
        check(bytes.isNotEmpty()) { "Capture compressée vide." }
        val image = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val systemPrompt = "Tu es l'agent de vision du jeu Board Aventure (package com.moonactive.cmboard). " +
            "Analyse uniquement ce qui est visible, sans inventer. Réponds en JSON strict avec recognized(boolean), " +
            "confidence(number 0..1), screen(string), coins(string|null), " +
            "gems(string|null), stars(string|null), energy(string|null), energy_bonus(string|null), multiplier(string|null), spin_state(ready|auto_active|unknown), village_upgrade_costs(string|null), recommendation(string), " +
            "action('none'|'spin'|'collect_free_reward'|'upgrade_village'), x(integer|null), y(integer|null), reason(string), " +
            "damaged_buildings(boolean|null), free_reward_available(boolean|null), village_mania_active(boolean|null), village_master_active(boolean|null), build_rush_active(boolean|null), shield_count(string|null), active_events(string|null), progression(string|null). " +
            "Si le jeu n'est pas clairement reconnu: recognized=false et action=none. Valeur illisible=null. " +
            "Tours, bonus, pièces et gemmes peuvent monter ou baisser ; étoiles seulement augmenter. " +
            "Repères visuels issus des captures réelles du téléphone en portrait (684x1536, coordonnées proportionnelles): " +
            "pièces en haut à gauche, environ x=8-36% et y=7-12%; gemmes dans la barre supérieure juste à droite des pièces, " +
            "environ x=36-60% et y=7-12%; énergie en bas au centre, x=32-68% et y=92-97%; bonus d'énergie sous cette barre, " +
            "x=38-63% et y=97-100%; multiplicateur de lancer en bas à droite près du gros bouton rouge, x=68-85% et y=84-90%; " +
            "compteur bonus visible près de la progression supérieure, x=65-78% et y=22-28%. " +
            "Repère et lis systématiquement les étoiles, les compteurs de ressources " +
            "en utilisant aussi les captures de référence déjà fournies par l'utilisateur: ne les considère pas comme absents " +
            "simplement parce qu'ils ne sont pas dans les zones pièces/gemmes/énergie. Distingue clairement tours principaux, " +
            "tours supplémentaires/bonus et énergie; ne les additionne jamais et ne confonds pas l'énergie avec les tours. " +
            "Pour chaque compteur, cherche son icône et son nombre sur l'ensemble de l'écran, y compris les côtés et les éléments " +
            "de progression. Les étoiles sont un compteur monotone (elles ne font qu'augmenter). " +
            "Un panneau flottant peut cacher une partie de l'écran: ne devine jamais les chiffres réellement cachés, " +
            "mais continue de lire les autres compteurs visibles. " +
            "Repère les cartes du village par leur présentation visuelle: cartes alignées, étoiles en haut, illustration au centre et bandeau vert en bas avec une icône de pièce et un prix. Le mot « améliorer » peut être absent: ne l'exige jamais. Lis chaque prix réellement affiché dans ces bandeaux, quel que soit son montant et quel que soit le nombre de cartes visibles. village_upgrade_costs doit être une liste de montants en pièces entières séparés par des virgules, dans l'ordre gauche à droite; convertis les abréviations (par exemple 90M en 90000000). Ne réutilise jamais les anciens prix si ceux de l'écran actuel sont différents; si un prix est illisible, indique -1 à sa position; si aucune carte n'est visible, null. Ne propose jamais d'achat en argent réel. Détecte le bouton central: si le gros bouton rouge affiche le dé, spin_state=ready. Le lancer simple est déclenché par un clic rapide (appui puis relâchement immédiat). Les lancers automatiques sont déclenchés uniquement par un maintien prolongé du même bouton. Si le bouton rouge affiche STOP, spin_state=auto_active: le mode automatique est actif. Un seul clic rapide sur STOP suffit à l'arrêter; il ne faut pas maintenir STOP. Sinon spin_state=unknown. Lis séparément le multiplicateur jaune immédiatement à droite du bouton rouge, par exemple x1, x2 ou une autre valeur affichée. Le multiplicateur est distinct de l'état du bouton. Ne confonds jamais un simple appui avec un maintien. Ignore la date et l'heure. Ne fournis aucune coordonnée non vérifiable."
        val userPrompt = "Lis tous les compteurs visibles: étoiles, énergie actuelle/maximale, " +
            "bonus d'énergie, pièces, gemmes, multiplicateur et les prix des cartes du village. Les cartes du village sont reconnaissables à leur bande verte avec icône de pièce et prix; le mot « améliorer » n'est pas nécessaire. Les prix peuvent changer et le nombre de cartes aussi: lis les valeurs de la capture actuelle, de gauche à droite, converties en pièces entières (90M devient 90000000). Mets -1 pour un prix de carte illisible, null s'il n'y a aucune carte visible. " +
            "" +
            "" +
            "Lis aussi la progression actuelle sous forme exacte actuel/objectif (exemple 725/725) si elle est visible; sinon null. Détecte aussi si le bouton central est prêt à lancer ou affiche STOP en cours, les bâtiments endommagés, les récompenses gratuites disponibles, les boucliers et les événements actifs. N’inclus jamais une offre payante comme récompense gratuite. Réponds avec un JSON compact, sans explication, exactement selon le schéma."
        val content = org.json.JSONArray()
            .put(JSONObject().put("type", "text").put("text", userPrompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image")))
        // Keep every fallback on a free, image-capable model. OpenRouter
        // advances to the next model when the upstream provider returns 429.
        // No paid model is included, so fallback cannot silently incur charges.
        val fallbackModels = org.json.JSONArray()
            .put(effectiveModel)
            .put("inclusionai/ling-3.2-flash:free")
            .put("dots-studio/dots-3-note-preview:free")
        val payload = JSONObject().put("models", fallbackModels).put("temperature", 0.1)
            .put("max_tokens", 1600)
            .put("reasoning", JSONObject().put("effort", "none"))
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
                ?: error("Réponse OpenRouter sans choices. Modèle demandé : $effectiveModel. Réponse : ${body.take(300)}")
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
                val usage = root.optJSONObject("usage")
                val completion = usage?.optInt("completion_tokens", -1) ?: -1
                val reasoningTokens = usage?.optJSONObject("completion_tokens_details")?.optInt("reasoning_tokens", -1) ?: -1
                val finish = choice.optString("finish_reason", "inconnu")
                val nativeFinish = choice.optString("native_finish_reason", "")
                val refusalValue = message.opt("refusal")
                val refusal = if (refusalValue is String) refusalValue else ""
                val returnedModel = root.optString("model", effectiveModel)
                val diagnostic = when {
                    refusal.isNotBlank() -> "Refus du modèle : ${refusal.take(180)}"
                    finish == "length" -> "Réponse coupée par la limite de tokens."
                    else -> "Le fournisseur n'a renvoyé aucun texte final."
                }
                error("Réponse IA vide. Modèle servi : $returnedModel. Fin : $finish${if (nativeFinish.isNotBlank()) " ($nativeFinish)" else ""}. Tokens sortie=$completion, raisonnement=$reasoningTokens. $diagnostic")
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
            fun boolField(key: String): Boolean? {
                if (!json.has(key) || json.isNull(key)) return null
                return json.opt(key) as? Boolean ?: error("Champ $key invalide : booléen attendu.")
            }
            GameAnalysis(
                recognized, confidence,
                textField("coins"), textField("gems"), textField("stars"), textField("energy"), textField("energy_bonus"), textField("multiplier"), textField("spin_state"), textField("village_upgrade_costs"),
                textField("screen") ?: "inconnu", textField("recommendation") ?: "", action,
                intField("x"), intField("y"), textField("reason") ?: "",
                boolField("damaged_buildings"), boolField("free_reward_available"),
                boolField("village_mania_active"), boolField("village_master_active"),
                boolField("build_rush_active"), textField("shield_count"), textField("active_events"), textField("progression")
            )
        }
    }
}
