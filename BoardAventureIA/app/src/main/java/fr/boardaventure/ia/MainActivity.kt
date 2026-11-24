package fr.boardaventure.ia

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private lateinit var store: SecureStore
    private lateinit var provider: Spinner
    private lateinit var model: Spinner
    private lateinit var openRouterKey: EditText
    private lateinit var geminiKey: EditText
    private lateinit var groqKey: EditText
    private lateinit var status: TextView
    private lateinit var summary: TextView

    private val providers = listOf("OpenRouter", "Google Gemini", "Groq")
    private val providerIds = listOf("openrouter", "gemini", "groq")
    private val models = mapOf(
        "openrouter" to listOf(
            "nvidia/nemotron-3.5-super-vl:free",
            "inclusionai/ling-3.2-flash:free",
            "dots-studio/dots-3-note-preview:free"
        ),
        "gemini" to listOf("gemini-2.5-flash"),
        "groq" to listOf("qwen/qwen3.6-27b")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.rgb(28, 38, 55))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.rgb(56, 76, 102))
        }
    }

    private fun heading(text: String, size: Float = 18f) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(204, 216, 232))
    }

    private fun field(hintText: String, value: String? = null): EditText = EditText(this).apply {
        hint = hintText
        setText(value.orEmpty())
        setSingleLine(true)
        textSize = 15f
        setTextColor(Color.WHITE)
        setHintTextColor(Color.rgb(150, 165, 185))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        transformationMethod = PasswordTransformationMethod.getInstance()
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply {
            setColor(Color.rgb(17, 25, 39))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(65, 86, 114))
        }
    }

    private fun addGap(box: LinearLayout, height: Int = 10) {
        box.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun addSection(root: LinearLayout, title: String, description: String? = null): LinearLayout {
        val section = card()
        section.addView(heading(title))
        if (description != null) {
            addGap(section, 6)
            section.addView(body(description))
        }
        root.addView(section, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
        return section
    }

    private fun spinner(items: List<String>): Spinner {
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
            minimumHeight = dp(48)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(8, 17, 33), Color.rgb(17, 42, 68), Color.rgb(10, 25, 43))
            )
        }

        root.addView(heading("BOARD AVENTURE", 27f))
        addGap(root, 4)
        root.addView(TextView(this).apply {
            text = "ASSISTANT IA  ·  CONFIGURATION SIMPLE"
            textSize = 12f
            setTextColor(Color.rgb(94, 190, 255))
            setTypeface(typeface, Typeface.BOLD)
        })
        addGap(root, 16)

        val statusCard = addSection(root, "État de l'assistant")
        status = body("Vérification des services…")
        statusCard.addView(status)
        summary = body("")
        addGap(statusCard, 6)
        statusCard.addView(summary)

        val aiCard = addSection(root, "1. Fournisseurs IA", "Choisis ton fournisseur principal. En cas de quota épuisé, l'application essaie automatiquement les autres clés configurées.");
        aiCard.addView(heading("Fournisseur préféré", 14f))
        provider = spinner(providers)
        aiCard.addView(provider)
        provider.setSelection(providerIds.indexOf(store.provider()).coerceAtLeast(0))
        addGap(aiCard, 8)
        aiCard.addView(heading("Modèle vision", 14f))
        model = spinner(models[store.provider()] ?: models["openrouter"]!!)
        aiCard.addView(model)
        model.setSelection((models[store.provider()] ?: models["openrouter"]!!).indexOf(store.model()).coerceAtLeast(0))
        provider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val idValue = providerIds[position]
                val options = models[idValue] ?: models["openrouter"]!!
                model.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
                val saved = if (idValue == "openrouter") store.model() else options.first()
                model.setSelection(options.indexOf(saved).coerceAtLeast(0))
            }
        }

        val keysCard = addSection(root, "2. Clés API", "Les clés sont chiffrées sur ce téléphone. Configure au moins deux fournisseurs pour profiter du basculement automatique. Aucun achat ni crédit n'est déclenché par l'application.")
        keysCard.addView(heading("OpenRouter", 14f))
        keysCard.addView(body("Modèles gratuits · quota quotidien partagé"))
        addGap(keysCard, 4)
        openRouterKey = field("Clé OpenRouter · laisser vide pour conserver")
        keysCard.addView(openRouterKey)
        addGap(keysCard)
        keysCard.addView(heading("Google Gemini", 14f))
        keysCard.addView(body("API gratuite selon les limites de ton compte Google AI Studio"))
        addGap(keysCard, 4)
        geminiKey = field("Clé Gemini · laisser vide pour conserver")
        keysCard.addView(geminiKey)
        addGap(keysCard)
        keysCard.addView(heading("Groq Vision", 14f))
        keysCard.addView(body("Vision Qwen · quota gratuit selon ton compte Groq"))
        addGap(keysCard, 4)
        groqKey = field("Clé Groq · laisser vide pour conserver")
        keysCard.addView(groqKey)

        val actions = addSection(root, "3. Démarrage")
        actions.addView(Button(this).apply {
            text = "ENREGISTRER LES PARAMÈTRES"
            textSize = 14f
            minHeight = dp(52)
            setOnClickListener { saveSettings() }
        })
        addGap(actions, 8)
        actions.addView(Button(this).apply {
            text = "ACTIVER L'ASSISTANT ANDROID"
            minHeight = dp(50)
            setOnClickListener {
                if (isAccessibilityServiceEnabled()) status.text = "Assistant Android activé."
                else {
                    status.text = "Active « Assistant Board Aventure » dans les paramètres Accessibilité."
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        })
        addGap(actions, 8)
        actions.addView(Button(this).apply {
            text = "OUVRIR BOARD AVENTURE"
            minHeight = dp(50)
            setOnClickListener { openBoardAventure() }
        })

        val safety = addSection(root, "Sécurité")
        safety.addView(body("• Bascule uniquement vers les fournisseurs dont tu as enregistré une clé.\n• Aucun fournisseur payant n'est sélectionné automatiquement.\n• Une erreur 429 déclenche le fournisseur suivant.\n• Les actions de jeu restent soumises aux contrôles locaux et aux réserves de ressources."))
        safety.addView(body("Pour le moment, l'AUTO et les changements de multiplicateur restent verrouillés si les données visuelles ne sont pas fiables."))

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(-1, -2))
        })
        refreshStatus()
    }

    private fun saveSettings() {
        val selectedProvider = providerIds[provider.selectedItemPosition.coerceIn(providerIds.indices)]
        val selectedModel = model.selectedItem?.toString().orEmpty()
        if (selectedModel.isBlank()) {
            status.text = "Sélectionne un modèle vision."
            return
        }
        if (openRouterKey.text.isNotBlank()) store.saveApiKey(openRouterKey.text.toString().trim())
        if (geminiKey.text.isNotBlank()) store.saveProviderKey("gemini", geminiKey.text.toString().trim())
        if (groqKey.text.isNotBlank()) store.saveProviderKey("groq", groqKey.text.toString().trim())
        store.saveProvider(selectedProvider)
        if (selectedProvider == "openrouter") store.saveModel(selectedModel)
        openRouterKey.text.clear()
        geminiKey.text.clear()
        groqKey.text.clear()
        refreshStatus()
        status.text = "Paramètres enregistrés. Bascule automatique activée pour les clés disponibles."
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val configured = buildList {
            if (!store.apiKey().isNullOrBlank()) add("OpenRouter")
            if (!store.providerKey("gemini").isNullOrBlank()) add("Gemini")
            if (!store.providerKey("groq").isNullOrBlank()) add("Groq")
        }
        status.text = if (configured.isEmpty()) "Aucun fournisseur configuré" else "Fournisseurs prêts : " + configured.joinToString(" · ")
        summary.text = "Préféré : ${providerIds.firstOrNull { it == store.provider() } ?: "openrouter"} · " +
            if (isAccessibilityServiceEnabled()) "Service Android actif" else "Service Android à activer"
    }

    private fun openBoardAventure() {
        val launchIntent = packageManager.getLaunchIntentForPackage(ActionPolicy.TARGET_PACKAGE)
        if (launchIntent == null) {
            status.text = "Board Aventure est introuvable. Installe ou ouvre d'abord le jeu."
            return
        }
        runCatching { startActivity(launchIntent) }
            .onFailure { status.text = "Impossible d'ouvrir Board Aventure : ${it.message}" }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val expected = ComponentName(this, GameAssistService::class.java).flattenToString()
        return !TextUtils.isEmpty(enabled) && enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
