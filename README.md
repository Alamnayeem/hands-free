# Hands-Free Phone Controller 📱👁️🎙️

A production-ready system-wide Android application built using Flutter and Android Native (Kotlin) Accessibility Services. This application allows users to completely control every element of their mobile device without touch input.

## 🌟 Key Features

1. **System-Wide Eye Tracking**: Integrates with the front camera to render a real-time gaze cursor, allowing blink detection and dwell clicks on buttons and layouts.
2. **Speech Recognition (English + Bengali)**: Utilizes dual-language continuous listening. Automatically detects and interprets speech in English and Bengali without manual language swaps.
3. **Accessibility Service Integration**: Dispatches real system taps, double taps, long presses, scrolls, and navigation actions system-wide.
4. **Foreground Service**: Ensures background persistence, allowing continuous gaze tracking and audio processing even when the screen is locked or another application is active.
5. **Floating Overlay Windows**: Provides custom floating control tiles and active tracking bubbles to trigger commands directly.

---

## 🏗️ Architecture

```
hands_free_android/
├── android/                   # Native Android Layer
│   └── app/src/main/
│       ├── AndroidManifest.xml # Declares permissions, accessibility & foreground services
│       ├── kotlin/com/handsfree/controller/
│       │   ├── MainActivity.kt                 # Binds MethodChannels to Android OS settings
│       │   ├── ForegroundService.kt            # High-priority background tracking node
│       │   └── HandsFreeAccessibilityService.kt # Dispatches real gestures and taps system-wide
│       └── res/xml/accessibility_service_config.xml
├── lib/                       # Flutter Application Layer
│   ├── main.dart              # Main entry point and state providers
│   ├── models/
│   │   └── command.dart       # Expansion engine for command-phrase mappings
│   ├── services/
│   │   └── method_channel_service.dart # Interprocess communicator
│   ├── screens/
│   │   ├── dashboard_screen.dart       # Live dashboards & permission triggers
│   │   ├── calibration_screen.dart     # 5-Point gaze matrix calibration
│   │   ├── settings_screen.dart        # Configures sensitivies & custom overlays
│   │   └── command_history_screen.dart # Real-time action loggers
│   └── widgets/
│       └── floating_controls.dart      # Custom overlay layouts
└── pubspec.yaml               # Dependency Manifest
```

---

## 🛠️ Step-by-Step Installation & Build

### 1. Prerequisites
Ensure you have the Flutter SDK installed on your workstation.

```bash
flutter --version
```

### 2. Install Project Dependencies
Fetch the reactive UI elements and system wrappers declared in `pubspec.yaml`:

```bash
flutter pub get
```

### 3. Build & Package Android Application (APK)
Compile the Flutter engine and bundle the custom Native Kotlin Accessibility class into a production-ready Release APK compatible with Android 10-16:

```bash
flutter build apk --release
```

The compiled release artifact will be generated at:
`build/app/outputs/flutter-apk/app-release.apk`

---

## ⚙️ Configuration Setup on Device

Once the APK is installed on your Android device, configure these critical system permissions:

1. **Accessibility Services**: Navigate to `Settings` -> `Accessibility` -> `Hands-Free Accessibility Controller` -> Toggle **ON**. This allows the app to perform taps and navigation gestures.
2. **Display Over Other Apps**: Allow overlay permissions so the Gaze Cursor can render system-wide.
3. **Camera & Microphone Permissions**: Grant camera and audio access when prompted by the application dashboard.
