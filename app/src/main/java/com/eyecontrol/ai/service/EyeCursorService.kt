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

class LowPassFilter(private var alpha: Float) {
    private var lastValue: Float? = null

    fun filter(value: Float, alpha: Float): Float {
        val lastVal = lastValue ?: run {
            lastValue = value
            return value
        }
        val result = alpha * value + (1f - alpha) * lastVal
        lastValue = result
        return result
    }

    fun lastValue(): Float? = lastValue
    
    fun reset() {
        lastValue = null
    }
}

class OneEuroFilter(
    private val minCutoff: Float = 0.5f,
    private val beta: Float = 0.02f,
    private val dCutoff: Float = 1.0f
) {
    private val xFilter = LowPassFilter(1f)
    private val dxFilter = LowPassFilter(1f)
    private var lastTime: Long = 0L

    fun filter(value: Float, timestamp: Long): Float {
        if (lastTime == 0L) {
            lastTime = timestamp
            return xFilter.filter(value, 1f)
        }
        val dt = ((timestamp - lastTime).coerceAtLeast(1L)) / 1000f
        lastTime = timestamp
        if (dt <= 0f) {
            return xFilter.lastValue() ?: value
        }

        val prevX = xFilter.lastValue() ?: value
        val dx = (value - prevX) / dt
        val alphaD = alpha(dt, dCutoff)
        val edx = dxFilter.filter(dx, alphaD)
        
        val cutoff = minCutoff + beta * kotlin.math.abs(edx)
        val alpha = alpha(dt, cutoff)
        return xFilter.filter(value, alpha)
    }

    private fun alpha(dt: Float, cutoff: Float): Float {
        val tau = 1f / (2f * kotlin.math.PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    fun reset() {
        xFilter.reset()
        dxFilter.reset()
        lastTime = 0L
    }
}

class KalmanFilter(
    private var q: Float = 0.03f,
    private var r: Float = 0.6f
) {
    private var x: Float? = null
    private var p: Float = 1.0f

    fun update(measurement: Float, customQ: Float? = null, customR: Float? = null): Float {
        val currentQ = customQ ?: q
        val currentR = customR ?: r
        val currentX = x ?: run {
            x = measurement
            return measurement
        }

        p += currentQ

        val k = p / (p + currentR)
        val newX = currentX + k * (measurement - currentX)
        p *= (1f - k)

        x = newX
        return newX
    }

    fun reset() {
        x = null
        p = 1.0f
    }
}

class VelocityTracker {
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastTime: Long = 0L
    
    var velocityX: Float = 0f
        private set
    var velocityY: Float = 0f
        private set
    var speed: Float = 0f
        private set

    fun addMovement(x: Float, y: Float, timestamp: Long) {
        if (lastTime == 0L) {
            lastX = x
            lastY = y
            lastTime = timestamp
            return
        }
        val dt = ((timestamp - lastTime).coerceAtLeast(1L)) / 1000f
        if (dt > 0.001f) {
            val instVx = (x - lastX) / dt
            val instVy = (y - lastY) / dt
            
            val alpha = 0.25f
            velocityX = alpha * instVx + (1f - alpha) * velocityX
            velocityY = alpha * instVy + (1f - alpha) * velocityY
            speed = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)
            
            lastX = x
            lastY = y
            lastTime = timestamp
        }
    }

    fun reset() {
        lastTime = 0L
        velocityX = 0f
        velocityY = 0f
        speed = 0f
    }
}

class AccelerationEngine {
    fun getAccelerationMultiplier(speed: Float, distance: Float): Float {
        return when {
            distance < 8f -> {
                val ratio = distance / 8f
                0.04f + 0.16f * (ratio * ratio)
            }
            distance < 45f -> {
                val ratio = (distance - 8f) / 37f
                0.2f + 0.8f * ratio
            }
            else -> {
                val ratio = ((distance - 45f) / 150f).coerceAtMost(2.5f)
                1.0f + 1.6f * ratio
            }
        }
    }
}

class PredictionEngine(private val basePredictionSeconds: Float = 0.025f) {
    fun predict(currentX: Float, currentY: Float, velocityX: Float, velocityY: Float, speed: Float): Pair<Float, Float> {
        if (speed < 50f) {
            return Pair(currentX, currentY)
        }
        val predFactor = (speed / 800f).coerceIn(0.1f, 1.0f) * basePredictionSeconds
        val predX = currentX + velocityX * predFactor
        val predY = currentY + velocityY * predFactor
        return Pair(predX, predY)
    }
}

class MotionInterpolator {
    private var currentX: Float = -1f
    private var currentY: Float = -1f

    fun interpolate(targetX: Float, targetY: Float, dt: Float, responsiveness: Float): Pair<Float, Float> {
        if (currentX == -1f || currentY == -1f) {
            currentX = targetX
            currentY = targetY
            return Pair(targetX, targetY)
        }
        val factor = 1.0f - kotlin.math.exp(-responsiveness * dt)
        currentX = currentX + (targetX - currentX) * factor
        currentY = currentY + (targetY - currentY) * factor
        return Pair(currentX, currentY)
    }

    fun setPosition(x: Float, y: Float) {
        currentX = x
        currentY = y
    }

    fun reset() {
        currentX = -1f
        currentY = -1f
    }
}

class DeadZoneProcessor {
    fun process(targetX: Float, targetY: Float, lastX: Float, lastY: Float, deadZoneVal: Float): Pair<Float, Float> {
        if (lastX == -1f || lastY == -1f) return Pair(targetX, targetY)
        
        val dx = targetX - lastX
        val dy = targetY - lastY
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        
        val threshold = if (distance < 15f) deadZoneVal else 1.0f
        
        if (distance < threshold) {
            return Pair(lastX, lastY)
        }
        
        val ratio = (distance - threshold) / distance
        val newX = lastX + dx * ratio
        val newY = lastY + dy * ratio
        return Pair(newX, newY)
    }
}

class PrecisionController {
    private var clickableRects = mutableListOf<android.graphics.Rect>()
    private var lastQueryTime = 0L
    private val queryInterval = 250L
    
    fun updateClickableRegions(service: android.accessibilityservice.AccessibilityService?, now: Long) {
        if (service == null) return
        if (now - lastQueryTime < queryInterval) return
        
        lastQueryTime = now
        val rootNode = service.rootInActiveWindow
        if (rootNode != null) {
            clickableRects.clear()
            findClickableRects(rootNode, clickableRects)
            rootNode.recycle()
        }
    }
    
    private fun findClickableRects(
        node: android.view.accessibility.AccessibilityNodeInfo?,
        outRects: MutableList<android.graphics.Rect>
    ) {
        if (node == null) return
        if (node.isClickable && node.isVisibleToUser) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                outRects.add(rect)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            findClickableRects(child, outRects)
        }
    }
    
    fun getSpeedModifier(cursorX: Float, cursorY: Float, baseSnapDistance: Float): Float {
        if (clickableRects.isEmpty()) return 1.0f
        
        var minDistance = Float.MAX_VALUE
        for (rect in clickableRects) {
            val cx = rect.centerX().toFloat()
            val cy = rect.centerY().toFloat()
            val dx = cursorX - cx
            val dy = cursorY - cy
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        
        val influenceRange = baseSnapDistance * 1.5f
        return if (minDistance < influenceRange) {
            val ratio = minDistance / influenceRange
            0.4f + 0.6f * ratio
        } else {
            1.0f
        }
    }
}

class EdgeClamp {
    fun clamp(x: Float, y: Float, width: Float, height: Float, margin: Float = 5f): Pair<Float, Float> {
        val clampedX = x.coerceIn(margin, width - margin)
        val clampedY = y.coerceIn(margin, height - margin)
        return Pair(clampedX, clampedY)
    }
}

class CursorEngine {
    private val oneEuroX = OneEuroFilter()
    private val oneEuroY = OneEuroFilter()
    private val kalmanX = KalmanFilter()
    private val kalmanY = KalmanFilter()
    private val velocityTracker = VelocityTracker()
    private val accelerationEngine = AccelerationEngine()
    private val predictionEngine = PredictionEngine()
    private val motionInterpolator = MotionInterpolator()
    private val edgeClamp = EdgeClamp()
    private val precisionController = PrecisionController()
    private val deadZoneProcessor = DeadZoneProcessor()
    
    private var lastX = -1f
    private var lastY = -1f
    private var lastTime = 0L

    fun process(
        rawTargetX: Float,
        rawTargetY: Float,
        screenWidth: Float,
        screenHeight: Float,
        curSpeed: Float,
        speedProfile: Int,
        deadZoneVal: Int,
        enablePrecisionMode: Boolean,
        enableTurboMode: Boolean,
        snapDistanceVal: Int,
        confidence: Float,
        accessibilityService: android.accessibilityservice.AccessibilityService?,
        now: Long
    ): Pair<Float, Float> {
        if (confidence < 0.5f) {
            return Pair(if (lastX == -1f) screenWidth / 2f else lastX, if (lastY == -1f) screenHeight / 2f else lastY)
        }

        if (lastTime == 0L || lastX == -1f || lastY == -1f) {
            lastX = rawTargetX
            lastY = rawTargetY
            lastTime = now
            oneEuroX.reset()
            oneEuroY.reset()
            kalmanX.reset()
            kalmanY.reset()
            velocityTracker.reset()
            motionInterpolator.setPosition(rawTargetX, rawTargetY)
            return Pair(rawTargetX, rawTargetY)
        }

        val dt = ((now - lastTime).coerceAtLeast(1L)) / 1000f
        lastTime = now

        val oX = oneEuroX.filter(rawTargetX, now)
        val oY = oneEuroY.filter(rawTargetY, now)

        val kX = kalmanX.update(oX)
        val kY = kalmanY.update(oY)

        val dxRaw = kX - lastX
        val dyRaw = kY - lastY
        val distRaw = kotlin.math.sqrt(dxRaw * dxRaw + dyRaw * dyRaw)

        val actualDeadZone = deadZoneVal.toFloat().coerceAtLeast(1.5f)
        val (dzX, dzY) = deadZoneProcessor.process(kX, kY, lastX, lastY, actualDeadZone)

        if (dzX == lastX && dzY == lastY) {
            velocityTracker.reset()
            return Pair(lastX, lastY)
        }

        velocityTracker.addMovement(dzX, dzY, now)
        val velocityX = velocityTracker.velocityX
        val velocityY = velocityTracker.velocityY
        val currentSpeed = velocityTracker.speed

        var speedMultiplier = curSpeed
        when (speedProfile) {
            0 -> speedMultiplier *= 0.5f
            2 -> speedMultiplier *= 1.5f
        }

        val accelMultiplier = accelerationEngine.getAccelerationMultiplier(currentSpeed, distRaw)
        speedMultiplier *= accelMultiplier

        precisionController.updateClickableRegions(accessibilityService, now)
        val precisionModifier = precisionController.getSpeedModifier(lastX, lastY, snapDistanceVal.toFloat())
        if (enablePrecisionMode) {
            speedMultiplier *= precisionModifier
        }

        val dxFiltered = dzX - lastX
        val dyFiltered = dzY - lastY
        
        var finalTargetX = lastX + dxFiltered * speedMultiplier
        var finalTargetY = lastY + dyFiltered * speedMultiplier

        if (currentSpeed > 60f) {
            val (predX, predY) = predictionEngine.predict(finalTargetX, finalTargetY, velocityX, velocityY, currentSpeed)
            val mix = (currentSpeed / 1000f).coerceIn(0f, 0.6f)
            finalTargetX = (1f - mix) * finalTargetX + mix * predX
            finalTargetY = (1f - mix) * finalTargetY + mix * predY
        }

        val baseResponsiveness = 10f
        val adaptiveResponsiveness = if (currentSpeed < 40f) {
            4f
        } else if (distRaw > 200f && enableTurboMode) {
            18f
        } else {
            baseResponsiveness
        }
        
        val (interpolatedX, interpolatedY) = motionInterpolator.interpolate(
            finalTargetX,
            finalTargetY,
            dt,
            adaptiveResponsiveness
        )

        val (clampedX, clampedY) = edgeClamp.clamp(interpolatedX, interpolatedY, screenWidth, screenHeight)

        lastX = clampedX
        lastY = clampedY

        return Pair(clampedX, clampedY)
    }

    fun recenter(centerX: Float, centerY: Float) {
        lastX = centerX
        lastY = centerY
        oneEuroX.reset()
        oneEuroY.reset()
        kalmanX.reset()
        kalmanY.reset()
        velocityTracker.reset()
        motionInterpolator.setPosition(centerX, centerY)
    }

    fun reset() {
        lastX = -1f
        lastY = -1f
        lastTime = 0L
        oneEuroX.reset()
        oneEuroY.reset()
        kalmanX.reset()
        kalmanY.reset()
        velocityTracker.reset()
        motionInterpolator.reset()
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
    private val cursorEngine = CursorEngine()

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
    private var hasBothTriggered = false
    private var stationaryStartTime = 0L
    private var lastClickTime = 0L


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
                                if (bothClosedTime == 0L) {
                                    bothClosedTime = now
                                    hasBothTriggered = false
                                }
                                if (!hasBothTriggered && (now - bothClosedTime >= 2000L)) {
                                    hasBothTriggered = true
                                    EyeControlAccessibilityService.getInstance()?.performSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                                    lastGestureTime = now
                                    vibrateFeedback()
                                    speakFeedback("Home")
                                }
                                leftClosedTime = 0L
                                rightClosedTime = 0L
                            } else if (isLeftClosed) {
                                if (leftClosedTime == 0L && bothClosedTime == 0L) {
                                    leftClosedTime = now
                                }
                                bothClosedTime = 0L
                                rightClosedTime = 0L
                            } else if (isRightClosed) {
                                if (rightClosedTime == 0L && bothClosedTime == 0L) {
                                    rightClosedTime = now
                                }
                                bothClosedTime = 0L
                                leftClosedTime = 0L
                            } else {
                                if (bothClosedTime > 0L) {
                                    bothClosedTime = 0L
                                    hasBothTriggered = false
                                }
                                if (leftClosedTime > 0L) {
                                    val duration = now - leftClosedTime
                                    leftClosedTime = 0L
                                    if (duration in 700..900) {
                                        EyeControlAccessibilityService.getInstance()?.performSystemAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                                        lastGestureTime = now
                                        vibrateFeedback()
                                        speakFeedback("Back")
                                    }
                                }
                                if (rightClosedTime > 0L) {
                                    val duration = now - rightClosedTime
                                    rightClosedTime = 0L
                                    if (duration in 700..900) {
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
                                }
                                bothClosedTime = 0L
                                leftClosedTime = 0L
                                rightClosedTime = 0L
                                hasBothTriggered = false
                            }
                        } else {
                            bothClosedTime = 0L
                            leftClosedTime = 0L
                            rightClosedTime = 0L
                            hasBothTriggered = false
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
        if (!isFaceDetected.value) {
            stableDwellX = -1f
            stableDwellY = -1f
            stationaryStartTime = 0L
            dwellStartTime = 0L
            hasTriggeredDwellClick = false
            mainHandler.post {
                cursorView?.dwellProgress = 0f
            }
            return
        }

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

        val targetX = screenWidth / 2f + normX * (screenWidth / 2f)
        val targetY = screenHeight / 2f + normY * (screenHeight / 2f)

        val now = SystemClock.uptimeMillis()
        val accessibilityService = EyeControlAccessibilityService.getInstance()
        
        val (finalX, finalY) = cursorEngine.process(
            rawTargetX = targetX,
            rawTargetY = targetY,
            screenWidth = screenWidth.toFloat(),
            screenHeight = screenHeight.toFloat(),
            curSpeed = curSpeed,
            speedProfile = speedProfile,
            deadZoneVal = deadZone,
            enablePrecisionMode = enablePrecisionMode,
            enableTurboMode = enableTurboMode,
            snapDistanceVal = snapDistance,
            confidence = if (isFaceDetected.value) 1.0f else 0.0f,
            accessibilityService = accessibilityService,
            now = now
        )

        lastX = finalX
        lastY = finalY
        lastTrackingTimestamp = now

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
                            isAtTopEdge -> accessibilityService?.performScroll(screenWidth / 2f, screenHeight / 2f, 0, scrollAmount)
                            isAtBottomEdge -> accessibilityService?.performScroll(screenWidth / 2f, screenHeight / 2f, 0, -scrollAmount)
                            isAtLeftEdge -> accessibilityService?.performScroll(screenWidth / 2f, screenHeight / 2f, scrollAmount, 0)
                            isAtRightEdge -> accessibilityService?.performScroll(screenWidth / 2f, screenHeight / 2f, -scrollAmount, 0)
                        }
                    }
                }
            } else {
                edgeStayStartTime = 0L
            }
        }

        // Dwell Click detection
        if (enableDwellClick) {
            if (now - lastClickTime < 1500L) {
                stableDwellX = finalX
                stableDwellY = finalY
                stationaryStartTime = 0L
                dwellStartTime = 0L
                hasTriggeredDwellClick = false
                mainHandler.post {
                    cursorView?.dwellProgress = 0f
                }
            } else {
                if (stableDwellX == -1f || stableDwellY == -1f) {
                    stableDwellX = finalX
                    stableDwellY = finalY
                    stationaryStartTime = now
                    dwellStartTime = 0L
                    hasTriggeredDwellClick = false
                }

                val distFromStable = sqrt((finalX - stableDwellX) * (finalX - stableDwellX) + (finalY - stableDwellY) * (finalY - stableDwellY))

                if (distFromStable < 3f) {
                    if (stationaryStartTime == 0L) {
                        stationaryStartTime = now
                    } else {
                        val stationaryElapsed = now - stationaryStartTime
                        if (stationaryElapsed >= 300L) {
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
                                    accessibilityService?.performClick(finalX, finalY)
                                    hasTriggeredDwellClick = true
                                    lastClickTime = now
                                    vibrateFeedback()
                                    triggerRippleEffect()
                                    speakFeedback("Selected")
                                    mainHandler.post {
                                        cursorView?.dwellProgress = 0f
                                    }
                                }
                            }
                        } else {
                            mainHandler.post {
                                cursorView?.dwellProgress = 0f
                            }
                        }
                    }
                } else {
                    stableDwellX = finalX
                    stableDwellY = finalY
                    stationaryStartTime = now
                    dwellStartTime = 0L
                    hasTriggeredDwellClick = false
                    mainHandler.post {
                        cursorView?.dwellProgress = 0f
                    }
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
        cursorEngine.recenter(screenWidth / 2f, screenHeight / 2f)
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
        cursorEngine.reset()
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
