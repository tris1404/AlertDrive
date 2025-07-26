package com.intelligenttraffic.alertdrive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.intelligenttraffic.alertdrive.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var alertManager: AlertManager
    private var isDetectionActive = false
    private var isCameraStarted = false
    private var cameraProvider: ProcessCameraProvider? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d("MainActivity", "Permission result: $isGranted")
        if (isGranted) {
            Log.d("MainActivity", "Camera permission granted by user")
            Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
            startCamera()
        } else {
            Log.e("MainActivity", "Camera permission denied by user")
            Toast.makeText(this, "Camera permission is required for face detection", Toast.LENGTH_LONG).show()
            // Không finish app ngay, cho phép user thử lại
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("MainActivity", "=== onCreate started ===")
        
        alertManager = AlertManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupClickListeners()
        
        // Test PreviewView availability
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
        Log.d("MainActivity", "PreviewView in onCreate: ${previewView != null}")
        
        // Không auto-start camera khi mở app
        Log.d("MainActivity", "App started - camera will start when user enables detection")
        
        Log.d("MainActivity", "=== onCreate completed ===")
    }

    private fun setupClickListeners() {
        try {
            Log.d("MainActivity", "=== Setting up click listeners ===")
            
            // Sử dụng ID button card trực tiếp
            val buttonCard = findViewById<androidx.cardview.widget.CardView>(R.id.buttonCard)
            
            if (buttonCard != null) {
                buttonCard.setOnClickListener {
                    Log.d("MainActivity", "🎯 Button CardView CLICKED!")
                    toggleDetection()
                }
                Log.d("MainActivity", "✅ Click listener setup successfully on button CardView")
            } else {
                Log.e("MainActivity", "❌ buttonCard not found!")
            }
            
            // Backup: Click listener cho TextView
            val btnText = findViewById<android.widget.TextView>(R.id.btnToggle)
            if (btnText != null) {
                btnText.setOnClickListener {
                    Log.d("MainActivity", "🎯 Button TextView CLICKED!")
                    toggleDetection()
                }
                Log.d("MainActivity", "✅ Backup click listener setup on TextView")
            } else {
                Log.e("MainActivity", "❌ btnToggle TextView not found!")
            }
            
            // Test method: Long click to trigger test alert
            buttonCard?.setOnLongClickListener {
                Log.d("MainActivity", "🧪 LONG CLICK - Testing alert system")
                testAlertSystem()
                true
            }
            
            // Test click programmatically
            Log.d("MainActivity", "Testing programmatic click...")
            buttonCard?.post {
                Log.d("MainActivity", "Button card post executed")
            }
            
            Log.d("MainActivity", "=== Click listeners setup completed ===")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error setting up click listener", e)
        }
    }
    
    /**
     * Test method để kiểm tra alert system hoạt động
     */
    private fun testAlertSystem() {
        Log.d("MainActivity", "🧪 Testing alert system...")
        Toast.makeText(this, "🧪 Testing Alert System", Toast.LENGTH_SHORT).show()
        
        // Test critical alert
        val testState = DrowsinessState(
            faceDetected = true,
            eyeAspectRatio = 0.15f,  // Dưới ngưỡng 0.25
            consecutiveClosedFrames = 5,  // Đạt ngưỡng nguy hiểm
            alertLevel = AlertLevel.CRITICAL
        )
        
        updateUI(testState)
        alertManager.handleAlert(AlertLevel.CRITICAL)
        
        // Reset sau 3 giây
        findViewById<android.widget.TextView>(R.id.btnToggle)?.postDelayed({
            alertManager.handleAlert(AlertLevel.NORMAL)
            val normalState = DrowsinessState()
            updateUI(normalState)
            Toast.makeText(this, "🧪 Test completed", Toast.LENGTH_SHORT).show()
        }, 3000)
    }
    
    private fun toggleDetection() {
        Log.d("MainActivity", "=== TOGGLE DETECTION CALLED ===")
        Log.d("MainActivity", "Current state: isDetectionActive=$isDetectionActive, isCameraStarted=$isCameraStarted")

        isDetectionActive = !isDetectionActive
        Log.d("MainActivity", "New state: isDetectionActive=$isDetectionActive")

        val btnText = findViewById<android.widget.TextView>(R.id.btnToggle)
        btnText?.text = if (isDetectionActive) {
            "Stop Detection"
        } else {
            "Start Detection"
        }

        val btnIcon = findViewById<android.widget.ImageView>(R.id.btnIcon)
        btnIcon?.setImageResource(
            if (isDetectionActive) R.drawable.ic_pause 
            else R.drawable.ic_play
        )

        if (!isDetectionActive) {
            val overlayImageView = findViewById<android.widget.ImageView>(R.id.overlayImageView)
            overlayImageView?.setImageBitmap(null)
            overlayImageView?.visibility = android.view.View.GONE

            Log.d("MainActivity", "🔄 Stopping camera...")
            stopCamera()
        }

        Toast.makeText(this,
            if (isDetectionActive) "✅ Detection Started" else "⏹️ Detection Stopped",
            Toast.LENGTH_SHORT
        ).show()

        if (isDetectionActive) {
            Log.d("MainActivity", "🔄 Detection activated, starting camera...")
            Toast.makeText(this, "🔄 Detection activated, starting camera...", Toast.LENGTH_SHORT).show()
            checkCameraPermission()
            Log.d("MainActivity", "Calling startCamera() from toggleDetection")
        } else {
            Log.d("MainActivity", "🔄 Detection deactivated, camera stopped")
        }
    }

    private fun checkCameraPermission() {
        Log.d("MainActivity", "=== CHECKING CAMERA PERMISSION ===")
        Log.d("MainActivity", "isDetectionActive: $isDetectionActive")
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "✅ Camera permission GRANTED, starting camera")
            startCamera()
        } else {
            Log.d("MainActivity", "❌ Camera permission NOT granted, requesting...")
            Toast.makeText(this, "Cần quyền camera để phát hiện buồn ngủ", Toast.LENGTH_SHORT).show()
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun stopCamera() {
        try {
            Log.d("MainActivity", "Stopping camera...")
            cameraProvider?.unbindAll()
            isCameraStarted = false
            
            // Clear preview
            val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
            previewView?.let {
                // Reset preview view
                it.surfaceProvider?.let { surfaceProvider ->
                    // Surface will be cleared automatically when unbound
                }
            }
            
            Log.d("MainActivity", "Camera stopped successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping camera", e)
        }
    }
    
    // Thêm method để retry camera với timeout handling
    private fun retryCameraStart() {
        if (!isDetectionActive) return
        
        Log.d("MainActivity", "Retrying camera start due to timeout...")
        
        // Stop current camera first
        stopCamera()
        
        // Wait a bit before retry
        cameraExecutor.execute {
            try {
                Thread.sleep(1000) // Wait 1 second
                runOnUiThread {
                    checkCameraPermission()
                }
            } catch (e: InterruptedException) {
                Log.e("MainActivity", "Retry interrupted", e)
            }
        }
    }

    private fun startCamera() {
        if (!isDetectionActive) {
            Log.d("MainActivity", "Detection not active, skipping camera start")
            return
        }
        
        Log.d("MainActivity", "=== STARTING CAMERA ===")
        
        // Đơn giản hóa - gọi trực tiếp
        startCameraInternal()
    }
    
    private fun startCameraInternal() {
        Log.d("MainActivity", "=== STARTING SIMPLE CAMERA ===")
        
        if (isCameraStarted) {
            Log.d("MainActivity", "Camera already started")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                Log.d("MainActivity", "Camera provider future completed")
                cameraProvider = cameraProviderFuture.get()
                
                Log.d("MainActivity", "Camera provider obtained")
                
                // Unbind everything first
                cameraProvider?.unbindAll()
                
                // Create simplest preview possible
                val preview = Preview.Builder().build()
                
                // Get PreviewView
                val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
                if (previewView == null) {
                    Log.e("MainActivity", "❌ PreviewView is NULL!")
                    return@addListener
                }
                
                Log.d("MainActivity", "PreviewView found, setting surface provider...")
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                // Try to bind with front camera first (for drowsiness detection)
                try {
                    Log.d("MainActivity", "Attempting to bind front camera...")
                    val camera = cameraProvider?.bindToLifecycle(
                        this@MainActivity,
                        CameraSelector.DEFAULT_FRONT_CAMERA,  // Sử dụng camera trước
                        preview
                    )
                    
                    if (camera != null) {
                        Log.d("MainActivity", "✅ FRONT CAMERA SUCCESS!")
                        Toast.makeText(this@MainActivity, "✅ Camera trước đã hoạt động!", Toast.LENGTH_LONG).show()
                        isCameraStarted = true
                        
                        // Thêm analyzer sau khi camera đã hoạt động
                        tryAddAnalyzer(preview)
                    } else {
                        Log.e("MainActivity", "❌ Front camera binding returned null")
                        Toast.makeText(this@MainActivity, "❌ Camera trước không hoạt động", Toast.LENGTH_LONG).show()
                    }
                    
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Front camera binding exception: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "❌ Front camera error: ${e.message}", Toast.LENGTH_LONG).show()
                    
                    // Thử với back camera nếu front camera thất bại
                    tryBackCamera(preview)
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Camera provider exception: ${e.message}", e)
                Toast.makeText(this@MainActivity, "❌ Camera provider error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun tryBackCamera(preview: Preview) {
        try {
            Log.d("MainActivity", "Trying back camera as fallback...")
            val camera = cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,  // Camera sau làm fallback
                preview
            )
            
            if (camera != null) {
                Log.d("MainActivity", "✅ BACK CAMERA SUCCESS!")
                Toast.makeText(this, "✅ Camera sau đã hoạt động!", Toast.LENGTH_LONG).show()
                isCameraStarted = true
                
                // Thêm analyzer sau khi camera đã hoạt động
                tryAddAnalyzer(preview)
            } else {
                Log.e("MainActivity", "❌ Back camera also failed")
                Toast.makeText(this, "❌ Tất cả camera đều thất bại", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Back camera exception: ${e.message}", e)
            Toast.makeText(this, "❌ Back camera error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun tryAddAnalyzer(preview: Preview) {
        try {
            Log.d("MainActivity", "Adding face analyzer...")

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(320, 240))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val faceAnalyzer = DrowsinessDetector(
                context = this@MainActivity,
                onStateChanged = { state ->
                    Log.d("MainActivity", "DrowsinessDetector onStateChanged: $state")
                    if (isDetectionActive) {
                        runOnUiThread {
                            updateUI(state)
                            alertManager.handleAlert(state.alertLevel)
                        }
                    }
                },
                onOverlayUpdate = { overlayBitmap ->
                    Log.d("MainActivity", "DrowsinessDetector onOverlayUpdate called")
                    if (isDetectionActive) {
                        runOnUiThread {
                            val overlayImageView = findViewById<android.widget.ImageView>(R.id.overlayImageView)
                            if (overlayBitmap != null) {
                                overlayImageView?.visibility = android.view.View.VISIBLE
                                overlayImageView?.setImageBitmap(overlayBitmap)
                                overlayImageView?.alpha = 1.0f
                                overlayImageView?.scaleType = android.widget.ImageView.ScaleType.MATRIX
                                Log.d("MainActivity", "✅ Drowsiness overlay displayed")
                            } else {
                                overlayImageView?.visibility = android.view.View.GONE
                            }
                        }
                    }
                },
                onFrameUpdate = { frameBitmap ->
                    Log.d("MainActivity", "DrowsinessDetector onFrameUpdate called")
                }
            )

            imageAnalyzer.setAnalyzer(cameraExecutor, faceAnalyzer)

            cameraProvider?.unbindAll()
            val camera = cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageAnalyzer
            )

            if (camera != null) {
                Log.d("MainActivity", "✅ Face analyzer added successfully")
                Toast.makeText(this, "📷 Camera + Face Detection hoạt động", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("MainActivity", "❌ Camera binding failed in tryAddAnalyzer")
            }

        } catch (e: Exception) {
            Log.w("MainActivity", "Face analyzer failed, keeping preview-only: ${e.message}")
        }
    }

    private fun updateUI(state: DrowsinessState) {
        // Update status theo dự án GitHub gốc
        val statusIcon = findViewById<android.widget.ImageView>(R.id.statusIcon)
        val statusText = findViewById<android.widget.TextView>(R.id.tvStatus)
        val cameraStatus = findViewById<android.widget.TextView>(R.id.tvCameraStatus)
        
        when (state.alertLevel) {
            AlertLevel.NORMAL -> {
                val statusMessage = if (state.faceDetected) {
                    "👁️ EYES MONITORING - Normal Activity"
                } else {
                    "🔍 SEARCHING FOR FACE..."
                }
                statusText?.text = statusMessage
                statusText?.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                statusIcon?.setImageResource(R.drawable.ic_check)
                statusIcon?.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light))
                
                cameraStatus?.text = if (state.faceDetected) "FACE DETECTED - MONITORING" else "POSITION FACE IN CAMERA"
                cameraStatus?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            }
            AlertLevel.WARNING -> {
                val warningText = "⚠️ DROWSINESS WARNING - Closed Eyes Detected (${state.consecutiveClosedFrames}/5)"
                statusText?.text = warningText
                statusText?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                statusIcon?.setImageResource(R.drawable.ic_warning)
                statusIcon?.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                
                cameraStatus?.text = "⚠️ DROWSINESS WARNING - STAY ALERT"
                cameraStatus?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            }
            AlertLevel.CRITICAL -> {
                val criticalText = "🚨 CRITICAL ALERT - DRIVER DROWSY! (${state.consecutiveClosedFrames}/5)"
                statusText?.text = criticalText
                statusText?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                statusIcon?.setImageResource(R.drawable.ic_danger)
                statusIcon?.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light))
                
                cameraStatus?.text = "🚨 DROWSINESS ALERT - WAKE UP!"
                cameraStatus?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            }
        }

        // Update EAR value theo format dự án gốc
        val earText = findViewById<android.widget.TextView>(R.id.tvEyeRatio)
        earText?.text = "%.3f".format(state.eyeAspectRatio)
        
        // Color code EAR theo ngưỡng 0.25 của dự án gốc
        val earColor = when {
            state.eyeAspectRatio < 0.25f -> ContextCompat.getColor(this, android.R.color.holo_red_light)    // Dưới ngưỡng nguy hiểm
            state.eyeAspectRatio < 0.30f -> ContextCompat.getColor(this, android.R.color.holo_orange_light) // Gần ngưỡng
            else -> ContextCompat.getColor(this, android.R.color.holo_green_light)                         // An toàn
        }
        earText?.setTextColor(earColor)
        
        // Update frame count theo ngưỡng 5 frames của dự án gốc
        val frameText = findViewById<android.widget.TextView>(R.id.tvFrameCount)
        val frameDisplay = "${state.consecutiveClosedFrames}/5"
        frameText?.text = frameDisplay
        
        // Color code frame count theo ngưỡng nguy hiểm
        val frameColor = when {
            state.consecutiveClosedFrames >= 5 -> ContextCompat.getColor(this, android.R.color.holo_red_light)     // NGUY HIỂM
            state.consecutiveClosedFrames >= 3 -> ContextCompat.getColor(this, android.R.color.holo_orange_light)  // CẢNH BÁO
            else -> ContextCompat.getColor(this, android.R.color.white)
        }
        frameText?.setTextColor(frameColor)

        // Update progress bar theo ngưỡng 5 frames
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val maxFrames = 5 // Như dự án gốc
        val progress = (state.consecutiveClosedFrames * 100 / maxFrames).coerceAtMost(100)
        progressBar?.progress = progress
        
        // Update progress bar color theo mức độ nguy hiểm
        val progressColor = when {
            progress >= 100 -> ContextCompat.getColor(this, android.R.color.holo_red_light)    // 5/5 frames = CRITICAL
            progress >= 60 -> ContextCompat.getColor(this, android.R.color.holo_orange_light)  // 3/5 frames = WARNING
            else -> ContextCompat.getColor(this, android.R.color.holo_green_light)
        }
        progressBar?.progressTintList = android.content.res.ColorStateList.valueOf(progressColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        cameraExecutor.shutdown()
        alertManager.release()
    }

    // Handle orientation changes - đơn giản hóa  
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "Orientation changed: ${newConfig.orientation}")
        
        // Không restart camera cho orientation change để tránh lỗi
        // Camera sẽ tự động adapt với orientation mới
        Log.d("MainActivity", "Orientation change handled without camera restart")
    }
}
