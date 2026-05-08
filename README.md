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

Setup accepts either a full URL or a bare host/IP. Bare entries are normalized
to HTTPS, so `api.yourdomain.com` becomes `https://api.yourdomain.com`.

> Release builds disallow cleartext HTTP (see `network_security_config.xml`).
> Use an HTTPS hostname that resolves on the current network for release, or use
> a debug build when targeting a plain `http://…` dev server.

### 3. Build & Run Commands

All commands are run from the repo root via the Gradle wrapper (`./gradlew`).

**Debug Build (Fastest for testing)**
```bash
./gradlew installDebug                  # build + install to connected device
./gradlew assembleDebug                 # APK only → app/build/outputs/apk/debug/
```

**Release Build (Optimized)**

The `release` build type has R8 code-shrinking, resource shrinking, and the
optimizing ProGuard preset enabled (see `app/build.gradle.kts`). Output size is
typically ~40–60% smaller than debug.

First-time setup for a personal signing key (one-time):
```bash
# 1. Generate a keystore in the repo root
keytool -genkey -v -keystore flux-release.jks -alias flux \
        -keyalg RSA -keysize 2048 -validity 36500

# 2. Copy the template and fill in the passwords you chose
cp keystore.properties.example keystore.properties
```

Both `flux-release.jks` and `keystore.properties` are gitignored. Release builds
require `keystore.properties`; Gradle fails the build if it is absent so a
production APK is never published unsigned or debug-signed by accident.

Build and install:
```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

**Common install failures**
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → `adb uninstall dev.zun.flux` first (signature mismatch with an already-installed build).
- `INSTALL_FAILED_VERSION_DOWNGRADE` → bump `versionCode` in `app/build.gradle.kts`.
- `INSTALL_PARSE_FAILED_NO_CERTIFICATES` → `keystore.properties` paths or passwords are wrong.

**Other useful tasks**
```bash
./gradlew spotlessApply                 # format Kotlin sources
./gradlew clean                         # wipe build/ directories
./gradlew :app:tasks                    # list every task on the app module
```

**Automate Formatting (Git Hooks)**
To automatically run Spotless before every commit, run:
```bash
./scripts/setup-hooks.sh
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
