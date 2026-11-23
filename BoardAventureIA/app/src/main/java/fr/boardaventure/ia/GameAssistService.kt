package fr.boardaventure.ia

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var analyzeButton: TextView? = null
    private var statusView: TextView? = null
    private var resultView: TextView? = null
    private var analyzing = false
    private var lastForegroundPackage = "Application en attente"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val activePackage = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        val gameIsActive = pkg == ActionPolicy.TARGET_PACKAGE ||
            activePackage == ActionPolicy.TARGET_PACKAGE
        if (gameIsActive) {
            lastForegroundPackage = ActionPolicy.TARGET_PACKAGE
            showFloatingPanel()
            statusView?.text = "Jeu Board Aventure détecté"
            return
        }
        // Ignore child/content events from keyboards, dialogs and system overlays.
        // Hide only when Android reports a genuine foreground-window transition.
        val isWindowTransition = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (isWindowTransition) {
            lastForegroundPackage = activePackage ?: pkg
            removeFloatingPanel()
        }
    }

    override fun onInterrupt() {
        statusView?.text = "Assistant interrompu"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val activePackage = rootInActiveWindow?.packageName?.toString()
        lastForegroundPackage = activePackage ?: "Application en attente"
        if (activePackage == ActionPolicy.TARGET_PACKAGE) {
            showFloatingPanel()
            statusView?.text = "Jeu Board Aventure détecté"
        }
    }

    override fun onDestroy() {
        removeFloatingPanel()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    private fun showFloatingPanel() {
        if (panel != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = GradientDrawable().apply {
                setColor(Color.rgb(28, 35, 48))
                cornerRadius = 22f
                setStroke(2, Color.rgb(65, 130, 210))
            }
            elevation = 12f
        }
        val button = TextView(this).apply {
            text = "BOARD IA · ANALYSER"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(18, 13, 18, 13)
            background = GradientDrawable().apply {
                setColor(Color.rgb(30, 105, 190))
                cornerRadius = 16f
            }
            setOnClickListener { analyzeCurrentScreen() }
        }
        val status = TextView(this).apply {
            text = "Assistant actif"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(3, 8, 3, 2)
        }
        val result = TextView(this).apply {
            text = "Résultats de l'analyse affichés ici."
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(3, 5, 3, 2)
            visibility = View.GONE
        }
        root.addView(button)
        root.addView(status)
        root.addView(result)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 180
        }
        try {
            windowManager?.addView(root, params)
            panel = root
            analyzeButton = button
            statusView = status
            resultView = result
        } catch (e: Exception) {
            Toast.makeText(this, "Panneau indisponible : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeFloatingPanel() {
        panel?.let { view -> runCatching { windowManager?.removeView(view) } }
        panel = null
        analyzeButton = null
        statusView = null
        resultView = null
    }

    private fun analyzeCurrentScreen() {
        if (analyzing) return
        val button = analyzeButton ?: return
        if (lastForegroundPackage != ActionPolicy.TARGET_PACKAGE &&
            rootInActiveWindow?.packageName?.toString() != ActionPolicy.TARGET_PACKAGE) {
            statusView?.text = "Ouvre d'abord Board Aventure avant de lancer l'analyse."
            return
        }
        analyzing = true
        button.isEnabled = false
        button.text = "CAPTURE…"
        statusView?.text = "Masquage du panneau avant capture"
        // Hide our own overlay so it cannot cover or contaminate the game screenshot.
        panel?.visibility = View.INVISIBLE
        mainHandler.postDelayed({
            takeGameScreenshot { bitmap, error ->
                panel?.visibility = View.VISIBLE
                if (bitmap == null) {
                    finishAnalysis("Échec capture : ${error ?: "inconnue"}", "RÉESSAYER")
                    return@takeGameScreenshot
                }
                runAnalysis(bitmap)
            }
        }, 180)
    }

    private fun takeGameScreenshot(callback: (Bitmap?, String?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            callback(null, "Android 11 ou plus requis.")
            return
        }
        val screenshotCallback = object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hardware = result.hardwareBuffer
                val colorSpace = result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
                val bitmap = try {
                    Bitmap.wrapHardwareBuffer(hardware, colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                } finally {
                    hardware.close()
                }
                callback(bitmap, if (bitmap == null) "Conversion image impossible." else null)
            }

            override fun onFailure(errorCode: Int) {
                callback(null, "Capture Android refusée (code $errorCode).")
            }
        }
        // API 34+: capture only the actual game window, excluding accessibility overlays.
        val gameWindow = if (Build.VERSION.SDK_INT >= 34) {
            windows.firstOrNull { window ->
                runCatching { window.root?.packageName?.toString() == ActionPolicy.TARGET_PACKAGE }
                    .getOrDefault(false)
            }
        } else null
        try {
            if (Build.VERSION.SDK_INT >= 34 && gameWindow != null) {
                takeScreenshotOfWindow(gameWindow.id, mainExecutor, screenshotCallback)
            } else {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, screenshotCallback)
            }
        } catch (e: Exception) {
            callback(null, "Impossible de lancer la capture : ${e.message}")
        }
    }

    private fun runAnalysis(bitmap: Bitmap) {
        scope.launch {
            try {
                val store = SecureStore(this@GameAssistService)
                val apiKey = store.apiKey()
                if (apiKey.isNullOrBlank()) {
                    finishAnalysis("Clé OpenRouter absente. Enregistre-la dans l'application.", "CLÉ ABSENTE")
                    return@launch
                }
                val model = store.model().trim()
                if (model.isBlank()) {
                    finishAnalysis("Identifiant du modèle vision manquant.", "MODÈLE ABSENT")
                    return@launch
                }
                statusView?.text = "Analyse OpenRouter en cours…"
                val a = OpenRouterVision().analyze(apiKey, model, bitmap)
                val recognized = a.recognized && a.confidence >= 0.75
                val details = if (!recognized) {
                    "Jeu non confirmé (confiance ${(a.confidence * 100).toInt()} %).\nÉcran identifié : ${a.screen}\nMotif : ${a.reason}\nAucune action effectuée."
                } else {
                    "Board Aventure reconnu · ${(a.confidence * 100).toInt()} %\n" +
                    "Tours : ${a.turns ?: "illisible"}\n" +
                    "Tours bonus : ${a.bonusTurns ?: "illisible"}\n" +
                    "Pièces : ${a.coins ?: "illisible"}\n" +
                    "Gemmes : ${a.gems ?: "illisible"}\n" +
                    "Étoiles : ${a.stars ?: "illisible"}\n" +
                    "Multiplicateur : ${a.multiplier ?: "illisible"}\n" +
                    "Conseil : ${a.recommendation}\n" +
                    "Action proposée : ${a.action}\nAucun clic ni dépense exécuté."
                }
                resultView?.text = details
                resultView?.visibility = View.VISIBLE
                finishAnalysis(if (recognized) "Analyse terminée" else "Jeu non reconnu", "ANALYSER")
            } catch (e: Exception) {
                finishAnalysis("Erreur IA : ${e.message ?: e.javaClass.simpleName}", "RÉESSAYER")
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun finishAnalysis(message: String, buttonText: String) {
        analyzing = false
        statusView?.text = "$message · écran : $lastForegroundPackage"
        analyzeButton?.apply {
            text = buttonText
            isEnabled = true
        }
    }

    companion object {
        @Volatile var instance: GameAssistService? = null
            private set
    }
}
