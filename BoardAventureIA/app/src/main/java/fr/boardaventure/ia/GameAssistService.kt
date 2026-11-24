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
import android.view.MotionEvent
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
    private var panelLayoutParams: WindowManager.LayoutParams? = null
    private var analyzeButton: TextView? = null
    private var statusView: TextView? = null
    private var resultView: TextView? = null
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var minimized = false
    private var dragHandleView: TextView? = null
    private var minimizeButton: TextView? = null
    private var compactBubble: TextView? = null
    private var analyzing = false
    private var lastForegroundPackage = "Application en attente"
    private var discoveryAttempts = 0
    private var autoAnalysisStartedForSession = false

    // Safe UI-only automation: collapse the panel after 30 seconds without interaction.
    private val idleCollapseRunnable = Runnable {
        if (!analyzing && panel != null && !minimized && isGameVisible()) {
            minimizeFloatingPanel()
        }
    }

    private fun scheduleIdleCollapse() {
        mainHandler.removeCallbacks(idleCollapseRunnable)
        if (!analyzing && panel != null && !minimized) {
            mainHandler.postDelayed(idleCollapseRunnable, 30_000L)
        }
    }

    private val discoveryRunnable = object : Runnable {
        override fun run() {
            if (isGameVisible()) {
                lastForegroundPackage = ActionPolicy.TARGET_PACKAGE
                showFloatingPanel()
                if (!analyzing) statusView?.text = "Jeu Board Aventure détecté"
                startAutomaticInitialAnalysis()
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
        mainHandler.removeCallbacks(idleCollapseRunnable)
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
        val dragHandle = TextView(this).apply {
            text = "☷  MAINTENIR POUR DÉPLACER"
            textSize = 10f
            setTextColor(Color.rgb(190, 210, 235))
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
            isFocusable = false
        }
        val minimize = TextView(this).apply {
            text = "—  Réduire en bulle"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 7, 8, 7)
            setOnClickListener { minimizeFloatingPanel() }
        }
        val bubble = TextView(this).apply {
            text = "IA"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(30, 105, 190))
                setStroke(2, Color.WHITE)
            }
            visibility = View.GONE
            setOnClickListener { restoreFloatingPanel() }
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
            setOnClickListener {
                mainHandler.removeCallbacks(idleCollapseRunnable)
                analyzeCurrentScreen()
            }
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
        root.addView(dragHandle)
        root.addView(minimize)
        root.addView(button)
        root.addView(status)
        root.addView(result)
        root.addView(bubble, LinearLayout.LayoutParams(54, 54))
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
        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawX = event.rawX
                    dragStartRawY = event.rawY
                    dragStartX = params.x
                    dragStartY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (dragStartX - (event.rawX - dragStartRawX).toInt()).coerceAtLeast(0)
                    params.y = (dragStartY + (event.rawY - dragStartRawY).toInt()).coerceAtLeast(0)
                    runCatching { windowManager?.updateViewLayout(root, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        try {
            windowManager?.addView(root, params)
            panelLayoutParams = params
            panel = root
            dragHandleView = dragHandle
            minimizeButton = minimize
            compactBubble = bubble
            analyzeButton = button
            statusView = status
            resultView = result
        } catch (e: Exception) {
            Toast.makeText(this, "Panneau indisponible : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun minimizeFloatingPanel() {
        if (minimized || analyzing) return
        mainHandler.removeCallbacks(idleCollapseRunnable)
        minimized = true
        dragHandleView?.visibility = View.GONE
        minimizeButton?.visibility = View.GONE
        analyzeButton?.visibility = View.GONE
        statusView?.visibility = View.GONE
        resultView?.visibility = View.GONE
        panel?.setPadding(0, 0, 0, 0)
        panel?.background = null
        compactBubble?.visibility = View.VISIBLE
        panelLayoutParams?.let { params ->
            params.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            params.x = 0
            runCatching { panel?.let { windowManager?.updateViewLayout(it, params) } }
        }
    }

    private fun restoreFloatingPanel() {
        if (!minimized) return
        minimized = false
        panel?.setPadding(12, 10, 12, 10)
        panel?.background = GradientDrawable().apply {
            setColor(Color.rgb(28, 35, 48))
            cornerRadius = 22f
            setStroke(2, Color.rgb(65, 130, 210))
        }
        compactBubble?.visibility = View.GONE
        dragHandleView?.visibility = View.VISIBLE
        minimizeButton?.visibility = View.VISIBLE
        analyzeButton?.visibility = View.VISIBLE
        statusView?.visibility = View.VISIBLE
        panelLayoutParams?.let { params ->
            params.gravity = Gravity.TOP or Gravity.END
            params.y = 180
            runCatching { panel?.let { windowManager?.updateViewLayout(it, params) } }
        }
        scheduleIdleCollapse()
    }

    private fun removeFloatingPanel() {
        panel?.let { view -> runCatching { windowManager?.removeView(view) } }
        panel = null
        panelLayoutParams = null
        minimized = false
        dragHandleView = null
        minimizeButton = null
        compactBubble = null
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
                val plan = ResourceStrategy.plan(
                    recognized = recognized,
                    confidence = analysis.confidence,
                    coinsText = analysis.coins,
                    energyText = analysis.energy,
                    energyBonusText = analysis.energyBonus,
                    multiplierText = analysis.multiplier,
                    spinState = analysis.spinState,
                    villageCostsText = analysis.villageUpgradeCosts
                )
                val gestureStatus = if (recognized) dispatchPlannedSpin(plan.spinCommand, analysis.confidence) else "bloqué : jeu non reconnu"
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
                        "Multiplicateur détecté : ${analysis.multiplier ?: "illisible"}\n" +
                        "Multiplicateur conseillé : x${plan.recommendedMultiplier} (prudent)\n" +
                        "Mode de lancer : ${spinStateLabel(analysis.spinState)}\n" +
                        "Bâtiments endommagés : ${analysis.damagedBuildings?.let { if (it) "oui — réparer avant amélioration" else "non détectés" } ?: "à vérifier"}\n" +
                        "Récompense gratuite : ${analysis.freeRewardAvailable?.let { if (it) "disponible" else "non détectée" } ?: "à vérifier"}\n" +
                        "Boucliers : ${analysis.shieldCount ?: "illisible"}\n" +
                        "Événements : ${analysis.activeEvents ?: "aucun confirmé"}\n" +
                        "Village Mania : ${analysis.villageManiaActive?.let { if (it) "actif" else "inactif" } ?: "à vérifier"} · Village Master : ${analysis.villageMasterActive?.let { if (it) "actif" else "inactif" } ?: "à vérifier"} · Build Rush : ${analysis.buildRushActive?.let { if (it) "actif" else "inactif" } ?: "à vérifier"}\n" +
                        "Cartes village :\n" + formatVillageCards(analysis.coins, analysis.villageUpgradeCosts) +
                        "Cartes abordables après réserve : ${plan.affordableCards.joinToString().ifBlank { "aucune confirmée" }}\n" +
                        "Carte conseillée : ${plan.suggestedCard?.let { "n°$it" } ?: "aucune"}\n" +
                        "Décision lancer : ${spinCommandLabel(plan.spinCommand)}\n" +
                        "Geste automatique : $gestureStatus\n" +
                        "Plan ressources : ${plan.explanation}\n" +
                        "Conseil IA : ${analysis.recommendation}\n" +
                        "Action proposée : ${analysis.action}\nAchats, gemmes et paiements automatiques désactivés."
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

    private fun dispatchPlannedSpin(command: ResourceStrategy.SpinCommand, confidence: Double): String {
        if (!ActionPolicy.DEVICE_GESTURE_CALIBRATION_CONFIRMED) return "bloqué : zones non confirmées"
        if (!isGameVisible()) return "bloqué : jeu non visible"
        val metrics = resources.displayMetrics
        val point = ActionPolicy.controlPreview("spin", metrics.widthPixels, metrics.heightPixels)
            ?: return "bloqué : format écran différent des captures"
        val decision = ActionPolicy.validate(
            foregroundPackage = ActionPolicy.TARGET_PACKAGE,
            recognized = true,
            confidence = confidence,
            action = "spin",
            x = point.first,
            y = point.second,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        if (!decision.allowed) return "bloqué : ${decision.explanation}"
        val target = GameGestureController.Target(point.first.toFloat(), point.second.toFloat())
        val completion: (Boolean) -> Unit = { ok ->
            mainHandler.post { statusView?.text = if (ok) "Geste envoyé" else "Geste refusé ou annulé" }
        }
        when (command) {
            ResourceStrategy.SpinCommand.TAP_SINGLE, ResourceStrategy.SpinCommand.TAP_STOP ->
                GameGestureController.tap(this, target, decision.allowed, completion)
            ResourceStrategy.SpinCommand.HOLD_TO_START_AUTO ->
                return "AUTO bloqué : surveillance continue et arrêt de sécurité non armés"
            ResourceStrategy.SpinCommand.WAIT -> return "attente : ressources surveillées"
            ResourceStrategy.SpinCommand.BLOCKED -> return "bloqué : données insuffisantes"
        }
        return "geste envoyé au service Android"
    }

    private fun spinCommandLabel(value: ResourceStrategy.SpinCommand): String = when (value) {
        ResourceStrategy.SpinCommand.TAP_SINGLE -> "lancer simple (un clic bref)"
        ResourceStrategy.SpinCommand.HOLD_TO_START_AUTO -> "AUTO (maintien prolongé)"
        ResourceStrategy.SpinCommand.TAP_STOP -> "arrêter AUTO (clic bref sur STOP)"
        ResourceStrategy.SpinCommand.WAIT -> "attendre / surveiller les ressources"
        ResourceStrategy.SpinCommand.BLOCKED -> "bloqué : données insuffisantes"
    }

    private fun spinStateLabel(value: String?): String = when (value?.lowercase()) {
        "ready" -> "prêt à lancer — maintien du bouton pour activer AUTO"
        "auto_active" -> "AUTO actif — clic bref sur STOP pour arrêter"
        else -> "état inconnu — action bloquée"
    }

    private fun formatVillageCards(coinsText: String?, costsText: String?): String {
        if (costsText.isNullOrBlank()) return "Cartes village : non détectées\n"
        val coins = coinsText?.filter { it.isDigit() }?.toLongOrNull()
        val costs = costsText.split(",")
        return buildString {
            append("Cartes village (gauche → droite) :\n")
            costs.forEachIndexed { index, raw ->
                val cost = raw.trim().filter { it.isDigit() }.toLongOrNull()
                append("Carte ${index + 1} : ")
                append(if (cost == null || raw.trim() == "-1") "prix illisible" else formatCoins(cost))
                append("\n")
            }
            val affordable = costs.mapIndexedNotNull { index, raw ->
                val cost = raw.trim().filter { it.isDigit() }.toLongOrNull()
                if (cost != null && raw.trim() != "-1" && coins != null && cost <= coins) index + 1 else null
            }
            append("Abordables : ")
            append(if (coins == null) "solde illisible" else if (affordable.isEmpty()) "aucune" else affordable.joinToString(", ") { "carte $it" })
            append("\n")
        }
    }

    private fun formatCoins(value: Long): String = when {
        value >= 1_000_000_000L -> "%.2f Md".format(value / 1_000_000_000.0)
        value >= 1_000_000L -> "%.2f M".format(value / 1_000_000.0)
        else -> value.toString()
    }

    private fun finishAnalysis(message: String, buttonText: String) {
        analyzing = false
        statusView?.text = message
        analyzeButton?.apply {
            text = buttonText
            isEnabled = true
        }
        scheduleIdleCollapse()
    }

    companion object {
        @Volatile var instance: GameAssistService? = null
            private set
    }
}
