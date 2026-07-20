package com.eyecontrol.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.eyecontrol.ai.viewmodel.MainViewModel
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.graphics.PointF

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordAudioPermission = isGranted
    }

    var spokenText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceLanguage by remember { mutableStateOf("en-US") }
    val voiceLangState by viewModel.voiceLanguageFlow.collectAsState(initial = "en-US")
    
    LaunchedEffect(voiceLangState) {
        voiceLanguage = voiceLangState
    }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context.applicationContext) }
    val recognizerIntent = remember(voiceLanguage) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    spokenText = matches[0]
                }
                isListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    spokenText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        onDispose {
            speechRecognizer.destroy()
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/${context.packageName}.service.EyeControlAccessibilityService"
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Exception) {
            // Error handling
        }
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (settingValue != null) {
                return settingValue.contains(service)
            }
        }
        return false
    }

    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    LaunchedEffect(Unit) {
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eye Control AI", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { navController.navigate("about") }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        if (hasCameraPermission) {
                            val leftEyePoints by viewModel.leftEyeLandmarks.collectAsState()
                            val rightEyePoints by viewModel.rightEyeLandmarks.collectAsState()
                            val allFacePoints by viewModel.allFaceLandmarks.collectAsState()
                            val isDetecting by viewModel.isDetecting.collectAsState()
                            val detectionStatus by viewModel.detectionStatus.collectAsState()
                            val fpsVal by viewModel.fps.collectAsState()
                            val imageWidth by viewModel.imageWidth.collectAsState()
                            val imageHeight by viewModel.imageHeight.collectAsState()
                            val rotationDegrees by viewModel.rotationDegrees.collectAsState()

                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        
                                        val preview = androidx.camera.core.Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                                            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                            viewModel.detectFrame(imageProxy)
                                        }

                                        val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                imageAnalysis
                                            )
                                        } catch (exc: Exception) {
                                            // Error binding CameraX
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Canvas Overlay for Face and Eye Landmarks
                            if (isDetecting) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    val width = size.width
                                    val height = size.height

                                    if (imageWidth > 0 && imageHeight > 0) {
                                        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
                                        val rotatedWidth = if (isRotated) imageHeight else imageWidth
                                        val rotatedHeight = if (isRotated) imageWidth else imageHeight

                                        val scaleFactor = if (width / rotatedWidth > height / rotatedHeight) width / rotatedWidth else height / rotatedHeight
                                        val scaledWidth = rotatedWidth * scaleFactor
                                        val scaledHeight = rotatedHeight * scaleFactor

                                        val xOffset = (width - scaledWidth) / 2f
                                        val yOffset = (height - scaledHeight) / 2f

                                        // Draw face mesh landmarks in semi-transparent white
                                        allFacePoints.forEach { point ->
                                            val drawX = width - (point.x * scaledWidth + xOffset)
                                            val drawY = point.y * scaledHeight + yOffset
                                            drawCircle(
                                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                                                radius = 1.5.dp.toPx(),
                                                center = androidx.compose.ui.geometry.Offset(drawX, drawY)
                                            )
                                        }

                                        // Draw Left Eye landmarks in glowing Cyan
                                        leftEyePoints.forEach { point ->
                                            val drawX = width - (point.x * scaledWidth + xOffset)
                                            val drawY = point.y * scaledHeight + yOffset
                                            drawCircle(
                                                color = androidx.compose.ui.graphics.Color.Cyan,
                                                radius = 3.dp.toPx(),
                                                center = androidx.compose.ui.geometry.Offset(drawX, drawY)
                                            )
                                        }

                                        // Draw Right Eye landmarks in glowing Yellow
                                        rightEyePoints.forEach { point ->
                                            val drawX = width - (point.x * scaledWidth + xOffset)
                                            val drawY = point.y * scaledHeight + yOffset
                                            drawCircle(
                                                color = androidx.compose.ui.graphics.Color.Yellow,
                                                radius = 3.dp.toPx(),
                                                center = androidx.compose.ui.geometry.Offset(drawX, drawY)
                                            )
                                        }
                                    } else {
                                        // Fallback layout if dimensions are not yet received
                                        allFacePoints.forEach { point ->
                                            // Mirror horizontally because front camera is mirrored in preview
                                            val drawX = (1f - point.x) * width
                                            val drawY = point.y * height
                                            drawCircle(
                                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                                                radius = 1.5.dp.toPx(),
                                                center = androidx.compose.ui.geometry.Offset(drawX, drawY)
                                            )
                                        }

                                        // Draw Left Eye landmarks in glowing Cyan
                                        leftEyePoints.forEach { point ->
                                            val drawX = (1f - point.x) * width
                                            val drawY = point.y * height
                                            drawCircle(
                                                color = androidx.compose.ui.graphics.Color.Cyan,
                                                radius = 3.dp.toPx(),
                                                center = androidx.compose.ui.geometry.Offset(drawX, drawY)
                                            )
                                        }

                                        // Draw Right Eye landmarks in glowing Yellow
                                        rightEyePoints.forEach { point ->
                                            val drawX = (1f - point.x) * width
                                            val drawY = point.y * height
                                            drawCircle(
                                                color = androidx.compose.ui.graphics.Color.Yellow,
                                                radius = 3.dp.toPx(),
                                                center = androidx.compose.ui.geometry.Offset(drawX, drawY)
                                            )
                                        }
                                    }
                                }
                            }

                            // Camera Indicator Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("FRONT CAM ACTIVE", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            // FPS Counter Badge
                            if (isDetecting && fpsVal > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("FPS: $fpsVal", color = MaterialTheme.colorScheme.onTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Overlay when detection is active but no face is found
                            if (isDetecting && detectionStatus == "No face detected") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = "No Face",
                                            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "No face detected",
                                            color = androidx.compose.ui.graphics.Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Camera Off", modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Camera permission required", fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text("Grant Permission")
                                }
                            }
                        }
                    }

                    if (hasCameraPermission) {
                        val isDetecting by viewModel.isDetecting.collectAsState()
                        val detectionStatus by viewModel.detectionStatus.collectAsState()

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Detection Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val indicatorColor = when (detectionStatus) {
                                        "Eyes Detected" -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
                                        "Face Detected" -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
                                        "No face detected" -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
                                        else -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue (Camera Ready)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(indicatorColor, RoundedCornerShape(50))
                                    )
                                    Text(
                                        text = detectionStatus,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.startDetection() },
                                    enabled = !isDetecting,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { viewModel.stopDetection() },
                                    enabled = isDetecting,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Voice Typing (Speech Recognition)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    
                    OutlinedTextField(
                        value = spokenText,
                        onValueChange = { spokenText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        placeholder = { Text("Spoken text will appear here...") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lang: $voiceLanguage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        
                        if (hasRecordAudioPermission) {
                            Button(
                                onClick = {
                                    if (isListening) {
                                        speechRecognizer.stopListening()
                                        isListening = false
                                    } else {
                                        speechRecognizer.startListening(recognizerIntent)
                                        isListening = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = "Voice Input"
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isListening) "Listening..." else "Start Listening")
                            }
                        } else {
                            Button(onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                                Text("Enable Microphone")
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Accessibility Service", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                if (isAccessibilityEnabled) "Active and running" else "Inactive",
                                color = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Status",
                            tint = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        "The accessibility service allows Eye Control AI to perform gestures and interact with other apps on your screen using eye movements.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isAccessibilityEnabled) "Open Accessibility Settings" else "Enable in Settings")
                    }
                }
            }
        }
    }
}
