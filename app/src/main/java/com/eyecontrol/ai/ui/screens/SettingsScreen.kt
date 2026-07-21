package com.eyecontrol.ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.eyecontrol.ai.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    val darkTheme by viewModel.darkThemeFlow.collectAsState(initial = false)
    val cameraSelection by viewModel.cameraSelectionFlow.collectAsState(initial = "Front")
    val voiceLanguage by viewModel.voiceLanguageFlow.collectAsState(initial = "en-US")

    val cursorSpeed by viewModel.cursorSpeedFlow.collectAsState(initial = 1.0f)
    val cursorSmoothing by viewModel.cursorSmoothingFlow.collectAsState(initial = 0.5f)
    val cursorSize by viewModel.cursorSizeFlow.collectAsState(initial = 40)
    val isCalibrated by viewModel.isCalibratedFlow.collectAsState(initial = false)

    val dwellTime by viewModel.dwellTimeFlow.collectAsState(initial = 1000)
    val blinkSensitivity by viewModel.blinkSensitivityFlow.collectAsState(initial = 1.0f)
    val scrollSpeed by viewModel.scrollSpeedFlow.collectAsState(initial = 5.0f)
    val dragSpeed by viewModel.dragSpeedFlow.collectAsState(initial = 5.0f)
    val clickDelay by viewModel.clickDelayFlow.collectAsState(initial = 300)
    val enableBlinkClick by viewModel.enableBlinkClickFlow.collectAsState(initial = true)
    val enableDwellClick by viewModel.enableDwellClickFlow.collectAsState(initial = true)

    var showCameraDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: General Settings
            Text("General Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Dark Mode", fontSize = 16.sp)
                    Text("Enable dark interface style", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = darkTheme,
                    onCheckedChange = { viewModel.setDarkTheme(it) }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCameraDialog = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Camera Selection", fontSize = 16.sp)
                    Text("Current: $cameraSelection", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Voice Typing Language", fontSize = 16.sp)
                    Text("Current: $voiceLanguage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            // Section: Eye Cursor Engine
            Spacer(modifier = Modifier.height(8.dp))
            Text("Eye Cursor Engine", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

            // Cursor Speed
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cursor Speed", fontSize = 16.sp)
                    Text(String.format("%.1fx", cursorSpeed), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = cursorSpeed,
                    onValueChange = { viewModel.setCursorSpeed(it) },
                    valueRange = 0.1f..5.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            // Cursor Smoothing
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Jitter Smoothing (EMA)", fontSize = 16.sp)
                    Text(String.format("%.2f", cursorSmoothing), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = cursorSmoothing,
                    onValueChange = { viewModel.setCursorSmoothing(it) },
                    valueRange = 0.0f..0.99f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            // Cursor Size
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cursor Size", fontSize = 16.sp)
                    Text("${cursorSize}dp", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = cursorSize.toFloat(),
                    onValueChange = { viewModel.setCursorSize(it.toInt()) },
                    valueRange = 20f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            // Section: Hands-Free & Gestures
            Spacer(modifier = Modifier.height(8.dp))
            Text("Hands-Free & Gestures", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

            // Dwell Click Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dwell Click", fontSize = 16.sp)
                    Text("Auto-trigger clicks when gaze is held still", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = enableDwellClick,
                    onCheckedChange = { viewModel.setEnableDwellClick(it) }
                )
            }

            if (enableDwellClick) {
                // Dwell Time Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dwell Time Delay", fontSize = 16.sp)
                        Text("${dwellTime}ms", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = dwellTime.toFloat(),
                        onValueChange = { viewModel.setDwellTime(it.toInt()) },
                        valueRange = 300f..2000f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider()

            // Blink Click Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Blink Actions", fontSize = 16.sp)
                    Text("Trigger gestures via ocular blinks (Left = Back, Right = Click)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = enableBlinkClick,
                    onCheckedChange = { viewModel.setEnableBlinkClick(it) }
                )
            }

            if (enableBlinkClick) {
                // Blink Sensitivity Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Blink Threshold Multiplier", fontSize = 16.sp)
                        Text(String.format("%.2f", blinkSensitivity), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = blinkSensitivity,
                        onValueChange = { viewModel.setBlinkSensitivity(it) },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider()

            // Scroll Speed Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("System Scroll Speed", fontSize = 16.sp)
                    Text(String.format("%.1fx", scrollSpeed), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = scrollSpeed,
                    onValueChange = { viewModel.setScrollSpeed(it) },
                    valueRange = 1.0f..10.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            // Calibration Info & Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Calibration Status", fontSize = 16.sp)
                    SuggestionChip(
                        onClick = {},
                        label = { Text(if (isCalibrated) "Calibrated" else "Not Calibrated") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            labelColor = if (isCalibrated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    )
                }

                Button(
                    onClick = { navController.navigate("calibration") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isCalibrated) "Recalibrate Gaze" else "Start Gaze Calibration")
                }

                if (isCalibrated) {
                    OutlinedButton(
                        onClick = { viewModel.clearCalibration() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Calibration Data")
                    }
                }
            }

            HorizontalDivider()

            Button(
                onClick = { viewModel.resetSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Settings")
            }
        }
    }

    if (showCameraDialog) {
        AlertDialog(
            onDismissRequest = { showCameraDialog = false },
            title = { Text("Select Camera") },
            text = {
                Column {
                    listOf("Front", "Back", "Wide Angle").forEach { cam ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setCameraSelection(cam)
                                    showCameraDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            RadioButton(selected = cameraSelection == cam, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cam)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCameraDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Voice Language") },
            text = {
                Column {
                    listOf("en-US", "es-ES", "fr-FR", "de-DE", "ja-JP").forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setVoiceLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            RadioButton(selected = voiceLanguage == lang, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
            }
        )
    }
}
