package fr.boardaventure.ia
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.Path
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executor

class GameAssistService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
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
    companion object { @Volatile var instance: GameAssistService? = null; private set }
}
