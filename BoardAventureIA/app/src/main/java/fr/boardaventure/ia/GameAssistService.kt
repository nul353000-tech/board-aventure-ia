package fr.boardaventure.ia

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class GameAssistService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var floatingButton: TextView? = null
    private var analyzing = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentPackage = event?.packageName?.toString() ?: return
        if (currentPackage == ActionPolicy.TARGET_PACKAGE) showFloatingButton()
        else removeFloatingButton()
    }

    override fun onInterrupt() = removeFloatingButton()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onDestroy() {
        removeFloatingButton()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    private fun showFloatingButton() {
        if (floatingButton != null) return
        val button = TextView(this).apply {
            text = "Analyser"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(22, 14, 22, 14)
            background = GradientDrawable().apply {
                setColor(Color.rgb(30, 105, 190))
                cornerRadius = 48f
            }
            elevation = 10f
            setOnClickListener { analyzeFromGame(this) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18
            y = 220
        }
        runCatching {
            windowManager?.addView(button, params)
            floatingButton = button
        }.onFailure {
            Toast.makeText(this, "Bouton flottant indisponible : ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeFloatingButton() {
        floatingButton?.let { view -> runCatching { windowManager?.removeView(view) } }
        floatingButton = null
    }

    private fun analyzeFromGame(button: TextView) {
        if (analyzing) return
        analyzing = true
        button.isEnabled = false
        button.text = "Lecture…"
        capture { bitmap, error ->
            if (bitmap == null) {
                analyzing = false
                button.text = "Réessayer"
                button.isEnabled = true
                Toast.makeText(this, error ?: "Capture impossible", Toast.LENGTH_LONG).show()
                return@capture
            }
            scope.launch {
                try {
                    val store = SecureStore(this@GameAssistService)
                    val api = store.apiKey()
                    if (api.isNullOrBlank()) {
                        button.text = "Clé absente"
                        Toast.makeText(this@GameAssistService, "Enregistre d'abord ta clé OpenRouter dans Board Aventure IA.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val a = OpenRouterVision().analyze(api, store.model(), bitmap)
                    val recognized = a.recognized && a.confidence >= 0.75
                    button.text = if (recognized) "Jeu ${(a.confidence * 100).toInt()}%" else "Non reconnu"
                    val message = if (recognized) {
                        "Tours : ${a.turns ?: "illisible"} | Bonus : ${a.bonusTurns ?: "illisible"}\n" +
                        "Pièces : ${a.coins ?: "illisible"} | Gemmes : ${a.gems ?: "illisible"}\n" +
                        "Étoiles : ${a.stars ?: "illisible"} | Multiplicateur : ${a.multiplier ?: "illisible"}\n" +
                        "Conseil : ${a.recommendation}"
                    } else {
                        "Board Aventure non confirmé. Vérifie que le jeu est affiché au premier plan."
                    }
                    Toast.makeText(this@GameAssistService, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    button.text = "Erreur IA"
                    Toast.makeText(this@GameAssistService, e.message ?: "Erreur d'analyse", Toast.LENGTH_LONG).show()
                } finally {
                    bitmap.recycle()
                    analyzing = false
                    button.isEnabled = true
                }
            }
        }
    }

    fun capture(callback: (Bitmap?, String?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            callback(null, "Capture d'écran non prise en charge.")
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, Executor { it.run() }, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hardware = result.hardwareBuffer
                val colorSpace = result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
                val bitmap = try {
                    Bitmap.wrapHardwareBuffer(hardware, colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                } finally {
                    hardware.close()
                }
                callback(bitmap, if (bitmap == null) "Conversion de la capture impossible." else null)
            }
            override fun onFailure(errorCode: Int) {
                callback(null, "Échec de capture écran (code $errorCode).")
            }
        })
    }

    fun tap(x: Float, y: Float, done: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = done(false)
        }, null)
    }

    companion object {
        @Volatile var instance: GameAssistService? = null
            private set
    }
}
