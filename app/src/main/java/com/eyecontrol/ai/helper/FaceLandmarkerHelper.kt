package com.eyecontrol.ai.helper

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceLandmarkerHelper(
    private val context: Context,
    private val listener: LandmarkerListener
) {
    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(result: FaceLandmarkerResult, inputImageWidth: Int, inputImageHeight: Int, rotationDegrees: Int)
    }

    private var faceLandmarker: FaceLandmarker? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var lastRotationDegrees = 0

    init {
        executor.execute {
            setupFaceLandmarker()
        }
    }

    private fun setupFaceLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")

            val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setMinFaceDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, inputImage ->
                    listener.onResults(result, inputImage.width, inputImage.height, lastRotationDegrees)
                }
                .setErrorListener { error ->
                    listener.onError(error.message ?: "Unknown MediaPipe error")
                }

            faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            listener.onError("Failed to initialize Face Landmarker: ${e.message}")
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy) {
        if (faceLandmarker == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        lastRotationDegrees = rotationDegrees
        val bitmap = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.e("FaceLandmarkerHelper", "Error converting ImageProxy to Bitmap: ${e.message}")
            null
        } finally {
            imageProxy.close()
        }

        if (bitmap == null) {
            return
        }

        executor.execute {
            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val imageProcessingOptions = ImageProcessingOptions.builder()
                    .setRotationDegrees(rotationDegrees)
                    .build()
                
                val frameTime = SystemClock.uptimeMillis()
                faceLandmarker?.detectAsync(mpImage, imageProcessingOptions, frameTime)
            } catch (e: Exception) {
                Log.e("FaceLandmarkerHelper", "Error processing image: ${e.message}")
            }
        }
    }

    fun close() {
        executor.execute {
            faceLandmarker?.close()
            faceLandmarker = null
        }
        executor.shutdown()
    }
}
