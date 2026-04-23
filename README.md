# FluxEdit — Project ZUN Android Client

A personal, high-performance image editing client for the **Project ZUN** stack. Optimized for **Samsung Galaxy Z Fold 7**.

## 🚀 Quick Start Cookbook

### 1. Prerequisites
- **Android Studio** (Koala or newer)
- **Java 21** (JetBrains Runtime preferred)
- **Rust Server** (Running on your workstation)

### 2. Configuration (The "Secrets")
Server URL and API token are **not** baked into the APK. Install the app, then
enter them on the in-app **Setup** screen — they are persisted in
`EncryptedSharedPreferences`.

> Release builds disallow cleartext HTTP (see `network_security_config.xml`).
> Use an HTTPS endpoint (e.g. Tailscale MagicDNS) for release, or use a debug
> build when targeting a plain `http://…` dev server.

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
