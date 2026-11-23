package fr.boardaventure.ia
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var store: SecureStore
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var status: TextView
    private lateinit var result: TextView
    private val vision = OpenRouterVision()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); store = SecureStore(this); ui() }
    private fun ui() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        box.addView(TextView(this).apply { text = "BOARD AVENTURE IA"; textSize = 24f })
        box.addView(TextView(this).apply { text = "Analyse visuelle OpenRouter"; setPadding(0, 8, 0, 16) })
        key = EditText(this).apply { hint = "Clé API OpenRouter"; inputType = 129; setText(store.apiKey().orEmpty()) }
        model = EditText(this).apply { hint = "Modèle vision"; setText(store.model()) }
        box.addView(key); box.addView(model)
        box.addView(Button(this).apply { text = "Enregistrer"; setOnClickListener {
            store.saveApiKey(key.text.toString().trim()); store.saveModel(model.text.toString().trim())
            status.text = "Configuration enregistrée et clé chiffrée sur l'appareil."
        }})
        box.addView(Button(this).apply { text = "Activer l'assistant Android"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }})
        box.addView(Button(this).apply { text = "Analyser l'écran du jeu"; setOnClickListener { analyze() }})
        status = TextView(this).apply { text = "État : en attente"; setPadding(0, 16, 0, 8) }
        result = TextView(this).apply { textSize = 14f }
        box.addView(status); box.addView(result)
        box.addView(TextView(this).apply {
            text = "Mode sécurisé : aucune action de jeu ni dépense n'est exécutée. Les clics automatiques seront ajoutés après validation sur captures réelles."
            setPadding(0, 18, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(box) })
    }
    private fun analyze() {
        val api = store.apiKey()
        if (api.isNullOrBlank()) { status.text = "Enregistre ta clé OpenRouter."; return }
        val svc = GameAssistService.instance
        if (svc == null) { status.text = "Active l'assistant dans les réglages d'accessibilité."; return }
        status.text = "Capture en cours…"
        svc.capture { bmp, err ->
            if (bmp == null) { status.text = err ?: "Capture impossible"; return@capture }
            status.text = "Analyse IA…"
            lifecycleScope.launch {
                runCatching { vision.analyze(api, store.model(), bmp) }
                    .onSuccess { a ->
                        status.text = if (a.recognized && a.confidence >= 0.75) "Board Aventure reconnu" else "Écran non confirmé — aucune action"
                        result.text = """
Confiance : ${(a.confidence * 100).toInt()} %
Écran : ${a.screen}
Tours : ${a.turns ?: "illisible"}
Tours bonus : ${a.bonusTurns ?: "illisible"}
Pièces : ${a.coins ?: "illisible"}
Gemmes : ${a.gems ?: "illisible"}
Étoiles : ${a.stars ?: "illisible"}
Multiplicateur : ${a.multiplier ?: "illisible"}

Conseil : ${a.recommendation}
Action suggérée : ${a.action}
Motif : ${a.reason}

Aucun clic effectué.
""".trimIndent()
                    }.onFailure { status.text = "Erreur IA : ${it.message}" }
            }
        }
    }
}
