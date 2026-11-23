package fr.boardaventure.ia
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.content.ComponentName
import android.widget.*
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private lateinit var store: SecureStore
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var status: TextView
    private lateinit var result: TextView
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
        box.addView(Button(this).apply { text = "Activer l'assistant Android"; setOnClickListener {
            if (isAccessibilityServiceEnabled()) {
                status.text = "Assistant déjà activé. Ouvre Board Aventure : le bouton flottant Analyser sera disponible."
            } else {
                status.text = "Active une seule fois Assistant Board Aventure dans les services installés."
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }})
        box.addView(Button(this).apply { text = "Comment lancer l'analyse"; setOnClickListener {
            status.text = "1. Enregistre ta clé. 2. Active l'assistant une seule fois. 3. Ouvre Board Aventure : le bouton flottant Analyser apparaît sur le jeu."
        }})
        status = TextView(this).apply { text = "État : en attente"; setPadding(0, 16, 0, 8) }
        result = TextView(this).apply { textSize = 14f }
        box.addView(status); box.addView(result)
        box.addView(TextView(this).apply {
            text = "Mode sécurisé : aucune action de jeu ni dépense n'est exécutée. Les clics automatiques seront ajoutés après validation sur captures réelles."
            setPadding(0, 18, 0, 0)
        })
        setContentView(ScrollView(this).apply { addView(box) })
    }
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val expected = ComponentName(this, GameAssistService::class.java).flattenToString()
        return !TextUtils.isEmpty(enabled) && enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

}
