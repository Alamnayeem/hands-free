package com.eyecontrol.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.eyecontrol.ai.data.SettingsRepository
import com.eyecontrol.ai.helper.FaceLandmarkerHelper
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt
import android.os.Vibrator
import android.os.VibrationEffect
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.media.MediaPlayer

class SimpleLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}

class CursorOverlayView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val strokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val progressPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val ripplePaint = Paint().apply {
        color = Color.argb(128, 0, 255, 0)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    var cursorSize = 40f
        set(value) {
            field = value
            invalidate()
        }

    var cursorColor: Int = Color.RED
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    var dwellProgress = 0f // 0f to 1f
        set(value) {
            field = value
            invalidate()
        }

    var showProgressRing = true
        set(value) {
            field = value
            invalidate()
        }

    var rippleRadius = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = cursorSize / 2f
        
        // Draw main cursor point
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawCircle(cx, cy, radius - 2f, strokePaint)

        // Draw dwell progress circular progress ring
        if (showProgressRing && dwellProgress > 0f) {
            val progressRadius = radius + 10f
            progressPaint.color = cursorColor
            canvas.drawArc(
                cx - progressRadius, cy - progressRadius,
                cx + progressRadius, cy + progressRadius,
                -90f, 360f * dwellProgress, false, progressPaint
            )
        }

        // Draw visual ripple feedback
        if (rippleRadius > 0f) {
            ripplePaint.color = cursorColor
            canvas.drawCircle(cx, cy, rippleRadius, ripplePaint)
        }
    }
}

class FaceMeshOverlayView(context: Context) : View(context) {
    var allFacePoints: List<PointF> = emptyList()
    var leftEyePoints: List<PointF> = emptyList()
    var rightEyePoints: List<PointF> = emptyList()

    private val facePaint = Paint().apply {
        color = Color.argb(102, 255, 255, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val leftEyePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val rightEyePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        allFacePoints.forEach { pt ->
            val drawX = pt.x * w
            val drawY = pt.y * h
            canvas.drawCircle(drawX, drawY, 1.5f, facePaint)
        }

        leftEyePoints.forEach { pt ->
            val drawX = pt.x * w
            val drawY = pt.y * h
            canvas.drawCircle(drawX, drawY, 3f, leftEyePaint)
        }

        rightEyePoints.forEach { pt ->
            val drawX = pt.x * w
            val drawY = pt.y * h
            canvas.drawCircle(drawX, drawY, 3f, rightEyePaint)
        }
    }
}

class EyeCursorService : Service() {

    companion object {
        private const val CHANNEL_ID = "eye_cursor_service_channel"
        private const val NOTIFICATION_ID = 1001

        val currentRatios = MutableStateFlow<PointF?>(null)
        val isFaceDetected = MutableStateFlow(false)
    }

    private var windowManager: WindowManager? = null
    private var cursorView: CursorOverlayView? = null
    private var previewContainer: FrameLayout? = null
    private var meshOverlayView: FaceMeshOverlayView? = null

    private lateinit var cursorParams: WindowManager.LayoutParams
    private lateinit var previewParams: WindowManager.LayoutParams

    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private val lifecycleOwner = SimpleLifecycleOwner()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var repository: SettingsRepository

    private var curSpeed = 1.0f
    private var curSmoothing = 0.5f
    private var curSize = 40
    private var calibData: SettingsRepository.CalibrationData? = null

    private var screenWidth = 1080
    private var screenHeight = 1920

    private var lastX = -1f
    private var lastY = -1f
    private var lastTrackingTimestamp = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var floatingPreviewExpanded = true

    // Premium settings
    private var dwellTime = 1000
    private var blinkSensitivity = 1.0f
    private var scrollSpeed = 5.0f
    private var dragSpeed = 5.0f
    private var clickDelay = 300
    private var enableBlinkClick = true
    private var enableDwellClick = true

    // New premium settings
    private var cursorColor = 0xFFFF0000.toInt()
    private var deadZone = 10
    private var enablePrecisionMode = true
    private var enableTurboMode = true
    private var snapDistance = 80
    private var blinkHoldTime = 600
    private var gestureCooldown = 800
    private var enableAutoRecenter = true
    private var enableEdgeScroll = true
    private var enableReelNavigation = true
    private var enableVoiceCommands = true
    private var enableProgressRing = true
    private var enableVibration = true
    private var enableSoundFeedback = true
    private var speedProfile = 1

    // State tracking
    private var lastGazeMovementTime = 0L
    private var lastFaceDetectedTime = 0L
    private var edgeStayStartTime = 0L
    private var edgeScrollStartTime = 0L
    private var lastGestureTime = 0L
    private var isCursorPaused = false
    private var isPrecisionModeActive = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var openEarSum = 0f
    private var openEarCount = 0
    private var adaptiveThreshold = 0.16f

    // Dwell and blink state tracking
    private var stableDwellX = -1f
    private var stableDwellY = -1f
    private var dwellStartTime = 0L
    private var hasTriggeredDwellClick = false

    private var leftClosedTime = 0L
    private var rightClosedTime = 0L
    private var bothClosedTime = 0L
    private var wasLeftClosed = false
    private var wasRightClosed = false
    private var wasBothClosed = false


    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Fetch real screen dimensions
        val display = windowManager?.defaultDisplay
        val size = Point()
        display?.getRealSize(size)
        screenWidth = size.x
        screenHeight = size.y

        createNotificationChannel()
        setupFloatingCursor()
        setupFloatingPreview()

        lifecycleOwner.start()

        // Start listening to settings changes
        serviceScope.launch {
            repository.cursorSpeedFlow.collectLatest { curSpeed = it }
        }
        serviceScope.launch {
            repository.cursorSmoothingFlow.collectLatest { curSmoothing = it }
        }
        serviceScope.launch {
            repository.cursorSizeFlow.collectLatest { size ->
                curSize = size
                mainHandler.post {
                    if (cursorView != null) {
                        cursorView?.cursorSize = size.toFloat()
                        cursorView?.invalidate()
                    }
                }
            }
        }
        serviceScope.launch {
            repository.calibrationDataFlow.collectLatest { calibData = it }
        }
        serviceScope.launch {
            repository.dwellTimeFlow.collectLatest { dwellTime = it }
        }
        serviceScope.launch {
            repository.blinkSensitivityFlow.collectLatest { blinkSensitivity = it }
        }
        serviceScope.launch {
            repository.scrollSpeedFlow.collectLatest { scrollSpeed = it }
        }
        serviceScope.launch {
            repository.dragSpeedFlow.collectLatest { dragSpeed = it }
        }
        serviceScope.launch {
            repository.clickDelayFlow.collectLatest { clickDelay = it }
        }
        serviceScope.launch {
            repository.enableBlinkClickFlow.collectLatest { enableBlinkClick = it }
        }
        serviceScope.launch {
            repository.enableDwellClickFlow.collectLatest { enableDwellClick = it }
        }
        serviceScope.launch {
            repository.cursorColorFlow.collectLatest { color ->
                cursorColor = color
                mainHandler.post { cursorView?.cursorColor = color }
            }
        }
        serviceScope.launch {
            repository.deadZoneFlow.collectLatest { deadZone = it }
        }
        serviceScope.launch {
            repository.precisionModeFlow.collectLatest { enablePrecisionMode = it }
        }
        serviceScope.launch {
            repository.turboModeFlow.collectLatest { enableTurboMode = it }
        }
        serviceScope.launch {
            repository.snapDistanceFlow.collectLatest { snapDistance = it }
        }
        serviceScope.launch {
            repository.blinkHoldTimeFlow.collectLatest { blinkHoldTime = it }
        }
        serviceScope.launch {
            repository.gestureCooldownFlow.collectLatest { gestureCooldown = it }
        }
        serviceScope.launch {
            repository.autoRecenterFlow.collectLatest { enableAutoRecenter = it }
        }
        serviceScope.launch {
            repository.edgeScrollFlow.collectLatest { enableEdgeScroll = it }
        }
        serviceScope.launch {
            repository.reelNavigationFlow.collectLatest { enableReelNavigation = it }
        }
        serviceScope.launch {
            repository.voiceCommandsFlow.collectLatest { enabled ->
                enableVoiceCommands = enabled
                if (enabled) {
                    startVoiceRecognition()
                } else {
                    stopVoiceRecognition()
                }
            }
        }
        serviceScope.launch {
            repository.progressRingFlow.collectLatest { enableProgressRing = it }
        }
        serviceScope.launch {
            repository.vibrationFlow.collectLatest { enableVibration = it }
        }
        serviceScope.launch {
            repository.soundFeedbackFlow.collectLatest { enableSoundFeedback = it }
        }
        serviceScope.launch {
            repository.speedProfileFlow.collectLatest { speedProfile = it }
        }

        // Setup MediaPipe and CameraX
        setupCamera()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Eye Cursor Active Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun setupFloatingCursor() {
        cursorView = CursorOverlayView(this)
        cursorParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = screenWidth / 2
            y = screenHeight / 2
        }
        windowManager?.addView(cursorView, cursorParams)
    }

    private fun setupFloatingPreview() {
        previewContainer = FrameLayout(this)
        val previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewContainer?.addView(previewView)

        meshOverlayView = FaceMeshOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewContainer?.addView(meshOverlayView)

        // Little header bar for visual drag indicator
        val header = View(this).apply {
            setBackgroundColor(Color.argb(180, 98, 0, 238)) // Semi-transparent Purple
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(16)
            )
        }
        previewContainer?.addView(header)

        previewParams = WindowManager.LayoutParams(
            dpToPx(120),
            dpToPx(160),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            x = 24
            y = 120
        }

        windowManager?.addView(previewContainer, previewParams)

        // Draggable touch listener
        previewContainer?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = previewParams.x
                        initialY = previewParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        previewParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        previewParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(previewContainer, previewParams)
                        } catch (e: Exception) {}
                        return true
                    }
                }
                return false
            }
        })

        // Minimize on tap
        previewContainer?.setOnClickListener {
            toggleMinimize()
        }
    }

    private fun toggleMinimize() {
        floatingPreviewExpanded = !floatingPreviewExpanded
        if (floatingPreviewExpanded) {
            previewParams.width = dpToPx(120)
            previewParams.height = dpToPx(160)
            meshOverlayView?.visibility = View.VISIBLE
        } else {
            previewParams.width = dpToPx(36)
            previewParams.height = dpToPx(36)
            meshOverlayView?.visibility = View.GONE
        }
        try {
            windowManager?.updateViewLayout(previewContainer, previewParams)
        } catch (e: Exception) {}
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            faceLandmarkerHelper = FaceLandmarkerHelper(
                this,
                object : FaceLandmarkerHelper.LandmarkerListener {
                    override fun onError(error: String) {
                        Log.e("EyeCursorService", "MediaPipe FaceLandmarker error: $error")
                    }

                    override fun onResults(
                        result: FaceLandmarkerResult,
                        inputImageWidth: Int,
                        inputImageHeight: Int,
                        rotationDegrees: Int
                    ) {
                        val landmarks = result.faceLandmarks()
                        if (landmarks.isNullOrEmpty()) {
                            isFaceDetected.value = false
                            currentRatios.value = null
                            return
                        }
                        isFaceDetected.value = true

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

                        if (floatingPreviewExpanded && meshOverlayView != null) {
                            meshOverlayView?.allFacePoints = facePoints
                            meshOverlayView?.leftEyePoints = leftPoints
                            meshOverlayView?.rightEyePoints = rightPoints
                            meshOverlayView?.postInvalidate()
                        }

                        if (leftPoints.isEmpty() || rightPoints.isEmpty()) {
                            currentRatios.value = null
                            return
                        }

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

                        val leftWidth = abs(leftXmax - leftXmin)
                        val leftHeight = abs(leftYmax - leftYmin)
                        val leftEAR = if (leftWidth > 0f) leftHeight / leftWidth else 0f

                        val rightWidth = abs(rightXmax - rightXmin)
                        val rightHeight = abs(rightYmax - rightYmin)
                        val rightEAR = if (rightWidth > 0f) rightHeight / rightWidth else 0f

                        // Adaptive threshold learning
                        val currentOpenEAR = (leftEAR + rightEAR) / 2f
                        if (currentOpenEAR > 0.22f) {
                            openEarSum += currentOpenEAR
                            openEarCount++
                            if (openEarCount > 100) {
                                openEarSum = (openEarSum / openEarCount) * 50f
                                openEarCount = 50
                            }
                            val avgOpenEar = openEarSum / openEarCount
                            adaptiveThreshold = (avgOpenEar * 0.5f * blinkSensitivity).coerceIn(0.12f, 0.25f)
                        }

                        val isLeftClosed = leftEAR < adaptiveThreshold
                        val isRightClosed = rightEAR < adaptiveThreshold
                        val now = SystemClock.uptimeMillis()

                        if (enableBlinkClick && (now - lastGestureTime >= gestureCooldown)) {
                            if (isLeftClosed && isRightClosed) {
                                if (!wasBothClosed) {
                                    bothClosedTime = now
                                    wasBothClosed = true
                                }
                                wasLeftClosed = false
                                wasRightClosed = false
                            } else if (isLeftClosed) {
                                if (!wasLeftClosed && !wasBothClosed) {
                                    leftClosedTime = now
                                    wasLeftClosed = true
                                }
                                if (wasBothClosed) {
                                    val duration = now - bothClosedTime
                                    if (duration in 200..1200) {
                                        EyeControlAccessibilityService.getInstance()?.performSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                                        lastGestureTime = now
                                        vibrateFeedback()
                                        speakFeedback("Home")
                                    }
                                    wasBothClosed = false
                                }
                                wasRightClosed = false
                            } else if (isRightClosed) {
                                if (!wasRightClosed && !wasBothClosed) {
                                    rightClosedTime = now
                                    wasRightClosed = true
                                }
                                if (wasBothClosed) {
                                    val duration = now - bothClosedTime
                                    if (duration in 200..1200) {
                                        EyeControlAccessibilityService.getInstance()?.performSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                                        lastGestureTime = now
                                        vibrateFeedback()
                                        speakFeedback("Home")
                                    }
                                    wasBothClosed = false
                                }
                                wasLeftClosed = false
                            } else {
                                if (wasBothClosed) {
                                    val duration = now - bothClosedTime
                                    if (duration in 200..1200) {
                                        EyeControlAccessibilityService.getInstance()?.performSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                                        lastGestureTime = now
                                        vibrateFeedback()
                                        speakFeedback("Home")
                                    }
                                    wasBothClosed = false
                                }
                                if (wasLeftClosed) {
                                    val duration = now - leftClosedTime
                                    if (duration in 200..1200) {
                                        EyeControlAccessibilityService.getInstance()?.performSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                                        lastGestureTime = now
                                        vibrateFeedback()
                                        speakFeedback("Back")
                                    }
                                    wasLeftClosed = false
                                }
                                if (wasRightClosed) {
                                    val duration = now - rightClosedTime
                                    if (duration in 200..1200) {
                                        val cx = lastX
                                        val cy = lastY
                                        if (cx >= 0 && cy >= 0) {
                                            EyeControlAccessibilityService.getInstance()?.performClick(cx, cy)
                                            lastGestureTime = now
                                            vibrateFeedback()
                                            triggerRippleEffect()
                                            speakFeedback("Click")
                                        }
                                    }
                                    wasRightClosed = false
                                }
                            }
                        }

                        val leftXRatio = if (leftXmax - leftXmin > 0f) (irisLeftX - leftXmin) / (leftXmax - leftXmin) else 0.5f
                        val leftYRatio = if (leftYmax - leftYmin > 0f) (irisLeftY - leftYmin) / (leftYmax - leftYmin) else 0.5f
                        val rightXRatio = if (rightXmax - rightXmin > 0f) (irisRightX - rightXmin) / (rightXmax - rightXmin) else 0.5f
                        val rightYRatio = if (rightYmax - rightYmin > 0f) (irisRightY - rightYmin) / (rightYmax - rightYmin) else 0.5f

                        val xRatio = (leftXRatio + rightXRatio) / 2f
                        val yRatio = (leftYRatio + rightYRatio) / 2f

                        currentRatios.value = PointF(xRatio, yRatio)
                        updateCursorPosition(xRatio, yRatio)
                    }
                }
            )

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                faceLandmarkerHelper?.detectLiveStream(imageProxy)
            }

            val preview = androidx.camera.core.Preview.Builder().build().also {
                val pView = previewContainer?.getChildAt(0) as? PreviewView
                if (pView != null) {
                    it.setSurfaceProvider(pView.surfaceProvider)
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
                lifecycleOwner.start()
            } catch (e: Exception) {
                Log.e("EyeCursorService", "Camera binding failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateCursorPosition(xRatio: Float, yRatio: Float) {
        if (isCursorPaused) return

        val calib = calibData ?: return
        if (!calib.isCalibrated) return

        val epsilon = 0.001f
        var normX = 0f
        var normY = 0f

        if (xRatio < calib.centerX) {
            val dx = calib.centerX - calib.leftX
            normX = if (abs(dx) > epsilon) -((calib.centerX - xRatio) / dx) else 0f
        } else {
            val dx = calib.rightX - calib.centerX
            normX = if (abs(dx) > epsilon) (xRatio - calib.centerX) / dx else 0f
        }

        if (yRatio < calib.centerY) {
            val dy = calib.centerY - calib.upY
            normY = if (abs(dy) > epsilon) -((calib.centerY - yRatio) / dy) else 0f
        } else {
            val dy = calib.downY - calib.centerY
            normY = if (abs(dy) > epsilon) (yRatio - calib.centerY) / dy else 0f
        }

        // Apply Speed Profile
        var speedMultiplier = curSpeed
        when (speedProfile) {
            0 -> speedMultiplier *= 0.6f  // Slow
            2 -> speedMultiplier *= 1.4f  // Fast
        }

        // Precision Mode & Turbo Mode
        if (isPrecisionModeActive) {
            speedMultiplier *= 0.5f
        }

        normX *= speedMultiplier
        normY *= speedMultiplier

        val targetX = screenWidth / 2f + normX * (screenWidth / 2f)
        val targetY = screenHeight / 2f + normY * (screenHeight / 2f)

        val now = SystemClock.uptimeMillis()
        if (now - lastTrackingTimestamp > 1000 || lastX == -1f || lastY == -1f) {
            lastX = targetX
            lastY = targetY
        } else {
            val distance = sqrt((targetX - lastX) * (targetX - lastX) + (targetY - lastY) * (targetY - lastY))
            
            // Dead zone: if gaze shift is under configurable pixels, don't move to stay completely stable
            val adjustedTargetX = if (distance < deadZone.toFloat()) lastX else targetX
            val adjustedTargetY = if (distance < deadZone.toFloat()) lastY else targetY
            
            val baseAlpha = 1.0f - (curSmoothing.coerceIn(0f, 1f) * 0.95f)
            
            // Dynamic adaptive smoothing: large movements are fast, micro-movements are smooth
            var dynamicAlpha = baseAlpha
            if (distance > 150f && enableTurboMode) {
                dynamicAlpha = (baseAlpha * 2.2f).coerceAtMost(1.0f) // Turbo boost
            } else if (distance < 40f && enablePrecisionMode) {
                dynamicAlpha = (baseAlpha * 0.35f).coerceAtLeast(0.01f) // Ultra smooth precision
            }

            lastX = dynamicAlpha * adjustedTargetX + (1f - dynamicAlpha) * lastX
            lastY = dynamicAlpha * adjustedTargetY + (1f - dynamicAlpha) * lastY
        }
        lastTrackingTimestamp = now

        // Magnetic Screen-Edge Protection: Pull cursor back gently at edges
        val magneticEdgeWidth = 40f
        var protectedX = lastX
        var protectedY = lastY
        if (protectedX < magneticEdgeWidth) {
            protectedX = magneticEdgeWidth + (protectedX - magneticEdgeWidth) * 0.3f
        } else if (protectedX > screenWidth - magneticEdgeWidth) {
            protectedX = (screenWidth - magneticEdgeWidth) + (protectedX - (screenWidth - magneticEdgeWidth)) * 0.3f
        }
        if (protectedY < magneticEdgeWidth) {
            protectedY = magneticEdgeWidth + (protectedY - magneticEdgeWidth) * 0.3f
        } else if (protectedY > screenHeight - magneticEdgeWidth) {
            protectedY = (screenHeight - magneticEdgeWidth) + (protectedY - (screenHeight - magneticEdgeWidth)) * 0.3f
        }

        val finalX = protectedX.coerceIn(0f, screenWidth.toFloat())
        val finalY = protectedY.coerceIn(0f, screenHeight.toFloat())

        // Auto Edge Scrolling
        if (enableEdgeScroll) {
            val edgeThreshold = 50f
            val isAtTopEdge = finalY < edgeThreshold
            val isAtBottomEdge = finalY > screenHeight - edgeThreshold
            val isAtLeftEdge = finalX < edgeThreshold
            val isAtRightEdge = finalX > screenWidth - edgeThreshold

            if (isAtTopEdge || isAtBottomEdge || isAtLeftEdge || isAtRightEdge) {
                if (edgeStayStartTime == 0L) {
                    edgeStayStartTime = now
                } else if (now - edgeStayStartTime > 600) {
                    // Trigger scroll!
                    if (now - edgeScrollStartTime > 1500) {
                        edgeScrollStartTime = now
                        val scrollAmount = (250f * scrollSpeed / 5f).toInt()
                        when {
                            isAtTopEdge -> EyeControlAccessibilityService.getInstance()?.performScroll(screenWidth / 2f, screenHeight / 2f, 0, scrollAmount)
                            isAtBottomEdge -> EyeControlAccessibilityService.getInstance()?.performScroll(screenWidth / 2f, screenHeight / 2f, 0, -scrollAmount)
                            isAtLeftEdge -> EyeControlAccessibilityService.getInstance()?.performScroll(screenWidth / 2f, screenHeight / 2f, scrollAmount, 0)
                            isAtRightEdge -> EyeControlAccessibilityService.getInstance()?.performScroll(screenWidth / 2f, screenHeight / 2f, -scrollAmount, 0)
                        }
                    }
                }
            } else {
                edgeStayStartTime = 0L
            }
        }

        // Dwell Click detection
        if (enableDwellClick) {
            val distThreshold = 35f // pixels
            val distFromStable = sqrt((finalX - stableDwellX) * (finalX - stableDwellX) + (finalY - stableDwellY) * (finalY - stableDwellY))
            
            if (distFromStable < distThreshold) {
                if (dwellStartTime == 0L) {
                    dwellStartTime = now
                    hasTriggeredDwellClick = false
                } else if (!hasTriggeredDwellClick) {
                    val elapsed = now - dwellStartTime
                    val progress = (elapsed.toFloat() / dwellTime.toFloat()).coerceIn(0f, 1f)
                    
                    mainHandler.post {
                        cursorView?.dwellProgress = progress
                        cursorView?.showProgressRing = enableProgressRing
                    }

                    if (elapsed >= dwellTime) {
                        // Trigger Click!
                        EyeControlAccessibilityService.getInstance()?.performClick(finalX, finalY)
                        hasTriggeredDwellClick = true
                        vibrateFeedback()
                        triggerRippleEffect()
                        speakFeedback("Selected")
                        mainHandler.post {
                            cursorView?.dwellProgress = 0f
                        }
                    }
                }
            } else {
                stableDwellX = finalX
                stableDwellY = finalY
                dwellStartTime = now
                hasTriggeredDwellClick = false
                mainHandler.post {
                    cursorView?.dwellProgress = 0f
                }
            }
        } else {
            mainHandler.post {
                cursorView?.dwellProgress = 0f
            }
        }

        mainHandler.post {
            if (cursorView != null && windowManager != null) {
                try {
                    cursorParams.x = (finalX - (curSize / 2)).toInt()
                    cursorParams.y = (finalY - (curSize / 2)).toInt()
                    windowManager?.updateViewLayout(cursorView, cursorParams)
                } catch (e: Exception) {}
            }
        }
    }

    private fun triggerRippleEffect() {
        mainHandler.post {
            val duration = 300L
            val startRadius = 0f
            val endRadius = curSize * 1.5f
            val startTime = SystemClock.uptimeMillis()
            
            val rippleRunnable = object : Runnable {
                override fun run() {
                    val elapsed = SystemClock.uptimeMillis() - startTime
                    if (elapsed < duration) {
                        val fraction = elapsed.toFloat() / duration.toFloat()
                        cursorView?.rippleRadius = startRadius + (endRadius - startRadius) * fraction
                        mainHandler.postDelayed(this, 16)
                    } else {
                        cursorView?.rippleRadius = 0f
                    }
                }
            }
            mainHandler.post(rippleRunnable)
        }
    }

    private fun recenterCursor() {
        lastX = screenWidth / 2f
        lastY = screenHeight / 2f
        mainHandler.post {
            if (cursorView != null && windowManager != null) {
                try {
                    cursorParams.x = (lastX - (curSize / 2)).toInt()
                    cursorParams.y = (lastY - (curSize / 2)).toInt()
                    windowManager?.updateViewLayout(cursorView, cursorParams)
                } catch (e: Exception) {}
            }
        }
    }

    private fun snapCursorToNearestButton() {
        val rootNode = EyeControlAccessibilityService.getInstance()?.rootInActiveWindow
        if (rootNode != null) {
            val nodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
            findClickableNodes(rootNode, nodes)
            
            var nearestNode: android.view.accessibility.AccessibilityNodeInfo? = null
            var minDistance = Float.MAX_VALUE
            val currentPoint = PointF(lastX, lastY)
            
            for (node in nodes) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                val cx = rect.centerX().toFloat()
                val cy = rect.centerY().toFloat()
                val dist = sqrt((cx - currentPoint.x) * (cx - currentPoint.x) + (cy - currentPoint.y) * (cy - currentPoint.y))
                if (dist < minDistance && dist < snapDistance) {
                    minDistance = dist
                    nearestNode = node
                }
            }
            
            if (nearestNode != null) {
                val rect = android.graphics.Rect()
                nearestNode.getBoundsInScreen(rect)
                lastX = rect.centerX().toFloat()
                lastY = rect.centerY().toFloat()
                recenterCursor()
                vibrateFeedback()
            }
            rootNode.recycle()
        }
    }

    private fun findClickableNodes(node: android.view.accessibility.AccessibilityNodeInfo?, outNodes: MutableList<android.view.accessibility.AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable && node.isVisibleToUser) {
            outNodes.add(node)
        }
        for (i in 0 until node.childCount) {
            findClickableNodes(node.getChild(i), outNodes)
        }
    }

    private fun startVoiceRecognition() {
        if (!enableVoiceCommands) return
        mainHandler.post {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer?.destroy()
                }
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    Log.e("EyeCursorService", "Speech recognition not available on this device")
                    return@post
                }
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (enableVoiceCommands) {
                            mainHandler.postDelayed({ startVoiceRecognition() }, 1500)
                        }
                    }
                    override fun onResults(results: android.os.Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            processVoiceCommand(matches[0])
                        }
                        if (enableVoiceCommands) {
                            startVoiceRecognition()
                        }
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            processVoicePartial(matches[0])
                        }
                    }
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
                
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("EyeCursorService", "Failed to start speech recognition: ${e.message}")
            }
        }
    }

    private fun stopVoiceRecognition() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e("EyeCursorService", "Failed to stop speech recognizer: ${e.message}")
            }
        }
    }

    private fun processVoicePartial(text: String) {
        val cmd = text.lowercase(Locale.getDefault()).trim()
        Log.d("EyeCursorService", "Partial voice command: $cmd")
    }

    private fun processVoiceCommand(text: String) {
        val cmd = text.lowercase(Locale.getDefault()).trim()
        Log.d("EyeCursorService", "Voice command: $cmd")
        
        when {
            cmd.contains("click") || cmd.contains("select") -> {
                val cx = lastX
                val cy = lastY
                if (cx >= 0 && cy >= 0) {
                    EyeControlAccessibilityService.getInstance()?.performClick(cx, cy)
                    speakFeedback("Click")
                    playBeepSound()
                    triggerRippleEffect()
                }
            }
            cmd.contains("scroll up") || cmd.contains("up") -> {
                val scrollDist = (300f * scrollSpeed / 5f).toInt()
                EyeControlAccessibilityService.getInstance()?.performScroll(screenWidth / 2f, screenHeight / 2f, 0, scrollDist)
                speakFeedback("Scroll Up")
                playBeepSound()
            }
            cmd.contains("scroll down") || cmd.contains("down") -> {
                val scrollDist = (-300f * scrollSpeed / 5f).toInt()
                EyeControlAccessibilityService.getInstance()?.performScroll(screenWidth / 2f, screenHeight / 2f, 0, scrollDist)
                speakFeedback("Scroll Down")
                playBeepSound()
            }
            cmd.contains("back") -> {
                EyeControlAccessibilityService.getInstance()?.performSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                speakFeedback("Back")
                playBeepSound()
            }
            cmd.contains("home") -> {
                EyeControlAccessibilityService.getInstance()?.performSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                speakFeedback("Home")
                playBeepSound()
            }
            cmd.contains("pause") || cmd.contains("stop tracking") -> {
                isCursorPaused = true
                speakFeedback("Cursor paused")
                playBeepSound()
            }
            cmd.contains("resume") || cmd.contains("start tracking") -> {
                isCursorPaused = false
                speakFeedback("Cursor resumed")
                playBeepSound()
            }
            cmd.contains("precision") || cmd.contains("slow") -> {
                isPrecisionModeActive = !isPrecisionModeActive
                speakFeedback(if (isPrecisionModeActive) "Precision mode on" else "Precision mode off")
                playBeepSound()
            }
            cmd.contains("snap") || cmd.contains("button") -> {
                snapCursorToNearestButton()
                speakFeedback("Snapped")
                playBeepSound()
            }
            cmd.contains("center") || cmd.contains("recenter") -> {
                recenterCursor()
                speakFeedback("Centered")
                playBeepSound()
            }
        }
    }

    private fun speakFeedback(message: String) {
        if (!enableSoundFeedback) return
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.getDefault()
                    textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        } else {
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun playBeepSound() {
        if (!enableSoundFeedback) return
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.e("EyeCursorService", "Tone generation failed: ${e.message}")
        }
    }

    private fun vibrateFeedback() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eye Control Cursor")
            .setContentText("Eye-tracking engine is running in background.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopVoiceRecognition()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.e("EyeCursorService", "TTS shutdown failed: ${e.message}")
        }
        serviceScope.cancel()
        lifecycleOwner.stop()
        faceLandmarkerHelper?.close()
        faceLandmarkerHelper = null

        if (cursorView != null) {
            windowManager?.removeView(cursorView)
            cursorView = null
        }
        if (previewContainer != null) {
            windowManager?.removeView(previewContainer)
            previewContainer = null
        }
    }
}
