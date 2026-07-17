package com.handsfree.controller

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.net.Uri

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.handsfree.controller/actions"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkAccessibilityPermission" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                "openAccessibilitySettings" -> {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    result.success(true)
                }
                "checkOverlayPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        result.success(Settings.canDrawOverlays(this))
                    } else {
                        result.success(true)
                    }
                }
                "openOverlaySettings" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                        result.success(true)
                    } else {
                        result.success(false)
                    }
                }
                "startService" -> {
                    val intent = Intent(this, ForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    result.success(true)
                }
                "stopService" -> {
                    val intent = Intent(this, ForegroundService::class.java)
                    stopService(intent)
                    result.success(true)
                }
                "executeAction" -> {
                    val actionName = call.argument<String>("action") ?: ""
                    val intent = Intent("com.handsfree.controller.EXECUTE_ACTION").apply {
                        putExtra("ACTION", actionName)
                        setPackage(packageName)
                    }
                    
                    // Put extra coordinates if specified
                    call.argument<Double>("x")?.let { intent.putExtra("X", it.toFloat()) }
                    call.argument<Double>("y")?.let { intent.putExtra("Y", it.toFloat()) }
                    call.argument<Double>("startX")?.let { intent.putExtra("START_X", it.toFloat()) }
                    call.argument<Double>("startY")?.let { intent.putExtra("START_Y", it.toFloat()) }
                    call.argument<Double>("endX")?.let { intent.putExtra("END_X", it.toFloat()) }
                    call.argument<Double>("endY")?.let { intent.putExtra("END_Y", it.toFloat()) }
                    call.argument<Int>("duration")?.let { intent.putExtra("DURATION", it) }
                    call.argument<String>("text")?.let { intent.putExtra("TEXT", it) }

                    sendBroadcast(intent)
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return HandsFreeAccessibilityService.instance != null
    }
}
