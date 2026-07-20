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

    fun resetSettings() {
        viewModelScope.launch {
            repository.resetSettings()
        }
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
                        inputImageHeight: Int
                    ) {
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
                            
                            val leftPoints = leftEyeIndices.map { idx ->
                                val lm = face.get(idx)
                                PointF(lm.x(), lm.y())
                            }
                            val rightPoints = rightEyeIndices.map { idx ->
                                val lm = face.get(idx)
                                PointF(lm.x(), lm.y())
                            }
                            
                            _leftEyeLandmarks.value = leftPoints
                            _rightEyeLandmarks.value = rightPoints
                            _allFaceLandmarks.value = face.map { lm ->
                                PointF(lm.x(), lm.y())
                            }
                            
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
