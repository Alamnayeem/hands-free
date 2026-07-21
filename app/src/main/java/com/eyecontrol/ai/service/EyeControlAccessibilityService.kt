package com.eyecontrol.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class EyeControlAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: EyeControlAccessibilityService? = null

        fun getInstance(): EyeControlAccessibilityService? = instance
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("EyeControlService", "Accessibility Event received: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.d("EyeControlService", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("EyeControlService", "Accessibility Service Connected")
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun performClick(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun performDoubleClick(x: Float, y: Float) {
        val path1 = Path().apply { moveTo(x, y) }
        val path2 = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0L, 50L)
        val stroke2 = GestureDescription.StrokeDescription(path2, 200L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke1).addStroke(stroke2).build()
        dispatchGesture(gesture, null, null)
    }

    fun performLongPress(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 800L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun performSystemAction(actionId: Int) {
        performGlobalAction(actionId)
    }
}
