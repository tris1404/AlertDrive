package com.intelligenttraffic.alertdrive

data class DrowsinessState(
    val isEyesClosed: Boolean = false,
    val eyeAspectRatio: Float = 0.3f,
    val consecutiveClosedFrames: Int = 0,
    val isDrowsy: Boolean = false,
    val alertLevel: AlertLevel = AlertLevel.NORMAL,
    val faceDetected: Boolean = false,      // Thêm trạng thái phát hiện mặt
    val timestamp: Long = 0L,               // Thời gian phát hiện
    val confidence: Float = 1.0f            // Độ tin cậy phát hiện
)

enum class AlertLevel {
    NORMAL,      // Bình thường - màu xanh
    WARNING,     // Cảnh báo - màu vàng
    CRITICAL     // Nguy hiểm - màu đỏ
}