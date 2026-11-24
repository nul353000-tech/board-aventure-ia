package fr.boardaventure.ia

import android.accessibilityservice.AccessibilityService
import android.graphics.Path
import android.os.Build
import android.view.GestureDescription
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility gesture adapter. The caller must pass an explicit authorization
 * from a device-calibrated policy. Screenshot estimates alone are not authorization.
 */
internal object GameGestureController {
    data class Target(val x: Float, val y: Float)

    fun tap(
        service: AccessibilityService,
        target: Target,
        authorized: Boolean,
        callback: (Boolean) -> Unit
    ) {
        dispatch(service, target, 70L, authorized, callback)
    }

    fun hold(
        service: AccessibilityService,
        target: Target,
        durationMs: Long = 1_200L,
        authorized: Boolean,
        callback: (Boolean) -> Unit
    ) {
        dispatch(service, target, durationMs.coerceIn(700L, 2_500L), authorized, callback)
    }

    private fun dispatch(
        service: AccessibilityService,
        target: Target,
        durationMs: Long,
        authorized: Boolean,
        callback: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < 24 || !authorized ||
            !target.x.isFinite() || !target.y.isFinite() ||
            target.x < 0f || target.y < 0f
        ) {
            callback(false)
            return
        }
        val path = Path().apply { moveTo(target.x, target.y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = runCatching {
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback(false)
                }
            }, null)
        }.getOrDefault(false)
        if (!accepted) callback(false)
    }
}
