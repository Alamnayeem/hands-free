package com.eyecontrol.ai

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.eyecontrol.ai.ui.theme.EyeControlAITheme
import com.eyecontrol.ai.viewmodel.MainViewModel
import com.eyecontrol.ai.ui.screens.SplashScreen
import com.eyecontrol.ai.ui.screens.HomeScreen
import com.eyecontrol.ai.ui.screens.SettingsScreen
import com.eyecontrol.ai.ui.screens.AboutScreen
import com.eyecontrol.ai.ui.screens.CalibrationScreen
import com.eyecontrol.ai.service.EyeCursorService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val darkTheme by viewModel.darkThemeFlow.collectAsState(initial = false)
            val eyeTrackingActive by viewModel.eyeTrackingActiveFlow.collectAsState(initial = false)

            LaunchedEffect(eyeTrackingActive) {
                if (eyeTrackingActive) {
                    if (Settings.canDrawOverlays(this@MainActivity)) {
                        val intent = Intent(this@MainActivity, EyeCursorService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    } else {
                        viewModel.setEyeTrackingActive(false)
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${packageName}")
                        )
                        startActivity(intent)
                    }
                } else {
                    val intent = Intent(this@MainActivity, EyeCursorService::class.java)
                    stopService(intent)
                }
            }

            EyeControlAITheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            SplashScreen(navController = navController)
                        }
                        composable("home") {
                            HomeScreen(navController = navController, viewModel = viewModel)
                        }
                        composable("settings") {
                            SettingsScreen(navController = navController, viewModel = viewModel)
                        }
                        composable("about") {
                            AboutScreen(navController = navController)
                        }
                        composable("calibration") {
                            CalibrationScreen(navController = navController, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
