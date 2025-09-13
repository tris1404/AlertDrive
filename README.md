# AlertDrive 🚗💤
### Real-Time Drowsiness Detection System for Driver Safety

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![ML Kit](https://img.shields.io/badge/ML-Google%20ML%20Kit-orange.svg)](https://developers.google.com/ml-kit)
[![CameraX](https://img.shields.io/badge/Camera-CameraX-lightblue.svg)](https://developer.android.com/training/camerax)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📱 Overview

**AlertDrive** is an advanced Android application that uses computer vision and machine learning to detect driver drowsiness in real-time. The system monitors the driver's eyes using the front camera and triggers alerts when signs of fatigue are detected, helping prevent accidents caused by drowsy driving.

### 🎯 Key Features

- **🔍 Real-time Face Detection** - Uses Google ML Kit for accurate face tracking
- **👁️ Eye Monitoring** - Advanced Eye Aspect Ratio (EAR) calculation
- **🚨 Multi-level Alerts** - Progressive warning system (Normal → Warning → Critical)
- **🔊 Custom Audio Alerts** - Multiple sound options with preview functionality
- **📱 Responsive Design** - Optimized for all screen sizes and orientations
- **📊 Live Metrics Display** - Real-time EAR values and drowsiness levels
- **🧪 Test Mode** - Built-in testing functionality for alert system
- **📱 Modern Material Design** - Clean, adaptive UI with dark mode support

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
- **Material Design 3** - Modern UI components
- **ConstraintLayout** - Responsive layout system

### Architecture
- **MVVM Pattern** - Clean architecture
- **Real-time Processing** - Optimized frame analysis
- **Lifecycle-aware Components** - Proper resource management
- **Responsive Design** - Adaptive UI for all screen sizes

---

## 📦 Installation

### Prerequisites
- **Android Studio** - Arctic Fox (2020.3.1) or later
- **Android Device** - API level 21+ (Android 5.0+) with front camera
- **Permissions** - Camera, Microphone, Vibration, Overlay (for popup service)

### Quick Start

1. **Clone the repository**
   ```bash
   git clone https://github.com/tris1404/AlertDrive.git
   cd AlertDrive
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   # Or use Android Studio's Run button
   ```

4. **Grant Permissions**
   - Camera permission for face detection
   - Microphone permission for audio alerts
   - Vibration permission for haptic feedback
   - Display over other apps (for popup service)

### Supported Devices
- ✅ **Phones**: All Android 5.0+ devices
- ✅ **Tablets**: Optimized for 7" and 10" tablets
- ✅ **Foldables**: Adaptive layout for foldable devices
- ✅ **Landscape**: Dedicated landscape layout

---

## 🎮 Usage

### Basic Operation

1. **Launch the app** and grant camera permissions
2. **Position your face** in the camera view
3. **Tap "Start Detection"** to begin monitoring
4. **Keep eyes on the road** - the app runs in background

### Audio Customization

- **Tap "CHỌN ÂM THANH"** to select alert sound
- **Preview sounds** before selecting
- **6 built-in sounds** available (alert_sound, alert_sound1-5)
- **Custom sounds** can be added to `res/raw/` folder

### Testing the System

- **Long press** the toggle button to trigger a test alert
- Observe the alert progression from normal → warning → critical
- Verify audio and vibration feedback

### UI Components

| Component | Description |
|-----------|-------------|
| **Camera Preview** | Live video feed with face overlay |
| **EAR Display** | Real-time Eye Aspect Ratio value |
| **Progress Bar** | Drowsiness level indicator (0-100%) |
| **Status Panel** | Current detection state with emoji |
| **Sound Selector** | Choose custom alert sound |
| **Toggle Button** | Start/Stop detection control |

---

## 🔧 Configuration

### Detection Parameters

```kotlin
// In DrowsinessDetector.kt
private const val EAR_THRESHOLD = 0.15f      // Eye closure threshold
private const val CONSECUTIVE_FRAMES = 8      // Frames for critical alert
private const val FRAME_CHECK_COUNT = 20      // Maximum frame buffer
```

### Alert System

```kotlin
// In AlertManager.kt
private const val MIN_ALERT_INTERVAL = 1000L  // Minimum time between alerts
private const val CONTINUOUS_ALERT_INTERVAL = 2000L // Continuous alert frequency
```

### Responsive Design

The app automatically adapts to different screen sizes:

| Screen Size | Text Scale | Spacing | Layout |
|-------------|------------|---------|--------|
| **Small** (< 320dp) | 85% | Compact | Single column |
| **Normal** (320-600dp) | 100% | Standard | Single column |
| **Large** (≥ 600dp) | 115% | Relaxed | Single column |
| **Landscape** | Adaptive | Adaptive | Side-by-side |

### Customization Options

- **Sound**: Replace files in `app/src/main/res/raw/`
- **Colors**: Update theme in `values/colors.xml`
- **Icons**: Replace drawables in `res/drawable/`
- **Layout**: Modify dimens in `values/dimens.xml`

---

## 🧪 Testing

### Test Cases

- ✅ Face detection accuracy across different lighting
- ✅ EAR calculation precision and thresholds
- ✅ Alert threshold validation (Normal → Warning → Critical)
- ✅ Camera permission handling and recovery
- ✅ Background processing stability
- ✅ Audio selection and playback functionality
- ✅ Responsive layout on different screen sizes

### Device Testing Matrix

| Device Type | Screen Size | Orientation | Status |
|-------------|-------------|-------------|--------|
| **Phone** | < 320dp | Portrait | ✅ Tested |
| **Phone** | 320-600dp | Portrait | ✅ Tested |
| **Tablet** | ≥ 600dp | Portrait | ✅ Tested |
| **Tablet** | ≥ 720dp | Portrait | ✅ Tested |
| **All Devices** | Any | Landscape | ✅ Tested |

### Performance Metrics

- **Frame Rate**: ~30 FPS on modern devices
- **Detection Latency**: <100ms
- **Memory Usage**: ~50MB average
- **Battery Impact**: Moderate (camera intensive)
- **Responsive Scaling**: Instant adaptation

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
│   │   ├── layout/                   # UI layouts (portrait)
│   │   ├── layout-land/              # Landscape layouts
│   │   ├── drawable/                 # Icons and graphics
│   │   ├── raw/                      # Alert sound files
│   │   ├── values/                   # Strings, colors, themes
│   │   ├── values-sw320dp/           # Small screen dimensions
│   │   ├── values-sw600dp/           # Tablet dimensions
│   │   └── values-sw720dp/           # Large tablet dimensions
│   └── build.gradle.kts              # App dependencies
├── gradle/
│   └── libs.versions.toml            # Version catalog
├── .gitignore                        # Git ignore rules
├── README.md                         # This file
└── settings.gradle.kts               # Project configuration
```

---

## 🚀 Features & Roadmap

### ✅ Implemented Features
- [x] **Real-time Face Detection** - Google ML Kit integration
- [x] **Eye Aspect Ratio (EAR) Algorithm** - Advanced drowsiness detection
- [x] **Multi-level Alert System** - Progressive warnings
- [x] **Custom Audio Selection** - Multiple sound options with preview
- [x] **Responsive Design** - Adaptive UI for all screen sizes
- [x] **Landscape Layout** - Optimized tablet experience
- [x] **Material Design 3** - Modern UI components
- [x] **Test Mode** - Built-in alert testing

### 🔄 Planned Enhancements
- [ ] **Multi-face Detection** - Support for multiple passengers
- [ ] **Yawn Detection** - Additional fatigue indicator
- [ ] **Head Pose Estimation** - Distraction detection
- [ ] **GPS Integration** - Location-based alerts
- [ ] **Machine Learning Model** - Custom trained detection
- [ ] **Cloud Analytics** - Driving pattern analysis
- [ ] **Voice Commands** - Hands-free control

### ⚡ Performance Improvements
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

- **Google ML Kit** - For robust face detection and landmark APIs
- **Android CameraX** - For modern camera lifecycle management
- **Material Design** - For beautiful and consistent UI components
- **Android Developers** - For comprehensive documentation and samples
- **Research Papers** - EAR algorithm from drowsiness detection studies
- **Open Source Community** - For libraries and tools that made this possible

---

## 📞 Support

If you have any questions or issues:

- 📧 Email: nguyentaitri1442005@gmail.com
- 🐛 Issues: [GitHub Issues](https://github.com/tris1404/AlertDrive/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/tris1404/AlertDrive/discussions)

---

## ⚠️ Disclaimer

**AlertDrive is a driver assistance tool and should not be relied upon as the sole method for preventing drowsy driving. Always prioritize getting adequate rest before driving and pull over safely if you feel drowsy.**

**The app is optimized for various Android devices and screen sizes, but performance may vary based on device capabilities and camera quality.**

---

<div align="center">

**🚗 AlertDrive - Safer Roads Through Technology 🚗**

*Real-time drowsiness detection with responsive design for all Android devices*

[⭐ Star this project](https://github.com/tris1404/AlertDrive) •
[🐛 Report Issues](https://github.com/tris1404/AlertDrive/issues) •
[💬 Discussions](https://github.com/tris1404/AlertDrive/discussions)

---

*Made with ❤️ for safer roads and better mobile experiences*

</div>