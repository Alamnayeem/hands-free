package com.eyecontrol.ai.helper

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object ModelDownloader {
    private const val TAG = "ModelDownloader"

    fun downloadModel(context: Context, urlString: String, outputFile: File): Boolean {
        if (outputFile.exists()) {
            return true
        }
        try {
            outputFile.parentFile?.mkdirs()
            Log.d(TAG, "Starting model download from: ${urlString}")
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connect()
            connection.getInputStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesRead = input.read(buffer)
                    }
                }
            }
            Log.d(TAG, "Model downloaded successfully to: ${outputFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return false
        }
    }
}
