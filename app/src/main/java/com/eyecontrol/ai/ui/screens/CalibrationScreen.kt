package com.eyecontrol.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.eyecontrol.ai.helper.FaceLandmarkerHelper
import com.eyecontrol.ai.viewmodel.MainViewModel
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

enum class CalibrationStep {
    CENTER, LEFT, RIGHT, UP, DOWN, DONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var currentStep by remember { mutableStateOf(CalibrationStep.CENTER) }
    var faceDetected by remember { mutableStateOf(false) }

    // Live detected values
    var liveXRatio by remember { mutableStateOf(0.5f) }
    var liveYRatio by remember { mutableStateOf(0.5f) }

    // Captured calibration coordinates
    var centerX by remember { mutableStateOf(0.5f) }
    var centerY by remember { mutableStateOf(0.5f) }
    var leftX by remember { mutableStateOf(0.45f) }
    var leftY by remember { mutableStateOf(0.5f) }
    var rightX by remember { mutableStateOf(0.55f) }
    var rightY by remember { mutableStateOf(0.5f) }
    var upX by remember { mutableStateOf(0.5f) }
    var upY by remember { mutableStateOf(0.45f) }
    var downX by remember { mutableStateOf(0.5f) }
    var downY by remember { mutableStateOf(0.55f) }

    var faceLandmarkerHelper by remember { mutableStateOf<FaceLandmarkerHelper?>(null) }

    // Initialize FaceLandmarkerHelper locally for calibration capturing
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission && faceLandmarkerHelper == null) {
            faceLandmarkerHelper = FaceLandmarkerHelper(
                context,
                object : FaceLandmarkerHelper.LandmarkerListener {
                    override fun onError(error: String) {
                        Log.e("CalibrationScreen", "Error: $error")
                    }

                    override fun onResults(
                        result: FaceLandmarkerResult,
                        inputImageWidth: Int,
                        inputImageHeight: Int,
                        rotationDegrees: Int
                    ) {
                        val landmarks = result.faceLandmarks()
                        if (landmarks.isNullOrEmpty()) {
                            faceDetected = false
                            return
                        }
                        faceDetected = true

                        val face = landmarks[0]
                        val leftEyeIndices = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)
                        val rightEyeIndices = listOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398)

                        val facePoints = face.map { lm ->
                            com.eyecontrol.ai.helper.FaceLandmarkerHelper.transformLandmark(lm.x(), lm.y(), rotationDegrees, true)
                        }

                        val leftPoints = leftEyeIndices.map { facePoints[it] }
                        val rightPoints = rightEyeIndices.map { facePoints[it] }

                        val irisLeft = facePoints.getOrNull(473)
                        val irisRight = facePoints.getOrNull(468)

                        if (leftPoints.isNotEmpty() && rightPoints.isNotEmpty()) {
                            val leftXmin = leftPoints.map { it.x }.minOrNull() ?: 0f
                            val leftXmax = leftPoints.map { it.x }.maxOrNull() ?: 1f
                            val leftYmin = leftPoints.map { it.y }.minOrNull() ?: 0f
                            val leftYmax = leftPoints.map { it.y }.maxOrNull() ?: 1f

                            val rightXmin = rightPoints.map { it.x }.minOrNull() ?: 0f
                            val rightXmax = rightPoints.map { it.x }.maxOrNull() ?: 1f
                            val rightYmin = rightPoints.map { it.y }.minOrNull() ?: 0f
                            val rightYmax = rightPoints.map { it.y }.maxOrNull() ?: 1f

                            val irisLeftX = irisLeft?.x ?: ((leftXmin + leftXmax) / 2f)
                            val irisLeftY = irisLeft?.y ?: ((leftYmin + leftYmax) / 2f)
                            val irisRightX = irisRight?.x ?: ((rightXmin + rightXmax) / 2f)
                            val irisRightY = irisRight?.y ?: ((rightYmin + rightYmax) / 2f)

                            val leftXRatio = if (leftXmax - leftXmin > 0f) (irisLeftX - leftXmin) / (leftXmax - leftXmin) else 0.5f
                            val leftYRatio = if (leftYmax - leftYmin > 0f) (irisLeftY - leftYmin) / (leftYmax - leftYmin) else 0.5f
                            val rightXRatio = if (rightXmax - rightXmin > 0f) (irisRightX - rightXmin) / (rightXmax - rightXmin) else 0.5f
                            val rightYRatio = if (rightYmax - rightYmin > 0f) (irisRightY - rightYmin) / (rightYmax - rightYmin) else 0.5f

                            liveXRatio = (leftXRatio + rightXRatio) / 2f
                            liveYRatio = (leftYRatio + rightYRatio) / 2f
                        }
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            faceLandmarkerHelper?.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gaze Calibration") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Camera permission is required to calibrate gaze.", textAlign = TextAlign.Center)
                    Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Background minimal camera feed
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier
                        .size(100.dp, 130.dp)
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                ) { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = androidx.camera.core.Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                            faceLandmarkerHelper?.detectLiveStream(imageProxy)
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("CalibrationScreen", "CameraX bind error: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(context))
                }

                // Interactive target point drawing based on steps
                if (currentStep != CalibrationStep.DONE) {
                    var targetAlignment = Alignment.Center
                    var stepName = "Center"
                    var stepInstructions = "Look directly at the center target dot and click Capture."

                    when (currentStep) {
                        CalibrationStep.CENTER -> {
                            targetAlignment = Alignment.Center
                            stepName = "Step 1: Center Calibration"
                            stepInstructions = "Stare directly at the center yellow dot, hold your head still, then press Capture."
                        }
                        CalibrationStep.LEFT -> {
                            targetAlignment = Alignment.CenterStart
                            stepName = "Step 2: Left Boundary"
                            stepInstructions = "Stare at the left edge dot, then press Capture."
                        }
                        CalibrationStep.RIGHT -> {
                            targetAlignment = Alignment.CenterEnd
                            stepName = "Step 3: Right Boundary"
                            stepInstructions = "Stare at the right edge dot, then press Capture."
                        }
                        CalibrationStep.UP -> {
                            targetAlignment = Alignment.TopCenter
                            stepName = "Step 4: Top Boundary"
                            stepInstructions = "Stare at the top edge dot, then press Capture."
                        }
                        CalibrationStep.DOWN -> {
                            targetAlignment = Alignment.BottomCenter
                            stepName = "Step 5: Bottom Boundary"
                            stepInstructions = "Stare at the bottom edge dot, then press Capture."
                        }
                        else -> {}
                    }

                    // Draw Interactive Gaze Target Point
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = targetAlignment
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color.Yellow, CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.Red, CircleShape)
                            )
                        }
                    }

                    // Content card overlay with instructions
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .align(Alignment.BottomStart),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stepName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                            Text(stepInstructions, textAlign = TextAlign.Center, fontSize = 14.sp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(if (faceDetected) Color.Green else Color.Red, CircleShape)
                                    )
                                    Text(if (faceDetected) "Signal OK" else "Adjust Face", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(String.format("Iris: %.3f, %.3f", liveXRatio, liveYRatio), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }

                            Button(
                                onClick = {
                                    when (currentStep) {
                                        CalibrationStep.CENTER -> {
                                            centerX = liveXRatio
                                            centerY = liveYRatio
                                            currentStep = CalibrationStep.LEFT
                                        }
                                        CalibrationStep.LEFT -> {
                                            leftX = liveXRatio
                                            leftY = liveYRatio
                                            currentStep = CalibrationStep.RIGHT
                                        }
                                        CalibrationStep.RIGHT -> {
                                            rightX = liveXRatio
                                            rightY = liveYRatio
                                            currentStep = CalibrationStep.UP
                                        }
                                        CalibrationStep.UP -> {
                                            upX = liveXRatio
                                            upY = liveYRatio
                                            currentStep = CalibrationStep.DOWN
                                        }
                                        CalibrationStep.DOWN -> {
                                            downX = liveXRatio
                                            downY = liveYRatio
                                            
                                            // Persist to Datastore Settings
                                            viewModel.saveCalibration(
                                                centerX, centerY,
                                                leftX, leftY,
                                                rightX, rightY,
                                                upX, upY,
                                                downX, downY
                                            )
                                            currentStep = CalibrationStep.DONE
                                        }
                                        else -> {}
                                    }
                                },
                                enabled = faceDetected,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Capture Point")
                            }
                        }
                    }
                } else {
                    // Success Completion State
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text("Calibration Complete!", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Your gaze calibration vectors have been computed and saved. You can now use the Gaze Cursor Engine with high accuracy.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                )
                                Button(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Back to Settings")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
