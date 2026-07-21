package com.eyecontrol.ai.viewmodel

import android.app.Application
import android.graphics.PointF
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyecontrol.ai.data.SettingsRepository
import com.eyecontrol.ai.helper.FaceLandmarkerHelper
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val darkThemeFlow: Flow<Boolean> = repository.darkThemeFlow
    val cameraSelectionFlow: Flow<String> = repository.cameraSelectionFlow
    val voiceLanguageFlow: Flow<String> = repository.voiceLanguageFlow

    val cursorSpeedFlow: Flow<Float> = repository.cursorSpeedFlow
    val cursorSmoothingFlow: Flow<Float> = repository.cursorSmoothingFlow
    val cursorSizeFlow: Flow<Int> = repository.cursorSizeFlow
    val eyeTrackingActiveFlow: Flow<Boolean> = repository.eyeTrackingActiveFlow
    val isCalibratedFlow: Flow<Boolean> = repository.isCalibratedFlow
    val calibrationDataFlow: Flow<SettingsRepository.CalibrationData> = repository.calibrationDataFlow

    val dwellTimeFlow: Flow<Int> = repository.dwellTimeFlow
    val blinkSensitivityFlow: Flow<Float> = repository.blinkSensitivityFlow
    val scrollSpeedFlow: Flow<Float> = repository.scrollSpeedFlow
    val dragSpeedFlow: Flow<Float> = repository.dragSpeedFlow
    val clickDelayFlow: Flow<Int> = repository.clickDelayFlow
    val enableBlinkClickFlow: Flow<Boolean> = repository.enableBlinkClickFlow
    val enableDwellClickFlow: Flow<Boolean> = repository.enableDwellClickFlow

    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()

    private val _detectionStatus = MutableStateFlow("Camera Ready")
    val detectionStatus: StateFlow<String> = _detectionStatus.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _leftEyeLandmarks = MutableStateFlow<List<PointF>>(emptyList())
    val leftEyeLandmarks: StateFlow<List<PointF>> = _leftEyeLandmarks.asStateFlow()

    private val _rightEyeLandmarks = MutableStateFlow<List<PointF>>(emptyList())
    val rightEyeLandmarks: StateFlow<List<PointF>> = _rightEyeLandmarks.asStateFlow()

    private val _allFaceLandmarks = MutableStateFlow<List<PointF>>(emptyList())
    val allFaceLandmarks: StateFlow<List<PointF>> = _allFaceLandmarks.asStateFlow()

    private val _imageWidth = MutableStateFlow(0)
    val imageWidth: StateFlow<Int> = _imageWidth.asStateFlow()

    private val _imageHeight = MutableStateFlow(0)
    val imageHeight: StateFlow<Int> = _imageHeight.asStateFlow()

    private val _rotationDegrees = MutableStateFlow(0)
    val rotationDegrees: StateFlow<Int> = _rotationDegrees.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(enabled)
        }
    }

    fun setCameraSelection(camera: String) {
        viewModelScope.launch {
            repository.setCameraSelection(camera)
        }
    }

    fun setVoiceLanguage(language: String) {
        viewModelScope.launch {
            repository.setVoiceLanguage(language)
        }
    }

    fun setCursorSpeed(speed: Float) {
        viewModelScope.launch {
            repository.setCursorSpeed(speed)
        }
    }

    fun setCursorSmoothing(smoothing: Float) {
        viewModelScope.launch {
            repository.setCursorSmoothing(smoothing)
        }
    }

    fun setCursorSize(size: Int) {
        viewModelScope.launch {
            repository.setCursorSize(size)
        }
    }

    fun setEyeTrackingActive(active: Boolean) {
        viewModelScope.launch {
            repository.setEyeTrackingActive(active)
        }
    }

    fun saveCalibration(
        centerX: Float, centerY: Float,
        leftX: Float, leftY: Float,
        rightX: Float, rightY: Float,
        upX: Float, upY: Float,
        downX: Float, downY: Float
    ) {
        viewModelScope.launch {
            repository.saveCalibration(
                centerX, centerY,
                leftX, leftY,
                rightX, rightY,
                upX, upY,
                downX, downY
            )
        }
    }

    fun clearCalibration() {
        viewModelScope.launch {
            repository.clearCalibration()
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            repository.resetSettings()
        }
    }

    fun setDwellTime(time: Int) {
        viewModelScope.launch { repository.setDwellTime(time) }
    }

    fun setBlinkSensitivity(sensitivity: Float) {
        viewModelScope.launch { repository.setBlinkSensitivity(sensitivity) }
    }

    fun setScrollSpeed(speed: Float) {
        viewModelScope.launch { repository.setScrollSpeed(speed) }
    }

    fun setDragSpeed(speed: Float) {
        viewModelScope.launch { repository.setDragSpeed(speed) }
    }

    fun setClickDelay(delay: Int) {
        viewModelScope.launch { repository.setClickDelay(delay) }
    }

    fun setEnableBlinkClick(enabled: Boolean) {
        viewModelScope.launch { repository.setEnableBlinkClick(enabled) }
    }

    fun setEnableDwellClick(enabled: Boolean) {
        viewModelScope.launch { repository.setEnableDwellClick(enabled) }
    }

    fun startDetection() {
        if (_isDetecting.value) return
        _isDetecting.value = true
        _detectionStatus.value = "Camera Ready"
        
        if (faceLandmarkerHelper == null) {
            faceLandmarkerHelper = FaceLandmarkerHelper(
                getApplication(),
                object : FaceLandmarkerHelper.LandmarkerListener {
                    private var frameCount = 0
                    private var lastFpsTimestamp = 0L

                    override fun onError(error: String) {
                        _detectionStatus.value = "Error: ${error}"
                    }

                    override fun onResults(
                        result: FaceLandmarkerResult,
                        inputImageWidth: Int,
                        inputImageHeight: Int,
                        rotationDegrees: Int
                    ) {
                        _imageWidth.value = inputImageWidth
                        _imageHeight.value = inputImageHeight
                        _rotationDegrees.value = rotationDegrees

                        val landmarks = result.faceLandmarks()
                        if (landmarks.isNullOrEmpty()) {
                            _detectionStatus.value = "No face detected"
                            _leftEyeLandmarks.value = emptyList()
                            _rightEyeLandmarks.value = emptyList()
                            _allFaceLandmarks.value = emptyList()
                        } else {
                            val face = landmarks[0]
                            _detectionStatus.value = "Face Detected"
                            
                            val leftEyeIndices = listOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)
                            val rightEyeIndices = listOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398)
                            
                            val facePoints = face.map { lm ->
                                com.eyecontrol.ai.helper.FaceLandmarkerHelper.transformLandmark(lm.x(), lm.y(), rotationDegrees, true)
                            }
                            val leftPoints = leftEyeIndices.map { idx -> facePoints[idx] }
                            val rightPoints = rightEyeIndices.map { idx -> facePoints[idx] }
                            
                            _leftEyeLandmarks.value = leftPoints
                            _rightEyeLandmarks.value = rightPoints
                            _allFaceLandmarks.value = facePoints
                            
                            if (leftPoints.isNotEmpty() && rightPoints.isNotEmpty()) {
                                _detectionStatus.value = "Eyes Detected"
                            }
                        }

                        // Calculate FPS
                        val now = SystemClock.uptimeMillis()
                        frameCount++
                        if (now - lastFpsTimestamp >= 1000) {
                            _fps.value = frameCount
                            frameCount = 0
                            lastFpsTimestamp = now
                        }
                    }
                }
            )
        }
    }

    fun stopDetection() {
        _isDetecting.value = false
        _detectionStatus.value = "Camera Ready"
        _leftEyeLandmarks.value = emptyList()
        _rightEyeLandmarks.value = emptyList()
        _allFaceLandmarks.value = emptyList()
        _imageWidth.value = 0
        _imageHeight.value = 0
        _rotationDegrees.value = 0
        _fps.value = 0
        
        faceLandmarkerHelper?.close()
        faceLandmarkerHelper = null
    }

    fun detectFrame(imageProxy: ImageProxy) {
        val helper = faceLandmarkerHelper
        if (_isDetecting.value && helper != null) {
            helper.detectLiveStream(imageProxy)
        } else {
            imageProxy.close()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDetection()
    }
}
