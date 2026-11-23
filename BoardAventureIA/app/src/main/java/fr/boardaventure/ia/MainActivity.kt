package fr.boardaventure.ia

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private lateinit var store: SecureStore
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var status: TextView
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildUi() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        box.addView(TextView(this).apply {
            text = "BOARD AVENTURE IA"
            textSize = 24f
        })
        box.addView(TextView(this).apply {
            text = "Assistant d'analyse visuelle"
            setPadding(0, 8, 0, 16)
        })

        key = EditText(this).apply {
            hint = "Clé API OpenRouter"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(store.apiKey().orEmpty())
            isSingleLine = true
        }
        model = EditText(this).apply {
            hint = "Modèle vision OpenRouter"
            setText(store.model())
            isSingleLine = true
        }
        box.addView(key)
        box.addView(model)

        status = TextView(this).apply {
            text = "État : vérification…"
            setPadding(0, 16, 0, 8)
        }
        result = TextView(this).apply { textSize = 14f }

        box.addView(Button(this).apply {
            text = "Enregistrer la configuration"
            setOnClickListener {
                val apiKey = key.text.toString().trim()
                val modelId = model.text.toString().trim()
                if (apiKey.isBlank()) {
                    status.text = "Saisis ta clé API OpenRouter."
                } else if (modelId.isBlank()) {
                    status.text = "Saisis un identifiant de modèle vision."
                } else {
                    store.saveApiKey(apiKey)
                    store.saveModel(modelId)
                    status.text = "Configuration enregistrée. Clé chiffrée sur l'appareil."
                }
            }
        })
        box.addView(Button(this).apply {
            text = "Activer l'assistant Android"
            setOnClickListener {
                if (isAccessibilityServiceEnabled()) {
                    status.text = "Assistant activé. Tu peux ouvrir Board Aventure."
                } else {
                    status.text = "Active « Assistant Board Aventure » dans les paramètres Accessibilité."
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        })
        box.addView(Button(this).apply {
            text = "Ouvrir Board Aventure"
            setOnClickListener { openBoardAventure() }
        })
        box.addView(Button(this).apply {
            text = "Vérifier la configuration"
            setOnClickListener { refreshStatus() }
        })
        box.addView(status)
        box.addView(result)
        box.addView(TextView(this).apply {
            text = "Sécurité : aucune action de jeu, aucun clic et aucune dépense ne sont exécutés. L'automatisation restera verrouillée tant que les zones de jeu n'auront pas été calibrées et vérifiées sur des captures réelles."
            setPadding(0, 18, 0, 0)
        })
        setContentView(ScrollView(this).apply {
            addView(box, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        })
        refreshStatus()
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val keyReady = !store.apiKey().isNullOrBlank()
        val modelReady = store.model().isNotBlank()
        val serviceReady = isAccessibilityServiceEnabled()
        status.text = buildString {
            append("État : ")
            append(if (keyReady) "clé enregistrée" else "clé OpenRouter absente")
            append(" · ")
            append(if (modelReady) "modèle défini" else "modèle absent")
            append(" · ")
            append(if (serviceReady) "assistant Android activé" else "assistant Android désactivé")
        }
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
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val expected = ComponentName(this, GameAssistService::class.java).flattenToString()
        return !TextUtils.isEmpty(enabled) &&
            enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
