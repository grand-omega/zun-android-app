# FluxEdit — Project ZUN Android Client

A personal, high-performance image editing client for the **Project ZUN** stack. Optimized for **Samsung Galaxy Z Fold 7**.

## 🚀 Quick Start Cookbook

### 1. Prerequisites
- **Android Studio** (Koala or newer)
- **Java 21** (JetBrains Runtime preferred)
- **Rust Server** (Running on your workstation)

### 2. Configuration (The "Secrets")
The app expects its server connection details in a `local.properties` file at the root of the project. **This file is gitignored.**

Create or edit `local.properties`:
```properties
# Your workstation's LAN IP or Tailscale IP
server.url=http://192.168.1.15:8080

# The token you set as ZUN_TOKEN on the server
api.token=your-secret-token-here
```

### 3. Build & Run Commands

**Debug Build (Fastest for testing)**
```bash
./gradlew installDebug
```

**Release Build (Optimized)**
```bash
# Generate the APK
./gradlew assembleRelease

# The APK will be located at:
# app/build/outputs/apk/release/app-release.apk
```

**Format Code (Lint/Style)**
```bash
./gradlew spotlessApply
```

---

## 🛠 Features

- **Foldable Optimized**: Dynamic Two-Pane layouts and adaptive List-Detail scaffolds.
- **Biometric Security**: Encrypted lockout with customizable timer (Settings).
- **Immersive Photo Viewer**:
    - **Normal Mode**: Swipe left/right between all generations.
    - **Zoom Mode**: Double-tap to lock and pinch-to-zoom/pan on details.
- **Performance**: Automatic **2048px downscaling** and real-time **upload progress tracking**.
- **Robustness**: Diagnostic "Live Ping" connectivity tracking and server-side job cancellation.

---

## 🔐 Security Note
This app is designed for **single-user personal use**. It uses `EncryptedSharedPreferences` for user settings and strictly follows the `Authorization: Bearer` token contract defined in the Rust server.

## 📱 Target Hardware
Primary target: **Samsung Galaxy Z Fold 7** (Android 14+).
The app handles fold/unfold transitions without state loss or Activity restarts.
