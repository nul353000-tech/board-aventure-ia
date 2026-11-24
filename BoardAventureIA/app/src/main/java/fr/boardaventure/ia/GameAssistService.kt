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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    private var discoveryAttempts = 0
    private var autoAnalysisStartedForSession = false

    private val discoveryRunnable = object : Runnable {
        override fun run() {
            if (isGameVisible()) {
                lastForegroundPackage = ActionPolicy.TARGET_PACKAGE
                showFloatingPanel()
                if (!analyzing) statusView?.text = "Jeu Board Aventure détecté"
                return
            }
            discoveryAttempts++
            if (discoveryAttempts < 12) mainHandler.postDelayed(this, 500L)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val gameIsActive = pkg == ActionPolicy.TARGET_PACKAGE || isGameVisible()
        if (gameIsActive) {
            lastForegroundPackage = ActionPolicy.TARGET_PACKAGE
            showFloatingPanel()
            if (!analyzing) statusView?.text = "Jeu Board Aventure détecté"
            startAutomaticInitialAnalysis()
            return
        }
        val isWindowTransition =
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (isWindowTransition) {
            val activePackage = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            if (activePackage != null && activePackage != ActionPolicy.TARGET_PACKAGE) {
                lastForegroundPackage = activePackage
                autoAnalysisStartedForSession = false
                mainHandler.removeCallbacks(autoAnalysisRunnable)
                removeFloatingPanel()
            }
        }
    }

    private fun isGameVisible(): Boolean {
        val active = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (active == ActionPolicy.TARGET_PACKAGE) return true
        return runCatching {
            windows.any { window ->
                runCatching {
                    window.root?.packageName?.toString() == ActionPolicy.TARGET_PACKAGE
                }.getOrDefault(false)
            }
        }.getOrDefault(false)
    }

    override fun onInterrupt() {
        if (!analyzing) statusView?.text = "Assistant interrompu"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        discoveryAttempts = 0
        mainHandler.removeCallbacks(discoveryRunnable)
        mainHandler.post(discoveryRunnable)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(discoveryRunnable)
        mainHandler.removeCallbacks(autoAnalysisRunnable)
        removeFloatingPanel()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    // Automatic first-stage feature: analyse once when the game opens.
    // No game taps, spending, or purchases are performed.
    private val autoAnalysisRunnable = Runnable {
        if (isGameVisible() && !analyzing) analyzeCurrentScreen()
    }

    private fun startAutomaticInitialAnalysis() {
        if (autoAnalysisStartedForSession || analyzing) return
        autoAnalysisStartedForSession = true
        mainHandler.removeCallbacks(autoAnalysisRunnable)
        mainHandler.postDelayed(autoAnalysisRunnable, 1800L)
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
        if (!isGameVisible()) {
            lastForegroundPackage = "Application en attente"
            statusView?.text = "Ouvre d'abord Board Aventure avant de lancer l'analyse."
            return
        }
        lastForegroundPackage = ActionPolicy.TARGET_PACKAGE
        analyzing = true
        button.isEnabled = false
        button.text = "CAPTURE…"
        statusView?.text = "Masquage du panneau avant capture"
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
        val completed = AtomicBoolean(false)
        val timeout = Runnable {
            if (completed.compareAndSet(false, true)) {
                callback(null, "Délai de capture dépassé. Réessaie.")
            }
        }
        mainHandler.postDelayed(timeout, 12_000L)
        fun complete(bitmap: Bitmap?, error: String?) {
            if (completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                callback(bitmap, error)
            } else {
                bitmap?.recycle()
            }
        }
        val screenshotCallback = object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val bitmap = runCatching {
                    val hardware = result.hardwareBuffer
                    try {
                        val colorSpace = result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
                        Bitmap.wrapHardwareBuffer(hardware, colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        runCatching { hardware.close() }
                    }
                }.getOrNull()
                complete(bitmap, if (bitmap == null) "Conversion image impossible." else null)
            }

            override fun onFailure(errorCode: Int) {
                complete(null, "Capture Android refusée (code $errorCode).")
            }
        }
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
            complete(null, "Impossible de lancer la capture : ${e.message}")
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
                val analysis = OpenRouterVision().analyze(apiKey, model, bitmap)
                val recognized = analysis.recognized &&
                    analysis.confidence >= ActionPolicy.MIN_CONFIDENCE
                val details = if (!recognized) {
                    "Jeu non confirmé (confiance ${(analysis.confidence * 100).toInt()} %).\n" +
                        "Écran identifié : ${analysis.screen}\nMotif : ${analysis.reason}\nAucune action effectuée."
                } else {
                    "Board Aventure reconnu · ${(analysis.confidence * 100).toInt()} %\n" +
    "Pièces : ${analysis.coins ?: "illisible"}\n" +
                        "Gemmes : ${analysis.gems ?: "illisible"}\n" +
                        "Étoiles : ${analysis.stars ?: "illisible"}\n" +
                        "Énergie : ${analysis.energy ?: "illisible"}\n" +
                        "Bonus d'énergie : ${analysis.energyBonus ?: "illisible"}\n" +
                        "Multiplicateur : ${analysis.multiplier ?: "illisible"}\n" +
                        "Conseil : ${analysis.recommendation}\n" +
                        "Action proposée : ${analysis.action}\nAucun clic ni dépense exécuté."
                }
                resultView?.text = details
                resultView?.visibility = View.VISIBLE
                finishAnalysis(if (recognized) "Analyse terminée" else "Jeu non reconnu", "ANALYSER")
            } catch (e: Exception) {
                val error = "Erreur IA : ${e.message ?: e.javaClass.simpleName}"
                resultView?.text = error
                resultView?.visibility = View.VISIBLE
                finishAnalysis(error, "RÉESSAYER")
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private fun finishAnalysis(message: String, buttonText: String) {
        analyzing = false
        statusView?.text = message
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
