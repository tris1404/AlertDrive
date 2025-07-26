package com.intelligenttraffic.alertdrive

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlertManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isAlerting = false
    private var lastAlertTime = 0L
    private var alertCount = 0
    private var currentAlertLevel = AlertLevel.NORMAL

    companion object {
        // Tham số theo dự án mẫu - alert ngay lập tức khi phát hiện
        private const val MIN_ALERT_INTERVAL = 1000L // Giảm xuống 1s để phản ứng nhanh như dự án gốc
        private const val ESCALATION_THRESHOLD = 2   // Giảm threshold để cảnh báo sớm hơn
        private const val CONTINUOUS_ALERT_INTERVAL = 2000L // Cảnh báo liên tục mỗi 2s
    }

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun handleAlert(alertLevel: AlertLevel) {
        val currentTime = System.currentTimeMillis()
        
        // Theo dự án mẫu: Alert ngay khi phát hiện CRITICAL, không chờ đợi
        val shouldAlert = when (alertLevel) {
            AlertLevel.CRITICAL -> true  // Luôn alert khi CRITICAL như dự án gốc
            AlertLevel.WARNING -> currentTime - lastAlertTime > MIN_ALERT_INTERVAL
            AlertLevel.NORMAL -> false
        }
        
        if (!shouldAlert && alertLevel == currentAlertLevel) {
            return
        }
        
        Log.d("AlertManager", "Handling alert level: $alertLevel (count: $alertCount)")
        
        when (alertLevel) {
            AlertLevel.NORMAL -> {
                stopAlert()
                alertCount = 0
            }
            AlertLevel.WARNING -> {
                if (!isAlerting) {
                    triggerWarningAlert()
                    alertCount++
                }
            }
            AlertLevel.CRITICAL -> {
                // Theo dự án mẫu: CRITICAL alert ngay lập tức và liên tục
                triggerCriticalAlert()
                alertCount++
            }
        }
        
        currentAlertLevel = alertLevel
        lastAlertTime = currentTime
    }
    
    /**
     * Cảnh báo WARNING nhẹ nhàng
     */
    private fun triggerWarningAlert() {
        Log.d("AlertManager", "Triggering WARNING alert")
        isAlerting = true
        
        // Rung nhẹ
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(300)
            }
        }
    }
    
    /**
     * Cảnh báo CRITICAL mạnh mẽ như dự án mẫu (có âm thanh + rung mạnh)
     */
    private fun triggerCriticalAlert() {
        Log.d("AlertManager", "🚨 CRITICAL DROWSINESS DETECTED - TRIGGERING ALARM 🚨")
        isAlerting = true
        
        // Rung mạnh như dự án mẫu
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                it.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
                it.vibrate(pattern, -1)
            }
        }
        
        // Phát âm thanh cảnh báo như dự án mẫu
        playAlarmSound()
    }
    
    /**
     * Phát âm thanh cảnh báo như Alert.wav trong dự án mẫu
     */
    private fun playAlarmSound() {
        try {
            // Stop any existing alarm
            mediaPlayer?.apply {
                if (isPlaying) {
                    Log.d("AlertManager", "Stopping existing alarm sound")
                    stop()
                }
                release()
            }
            
            // Tạo MediaPlayer với âm thanh cảnh báo từ file mp3
            Log.d("AlertManager", "Creating MediaPlayer for alert sound...")
            mediaPlayer = MediaPlayer.create(context, R.raw.alert_sound)
            
            if (mediaPlayer != null) {
                mediaPlayer?.setVolume(1.0f, 1.0f) // Full volume như dự án gốc
                mediaPlayer?.isLooping = true       // Loop continuous như dự án mẫu
                mediaPlayer?.setOnErrorListener { mp, what, extra ->
                    Log.e("AlertManager", "MediaPlayer error: what=$what, extra=$extra")
                    false
                }
                mediaPlayer?.setOnPreparedListener {
                    Log.d("AlertManager", "MediaPlayer prepared, starting playback")
                }
                mediaPlayer?.start()
                Log.d("AlertManager", "🔊 ALARM SOUND STARTED - WAKE UP! 🔊")
            } else {
                Log.e("AlertManager", "❌ Failed to create alarm MediaPlayer - alert_sound.mp3 not found or corrupted")
                // Fallback: Use system notification sound
                try {
                    val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    val ringtone = android.media.RingtoneManager.getRingtone(context, notification)
                    ringtone?.play()
                    Log.d("AlertManager", "🔊 Using system alarm sound as fallback")
                } catch (e: Exception) {
                    Log.e("AlertManager", "Failed to play system alarm sound", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AlertManager", "Error playing alarm sound", e)
        }
    }

    private fun stopAlert() {
        Log.d("AlertManager", "Stopping alert")
        if (isAlerting) {
            isAlerting = false
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            vibrator?.cancel()
            Log.d("AlertManager", "Alert stopped")
        }
    }

    fun release() {
        stopAlert()
    }
}