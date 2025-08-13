package com.intelligenttraffic.alertdrive

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.sqrt

/**
 * Real-Time Drowsiness Detection System
 * Dựa trên dự án: https://github.com/AnshumanSrivastava108/Real-Time-Drowsiness-Detection-System
 * 
 * Thuật toán chính xác:
 * 1. Phát hiện khuôn mặt bằng Haar Cascade (ML Kit thay thế)
 * 2. Phát hiện 68 landmarks (sử dụng ML Kit landmarks)
 * 3. Tính EAR (Eye Aspect Ratio) từ 6 điểm mắt
 * 4. Nếu EAR < 0.25 trong 5 frames liên tiếp → Alert
 * 5. Phát âm thanh Alert.wav
 */
class DrowsinessDetector(
    private val context: Context,
    private val onStateChanged: (DrowsinessState) -> Unit,
    private val onOverlayUpdate: (Bitmap?) -> Unit,
    private val onFrameUpdate: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        // Thuật toán chính xác dựa trên ML Kit eye open probability
        private const val EAR_THRESHOLD = 0.15f      // Ngưỡng cho eye open probability (< 0.15 = mắt nhắm)
        private const val CONSECUTIVE_FRAMES = 8      // Tăng lên 8 frames để tránh false positive
        private const val FRAME_CHECK_COUNT = 20      // Đếm tối đa 20 frames
        
        // Eye landmark indices for EAR calculation (similar to dlib 68-point model)
        // Left eye: 36, 37, 38, 39, 40, 41
        // Right eye: 42, 43, 44, 45, 46, 47
    }

    private var frameSkipCounter = 0
    private var currentState = DrowsinessState()
    private var consecutiveClosedCount = 0
    private var totalFrameCount = 0

    // Enhanced ML Kit Face Detector với contours và tracking để có face box chính xác
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Accurate mode cho contours
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)        // Cần landmarks cho EAR
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Eye open probability
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)          // Enable contours cho face box chính xác
            .enableTracking()                                              // Enable face tracking
            .setMinFaceSize(0.15f)  // Giảm min size để detect được face nhỏ hơn
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        // Tối ưu: chỉ xử lý mỗi 4 frames để giảm lag
        frameSkipCounter++
        if (frameSkipCounter % 4 != 0) {
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
                    processFaceDetection(faces, bitmap)
                }
                .addOnFailureListener { 
                    handleNoFaceDetected(bitmap)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processFaceDetection(faces: List<Face>, bitmap: Bitmap) {
        totalFrameCount++
        
        if (faces.isEmpty()) {
            handleNoFaceDetected(bitmap)
            return
        }

        val face = faces[0] // Sử dụng khuôn mặt đầu tiên
        
        // Tính EAR từ 16-point eye contours - chính xác như documentation
        val eyeAspectRatio = calculateEARFromContours(face)
        
        // Kiểm tra mắt nhắm theo ngưỡng
        val isEyesClosed = eyeAspectRatio < EAR_THRESHOLD
        
        if (isEyesClosed) {
            consecutiveClosedCount++
        } else {
            consecutiveClosedCount = 0
        }

        // Determine alert level với logic chính xác hơn
        val alertLevel = when {
            consecutiveClosedCount >= CONSECUTIVE_FRAMES -> AlertLevel.CRITICAL
            consecutiveClosedCount >= (CONSECUTIVE_FRAMES / 2) -> AlertLevel.WARNING
            else -> AlertLevel.NORMAL
        }

        // Update state
        currentState = currentState.copy(
            faceDetected = true,
            eyeAspectRatio = eyeAspectRatio,
            consecutiveClosedFrames = consecutiveClosedCount,
            alertLevel = alertLevel
        )

        onStateChanged(currentState)
        drawAccurateFaceTracking(bitmap, face, eyeAspectRatio, consecutiveClosedCount, alertLevel)
        
        // Log chi tiết để debug
        if (totalFrameCount % 10 == 0) { // Log mỗi 10 frames để không spam
            Log.d("DrowsinessDetector", "Frame $totalFrameCount: EAR=%.3f | Threshold=%.3f | Closed=%d/%d | Eyes=%s"
                .format(eyeAspectRatio, EAR_THRESHOLD, consecutiveClosedCount, CONSECUTIVE_FRAMES, 
                if (isEyesClosed) "CLOSED" else "OPEN"))
        }
        
        // Log alert khi có cảnh báo
        if (alertLevel != AlertLevel.NORMAL) {
            Log.w("DrowsinessDetector", "🚨 ALERT: EAR=%.3f | Frames=%d/%d | Level=%s"
                .format(eyeAspectRatio, consecutiveClosedCount, CONSECUTIVE_FRAMES, alertLevel))
        }
    }

    private fun handleNoFaceDetected(bitmap: Bitmap) {
        consecutiveClosedCount = 0
        
        currentState = currentState.copy(
            faceDetected = false,
            eyeAspectRatio = 0.0f,
            consecutiveClosedFrames = 0,
            alertLevel = AlertLevel.NORMAL
        )
        
        onStateChanged(currentState)
        drawAccurateFaceTracking(bitmap, null, 0.0f, 0, AlertLevel.NORMAL)
    }

    /**
     * Tính EAR chính xác từ ML Kit eye open probability
     * Theo tài liệu Google ML Kit Android official
     */
    private fun calculateEARFromContours(face: Face): Float {
        // Sử dụng eye open probability từ ML Kit (chính xác nhất)
        val leftEyeOpenProb = face.leftEyeOpenProbability
        val rightEyeOpenProb = face.rightEyeOpenProbability
        
        if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
            // Average của 2 mắt
            val avgEyeOpenProb = (leftEyeOpenProb + rightEyeOpenProb) / 2f
            
            // Convert sang EAR: probability càng cao = mắt càng mở = EAR càng cao
            // Ngược lại với buồn ngủ: EAR thấp = mắt nhắm = buồn ngủ
            val ear = avgEyeOpenProb * 0.4f // Scale to 0.0-0.4 range
            
            Log.d("DrowsinessDetector", "Eye Open: Left=%.3f, Right=%.3f, Avg=%.3f, EAR=%.3f"
                .format(leftEyeOpenProb, rightEyeOpenProb, avgEyeOpenProb, ear))
            
            return ear
        }
        
        // Fallback: dùng contours nếu có
        val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
        val rightEyeContour = face.getContour(FaceContour.RIGHT_EYE)?.points
        
        if (leftEyeContour != null && rightEyeContour != null && 
            leftEyeContour.size >= 8 && rightEyeContour.size >= 8) {
            
            val leftEAR = calculateEyeAspectRatio(leftEyeContour)
            val rightEAR = calculateEyeAspectRatio(rightEyeContour)
            val avgEAR = (leftEAR + rightEAR) / 2f
            
            Log.d("DrowsinessDetector", "EAR từ contours: Left=%.3f, Right=%.3f, Avg=%.3f"
                .format(leftEAR, rightEAR, avgEAR))
            
            return avgEAR
        }
        
        // Final fallback
        Log.w("DrowsinessDetector", "Không có eye data, sử dụng default EAR")
        return 0.3f
    }
    
    /**
     * Tính EAR từ 16 điểm contour của một mắt
     * Sử dụng công thức EAR chuẩn: (|p2-p6| + |p3-p5|) / (2 * |p1-p4|)
     */
    private fun calculateEyeAspectRatio(eyeContour: List<PointF>): Float {
        if (eyeContour.size < 16) return 0.25f
        
        // Với 16-point contour, chọn các điểm quan trọng cho EAR
        // Điểm 0 và 8: góc trái và phải của mắt (horizontal)
        // Điểm 4 và 12: đỉnh và đáy của mắt (vertical)
        // Điểm 2,6,10,14: các điểm vertical khác
        
        val p1 = eyeContour[0]   // Góc trái mắt
        val p2 = eyeContour[2]   // Điểm vertical 1
        val p3 = eyeContour[4]   // Điểm đỉnh mắt  
        val p4 = eyeContour[8]   // Góc phải mắt
        val p5 = eyeContour[12]  // Điểm đáy mắt
        val p6 = eyeContour[14]  // Điểm vertical 2
        
        // Khoảng cách vertical
        val vertical1 = distance(p2, p6)
        val vertical2 = distance(p3, p5)
        
        // Khoảng cách horizontal  
        val horizontal = distance(p1, p4)
        
        if (horizontal == 0f) return 0.25f
        
        // EAR formula
        val ear = (vertical1 + vertical2) / (2f * horizontal)
        
        return ear.coerceIn(0.1f, 0.5f)
    }
    
    /**
     * Tính khoảng cách Euclidean giữa 2 điểm
     */
    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Tính EAR từ eye open probability (method chính xác nhất)
     */
    private fun calculateSimplifiedEAR(face: Face): Float {
        // Sử dụng eye open probability từ ML Kit - chính xác nhất
        val leftProb = face.leftEyeOpenProbability
        val rightProb = face.rightEyeOpenProbability
        
        if (leftProb != null && rightProb != null) {
            val avgProb = (leftProb + rightProb) / 2f
            
            // Convert trực tiếp: probability -> EAR
            // probability cao = mắt mở = EAR cao
            // probability thấp = mắt nhắm = EAR thấp  
            val ear = avgProb * 0.4f // Scale to 0.0-0.4 range
            
            Log.d("DrowsinessDetector", "Eye open probability: Left=%.3f, Right=%.3f, EAR=%.3f"
                .format(leftProb, rightProb, ear))
            
            return ear
        }
        
        Log.w("DrowsinessDetector", "Không có eye open probability data")
        return 0.3f // Default khi không có data
    }

    /**
     * Ước lượng EAR từ landmarks có sẵn (tương tự shape_predictor_68_face_landmarks.dat)
     */
    private fun calculateEARFromLandmarks(face: Face): Float {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

        if (leftEye == null || rightEye == null) {
            Log.w("DrowsinessDetector", "Không nhận diện được landmarks mắt! leftEye=$leftEye, rightEye=$rightEye")
            return 0.3f // Default value when landmarks not available
        }

        // Ước lượng EAR từ khoảng cách giữa các điểm mắt
        // Đây là approximation vì ML Kit không cung cấp đủ 6 điểm như dlib

        val faceWidth = face.boundingBox.width().toFloat()
        val faceHeight = face.boundingBox.height().toFloat()

        // Estimate eye dimensions based on face proportions
        val estimatedEyeWidth = faceWidth * 0.15f  // Eye width ~15% of face width
        val estimatedEyeHeight = faceHeight * 0.05f // Eye height ~5% of face height

        // EAR formula approximation
        val ear = (2 * estimatedEyeHeight) / estimatedEyeWidth

        // Clamp to reasonable range
        Log.d("DrowsinessDetector", "EAR estimated from face: $ear (faceWidth=$faceWidth, faceHeight=$faceHeight)")
        return ear.coerceIn(0.1f, 0.4f)
    }

    /**
     * Vẽ hình vuông theo dõi khuôn mặt chính xác
     */
    private fun drawAccurateFaceTracking(
        bitmap: Bitmap,
        face: Face?,
        ear: Float,
        closedFrames: Int,
        alertLevel: AlertLevel
    ) {
        val overlayBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlayBitmap)
        
        if (face != null) {
            // Vẽ hình vuông theo dõi khuôn mặt chính xác
            drawPreciseFaceBox(canvas, face, alertLevel)
            
            // Status text đơn giản
            drawSimpleStatus(canvas, ear, closedFrames, alertLevel)
        } else {
            drawSimpleStatus(canvas, 0.0f, 0, AlertLevel.NORMAL)
        }
        
        onOverlayUpdate(overlayBitmap)
    }
    
    /**
     * Vẽ hình vuông theo dõi khuôn mặt với độ chính xác cao
     */
    private fun drawPreciseFaceBox(canvas: Canvas, face: Face, alertLevel: AlertLevel) {
        val boxColor = when (alertLevel) {
            AlertLevel.CRITICAL -> Color.RED
            AlertLevel.WARNING -> Color.YELLOW    
            AlertLevel.NORMAL -> Color.GREEN
        }
        
        val facePaint = Paint().apply {
            color = boxColor
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        
        // Phương pháp 1: Sử dụng face contour để có bounding box chính xác nhất
        val faceOval = face.getContour(FaceContour.FACE)?.points
        
        if (faceOval != null && faceOval.isNotEmpty()) {
            // Tính bounding box chính xác từ tất cả điểm contour của khuôn mặt
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            
            for (point in faceOval) {
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
            }
            
            // Tạo padding để hình vuông bao quanh khuôn mặt thoải mái hơn
            val padding = 20f
            val preciseRect = RectF(
                minX - padding, 
                minY - padding, 
                maxX + padding, 
                maxY + padding
            )
            
            // Vẽ hình vuông chính xác theo dõi khuôn mặt
            canvas.drawRect(preciseRect, facePaint)
            
            // Vẽ cross-hair ở trung tâm để tracking tốt hơn
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val crossSize = 10f
            
            val centerPaint = Paint().apply {
                color = boxColor
                strokeWidth = 2f
                isAntiAlias = true
            }
            
            canvas.drawLine(centerX - crossSize, centerY, centerX + crossSize, centerY, centerPaint)
            canvas.drawLine(centerX, centerY - crossSize, centerX, centerY + crossSize, centerPaint)
            
            // Hiển thị tracking ID nếu có
            val trackingId = face.trackingId
            if (trackingId != null) {
                val idPaint = Paint().apply {
                    color = boxColor
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
                canvas.drawText("ID: $trackingId", minX, minY - 5f, idPaint)
            }
            
            Log.d("DrowsinessDetector", "Face tracking: ID=$trackingId center(%.1f,%.1f) từ ${faceOval.size} contour points"
                .format(centerX, centerY))
                
        } else {
            // Phương pháp 2: Fallback với bounding box có sẵn nhưng cải thiện
            val originalBox = face.boundingBox
            
            // Mở rộng bounding box một chút để tracking tốt hơn
            val expandedBox = RectF(
                originalBox.left - 10f,
                originalBox.top - 20f,
                originalBox.right + 10f, 
                originalBox.bottom + 10f
            )
            
            canvas.drawRect(expandedBox, facePaint)
            
            // Vẽ cross-hair ở trung tâm
            val centerX = expandedBox.centerX()
            val centerY = expandedBox.centerY()
            val crossSize = 10f
            
            val centerPaint = Paint().apply {
                color = boxColor
                strokeWidth = 2f
                isAntiAlias = true
            }
            
            canvas.drawLine(centerX - crossSize, centerY, centerX + crossSize, centerY, centerPaint)
            canvas.drawLine(centerX, centerY - crossSize, centerX, centerY + crossSize, centerPaint)
            
            // Hiển thị tracking ID nếu có
            val trackingId = face.trackingId
            if (trackingId != null) {
                val idPaint = Paint().apply {
                    color = boxColor
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
                canvas.drawText("ID: $trackingId", expandedBox.left, expandedBox.top - 5f, idPaint)
            }
            
            Log.d("DrowsinessDetector", "Face tracking: ID=$trackingId fallback box center(%.1f,%.1f)"
                .format(centerX, centerY))
        }
    }
    
    private fun drawSimpleStatus(canvas: Canvas, ear: Float, closedFrames: Int, alertLevel: AlertLevel) {
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        
        val statusText = when (alertLevel) {
            AlertLevel.CRITICAL -> "🚨 DROWSY!"
            AlertLevel.WARNING -> "⚠️ WARNING" 
            AlertLevel.NORMAL -> "👁️ TRACKING"
        }
        
        canvas.drawText(statusText, 20f, 50f, textPaint)
        canvas.drawText("EAR: %.3f".format(ear), 20f, 80f, textPaint)
        
        if (closedFrames > 0) {
            canvas.drawText("Closed: $closedFrames", 20f, 110f, textPaint)
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val image = imageProxy.image ?: return null
            
            // Convert YUV_420_888 to RGB
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            // Try direct bitmap creation first
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            
            if (bitmap != null) {
                return bitmap
            }
            
            // Fallback: Convert YUV to RGB manually
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer  
            val vBuffer = image.planes[2].buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            
            val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
            val imageBytes = out.toByteArray()
            
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("DrowsinessDetector", "Error converting ImageProxy to Bitmap", e)
            null
        }
    }
}
