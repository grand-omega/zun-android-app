# Project ZUN — Android App (FluxEdit) Development Plan

> Project ZUN is a personal, self-hosted image editing stack:
> - **FLUX2 + ComfyUI** pipeline (the model side)
> - **Rust server** (axum + SQLite) that wraps ComfyUI and exposes an HTTP API
> - **FluxEdit** — this Android app; the client
>
> Sideloaded APK only. Single user. Samsung Galaxy Z Fold 7 as the target device.
>
> **Android applicationId / package:** `dev.zun.flux`

---

## Table of contents

1. [Overview and scope](#overview-and-scope)
2. [Current Status](#current-status-v20-stable)
3. [Architecture](#architecture)
4. [Tech stack](#tech-stack)
5. [Security model](#security-model)
6. [Feature set](#feature-set)
7. [UI design](#ui-design)
8. [Foldable support (Z Fold 7)](#foldable-support-z-fold-7)
9. [Screen-by-screen specification](#screen-by-screen-specification)
10. [Networking and API contract](#networking-and-api-contract)
11. [Biometric authentication](#biometric-authentication)
12. [Project structure](#project-structure)
13. [Build order and milestones](#build-order-and-milestones)
14. [Testing approach](#testing-approach)
15. [Development environment](#development-environment)
16. [Build and deployment](#build-and-deployment)

---

## Overview and scope

FluxEdit is a personal Android app that acts as a client for a self-hosted Rust server wrapping a ComfyUI + FLUX2 image editing pipeline. The user picks an image (camera or gallery), selects a predefined prompt, submits to the server, waits for generation, and views the result. A gallery screen browses past generations.

### Target device

Samsung Galaxy Z Fold 7 running a modern Android version (Android 14+). Must work well in **both folded and unfolded states**.

---

## Current Status: v2.0-stable
The app is **fully feature-complete and stable**. It is hardened with a Room database for persistent history, WorkManager for background polling, and exponential backoff retry logic. It is integrated with the real Rust server and features immersive photo viewing with "Zoom Mode."

---

## Architecture

### System diagram

```
┌─────────────────┐       Tailscale (WireGuard)        ┌──────────────────┐
│  Android App    │ ◄──────────────────────────────► │   Rust Server    │
│  (Kotlin/       │    HTTPS over tailnet (100.x.x.x) │  (axum + SQLite) │
│   Compose)      │                                    │        │          │
│                 │                                    │        ▼          │
│  - Camera/      │                                    │  ┌────────────┐  │
│    Gallery      │                                    │  │  ComfyUI   │  │
│  - Prompt       │                                    │  │  (FLUX2)   │  │
│    Picker       │                                    │  └────────────┘  │
│  - Gallery      │                                    │                  │
└─────────────────┘                                    └──────────────────┘
```

### App-level architecture

- **MVVM with Compose.** ViewModels hold UI state and orchestrate long-running operations. Composables are stateless where possible.
- **Single Activity.** `MainActivity` hosts a Compose `NavHost`.
- **Single `JobRepository`.** All API calls go through one repository; ViewModels depend on it.
- **Encrypted Settings.** `SettingsManager` handles secure persistence of user preferences.

---

## Tech stack

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Language | Kotlin 2.2.10 | Standard for modern Android; K2 compiler enabled |
| UI | Jetpack Compose | Declarative, foldable-friendly, modern |
| Min SDK | 30 (Android 11) | Covers Z Fold 7; unlocks modern APIs |
| Compile / Target SDK | 36 (Android 16) | Matches installed SDK platform |
| Build Tools | AGP 9.2.0 / Gradle 9.4.1 | Latest stable versions |
| Navigation | Navigation-Compose | Simple for a handful of screens |
| Networking | Retrofit + OkHttp | Standard, reliable |
| Serialization | kotlinx.serialization | First-party Kotlin, clean syntax |
| Image loading | Coil | Compose-native, great caching |
| Camera | CameraX | Lifecycle-aware, handles foldables |
| Adaptive layouts | Material 3 Adaptive | `ListDetailPaneScaffold` for foldables |

---

## Security model

### Layers

1. **Tailscale VPN.** Server binds only to its tailnet IP. Tailscale provides E2E encryption.
2. **Bearer token auth.** Every request carries `Authorization: Bearer <token>`.
3. **Biometric app gate.** Fingerprint (or device PIN fallback) required on launch and after a **customizable grace period**.
4. **Encrypted Storage.** User settings are stored using `EncryptedSharedPreferences`.

---

## Feature set

### v1 (Complete ✅)

- [x] Biometric lock on app launch (with device PIN fallback)
- [x] Pick image from gallery (Photo Picker API)
- [x] Take photo (CameraX)
- [x] Select from predefined prompts (fetched from server's `/api/prompts`)
- [x] Submit image + prompt to server
- [x] Poll for result with progress UI
- [x] Display result (before/after swipeable pager or side-by-side)
- [x] Save result to phone gallery (MediaStore)
- [x] Browse past generations (gallery grid)
- [x] View job detail (input + output + metadata)
- [x] Delete a past generation (with confirmation)
- [x] Full adaptive layout for folded and unfolded states
- [x] Share sheet integration (v1.1 completion)
- [x] Connection status banner (v1.1 robustness)
- [x] Haptic feedback on key actions (v1.1 polish)

### v2 (Complete ✅)

- [x] Real server integration (`RealJobRepository` implementation)
- [x] Immersive Photo Viewer (Horizontal swiping history)
- [x] Pinch-to-zoom and Pan support
- [x] Manual refresh (Pull-to-refresh on Home)
- [x] Diagnostic "Live Ping" connectivity tracking
- [x] Settings screen with App Info and customizable lockout
- [x] True server-side cancellation (DELETE on cancel)
- [x] Robust remote image saving (OkHttp streaming)

---

## UI design

### Design philosophy

- **Flat and minimal.** No gradients, no shadows.
- **Single accent color.** Blue (`#185FA5`) for all primary actions.
- **Material 3 with custom theming.** Fixed light/dark palettes in `Color.kt`.

---

## Foldable support (Z Fold 7)

### Core principle

**Pick layout by window width, not by device type.** Uses `WindowSizeClass.widthSizeClass`.

### Adaptive layouts implemented:
- **Home Screen**: Single column (folded) vs. Two-pane row (unfolded).
- **Gallery**: `ListDetailPaneScaffold` for side-by-side browsing.
- **Result Screen**: Pager (folded) vs. Side-by-side row (unfolded).

---

## Screen-by-screen specification

### 1. Lock screen (biometric gate)
Shown on start and resume after grace period. System biometric prompt overlay.

### 2. Home screen
Adaptive layout for image submission. Dynamic prompt chips. Connection indicator. Pull-to-refresh.

### 3. Camera screen
CameraX preview and capture. Gallery shortcut.

### 4. Progress screen
Async polling with input preview and metadata card. True cancellation support.

### 5. Result screen
Before/after comparison (Pager or Side-by-side). Save/Share/Re-run actions.

### 6. Gallery / Photo Viewer
Immersive horizontal pager with zoom/pan. Long-press for details and saving.

### 7. Settings screen
App metadata and security timer configuration.

---

## Networking and API contract

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/health` | Connectivity check |
| GET | `/api/prompts` | List predefined prompts |
| POST | `/api/jobs` | Submit new job |
| GET | `/api/jobs/{id}` | Get job status |
| GET | `/api/jobs/{id}/result` | Full-resolution result |
| GET | `/api/jobs` | Paginated job history |
| DELETE | `/api/jobs/{id}` | Delete job |

---

## Biometric authentication

- **UI-level gating.** Fingerprint prompt on launch and resume.
- **Grace Period.** Configurable via Settings (default 60s).
- **Security.** No cryptographic binding for v1/v2; Tailscale + Bearer token provided defense in depth.

---

## Project structure

- `dev.zun.flux.data`: API, DTOs, Repository.
- `dev.zun.flux.ui`: Compose screens, ViewModels, Theme.
- `dev.zun.flux.util`: Media/File helpers, Time, Window.

---

## Build order and milestones

### Milestone 1–8: v1 Foundations (Complete ✅)
### Milestone 9: Real Server Integration (Complete ✅)
### Milestone 10: v2 Performance & Resilience (Complete ✅)

---

## Testing approach

- Manual testing on Z Fold 7.
- Adaptive layout verification (Fold/Unfold).
- Networking stress tests (Refresh, Cancellation).

---

## Development environment

- Gentoo Linux workstation.
- Android Studio / SDK 36.
- GitHub Actions for CI.

---

## Build and deployment

- Sideloaded APK.
- Debug and Release variants.
- Automated CI APK generation.
