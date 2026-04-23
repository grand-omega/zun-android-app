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
2. [Current Status](#current-status-v013-beta)
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

## Current Status: v0.1.3-beta
The v1 client is **100% feature-complete** and has undergone a robustness/UX polish phase. It is currently running against a `FakeJobRepository`. The next major step is integration with the real Rust server.

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

---

## Tech stack

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Language | Kotlin | Standard for modern Android |
| UI | Jetpack Compose | Declarative, foldable-friendly, modern |
| Min SDK | 30 (Android 11) | Covers Z Fold 7; unlocks modern APIs |
| Compile / Target SDK | 36 (Android 16) | Matches installed SDK platform |
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
3. **Biometric app gate.** Fingerprint (or device PIN fallback) required on launch and after 60s backgrounding.

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

### v2 (Polish, up next)

- [ ] Real server integration (`RealJobRepository` implementation)
- [ ] Image preprocessing (downscale to 2048px before upload)
- [ ] WorkManager-based polling (survives app backgrounding)
- [ ] Re-run a past generation with a different prompt
- [ ] Custom free-text prompt alongside predefined ones
- [ ] Multi-select delete in gallery
- [ ] Filter gallery by prompt
- [ ] Local caching of thumbnails for offline gallery browsing

### v3 (Nice-to-have)

- [ ] Half-folded (tabletop/book) layout optimization
- [ ] Retry failed jobs (server-side persistence)
- [ ] Cancel running jobs (DELETE /api/jobs/{id})

---

## UI design

### Design philosophy

- **Flat and minimal.** No gradients, no shadows.
- **Single accent color.** Blue (`#185FA5`) for all primary actions.
- **Sentence case everywhere.** "Take photo", not "Take Photo".
- **Material 3 with custom theming.** Fixed light/dark palettes in `Color.kt`.

---

## Foldable support (Z Fold 7)

### Core principle

**Pick layout by window width, not by device type.** Uses `WindowSizeClass.widthSizeClass`.

### Adaptive layouts implemented:
- **Home Screen**: Single column (folded) vs. Two-pane row (unfolded).
- **Gallery**: `ListDetailPaneScaffold` for side-by-side browsing.
- **Result Screen**: Pager (folded) vs. Side-by-side row (unfolded).
- **Progress Screen**: Content width-capped to 500dp.

---

## Networking and API contract

### Endpoints (expected from server)

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

## Build order and milestones

### Milestone 1–8: v1 Client Foundations (Complete ✅)
All core screens, adaptive layouts, navigation, and fake data flow are implemented, tested on a Z Fold 7, and synchronized across `main` and `dev` branches.

### Milestone 9: Real Server Integration (Planned)
- [ ] Implement `RealJobRepository` using Retrofit and OkHttp.
- [ ] Set up `local.properties` with Tailnet URL and Bearer token.
- [ ] Verify connectivity and async flow against the Rust server.

---

## Recent Improvements (v0.1.3-beta)

### Robustness
- **Dynamic Health Tracking**: Home Screen now pings `/api/health` every 10s.
- **Polling Resilience**: Loop handles up to 5 consecutive network errors before failing.
- **Retry Logic**: Fully implemented the "Retry" button on the Progress Screen.

### UX & Polish
- **Haptic Feedback**: Vibrations on shutter, submission, and completion.
- **Sharing**: Integrated Android `FileProvider` for sharing results.
- **Technical Debt**: Refactored CameraX, enabled Edge-to-Edge, and fixed lint issues.
