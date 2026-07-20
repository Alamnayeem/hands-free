package com.eyecontrol.ai.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class EyeControlAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("EyeControlService", "Accessibility Event received: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.d("EyeControlService", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("EyeControlService", "Accessibility Service Connected")
    }
}
