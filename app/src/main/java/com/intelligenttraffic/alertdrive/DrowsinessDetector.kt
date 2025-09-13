package com.intelligenttraffic.alertdrive

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Simple Real-Time Drowsiness Detection System
 * Chỉ giữ lại những thứ thực sự cần thiết
 */
class DrowsinessDetector(
    private val context: Context,
    private val onStateChanged: (DrowsinessState) -> Unit,
    private val onOverlayUpdate: (Bitmap?) -> Unit,
    private val onFrameUpdate: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val EAR_THRESHOLD = 0.15f
        private const val CONSECUTIVE_FRAMES = 5
    }

    private var frameSkipCounter = 0
    private var currentState = DrowsinessState()
    private var consecutiveClosedCount = 0
    private var totalFrameCount = 0

    // Simple face detector
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .setMinFaceSize(0.15f)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // Process every 3 frames for better performance
        frameSkipCounter++
        if (frameSkipCounter % 3 != 0) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        onFrameUpdate(bitmap)

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    processFaces(faces, bitmap)
                }
                .addOnFailureListener {
                    handleNoFace(bitmap)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processFaces(faces: List<Face>, bitmap: Bitmap) {
        totalFrameCount++

        if (faces.isEmpty()) {
            handleNoFace(bitmap)
            return
        }

        // Take the first face (simplest approach)
        val face = faces[0]

        // Calculate EAR from eye open probability (most reliable)
        val ear = calculateEAR(face)

        // Check if eyes are closed
        val isEyesClosed = ear < EAR_THRESHOLD

        if (isEyesClosed) {
            consecutiveClosedCount++
        } else {
            consecutiveClosedCount = 0
        }

        // Determine alert level
        val alertLevel = when {
            consecutiveClosedCount >= CONSECUTIVE_FRAMES -> AlertLevel.CRITICAL
            consecutiveClosedCount >= 2 -> AlertLevel.WARNING
            else -> AlertLevel.NORMAL
        }

        // Update state
        currentState = currentState.copy(
            faceDetected = true,
            eyeAspectRatio = ear,
            consecutiveClosedFrames = consecutiveClosedCount,
            alertLevel = alertLevel
        )

        onStateChanged(currentState)
        drawOverlay(bitmap, face, alertLevel)

        // Simple logging
        if (totalFrameCount % 15 == 0) {
            Log.d("DrowsinessDetector", "EAR: %.3f | Closed: %d/%d | Alert: %s"
                .format(ear, consecutiveClosedCount, CONSECUTIVE_FRAMES, alertLevel))
        }
    }

    private fun calculateEAR(face: Face): Float {
        val leftProb = face.leftEyeOpenProbability
        val rightProb = face.rightEyeOpenProbability

        if (leftProb != null && rightProb != null) {
            // Use average eye open probability as EAR
            return (leftProb + rightProb) / 2f
        }

        // Fallback to default
        return 0.3f
    }

    private fun drawOverlay(bitmap: Bitmap, face: Face, alertLevel: AlertLevel) {
        val overlayBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlayBitmap)

        drawBoundingBox(canvas, face, alertLevel)

        onOverlayUpdate(overlayBitmap)
    }

    private fun drawBoundingBox(canvas: Canvas, face: Face, alertLevel: AlertLevel) {
        val color = when (alertLevel) {
            AlertLevel.CRITICAL -> Color.RED
            AlertLevel.WARNING -> Color.YELLOW
            AlertLevel.NORMAL -> Color.GREEN
        }

        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        // Get face bounding box
        val bounds = face.boundingBox
        val rect = RectF(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat()
        )

        // Add small padding
        val padding = 8f
        rect.left -= padding
        rect.top -= padding
        rect.right += padding
        rect.bottom += padding

        // Ensure box stays within screen bounds
        rect.left = maxOf(0f, rect.left)
        rect.top = maxOf(0f, rect.top)
        rect.right = minOf(canvas.width.toFloat(), rect.right)
        rect.bottom = minOf(canvas.height.toFloat(), rect.bottom)

        canvas.drawRect(rect, paint)
    }

    private fun handleNoFace(bitmap: Bitmap) {
        consecutiveClosedCount = 0

        currentState = currentState.copy(
            faceDetected = false,
            eyeAspectRatio = 0.0f,
            consecutiveClosedFrames = 0,
            alertLevel = AlertLevel.NORMAL
        )

        onStateChanged(currentState)
        onOverlayUpdate(null) // Clear overlay

        if (totalFrameCount % 30 == 0) {
            Log.d("DrowsinessDetector", "No face detected")
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val image = imageProxy.image ?: return null

            // Simple conversion
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) return bitmap

            // Fallback
            val yuvImage = android.graphics.YuvImage(
                bytes,
                android.graphics.ImageFormat.NV21,
                image.width,
                image.height,
                null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, image.width, image.height),
                90,
                out
            )
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("DrowsinessDetector", "Bitmap conversion error", e)
            null
        }
    }

    fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.e("DrowsinessDetector", "Error closing detector", e)
        }
    }
}
