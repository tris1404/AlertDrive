package com.intelligenttraffic.alertdrive

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraPopupService : Service(), LifecycleOwner {
    private lateinit var windowManager: WindowManager
    private var popupView: View? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null
    private var drowsinessDetector: DrowsinessDetector? = null
    private var alertMediaPlayer: MediaPlayer? = null

    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "camera_popup_channel"
        const val ACTION_STOP_SERVICE = "com.intelligenttraffic.alertdrive.STOP_SERVICE"
    }

    // Implement LifecycleOwner interface
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("CameraPopupService", "onStartCommand called with action: ${intent?.action}")

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("CameraPopupService", "onCreate called")

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        // Check overlay permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("CameraPopupService", "No overlay permission, stopping service")
            stopSelf()
            return
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        setupPopupWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera Popup Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Drowsiness detection camera popup"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CameraPopupService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Drowsiness Detection")
            .setContentText("Camera popup đang hoạt động - Tap để mở app")
            .setSmallIcon(android.R.drawable.ic_menu_camera) // Use system icon as fallback
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun setupPopupWindow() {
        try {
            // Tạo root view tạm thời
            val rootView = FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Inflate layout
            popupView = LayoutInflater.from(this).inflate(
                R.layout.popup_camera_layout,
                rootView,
                false
            )

            // Thiết lập layout params cho popup với kích thước cố định
            val params = WindowManager.LayoutParams(
                320, // Fixed width
                240, // Fixed height
                getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 20
                y = 100
            }

            // Setup views - chỉ cần PreviewView
            val previewView = popupView?.findViewById<PreviewView>(R.id.camera_preview)

            // Thêm click listener để mở lại app khi nhấn vào popup
            popupView?.setOnClickListener {
                Log.d("CameraPopupService", "Popup clicked, opening main app")
                openMainApp()
            }

            // Thêm touch listener để xử lý touch events
            popupView?.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_OUTSIDE -> {
                        // Nếu touch bên ngoài popup, không làm gì
                        false
                    }
                    else -> {
                        // Để OnClickListener xử lý
                        false
                    }
                }
            }

            // Initialize drowsiness detector với callbacks đơn giản
            drowsinessDetector = DrowsinessDetector(this,
                onStateChanged = { state ->
                    // Phát âm thanh cảnh báo khi phát hiện ngủ gật
                    if (state.alertLevel == AlertLevel.CRITICAL) {
                        playAlertSound()
                    }
                    Log.d("CameraPopupService", "Drowsiness state: ${state.alertLevel}, EAR: ${state.eyeAspectRatio}")
                },
                onOverlayUpdate = { /* Không cần overlay */ },
                onFrameUpdate = { /* Không cần xử lý frame */ }
            )

            windowManager.addView(popupView, params)
            Log.d("CameraPopupService", "Popup window added successfully with fixed size: 320x240")

            // Start camera after popup is set up
            if (previewView != null) {
                startCamera(previewView)
            } else {
                Log.e("CameraPopupService", "PreviewView not found in layout")
            }

        } catch (e: Exception) {
            Log.e("CameraPopupService", "Error setting up popup window", e)
            stopSelf()
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun getWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun startCamera(previewView: PreviewView) {
        Log.d("CameraPopupService", "Starting camera in popup...")

        // Ensure PreviewView is properly initialized
        previewView.post {
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    cameraProvider?.unbindAll()

                    // Create preview with optimized settings for popup
                    val preview = Preview.Builder()
                        .setTargetResolution(android.util.Size(640, 480)) // Higher resolution for better detection
                        .build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    // Create image analysis for drowsiness detection
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            drowsinessDetector?.let { detector ->
                                it.setAnalyzer(cameraExecutor, detector)
                            }
                        }

                    // Try front camera first
                    try {
                        val camera = cameraProvider?.bindToLifecycle(
                            this,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            imageAnalysis
                        )

                        if (camera != null) {
                            Log.d("CameraPopupService", "Front camera started successfully in popup")
                        } else {
                            Log.w("CameraPopupService", "Front camera binding returned null, trying back camera")
                            tryBackCamera(previewView)
                        }
                    } catch (e: Exception) {
                        Log.w("CameraPopupService", "Front camera failed, trying back camera", e)
                        tryBackCamera(previewView)
                    }

                } catch (e: Exception) {
                    Log.e("CameraPopupService", "Error starting camera in popup", e)
                    // Không có UI để hiển thị lỗi
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun tryBackCamera(previewView: PreviewView) {
        try {
            cameraProvider?.unbindAll()

            val preview = Preview.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    drowsinessDetector?.let { detector ->
                        it.setAnalyzer(cameraExecutor, detector)
                    }
                }

            val camera = cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

            if (camera != null) {
                Log.d("CameraPopupService", "Back camera started successfully in popup")
            } else {
                Log.e("CameraPopupService", "Failed to bind back camera")
                // Không có UI để hiển thị lỗi
            }
        } catch (e: Exception) {
            Log.e("CameraPopupService", "Back camera binding failed", e)
            // Không có UI để hiển thị lỗi
        }
    }

    private fun playAlertSound() {
        try {
            // Stop any existing alert sound
            alertMediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }

            // Create new MediaPlayer with alert sound from resources
            alertMediaPlayer = MediaPlayer.create(this, R.raw.alert_sound)?.apply {
                setVolume(1.0f, 1.0f) // Full volume
                isLooping = false // Don't loop in popup
                setOnCompletionListener {
                    it.release()
                    alertMediaPlayer = null
                }
                start()
            }

            if (alertMediaPlayer == null) {
                Log.w("CameraPopupService", "Could not create MediaPlayer for alert sound")
                // Fallback to vibration
                vibrateForAlert()
            } else {
                Log.d("CameraPopupService", "Alert sound started in popup")
            }
        } catch (e: Exception) {
            Log.e("CameraPopupService", "Error playing alert sound", e)
            // Fallback to vibration
            vibrateForAlert()
        }
    }

    private fun vibrateForAlert() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                vibrator.vibrate(pattern, -1)
            }
            Log.d("CameraPopupService", "Vibration alert used as fallback")
        } catch (e: Exception) {
            Log.e("CameraPopupService", "Error with vibration", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("CameraPopupService", "onDestroy called")

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        try {
            // Stop and release alert sound
            alertMediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            alertMediaPlayer = null

            // Cleanup camera and detector - đảm bảo cleanup đúng thứ tự
            Log.d("CameraPopupService", "Cleaning up camera resources...")

            // Unbind camera trước
            cameraProvider?.unbindAll()
            cameraProvider = null

            // Shutdown executor
            cameraExecutor.shutdown()
            try {
                if (!cameraExecutor.awaitTermination(1000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    cameraExecutor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                cameraExecutor.shutdownNow()
            }

            // Cleanup detector
            drowsinessDetector?.close()
            drowsinessDetector = null

            // Remove popup view
            popupView?.let { windowManager.removeView(it) }
            popupView = null

            Log.d("CameraPopupService", "CameraPopupService destroyed successfully")
        } catch (e: Exception) {
            Log.e("CameraPopupService", "Error in onDestroy", e)
        }
    }
}