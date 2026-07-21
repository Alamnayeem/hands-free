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
    var cursorSize = 40f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = cursorSize / 2f
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        canvas.drawCircle(width / 2f, height / 2f, radius - 2f, strokePaint)
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

        normX *= curSpeed
        normY *= curSpeed

        val targetX = screenWidth / 2f + normX * (screenWidth / 2f)
        val targetY = screenHeight / 2f + normY * (screenHeight / 2f)

        val alpha = 1.0f - (curSmoothing.coerceIn(0f, 1f) * 0.95f)

        val now = SystemClock.uptimeMillis()
        if (now - lastTrackingTimestamp > 1000 || lastX == -1f || lastY == -1f) {
            lastX = targetX
            lastY = targetY
        } else {
            lastX = alpha * targetX + (1f - alpha) * lastX
            lastY = alpha * targetY + (1f - alpha) * lastY
        }
        lastTrackingTimestamp = now

        val finalX = lastX.coerceIn(curSize.toFloat(), (screenWidth - curSize).toFloat())
val finalY = lastY.coerceIn(curSize.toFloat(), (screenHeight - curSize).toFloat())

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
