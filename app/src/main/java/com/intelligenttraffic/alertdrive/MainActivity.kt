package com.intelligenttraffic.alertdrive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
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

    // Popup camera ở màn hình chính
    private var backPressedTime: Long = 0

    // Modern back press handling using OnBackPressedDispatcher
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Chỉ xử lý thoát app bình thường khi ấn back, không tạo popup
            if (supportFragmentManager.backStackEntryCount > 0) {
                // If there are fragments in back stack, pop them
                supportFragmentManager.popBackStack()
            } else {
                // Handle double tap to exit
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    // Exit app completely
                    finish()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Nhấn back lần nữa để thoát ứng dụng",
                        Toast.LENGTH_SHORT
                    ).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("MainActivity", "=== onCreate started ===")

        // Register modern back press callback
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        alertManager = AlertManager(this)
        // Thiết lập âm thanh cảnh báo mặc định
        alertManager.setCustomAlertSound(selectedSoundResId)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupClickListeners()

        // Thiết lập click cho nút chọn âm thanh
        val btnSelectSound = findViewById<android.widget.LinearLayout>(R.id.btnSelectSound2)
        btnSelectSound?.setOnClickListener {
            showSoundPickerDialog()
        }

        // Tìm và lưu trữ TextView của nút chọn âm thanh
        val soundButtonLayout = findViewById<android.widget.LinearLayout>(R.id.btnSelectSound2)
        soundButtonText = soundButtonLayout?.findViewById<android.widget.TextView>(android.R.id.text1) ?: soundButtonLayout?.getChildAt(1) as? android.widget.TextView
        updateSoundButtonText()

        // Test PreviewView availability
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
        Log.d("MainActivity", "PreviewView in onCreate: ${previewView != null}")

        // Không auto-start camera khi mở app
        Log.d("MainActivity", "App started - camera will start when user enables detection")

        Log.d("MainActivity", "=== onCreate completed ===")
    }

    override fun onPause() {
        super.onPause()
        // Khi app bị minimize (Home button hoặc recent apps), tạo popup
        if (!isFinishing && isDetectionActive) {
            Log.d("MainActivity", "App paused, starting popup service")
            startPopupServiceIfNeeded()
        }
    }

    override fun onStop() {
        super.onStop()
        // Khi app không còn visible, đảm bảo popup được tạo
        if (!isFinishing && isDetectionActive) {
            Log.d("MainActivity", "App stopped, ensuring popup service")
            startPopupServiceIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        // Khi app được mở lại, tắt popup service
        Log.d("MainActivity", "App resumed, stopping popup service")
        stopPopupService()

        // Re-enable back press callback when resuming
        backPressedCallback.isEnabled = true

        // Thêm delay để đảm bảo popup service đã cleanup camera hoàn toàn
        if (isDetectionActive && !isCameraStarted) {
            Log.d("MainActivity", "Detection was active, restarting camera after popup cleanup")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isDetectionActive && !isFinishing) {
                    Log.d("MainActivity", "Delayed camera restart after popup cleanup")
                    checkCameraPermission()
                }
            }, 1000) // Delay 1 giây để đảm bảo cleanup hoàn tất
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove callback to prevent memory leaks
        backPressedCallback.remove()

        stopCamera()
        cameraExecutor.shutdown()
        alertManager.release()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startPopupServiceIfNeeded() {
        // Chỉ tạo popup nếu detection đang active và có quyền overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)) {
            // Request overlay permission
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ)
        } else {
            startPopupService()
        }
    }

    private fun stopPopupService() {
        try {
            val serviceIntent = Intent(this, CameraPopupService::class.java)
            stopService(serviceIntent)
            Log.d("MainActivity", "Popup service stopped")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping popup service", e)
        }
    }

    private fun startPopupService() {
        val serviceIntent = Intent(this, CameraPopupService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Settings.canDrawOverlays(this)) {
                startPopupService()
            }
        }
    }

    companion object {
        const val OVERLAY_PERMISSION_REQ = 1001
    }

    // Hiển thị dialog chọn âm thanh từ res/raw
    private fun showSoundPickerDialog() {
        val rawRes = R.raw::class.java.fields
        val soundNames = rawRes.map { it.name }.toTypedArray()
        val context = this
        android.app.AlertDialog.Builder(context)
            .setTitle("Chọn âm thanh cảnh báo")
            .setItems(soundNames) { _, which ->
                val selectedField = rawRes[which]
                val resId = selectedField.getInt(null)
                val soundName = selectedField.name

                // Lưu lại âm thanh được chọn
                selectedSoundResId = resId
                selectedSoundName = soundName

                // Cập nhật AlertManager sử dụng âm thanh mới
                alertManager.setCustomAlertSound(resId)

                // Cập nhật UI
                updateSoundButtonText()

                // Phát thử âm thanh
                playRawSound(resId)

                // Hiển thị thông báo
                Toast.makeText(this, "Đã chọn âm thanh: $soundName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    // Phát thử âm thanh từ res/raw
    private fun playRawSound(resId: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = android.media.MediaPlayer.create(this, resId)
            mediaPlayer?.setOnCompletionListener {
                it.release()
            }
            mediaPlayer?.start()
            // Dừng sau 5 giây nếu chưa kết thúc
            mediaPlayer?.let { mp ->
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (mp.isPlaying) {
                        mp.stop()
                        mp.release()
                    }
                }, 5000)
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Không phát được âm thanh", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    private var mediaPlayer: android.media.MediaPlayer? = null

    // Biến lưu trữ âm thanh cảnh báo được chọn
    private var selectedSoundResId: Int = R.raw.alert_sound
    private var selectedSoundName: String = "alert_sound"
    private var soundButtonText: android.widget.TextView? = null

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
     * Cập nhật text của nút chọn âm thanh
     */
    private fun updateSoundButtonText() {
        soundButtonText?.text = "Âm thanh: $selectedSoundName"
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

            Log.d("MainActivity", "📴 Stopping camera...")
            stopCamera()
        }

        Toast.makeText(this,
            if (isDetectionActive) "✅ Detection Started" else "ℹ️ Detection Stopped",
            Toast.LENGTH_SHORT
        ).show()

        if (isDetectionActive) {
            Log.d("MainActivity", "📴 Detection activated, starting camera...")
            Toast.makeText(this, "📴 Detection activated, starting camera...", Toast.LENGTH_SHORT).show()
            checkCameraPermission()
            Log.d("MainActivity", "Calling startCamera() from toggleDetection")
        } else {
            Log.d("MainActivity", "📴 Detection deactivated, camera stopped")
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

        // Đảm bảo camera provider từ popup service đã được cleanup
        if (cameraProvider != null) {
            try {
                cameraProvider?.unbindAll()
                cameraProvider = null
                Log.d("MainActivity", "Cleaned up existing camera provider")
            } catch (e: Exception) {
                Log.w("MainActivity", "Error cleaning up existing camera provider", e)
            }
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                Log.d("MainActivity", "Camera provider future completed")
                cameraProvider = cameraProviderFuture.get()

                Log.d("MainActivity", "Camera provider obtained")

                // Unbind everything first để đảm bảo clean state
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

                // Retry sau 2 giây nếu thất bại do conflict
                if (e.message?.contains("bind", ignoreCase = true) == true) {
                    Log.d("MainActivity", "Retrying camera start due to binding conflict...")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isDetectionActive && !isFinishing) {
                            startCameraInternal()
                        }
                    }, 2000)
                }
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

        // Update risk percent text
        val tvRiskPercent = findViewById<android.widget.TextView>(R.id.tvRiskPercent)
        tvRiskPercent?.text = "$progress%"

        // Update risk percent color theo mức độ nguy hiểm
        val riskColor = when {
            progress >= 100 -> ContextCompat.getColor(this, android.R.color.holo_red_light)
            progress >= 60 -> ContextCompat.getColor(this, android.R.color.holo_orange_light)
            else -> ContextCompat.getColor(this, android.R.color.holo_green_light)
        }
        tvRiskPercent?.setTextColor(riskColor)
    }

    // Handle orientation changes - Đơn giản hóa
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "Orientation changed: ${newConfig.orientation}")

        // Không restart camera cho orientation change để tránh lỗi
        // Camera sẽ tự động adapt với orientation mới
        Log.d("MainActivity", "Orientation change handled without camera restart")
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
}