# AlertDrive 🚗💤
### Real-Time Drowsiness Detection System for Driver Safety

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![ML Kit](https://img.shields.io/badge/ML-Google%20ML%20Kit-orange.svg)](https://developers.google.com/ml-kit)
[![OpenCV](https://img.shields.io/badge/CV-OpenCV%204.5.5-red.svg)](https://opencv.org)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📱 Overview

**AlertDrive** is an advanced Android application that uses computer vision and machine learning to detect driver drowsiness in real-time. The system monitors the driver's eyes using the front camera and triggers alerts when signs of fatigue are detected, helping prevent accidents caused by drowsy driving.

### 🎯 Key Features

- **🔍 Real-time Face Detection** - Uses Google ML Kit for accurate face tracking
- **👁️ Eye Monitoring** - Advanced Eye Aspect Ratio (EAR) calculation
- **🚨 Multi-level Alerts** - Progressive warning system (Normal → Warning → Critical)
- **🔊 Audio & Haptic Feedback** - Sound alerts and vibration patterns
- **📊 Live Metrics Display** - Real-time EAR values and drowsiness levels
- **🧪 Test Mode** - Built-in testing functionality for alert system
- **📱 Modern UI** - Clean Material Design interface

---

## 🧠 How It Works

### Detection Algorithm

The drowsiness detection is based on the **Eye Aspect Ratio (EAR)** algorithm:

```
EAR = (|p2-p6| + |p3-p5|) / (2 * |p1-p4|)
```

Where p1-p6 are the eye landmark coordinates.

### Alert Thresholds

| State | EAR Threshold | Consecutive Frames | Action |
|-------|---------------|-------------------|--------|
| **Normal** | EAR ≥ 0.25 | - | 👁️ Monitoring |
| **Warning** | EAR < 0.25 | 3-4 frames | ⚠️ Light vibration |
| **Critical** | EAR < 0.25 | 5+ frames | 🚨 Loud alarm + strong vibration |

---

## 🛠️ Technical Stack

### Core Technologies
- **Android SDK 36** - Target Android 14+
- **Kotlin** - Modern Android development
- **CameraX** - Camera lifecycle management
- **Google ML Kit** - Face detection and landmarks
- **OpenCV 4.5.5** - Computer vision processing

### Architecture
- **MVVM Pattern** - Clean architecture
- **Real-time Processing** - Optimized frame analysis
- **Lifecycle-aware Components** - Proper resource management

---

## 📦 Installation

### Prerequisites
- Android Studio Arctic Fox (2020.3.1) or later
- Android device with API level 21+ (Android 5.0+)
- Front-facing camera
- Microphone and vibration permissions

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/tris1404/AlertDrive.git
   cd AlertDrive
   ```

2. **Download OpenCV SDK**
   ```bash
   # Download OpenCV Android SDK 4.5.5
   # Extract to project root as 'sdk' folder
   # Or use the included gradle configuration
   ```

3. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

4. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   # Or use Android Studio's Run button
   ```

---

## 🎮 Usage

### Basic Operation

1. **Launch the app** and grant camera permissions
2. **Position your face** in the camera view
3. **Tap "Start Detection"** to begin monitoring
4. **Keep eyes on the road** - the app runs in background

### Testing the System

- **Long press** the toggle button to trigger a test alert
- Observe the alert progression from normal → warning → critical
- Verify audio and vibration feedback

### UI Components

| Component | Description |
|-----------|-------------|
| **Camera Preview** | Live video feed with face overlay |
| **EAR Display** | Real-time Eye Aspect Ratio value |
| **Progress Bar** | Drowsiness level indicator |
| **Status Panel** | Current detection state |
| **Toggle Button** | Start/Stop detection control |

---

## 🔧 Configuration

### Customizable Parameters

```kotlin
// In DrowsinessDetector.kt
private const val EAR_THRESHOLD = 0.25f      // Eye closure threshold
private const val CONSECUTIVE_FRAMES = 5      // Frames for critical alert
private const val FRAME_CHECK_COUNT = 20      // Maximum frame buffer

// In AlertManager.kt
private const val MIN_ALERT_INTERVAL = 1000L  // Minimum time between alerts
private const val CONTINUOUS_ALERT_INTERVAL = 2000L // Continuous alert frequency
```

### Alert Customization

- **Sound**: Replace `app/src/main/res/raw/alert_sound.mp3`
- **Vibration**: Modify vibration patterns in `AlertManager.kt`
- **UI Colors**: Update theme colors in `values/colors.xml`

---

## 🧪 Testing

### Test Cases

- ✅ Face detection accuracy
- ✅ EAR calculation precision
- ✅ Alert threshold validation
- ✅ Camera permission handling
- ✅ Background processing stability

### Performance Metrics

- **Frame Rate**: ~30 FPS on modern devices
- **Detection Latency**: <100ms
- **Memory Usage**: ~50MB average
- **Battery Impact**: Moderate (camera intensive)

---

## 📊 Project Structure

```
AlertDrive/
├── app/
│   ├── src/main/java/com/intelligenttraffic/alertdrive/
│   │   ├── MainActivity.kt           # Main app activity
│   │   ├── DrowsinessDetector.kt     # Core detection algorithm
│   │   ├── AlertManager.kt           # Alert system handler
│   │   └── DrowsinessState.kt        # State data classes
│   ├── src/main/res/
│   │   ├── layout/                   # UI layouts
│   │   ├── drawable/                 # Icons and graphics
│   │   ├── raw/                      # Alert sound file
│   │   └── values/                   # Strings, colors, themes
│   └── build.gradle.kts              # App dependencies
├── gradle/
│   └── libs.versions.toml            # Version catalog
├── sdk/                              # OpenCV SDK (not committed)
├── .gitignore                        # Git ignore rules
└── README.md                         # This file
```

---

## 🚀 Future Enhancements

### Planned Features
- [ ] **Multi-face Detection** - Support for multiple passengers
- [ ] **Yawn Detection** - Additional fatigue indicator
- [ ] **Head Pose Estimation** - Distraction detection
- [ ] **GPS Integration** - Location-based alerts
- [ ] **Machine Learning Model** - Custom trained detection
- [ ] **Cloud Analytics** - Driving pattern analysis
- [ ] **Voice Commands** - Hands-free control

### Performance Improvements
- [ ] **Edge AI Optimization** - On-device model inference
- [ ] **Battery Optimization** - Adaptive frame rate
- [ ] **Low-light Enhancement** - IR camera support

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Setup
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Code Style
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add comments for complex algorithms
- Write unit tests for new features

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👥 Authors

- **tris1404** - *Initial work* - [@tris1404](https://github.com/tris1404)

---

## 🙏 Acknowledgments

- **Google ML Kit** - For robust face detection APIs
- **OpenCV Community** - For computer vision libraries  
- **Android Developers** - For CameraX and modern Android APIs
- **Research Papers** - EAR algorithm from drowsiness detection studies

---

## 📞 Support

If you have any questions or issues:

- 📧 Email: nguyentaitri1442005@gmail.com
- 🐛 Issues: [GitHub Issues](https://github.com/tris1404/AlertDrive/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/tris1404/AlertDrive/discussions)

---

## ⚠️ Disclaimer

**AlertDrive is a driver assistance tool and should not be relied upon as the sole method for preventing drowsy driving. Always prioritize getting adequate rest before driving and pull over safely if you feel drowsy.**

---

<div align="center">

**Made with ❤️ for safer roads**

[⭐ Star this project](https://github.com/tris1404/AlertDrive) if you find it useful!

</div>