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
    private lateinit var eyeStatusText: TextView

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

            // Thiết lập layout params cho popup nhỏ hơn
            val params = WindowManager.LayoutParams(
                350, // Width nhỏ hơn
                250, // Height nhỏ hơn
                getWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 20
                y = 100
            }

            // Setup views
            val previewView = popupView?.findViewById<PreviewView>(R.id.camera_preview)
            eyeStatusText = popupView?.findViewById(R.id.eye_status) ?: return

            // Button để mở lại app chính
            popupView?.findViewById<Button>(R.id.close_button)?.apply {
                text = "Mở App"
                setOnClickListener {
                    openMainApp()
                }
            }

            // Thêm button đóng popup
            popupView?.findViewById<Button>(R.id.minimize_button)?.setOnClickListener {
                stopSelf()
            }

            // Initialize drowsiness detector
            drowsinessDetector = DrowsinessDetector(this,
                onStateChanged = { state ->
                    Handler(Looper.getMainLooper()).post {
                        eyeStatusText.text = if (state.isEyesClosed) "Mắt: Nhắm" else "Mắt: Mở"
                        if (state.alertLevel == AlertLevel.CRITICAL) {
                            playAlertSound()
                            // Make popup more visible when alert
                            makePopupVisible()
                        }
                    }
                },
                onOverlayUpdate = { /* Không cần overlay cho popup */ },
                onFrameUpdate = { /* Không cần xử lý frame */ }
            )

            windowManager.addView(popupView, params)
            Log.d("CameraPopupService", "Popup window added successfully")

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

    private fun makePopupVisible() {
        try {
            popupView?.let { view ->
                val params = view.layoutParams as WindowManager.LayoutParams
                // Bring to front and make more visible
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                windowManager.updateViewLayout(view, params)
            }
        } catch (e: Exception) {
            Log.e("CameraPopupService", "Error making popup visible", e)
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
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()

                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(320, 240)) // Lower resolution for popup
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(320, 240))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        drowsinessDetector?.let { detector ->
                            it.setAnalyzer(cameraExecutor, detector)
                        }
                    }

                val camera = cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )

                if (camera != null) {
                    Log.d("CameraPopupService", "Camera started successfully in popup")
                } else {
                    Log.e("CameraPopupService", "Failed to bind camera")
                }
            } catch (e: Exception) {
                Log.e("CameraPopupService", "Use case binding failed", e)
                // Try with back camera as fallback
                tryBackCamera(previewView)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun tryBackCamera(previewView: PreviewView) {
        try {
            cameraProvider?.unbindAll()

            val preview = Preview.Builder()
                .setTargetResolution(android.util.Size(320, 240))
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(320, 240))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    drowsinessDetector?.let { detector ->
                        it.setAnalyzer(cameraExecutor, detector)
                    }
                }

            cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            Log.d("CameraPopupService", "Back camera started as fallback")
        } catch (e: Exception) {
            Log.e("CameraPopupService", "Back camera also failed", e)
        }
    }

    private fun playAlertSound() {
        try {
            // Use system sound as fallback
            val mediaPlayer = MediaPlayer()
            val afd = assets.openFd("alert_sound.wav") // If you have this file
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer.prepare()
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener {
                it.release()
                afd.close()
            }
        } catch (e: Exception) {
            Log.e("CameraPopupService", "Error playing alert sound, using vibration instead", e)
            // Fallback to vibration
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("CameraPopupService", "onDestroy called")

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        try {
            drowsinessDetector?.close()
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            popupView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e("CameraPopupService", "Error in onDestroy", e)
        }
    }
}