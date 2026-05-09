# Architecture

Single-module Android app, Kotlin + Jetpack Compose. No DI framework — the application class wires concrete singletons and passes a narrow `Repositories` bundle into the navigation graph.

## Layers

```
Composables (ui/*)
    ↓  state hoisted from
ViewModels (one per feature)
    ↓  call narrow interfaces from
Repositories (data/repo)
    ↓  delegate to
┌──────────────┬──────────────┬─────────────────────┐
│ FluxApi      │ AppDatabase  │ WorkManager workers │
│ (Retrofit)   │ (Room)       │ (upload, delete)    │
└──────────────┴──────────────┴─────────────────────┘
        ↑
NetworkResolver picks LAN or Tailscale; CertPinStore enforces pinning;
Diagnostics interceptor records errors and timings.
```

## Package map

All paths relative to `app/src/main/java/dev/zun/flux/`.

### `data/`

| Package | Role | Key files |
|---|---|---|
| `data/api` | Retrofit interface for the server contract | `FluxApi.kt` |
| `data/local` | Room database, DAOs, entities | `AppDatabase.kt`, `JobDao.kt`, `JobEntity.kt`, `PendingDeleteEntity.kt` |
| `data/repo` | Narrow repository interfaces + the unified implementation + persistence helpers | `RealJobRepository.kt`, `SettingsManager.kt`, `KeystoreSecureStore.kt`, `OfflineImageCache.kt` |
| `data/net` | Discovery, route selection, cert pinning | `ServerDiscovery.kt`, `NetworkResolver.kt`, `CertPinStore.kt`, `CertCapturer.kt` |
| `data/worker` | Background WorkManager jobs | `JobUploadWorker.kt`, `DeleteSyncWorker.kt` |
| `data/diag` | OkHttp interceptor that records errors / timings for the Settings → Diagnostics panel | `Diagnostics.kt` |

### `ui/`

| Package | Role |
|---|---|
| `ui/auth` | Biometric gate, lock screen, `AuthStateHolder` (lockout timer, `isAuthed` flag) |
| `ui/capture` | CameraX-based source-image capture |
| `ui/common` | Shared Composables (snackbar host, error rows, etc.) |
| `ui/gallery` | Paginated job list, filtering, photo viewer, before/after compare |
| `ui/home` | Image picker, prompt picker, batch submission (`HomeViewModel`) |
| `ui/nav` | `AppNavHost` — start destination resolves to Setup or Home based on `isConfigured` |
| `ui/progress` | Single-job progress polling + batch multi-job progress |
| `ui/result` | Result detail, save to MediaStore, regenerate, delete |
| `ui/settings` | Server URL/token, offline cache stats, cert pinning, diagnostics, security lockout, `SetupScreen` |
| `ui/theme` | Material 3 theme |

### `util/`

Small helpers: `ImageUtils`, `ErrorMessages`, `ServerUrls`, `ShareUtils`, `MediaStoreSaver`, `PromptLabels`, `Time`.

### Entry points

- `FluxApp.kt` — `Application` subclass. Constructs the `OkHttpClient`, `NetworkResolver`, `OfflineImageCache`, `AuthStateHolder`, `SettingsManager`, and the `Repositories` bundle.
- `MainActivity.kt` — single activity hosting `AppNavHost`.
- `ui/nav/AppNavHost.kt` — Compose `NavHost`; routes screens, wires per-feature ViewModels with the right repository interfaces.

## Data flows

### 1. First-time setup

1. `AppNavHost` checks `SettingsManager.isConfigured`. If false, the start destination is `SetupScreen`.
2. The user enters an IP/hostname. `ServerDiscovery.scan(host)` tries `http`/`https` × `{5000, 5001, 7860, 8000, 8188}` against `/api/v1/health` with short timeouts, off the main thread.
3. Each responding host returns its server version and ComfyUI status; the user picks one (or taps **Enter URL manually**).
4. The user pastes their token. The screen calls `HealthRepository.check(url, token)` to verify.
5. On success, URLs/routing preferences are written to app-private preferences and the API token is written to `KeystoreSecureStore` via `SettingsManager`. `NetworkResolver.refresh()` rebuilds the OkHttp/Retrofit stack with the new credentials.

### 2. Submitting a job

1. User picks 1–N source images (gallery or `ui/capture`) and a prompt. `HomeViewModel` packages each into a draft.
2. For each draft: `UploadRepository.submit(...)` POSTs JSON describing the input. If the server already has the input bytes it returns `200`; if not it returns `409 need_upload`.
3. On `need_upload`, `JobUploadWorker` is enqueued via WorkManager. It performs the multipart upload (with retries up to `Tuning.MAX_UPLOAD_RETRIES`) and re-submits.
4. The UI navigates to single or batch progress. `ProgressViewModel` polls `JobRepository.poll(id)` until each job reaches a terminal state.
5. Final job rows are persisted to Room (`JobEntity`); thumb/preview/result images are written to `OfflineImageCache`.

### 3. Offline read path

- The gallery list is paged from Room (`JobDao` + `Paging 3`), so it renders without the network.
- Image rows render with Coil. The image source URL is resolved through `OfflineImageCache.uri(jobId, variant)` first (`file://...`), falling back to a server URL if no cache exists.
- When neither a cache entry nor a reachable server is available, the row shows the `gallery_not_cached_needs_network` badge.

### 4. Soft delete

1. Delete from gallery or viewer inserts a `PendingDeleteEntity` and removes the job from local visible state. A snackbar offers Undo.
2. `DeleteSyncWorker` runs in the background, posts each pending delete to the server, and clears the entity on success.
3. Undo within 30 days resurrects the row from `PendingDeleteEntity` before the worker drains it.

## Wiring (no DI framework)

`FluxApp.onCreate()` builds:

- `OkHttpClient` (with `Diagnostics` interceptor and the optional `CertPinStore`)
- `NetworkResolver` (holds active route, exposes a `refresh()` callback)
- `AppDatabase` (plain SQLite; SQLCipher was removed in commit `8f05e6b`)
- `OfflineImageCache` (rooted at `context.filesDir/offline_images`)
- `RealJobRepository` (the single concrete implementation), exposed through five narrow interfaces
- `Repositories` — a small bundle that's threaded through `AppNavHost` so each screen receives only the interfaces it needs

When the user changes the server URL, token, or pinning state, `NetworkResolver.refresh()` swaps the `OkHttpClient` and the Retrofit-backed `FluxApi` is rebuilt. Compose state holders observe the new instances on next composition.

## Persistence summary

| Where | What |
|---|---|
| Room (`AppDatabase`, plain SQLite) | `jobs` and `pending_deletes` tables. Schema version 4. Schemas exported under `app/schemas/`. |
| Plain SharedPreferences (`settings`) | Server URLs, active route, connection mode, biometric lockout duration, last successful unlock timestamp |
| `KeystoreSecureStore` (`secure_v2`) | API token encrypted as AES/GCM ciphertext with the key held in Android Keystore |
| `files/offline_images/` | JPEG cache for thumb / preview / result variants, LRU-evicted |
| WorkManager | Upload and delete-sync work, persists across process death |

## Server contract

The Retrofit interface in `data/api/FluxApi.kt` is the single point of truth for what the app expects from the server. The matching server-side contract — endpoint paths, payload shapes, error codes (including `409 need_upload`) — is documented in [`../../zun-rust-server/API_CONTRACT.md`](../../zun-rust-server/API_CONTRACT.md). Keep them in sync when adding endpoints.
