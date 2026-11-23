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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showFloatingButton()
    }

    override fun onDestroy() {
        removeFloatingButton()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    private fun showFloatingButton() {
        if (floatingButton != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
        runCatching { windowManager?.addView(button, params); floatingButton = button }
    }

    private fun removeFloatingButton() {
        floatingButton?.let { view -> runCatching { windowManager?.removeView(view) } }
        floatingButton = null
    }

    private fun analyzeFromGame(button: TextView) {
        button.isEnabled = false
        button.text = "Lecture…"
        capture { bitmap, error ->
            if (bitmap == null) {
                button.text = "Erreur capture"
                button.isEnabled = true
                return@capture
            }
            scope.launch {
                val store = SecureStore(this@GameAssistService)
                val api = store.apiKey()
                if (api.isNullOrBlank()) {
                    button.text = "Clé absente"
                    button.isEnabled = true
                    return@launch
                }
                runCatching { OpenRouterVision().analyze(api, store.model(), bitmap) }
                    .onSuccess { analysis ->
                        button.text = if (analysis.recognized && analysis.confidence >= 0.75) {
                            "Jeu ${analysis.confidence.times(100).toInt()}%"
                        } else "Jeu non reconnu"
                    }
                    .onFailure { button.text = "Erreur IA" }
                button.isEnabled = true
            }
        }
    }

    fun capture(callback: (Bitmap?, String?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) { callback(null, "Capture non prise en charge"); return }
        takeScreenshot(Display.DEFAULT_DISPLAY, Executor { it.run() }, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hw = result.hardwareBuffer
                val cs = result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
                val bmp = Bitmap.wrapHardwareBuffer(hw, cs)?.copy(Bitmap.Config.ARGB_8888, false)
                hw.close()
                callback(bmp, if (bmp == null) "Conversion image impossible" else null)
            }
            override fun onFailure(errorCode: Int) { callback(null, "Échec capture écran: $errorCode") }
        })
    }

    fun tap(x: Float, y: Float, done: (Boolean) -> Unit) {
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 80)).build()
        dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = done(false)
        }, null)
    }

    companion object {
        @Volatile var instance: GameAssistService? = null
            private set
    }
}
