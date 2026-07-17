package com.handsfree.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.media.AudioManager

class HandsFreeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HandsFreeAccessService"
        var instance: HandsFreeAccessibilityService? = null
            private set
    }

    private var commandReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        instance = this

        // Register broadcast receiver to accept commands from Flutter
        commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.getStringExtra("ACTION") ?: return
                Log.d(TAG, "Received command broadcast: $action")
                executeCommand(action, intent)
            }
        }
        val filter = IntentFilter("com.handsfree.controller.EXECUTE_ACTION")
        registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can be used to inspect active UI elements or read text
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (commandReceiver != null) {
            unregisterReceiver(commandReceiver)
        }
    }

    /**
     * Executes the incoming hands-free action
     */
    private fun executeCommand(action: String, intent: Intent) {
        when (action) {
            "TAP" -> {
                val x = intent.getFloatExtra("X", 0f)
                val y = intent.getFloatExtra("Y", 0f)
                performTap(x, y)
            }
            "DOUBLE_TAP" -> {
                val x = intent.getFloatExtra("X", 0f)
                val y = intent.getFloatExtra("Y", 0f)
                performDoubleTap(x, y)
            }
            "LONG_PRESS" -> {
                val x = intent.getFloatExtra("X", 0f)
                val y = intent.getFloatExtra("Y", 0f)
                performLongPress(x, y)
            }
            "SCROLL_UP" -> performScroll(true)
            "SCROLL_DOWN" -> performScroll(false)
            "SWIPE" -> {
                val startX = intent.getFloatExtra("START_X", 0f)
                val startY = intent.getFloatExtra("START_Y", 0f)
                val endX = intent.getFloatExtra("END_X", 0f)
                val endY = intent.getFloatExtra("END_Y", 0f)
                val duration = intent.getIntExtra("DURATION", 300)
                performSwipe(startX, startY, endX, endY, duration)
            }
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "QUICK_SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "SCREENSHOT" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    Log.w(TAG, "Screenshot global action is only supported on Android 9+")
                }
            }
            "LOCK_SCREEN" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    Log.w(TAG, "Lock screen is only supported on Android 9+")
                }
            }
            "VOLUME_UP" -> adjustVolume(true)
            "VOLUME_DOWN" -> adjustVolume(false)
            "TYPE_TEXT" -> {
                val text = intent.getStringExtra("TEXT") ?: ""
                inputTextIntoFocusedField(text)
            }
        }
    }

    private fun performTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performDoubleTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .addStroke(GestureDescription.StrokeDescription(path, 200, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performLongPress(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performScroll(up: Boolean) {
        // Find a scrollable area or perform swipe-like gestures on the screen
        val path = Path()
        // Standard swipe bounds to mimic scrolling: Top 25% <-> Bottom 75%
        if (up) {
            // Swipe down to scroll up
            path.moveTo(500f, 400f)
            path.lineTo(500f, 1500f)
        } else {
            // Swipe up to scroll down
            path.moveTo(500f, 1500f)
            path.lineTo(500f, 400f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Int) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong()))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun adjustVolume(increase: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun inputTextIntoFocusedField(text: String) {
        val rootNode = rootInActiveWindow ?: return
        val focusedNode = findFocusedInput(rootNode)
        if (focusedNode != null) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "Input text successful: $text")
        } else {
            Log.w(TAG, "No focused text field found to type: $text")
        }
    }

    private fun findFocusedInput(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.className == "android.widget.EditText") {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedInput(child)
            if (found != null) return found
        }
        return null
    }
}
