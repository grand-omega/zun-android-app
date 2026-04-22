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
2. [Architecture](#architecture)
3. [Tech stack](#tech-stack)
4. [Security model](#security-model)
5. [Feature set](#feature-set)
6. [UI design](#ui-design)
7. [Foldable support (Z Fold 7)](#foldable-support-z-fold-7)
8. [Screen-by-screen specification](#screen-by-screen-specification)
9. [Networking and API contract](#networking-and-api-contract)
10. [Biometric authentication](#biometric-authentication)
11. [Project structure](#project-structure)
12. [Build order and milestones](#build-order-and-milestones)
13. [Testing approach](#testing-approach)
14. [Development environment](#development-environment)
15. [Build and deployment](#build-and-deployment)

---

## Overview and scope

FluxEdit is a personal Android app that acts as a client for a self-hosted Rust server wrapping a ComfyUI + FLUX2 image editing pipeline. The user picks an image (camera or gallery), selects a predefined prompt, submits to the server, waits for generation, and views the result. A gallery screen browses past generations.

### Deliberate non-goals

- **Not for Play Store distribution.** APK-only, sideloaded to the developer's own device.
- **Not multi-user.** One user, one device, one server. Skip any feature that assumes user accounts, per-user data isolation, or public distribution.
- **Not a general image editor.** No cropping, filters, manual adjustments. The server's prompts do the work.
- **No offline mode.** The server is always reachable via Tailscale when the app is in use. No queueing jobs for later upload.

### Target device

Samsung Galaxy Z Fold 7 running a modern Android version (Android 14+). Must work well in **both folded and unfolded states**. Optional polish: half-folded (tabletop/book) modes.

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

### Client-server interaction model

- **Async job submission.** Image generation takes 10–60+ seconds. The app does NOT hold a synchronous HTTP connection. Instead: `POST /api/jobs` returns `{job_id}`, then the client polls `GET /api/jobs/{id}` until status is `done` or `failed`, then fetches the result.
- **Server is source of truth for history.** Past generations are stored on the server. The app does NOT maintain its own history database. The gallery screen simply queries the server's `/api/jobs?status=done&...` endpoint.
- **Thumbnails are server-generated.** The server produces ~400px thumbnails when jobs complete. The app loads thumbnails in the gallery grid, and only fetches full-resolution images when viewing a specific result in detail.

### App-level architecture

- **MVVM with Compose.** ViewModels hold UI state and orchestrate long-running operations. Composables are stateless where possible.
- **Single Activity.** `MainActivity` (extends `FragmentActivity` for biometric support) hosts a Compose `NavHost`.
- **Single `JobRepository`.** All API calls go through one repository; ViewModels depend on it. No Hilt/Dagger — manual DI via an `Application` class is sufficient for a single-user app.
- **No local database.** The server holds all job data. Transient UI state uses `rememberSaveable`; long-running state lives in ViewModels.

---

## Tech stack

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Language | Kotlin | Standard for modern Android |
| UI | Jetpack Compose | Declarative, foldable-friendly, modern |
| Min SDK | 30 (Android 11) | Covers Z Fold 7; unlocks modern APIs |
| Compile / Target SDK | 36 (Android 16) | Latest stable; matches installed SDK platform |
| Build tools | 36.0.0 | Installed via sdkmanager |
| Navigation | Navigation-Compose | Simple for a handful of screens |
| Networking | Retrofit + OkHttp | Standard, reliable |
| Serialization | kotlinx.serialization | First-party Kotlin, clean syntax |
| Image loading | Coil | Compose-native, great caching |
| Camera | CameraX | Lifecycle-aware, handles foldables |
| Gallery pick | ActivityResult Photo Picker | Modern, no permissions needed |
| Secure storage | EncryptedSharedPreferences | For token (v2); not strictly needed for v1 |
| Biometrics | AndroidX Biometric | Well-designed, handles all edge cases |
| Adaptive layouts | Material 3 Adaptive | `ListDetailPaneScaffold` for foldables |
| Build system | Gradle KTS | Standard |

### Dependency block (reference)

```kotlin
// app/build.gradle.kts
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity / Navigation
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Adaptive layouts (foldables)
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")

    // Window info (fold state)
    implementation("androidx.window:window:1.3.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Camera
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Biometric
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Secure prefs (optional for v1)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

Version numbers above are a snapshot — bump to latest stable when starting.

---

## Security model

### Threat model

- Single user, personal device. Primary threat: lost or stolen phone before it re-locks.
- Server is behind Tailscale; not exposed to the public internet.
- Bearer token is a shared secret between app and server.

### Layers

1. **Tailscale VPN.** Server binds only to its tailnet IP (e.g., `100.x.x.x`), not `0.0.0.0`. App reaches server via `https://your-server.your-tailnet.ts.net`. Tailscale provides WireGuard-based E2E encryption between devices.
2. **TLS via `tailscale cert`.** Real TLS certificate issued by Tailscale for the tailnet hostname. No self-signed cert handling on Android.
3. **Bearer token auth.** Every request carries `Authorization: Bearer <token>`. Server rejects requests without it.
4. **Biometric app gate.** Fingerprint (or device PIN fallback) required to unlock the app on launch and after 60 seconds of backgrounding.

### Token and URL handling

- **v1:** Store server URL and bearer token in `local.properties` (gitignored), exposed via `BuildConfig`. This keeps secrets out of source control but bakes them into the APK. Acceptable because the APK is only installed on the developer's own device.
- **Later:** Move to a first-run setup screen that accepts URL + token and persists them in `EncryptedSharedPreferences`. Not required for v1.

```properties
# local.properties (never commit — in .gitignore)
server.url=https://your-server.tailnet.ts.net
api.token=your-long-random-token-here
```

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        val props = Properties().apply {
            rootProject.file("local.properties").inputStream().use { load(it) }
        }
        buildConfigField("String", "SERVER_URL", "\"${props["server.url"]}\"")
        buildConfigField("String", "API_TOKEN", "\"${props["api.token"]}\"")
    }
    buildFeatures { buildConfig = true }
}
```

### What NOT to do

- Do not commit `local.properties`.
- Do not log the bearer token. Configure OkHttp's logging interceptor to strip the `Authorization` header in release builds, or disable it entirely.
- Do not embed the token in `strings.xml`, `BuildConfig.DEBUG` branches, or anywhere else that gets shipped in the APK except the single controlled spot above.

---

## Feature set

### v1 (must-have, build first)

- [ ] Biometric lock on app launch (with device PIN fallback)
- [ ] Pick image from gallery (Photo Picker API)
- [ ] Take photo (CameraX)
- [ ] Select from predefined prompts (fetched from server's `/api/prompts`)
- [ ] Submit image + prompt to server
- [ ] Poll for result with progress UI
- [ ] Display result (before/after swipeable pager)
- [ ] Save result to phone gallery (MediaStore)
- [ ] Browse past generations (gallery grid)
- [ ] View job detail (input + output + metadata)
- [ ] Delete a past generation (with confirmation)
- [ ] Full adaptive layout for folded and unfolded states

### v2 (polish, after v1 is in daily use)

- [ ] WorkManager-based polling (survives app backgrounding)
- [ ] Share sheet integration (receive images from other apps)
- [ ] Re-run a past generation with a different prompt
- [ ] Custom free-text prompt alongside predefined ones
- [ ] Multi-select delete in gallery
- [ ] Filter gallery by prompt
- [ ] Connection status banner when Tailscale is down
- [ ] Haptic feedback on key actions

### v3 (nice-to-have)

- [ ] Half-folded (tabletop/book) layout optimization
- [ ] Retry failed jobs
- [ ] Cancel running jobs (DELETE /api/jobs/{id})
- [ ] Local caching of thumbnails for offline gallery browsing

### Deliberately excluded

- Settings screen (v1 hardcodes URL/token in BuildConfig)
- Notifications for completed jobs (not needed — app will be in foreground when waiting)
- Multiple server profiles
- Batch processing
- Analytics, crash reporting (personal app)

---

## UI design

### Design philosophy

- **Flat and minimal.** No gradients, no shadows. Solid fills, clean borders.
- **Single accent color.** Blue (`#185FA5`) for all primary actions, selection states, and info indicators. Do not use Material 3 dynamic color (wallpaper-based) — it makes the app feel unstable across days.
- **Sentence case everywhere.** "Take photo", not "Take Photo" or "TAKE PHOTO".
- **Two weights only.** 400 regular, 500 medium. No 700 bold.
- **Generous whitespace.** Padding of 16–24dp between sections. The app should feel uncrowded.
- **Material 3 with custom theming.** Use `MaterialTheme` but override `colorScheme` with fixed light/dark palettes.

### Color palette

```kotlin
// Light theme
val primaryBlue = Color(0xFF185FA5)
val backgroundPrimary = Color.White
val backgroundSecondary = Color(0xFFF5F5F4)  // surfaces
val backgroundTertiary = Color(0xFFFAF9F6)   // page bg
val backgroundInfo = Color(0xFFE6F1FB)       // selected states
val textPrimary = Color(0xFF1A1A1A)
val textSecondary = Color(0xFF6B6B6B)
val textTertiary = Color(0xFF9A9A9A)
val textInfo = Color(0xFF185FA5)
val borderTertiary = Color(0x1A000000)       // 0.15 alpha
val borderSecondary = Color(0x33000000)      // 0.3 alpha
val textSuccess = Color(0xFF1D9E75)
val textDanger = Color(0xFFA32D2D)

// Dark theme: mirror with inverted lightness
```

### Typography

- Headings: 17–20sp, weight 500
- Body: 14–15sp, weight 400
- Secondary text: 12–13sp, weight 400, `textSecondary` color
- Labels on colored backgrounds: 12–13sp, weight 500
- Monospace (job IDs, technical info): `FontFamily.Monospace`

### Shape language

- Cards and buttons: 12dp radius
- Chips: fully rounded (50% of height, i.e., `CircleShape`)
- Selected source cards: 2dp border (only exception to 0.5–1dp borders elsewhere)

### Haptic and motion

- Haptic on generate submission, job completion, delete confirmation
- No fancy animations for v1 — Material 3 defaults only
- Fold/unfold transitions handled automatically by `ListDetailPaneScaffold`

---

## Foldable support (Z Fold 7)

The Z Fold 7 has three physical states the app must handle:

| State | Display | Width class | Strategy |
|-------|---------|-------------|----------|
| Folded | ~6.5" narrow portrait (cover) | Compact | Single-column layout |
| Unfolded | ~8" near-square (inner) | Medium / Expanded | Two-pane layout |
| Half-folded | Partially open (tabletop/book) | Varies + `FoldingFeature` | v3 polish only |

### Core principle

**Pick layout by window width, not by device type.** Never check "is this a Z Fold" — check `WindowSizeClass.widthSizeClass`. This also means the same code works on tablets, Dex mode, and split-screen multi-window.

### MainActivity setup

```kotlin
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            FluxEditTheme {
                AppNavHost(windowSizeClass = windowSizeClass)
            }
        }
    }
}
```

### Manifest config-change handling

Add to the activity in `AndroidManifest.xml` so folds don't trigger full Activity recreation:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:resizeableActivity="true"
    android:configChanges="screenSize|screenLayout|smallestScreenSize|orientation|keyboardHidden|density" >
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

`resizeableActivity="true"` enables seamless fold/unfold continuity. `configChanges` prevents Activity recreation — Compose responds to new constraints automatically.

### State preservation

All long-running work (upload, polling) must live in a ViewModel's `viewModelScope`, never in a Composable-scoped coroutine. ViewModels survive configuration changes; Composable scopes do not.

```kotlin
class JobViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<JobState>(JobState.Idle)
    val uiState = _uiState.asStateFlow()

    fun submit(image: Uri, promptId: String) {
        viewModelScope.launch {
            _uiState.value = JobState.Uploading
            // ... full lifecycle survives fold
        }
    }
}
```

UI state that must survive process death (e.g., which prompt is selected) uses `rememberSaveable`.

### Gallery → Detail with ListDetailPaneScaffold

The highest-impact foldable feature. On a folded phone it shows one pane at a time; on the unfolded display it shows gallery and detail side-by-side.

```kotlin
@Composable
fun GalleryScaffold(viewModel: GalleryViewModel) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    BackHandler(navigator.canNavigateBack()) { navigator.navigateBack() }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                GalleryGrid(
                    jobs = viewModel.jobs.collectAsState().value,
                    onJobClick = { jobId ->
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, jobId)
                    }
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val jobId = navigator.currentDestination?.content
                if (jobId != null) {
                    JobDetailPane(jobId)
                } else {
                    EmptyDetailPlaceholder()
                }
            }
        }
    )
}
```

### Adaptive grid columns

Use `GridCells.Adaptive` instead of `GridCells.Fixed` for the gallery:

```kotlin
LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 110.dp),
    contentPadding = PaddingValues(14.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) { ... }
```

This picks column count automatically: ~3 on folded, ~5 on unfolded, gracefully across everything in between.

### Home screen adaptive layout

Folded: vertical stack (source buttons → preview → prompt chips → generate).
Unfolded: two-pane row (left: source + preview; right: prompts + generate).

```kotlin
@Composable
fun HomeScreen(windowSizeClass: WindowSizeClass, ...) {
    val useWideLayout = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

    if (useWideLayout) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                SourceButtons(...)
                ImagePreview(...)
            }
            Column(Modifier.weight(1f)) {
                PromptChips(...)
                Spacer(Modifier.weight(1f))
                GenerateButton(...)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            SourceButtons(...)
            ImagePreview(...)
            PromptChips(...)
            Spacer(Modifier.weight(1f))
            GenerateButton(...)
        }
    }
}
```

### Camera on foldables

CameraX handles fold-camera switching automatically when you use `CameraSelector.DEFAULT_BACK_CAMERA`. Aspect ratio matters:

```kotlin
val preview = Preview.Builder()
    .setResolutionSelector(
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    )
    .build()
```

Never hardcode 9:16 — the inner display is near-square, and 9:16 results in a narrow strip with letterboxing.

### Safe drawing insets

Use `WindowInsets.safeDrawing` to avoid drawing under the punch-hole camera on the inner display:

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing
) { ... }
```

### Testing fold behavior

Use Android Studio's foldable emulators (Phone → "7.6 Fold-in with outer display" etc.). Key scenarios to test manually on the actual device:

1. Launch folded → unfold → verify no state loss and layout adapts
2. Start upload folded → unfold mid-upload → poll continues, UI adapts
3. Gallery scroll position preserved across fold/unfold
4. Biometric prompt works on both displays (side-button fingerprint sensor)
5. Rotation while unfolded (inner display rotates, don't assume fixed orientation)

---

## Screen-by-screen specification

### 1. Lock screen (biometric gate)

**Purpose:** Prevent unauthorized access when the phone is unlocked but the app is backgrounded briefly.

**Layout:**
- Centered app icon + "FluxEdit" name + "Locked" subtitle
- System biometric prompt overlays the screen (rendered by Android, not by us)
- Fallback: "Use device PIN instead" via `DEVICE_CREDENTIAL` authenticator

**Behavior:**
- Shown on cold start
- Shown on resume if more than 60 seconds have elapsed since last successful auth
- On success: navigate to Home
- On error: `finish()` (kills the app; user must reopen)
- If device has no biometric hardware enrolled: skip directly to Home (with log)

**Adaptive:** No layout adaptation needed — system prompt is the same on both displays.

### 2. Home screen

**Purpose:** Pick image source, pick prompt, submit generation.

**Folded layout (vertical stack):**
1. Top bar: "FluxEdit" title (left); gallery icon + settings icon (right)
2. "Image source" label
3. Two equal-weight cards side-by-side: "Take photo" / "From gallery"
4. Image preview card (180dp height; empty state when no image selected)
5. "Prompt" label
6. Flowing row of chips for predefined prompts (selected = filled blue, unselected = outlined)
7. Spacer
8. "Generate" button (primary, full-width; disabled until image + prompt selected)
9. Connection indicator row: green dot + "Connected to tailnet"

**Unfolded layout (two-pane row):**
- Left pane (weight 1): source cards + image preview (larger)
- Right pane (weight 1): prompt chips + generate button + connection indicator

**States:**
- Empty (no image, no prompt) → generate disabled, placeholder preview icon
- Image selected, no prompt → generate disabled, image shows with filename chip overlay
- Image + prompt selected → generate enabled (filled blue)
- Selected source card: filled blue bg, blue text, 2dp blue border
- Selected prompt chip: filled blue bg, blue text

**Navigation out:**
- "Take photo" → CameraScreen
- "From gallery" → system Photo Picker
- Gallery icon in top bar → GalleryScreen
- Settings icon in top bar → (v2; noop for v1 or shows a debug info dialog)
- Generate → ProgressScreen with the new job ID

### 3. Camera screen

**Purpose:** Capture a photo with CameraX.

**Layout (full-screen dark):**
- Top bar on dark background: close (X) on left, "Take photo" centered, forward arrow on right (to gallery picker as alternative)
- Viewfinder fills middle area with rounded corners; corner brackets overlay as framing affordance
- Bottom bar:
  - Left: shortcut to gallery picker (image icon in rounded square)
  - Center: shutter button (white circle in gray ring)
  - Right: flip-camera button (rotation icon in circle)

**Behavior:**
- On capture: write JPEG to cache dir, navigate back to Home with the URI preselected
- Use `CameraSelector.DEFAULT_BACK_CAMERA` (flip switches to front)
- Handle camera permission via `rememberLauncherForActivityResult(RequestPermission)`; show a friendly message if denied

**Adaptive:** Viewfinder respects aspect ratio on both displays. Consider increasing viewfinder size on unfolded, but layout otherwise identical.

### 4. Progress screen

**Purpose:** Show job status while the server generates. Poll until terminal state.

**Layout:**
- Top bar: back arrow, "Generating" title
- Dimmed preview of the input image at top (keeps user oriented)
- Circular progress indicator (indeterminate; switch to determinate if server reports step progress)
- Status text: "Running FLUX2"
- Secondary text: "Step 12 of 25 · about 18s remaining" (only if server reports it)
- Job metadata card: Job ID (monospace), Prompt label, Status chip
- Cancel button at bottom (outlined, not filled)

**Behavior:**
- Poll `GET /api/jobs/{id}` every 3 seconds from the ViewModel
- On status=done → navigate to ResultScreen
- On status=failed → show error state with retry option
- Cancel: call `DELETE /api/jobs/{id}` if v2; for v1, just navigate back (job completes server-side anyway)
- Back button: stops polling in UI, but job continues server-side; user can find it in Gallery

**Adaptive:** Content centered with `Modifier.widthIn(max = 500.dp)` — don't stretch across the inner display.

### 5. Result screen

**Purpose:** Show the generated image; allow save, share, re-run.

**Layout:**
- Top bar: back arrow; prompt label + "Just now · 24 seconds" subtitle; overflow menu (⋮)
- Main image area: `HorizontalPager` with 2 pages (input on page 0, output on page 1)
  - Current page indicated by pill-shaped pager dot at bottom
  - "Swipe to compare" hint text below dots
  - Small "After" / "Before" chip overlay on each page (top-left)
- Primary actions row (2 columns): "Save" (filled blue), "Share" (outlined)
- Secondary action: "Try another prompt on this image" (outlined with refresh icon)

**Overflow menu items:**
- View details (opens JobDetailScreen)
- Copy job ID
- Delete (with confirmation)

**Behavior:**
- On load, fetch full-resolution result via Coil (authenticated request)
- "Save" writes to `MediaStore.Images` (no permission needed on API 29+)
- "Share" uses `ACTION_SEND` with a content URI from FileProvider
- "Try another prompt" navigates to Home with the input URI pre-filled, preserving the input for another generation

**Adaptive:** On unfolded, show input and output side-by-side in a Row instead of pager (user can see both simultaneously). Actions remain in bottom bar.

### 6. Gallery screen

**Purpose:** Browse past generations.

**Layout:**
- Top bar: back arrow, "Gallery" title, filter icon, search icon
- Section headers: "Today", "Yesterday", "Earlier this week", "Earlier" (grouped client-side from timestamps)
- Adaptive grid of thumbnails (3 cols folded, 5–6 cols unfolded)
- Each thumbnail: square, 8dp radius, tapping opens JobDetailScreen
- Pull-to-refresh at top

**Behavior:**
- Initial fetch: `GET /api/jobs?status=done&limit=30` on screen enter
- Infinite scroll: when user scrolls within 5 items of end, fetch next 30 with `before=<earliest_timestamp>`
- Filter icon: dropdown/sheet filtering by prompt_id (v2)
- Search icon: text input filtering (v2)

**Adaptive:** With `ListDetailPaneScaffold`, the gallery is the list pane. On unfolded, selecting a thumbnail opens the detail pane alongside.

### 7. Job detail screen

**Purpose:** Review a past generation; re-run, save, or delete.

**Layout:**
- Top bar: back arrow; prompt label + "3 days ago" subtitle; overflow (⋮)
- Two thumbnails side-by-side: "Input" / "Output" (each ~150dp tall, tap for full-screen)
- Metadata card (white with 0.5dp border):
  - Prompt ID (monospace)
  - Job ID (monospace, blue to signal copyable)
  - Duration ("24s")
  - Created ("Apr 19, 2026 · 14:32")
  - Size ("1024 × 1024")
- Primary actions: "Save output" (filled blue) + "Re-run" (outlined)
- Destructive action: "Delete" (red outline, triggers confirmation dialog)

**Behavior:**
- Fetch job detail on load (`GET /api/jobs/{id}`)
- Tap thumbnail: open full-screen zoomable viewer (pinch-to-zoom)
- Re-run: navigate to Home with the input image pre-filled (allows picking a new prompt)
- Delete: show confirmation dialog; on confirm, `DELETE /api/jobs/{id}` then pop back to Gallery

**Adaptive:** When running as the detail pane of `ListDetailPaneScaffold`, the back arrow in the top bar dismisses the detail pane (if in single-pane mode) rather than closing the Gallery. Handled automatically by `rememberListDetailPaneScaffoldNavigator`.

---

## Networking and API contract

### Base configuration

All requests:
- Base URL: `BuildConfig.SERVER_URL`
- Header: `Authorization: Bearer ${BuildConfig.API_TOKEN}`
- Timeouts: 30s connect, 60s read (image uploads can be slow on mobile)

### Authenticated OkHttp / Coil setup

Configure a single app-wide `OkHttpClient` with the auth interceptor and use it for both Retrofit and Coil (so `AsyncImage` can load authenticated URLs):

```kotlin
class FluxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer ${BuildConfig.API_TOKEN}")
                        .build()
                )
            }
            .build()

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .build()
        Coil.setImageLoader(imageLoader)

        ApiClient.init(okHttp)
    }
}
```

Register in manifest: `<application android:name=".FluxApp" ... />`.

### API endpoints (expected from server)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/health` | Connectivity check |
| GET | `/api/prompts` | List predefined prompts |
| POST | `/api/jobs` | Submit new job (multipart: image + prompt_id) |
| GET | `/api/jobs/{id}` | Get job status |
| GET | `/api/jobs/{id}/thumb` | Result thumbnail (~400px) |
| GET | `/api/jobs/{id}/input` | Original input image |
| GET | `/api/jobs/{id}/result` | Full-resolution result |
| GET | `/api/jobs?status=done&limit=30&before=<ts>` | Paginated job history |
| DELETE | `/api/jobs/{id}` | Delete job and associated files |

### Retrofit interface

```kotlin
interface FluxApi {
    @GET("api/health")
    suspend fun health(): HealthResponse

    @GET("api/prompts")
    suspend fun listPrompts(): List<PromptDto>

    @Multipart
    @POST("api/jobs")
    suspend fun submitJob(
        @Part image: MultipartBody.Part,
        @Part("prompt_id") promptId: RequestBody
    ): JobCreatedResponse

    @GET("api/jobs/{id}")
    suspend fun getJob(@Path("id") id: String): JobStatusDto

    @GET("api/jobs")
    suspend fun listJobs(
        @Query("status") status: String = "done",
        @Query("limit") limit: Int = 30,
        @Query("before") before: Long? = null
    ): List<JobSummaryDto>

    @DELETE("api/jobs/{id}")
    suspend fun deleteJob(@Path("id") id: String)
}
```

### DTOs

```kotlin
@Serializable data class HealthResponse(val status: String)

@Serializable data class PromptDto(
    val id: String,
    val label: String,
    val description: String? = null
)

@Serializable data class JobCreatedResponse(val job_id: String)

@Serializable data class JobStatusDto(
    val id: String,
    val status: String,          // queued | running | done | failed
    val prompt_id: String,
    val prompt_label: String,
    val progress: Float? = null, // 0.0–1.0 when server reports it
    val error: String? = null,
    val created_at: Long,
    val completed_at: Long? = null
)

@Serializable data class JobSummaryDto(
    val id: String,
    val prompt_id: String,
    val prompt_label: String,
    val created_at: Long,
    val duration_seconds: Int? = null
)
```

### Image preprocessing before upload

Camera output can be 12+ MP (~5 MB). Downscale client-side before upload to save bandwidth and preserve server capacity:

- Max dimension: 2048 px
- JPEG quality: 90
- Handle EXIF rotation (read orientation, rotate bitmap, strip EXIF)

Utility function in `util/ImageUtils.kt`:

```kotlin
fun prepareForUpload(context: Context, uri: Uri, maxDim: Int = 2048): File {
    // 1. Decode bounds to get dimensions
    // 2. Calculate inSampleSize for initial downsample
    // 3. Decode bitmap
    // 4. Apply EXIF rotation
    // 5. If still > maxDim, scale further
    // 6. Write to cache as JPEG quality 90
    // 7. Return File
}
```

### Error handling

All errors surfaced as sealed `UiState`:

```kotlin
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>()
}
```

Classify errors at the repository level:
- `IOException`: "Cannot reach server. Is Tailscale connected?"
- `HttpException` 401: "Authentication failed. Check the bearer token."
- `HttpException` 5xx: "Server error. Try again."
- Other: generic message + `Log.e` for debugging

### Polling strategy (v1)

In `JobViewModel`:

```kotlin
fun startPolling(jobId: String) {
    viewModelScope.launch {
        while (isActive) {
            try {
                val job = repo.getJob(jobId)
                _state.value = mapToUiState(job)
                if (job.status == "done" || job.status == "failed") break
            } catch (e: Exception) {
                // Log but keep polling — transient network errors are common
            }
            delay(3000)
        }
    }
}
```

For v2, replace with WorkManager to survive app backgrounding.

---

## Biometric authentication

### Implementation level

v1 uses **UI-level gating** (not cryptographic binding). The fingerprint prompt is a convenience lock: successful auth flips a flag; failure finishes the app. This is sufficient for single-user, tailnet-only security.

Cryptographic binding (`setUserAuthenticationRequired(true, 30)` on a `MasterKey` to decrypt the bearer token) can be added in v2 if desired, but is overkill given the Tailscale + bearer token defense in depth.

### BiometricGate helper

```kotlin
sealed class BiometricResult {
    object Success : BiometricResult()
    data class Error(val message: String) : BiometricResult()
    object Unavailable : BiometricResult()
}

fun FragmentActivity.promptBiometric(
    onResult: (BiometricResult) -> Unit
) {
    val manager = BiometricManager.from(this)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(BiometricResult.Unavailable)
        return
    }

    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onResult(BiometricResult.Success)
            override fun onAuthenticationError(code: Int, msg: CharSequence) =
                onResult(BiometricResult.Error(msg.toString()))
        }
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock FluxEdit")
        .setSubtitle("Authenticate to continue")
        .setAllowedAuthenticators(authenticators)
        .build()

    prompt.authenticate(info)
}
```

### Re-lock on background (60s grace)

A single `AuthStateHolder` tracks last-auth timestamp:

```kotlin
class AuthStateHolder {
    private var lastAuthTime = 0L
    private val gracePeriodMs = 60_000L

    var authed by mutableStateOf(false)
        private set

    fun markAuthed() {
        authed = true
        lastAuthTime = System.currentTimeMillis()
    }

    fun checkLock() {
        if (System.currentTimeMillis() - lastAuthTime > gracePeriodMs) {
            authed = false
        }
    }
}
```

Hook into `Lifecycle.Event.ON_RESUME` in `MainActivity`. When `authed` flips to false, the nav host shows the lock screen and re-triggers the prompt.

### Important requirements

- `MainActivity` must extend `FragmentActivity`, not `ComponentActivity` (BiometricPrompt requires it).
- Add permission to manifest: `<uses-permission android:name="android.permission.USE_BIOMETRIC" />`.
- Support the side-button fingerprint sensor on Z Fold 7 — works for both folded and unfolded states automatically; no special handling.

---

## Project structure

```
app/src/main/java/dev/zun/flux/
├── FluxApp.kt                      # Application: Coil + ApiClient init
├── MainActivity.kt                 # FragmentActivity + Compose host + biometric gate
├── ui/
│   ├── nav/
│   │   ├── Screens.kt              # sealed class of routes
│   │   └── AppNavHost.kt
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── auth/
│   │   ├── BiometricGate.kt        # promptBiometric extension fn
│   │   ├── AuthStateHolder.kt
│   │   └── LockScreen.kt
│   ├── home/
│   │   ├── HomeScreen.kt           # adaptive (folded/unfolded)
│   │   ├── HomeViewModel.kt
│   │   ├── components/
│   │   │   ├── SourceButtons.kt
│   │   │   ├── ImagePreviewCard.kt
│   │   │   └── PromptChipRow.kt
│   ├── capture/
│   │   ├── CameraScreen.kt
│   │   └── CameraViewModel.kt
│   ├── progress/
│   │   ├── ProgressScreen.kt
│   │   └── ProgressViewModel.kt
│   ├── result/
│   │   ├── ResultScreen.kt         # HorizontalPager folded / Row unfolded
│   │   └── ResultViewModel.kt
│   └── gallery/
│       ├── GalleryScaffold.kt      # ListDetailPaneScaffold wrapper
│       ├── GalleryGrid.kt
│       ├── GalleryViewModel.kt
│       ├── JobDetailPane.kt
│       └── JobDetailViewModel.kt
├── data/
│   ├── api/
│   │   ├── FluxApi.kt              # Retrofit interface
│   │   ├── ApiClient.kt            # Retrofit builder with OkHttp
│   │   ├── dto.kt                  # DTOs
│   │   └── ApiError.kt             # exception mapping
│   ├── model/
│   │   └── Job.kt                  # domain models
│   └── repo/
│       └── JobRepository.kt
└── util/
    ├── ImageUtils.kt               # URI → downscaled JPEG, EXIF handling
    ├── MediaStoreSaver.kt          # save to phone gallery
    ├── Time.kt                     # "3 days ago" formatting
    └── Window.kt                   # WindowSizeClass CompositionLocal

app/src/main/res/
├── drawable/                       # app icon, SVG icons if needed
├── values/
│   └── strings.xml
└── xml/
    └── file_paths.xml              # FileProvider config for Share
```

### Minimal file count philosophy

- Avoid premature abstraction. One `JobRepository` with direct API calls is fine; don't add a use case layer for a personal app.
- ViewModels per screen, not per feature.
- Components subfolder only for widgets that are reused or big enough to warrant a separate file.

---

## Build order and milestones

### Server readiness and UI-first strategy

The Rust server is **not** ready at the time this plan was last updated. Rather than block on it, the app is built **UI-first against a mock backend**, then swapped to the real API when the server lands.

**Repository boundary.** All network-facing behaviour lives behind a `JobRepository` interface. Two implementations:

- `FakeJobRepository` — returns canned prompts, simulates job progression with delays, serves bundled fixture images. Used during Milestones 2–8 development.
- `RealJobRepository` — Retrofit-backed, hits the server. Introduced once the server exposes `/api/health`, `/api/prompts`, `/api/jobs` (POST/GET/DELETE), and thumbnails. Swap in `FluxApp` via a single wiring line.

This also means ViewModels, navigation, Compose screens, camera, and biometric work can all proceed to completion before a single byte hits the tailnet.

**Server acceptance criteria (when it does come online):**
- Working auth middleware (bearer token)
- `/api/jobs` POST/GET, `/api/jobs/{id}` GET/DELETE, `/api/jobs?status=done&…` list
- `/api/prompts` enumerates predefined prompts
- ComfyUI integration producing real outputs
- Tailscale + TLS set up so the phone can reach it via `https://<host>.<tailnet>.ts.net`

Within the Android app, build in this order. Each milestone is independently testable on the device.

### Milestone 1 — Skeleton and fake connectivity

- [ ] Scaffold Gradle project: `applicationId = "dev.zun.flux"`, `minSdk = 30`, `compileSdk = 36`
- [ ] Manifest: `resizeableActivity = true`, `configChanges` for folds, `USE_BIOMETRIC` permission
- [ ] Add all dependencies from the Tech Stack section
- [ ] Set up `local.properties` → `BuildConfig` for URL and token (placeholders OK)
- [ ] `FluxApp` Application class with Coil + OkHttp auth interceptor (real OkHttp; real interceptor; no server yet)
- [ ] `JobRepository` interface + `FakeJobRepository` impl (canned `health()`, canned prompts, fake job progression)
- [ ] `FluxApi` Retrofit interface stubbed (not yet called — kept in tree so `RealJobRepository` can land later)
- [ ] Throwaway debug screen: "Ping" button → `FakeJobRepository.health()` → display `{status: "ok"}`

**Done when:** `./gradlew assembleDebug` succeeds, `adb install` lands the APK on the phone, and pressing the button shows the fake health response.

### Milestone 2 — Single end-to-end flow (no camera, no gallery screen)

- [ ] Home screen: Photo Picker button, one hardcoded prompt button, Generate button
- [ ] On submit: call `/api/jobs`, navigate to Progress screen
- [ ] Progress screen: poll every 3s, show spinner + status text
- [ ] On done: navigate to Result screen
- [ ] Result screen: display output via Coil, "Save to gallery" button writing to MediaStore

**Done when:** pick photo → submit → watch spinner → see result → save to phone. No foldable adaptation yet; works on folded state only is fine.

### Milestone 3 — Dynamic prompts and multi-prompt

- [ ] Fetch `/api/prompts` on Home screen enter
- [ ] Render as `LazyRow` of chips
- [ ] Selection state preserved across recomposition

**Done when:** editing `prompts.yaml` on server + restarting → app shows updated chips.

### Milestone 4 — Camera capture

- [ ] `CameraScreen` with CameraX preview
- [ ] Shutter button captures JPEG to cache dir
- [ ] Navigate back to Home with captured URI pre-selected
- [ ] Flip-camera button
- [ ] Gracefully handle camera permission

**Done when:** full flow works starting from an in-app camera capture.

### Milestone 5 — Gallery grid and detail

- [ ] `GalleryScreen` with `LazyVerticalGrid(GridCells.Adaptive(110.dp))`
- [ ] Paginated fetch of `/api/jobs?status=done`
- [ ] Date-grouped section headers
- [ ] Thumbnail tap → `JobDetailScreen`
- [ ] Detail shows input + output thumbnails, metadata card, Save + Re-run + Delete
- [ ] Delete triggers confirmation dialog and `DELETE /api/jobs/{id}`

**Done when:** generate 5+ jobs, browse gallery, open detail, delete one, verify it's gone.

### Milestone 6 — Foldable adaptive layout

- [ ] Pass `WindowSizeClass` from MainActivity to all screens
- [ ] Home screen: two-pane row layout on Medium+ width
- [ ] Gallery + Detail: refactor to `ListDetailPaneScaffold`
- [ ] Result screen: side-by-side input/output on Medium+ width
- [ ] Progress screen: width-capped centered content

**Done when:** launch folded → use app → unfold → every screen adapts gracefully with no state loss.

### Milestone 7 — Biometric lock

- [ ] `BiometricGate` helper
- [ ] `AuthStateHolder` with 60s grace period
- [ ] Lock screen shown on cold start and after background timeout
- [ ] Lifecycle observer re-prompts on resume

**Done when:** launch → fingerprint prompt → app unlocks. Background for >60s, foreground → re-prompts. Fail auth → app exits cleanly.

### Milestone 8 — Polish

- [ ] Haptic on generate, completion, delete
- [ ] Connection status indicator (pings `/api/health` periodically)
- [ ] Before/after pager on Result screen
- [ ] "Try another prompt" flow from Result screen
- [ ] Safe-drawing insets for inner display punch-hole camera

**Done when:** app feels like a finished product in daily use.

---

## Testing approach

### No unit tests for v1

For a single-user personal app, the ROI on unit tests is low. Prefer:
- Manual testing on the actual device
- Compose `@Preview` for UI iteration
- Real end-to-end flows against the running server

Add tests selectively later if specific logic (e.g., image preprocessing) gets complex enough to warrant them.

### Manual test checklist (run after each milestone)

**Connectivity:**
- [ ] App reaches server when Tailscale is connected
- [ ] App shows clear error when Tailscale is disabled
- [ ] App handles server 401 gracefully (bad token)
- [ ] App handles server 5xx gracefully

**Core flow:**
- [ ] Pick photo from gallery → submit → get result
- [ ] Capture photo in-app → submit → get result
- [ ] Save result to phone gallery, verify it appears in Google Photos / Samsung Gallery
- [ ] Submit a job, background the app, return — poll state is correct

**Gallery:**
- [ ] Thumbnails load
- [ ] Scroll to end triggers pagination
- [ ] Pull-to-refresh works
- [ ] Tap thumbnail → detail loads
- [ ] Delete → returns to gallery, thumbnail gone

**Foldable (critical for your device):**
- [ ] Launch folded → layouts look correct
- [ ] Unfold mid-session → layouts adapt, no state loss
- [ ] Start upload folded, unfold while polling → continues correctly
- [ ] Gallery scroll position preserved across fold
- [ ] Rotation on inner display works

**Edge cases:**
- [ ] Airplane mode during upload → sensible error
- [ ] Kill app during polling → on relaunch, can find job in gallery
- [ ] Very large image (12 MP) → downscaling works
- [ ] Intentionally failed server job (feed garbage) → error surfaces
- [ ] Rapid-fire multiple submissions → UI state consistent

**Biometric:**
- [ ] Cold start → prompt → unlock
- [ ] Background <60s → no re-prompt
- [ ] Background >60s → re-prompts on resume
- [ ] Authentication failure → app exits

### Compose Previews

Use previews heavily for UI iteration without running the full app:

```kotlin
@Preview(name = "Home - Folded", widthDp = 380, heightDp = 800)
@Preview(name = "Home - Unfolded", widthDp = 800, heightDp = 900)
@Composable
fun HomeScreenPreview() {
    FluxEditTheme {
        HomeScreen(
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(800.dp, 900.dp)),
            // ... mock state
        )
    }
}
```

Run previews on both sizes to catch layout issues early.

---

## Development environment

The dev machine is a Gentoo Linux box. The toolchain is user-owned under `$HOME` (not Portage-managed) so `sdkmanager` can self-update in place and Studio can reuse the same SDK tree.

### Installed toolchain (as of 2026-04-22)

| Component | Path | Version |
|---|---|---|
| Android Studio | `/opt/android-studio` | on PATH as `android-studio` |
| JDK (bundled with Studio) | `/opt/android-studio/jbr` | OpenJDK 21 (JBR) |
| Android SDK | `~/Android/Sdk` | ~6.3 GB total |
| cmdline-tools | `~/Android/Sdk/cmdline-tools/latest` | 20.0 (build 14742923) |
| platform-tools (adb) | `~/Android/Sdk/platform-tools` | 37.0.0 |
| emulator | `~/Android/Sdk/emulator` | installed |
| Platforms | `~/Android/Sdk/platforms/` | `android-36`, `android-36.1` |
| Build-tools | `~/Android/Sdk/build-tools/` | `36.0.0`, `36.1.0`, `37.0.0` |
| System image | `~/Android/Sdk/system-images/android-36/google_apis/x86_64` | for foldable emulators |

### Shell environment (fish)

Configured in `~/.config/fish/config.fish`:

```fish
if test -d /opt/android-studio/jbr
    set -gx JAVA_HOME /opt/android-studio/jbr
end
if test -d $HOME/Android/Sdk
    set -gx ANDROID_HOME $HOME/Android/Sdk
    set -gx ANDROID_SDK_ROOT $HOME/Android/Sdk
    fish_add_path -gP $ANDROID_HOME/cmdline-tools/latest/bin $ANDROID_HOME/platform-tools $ANDROID_HOME/emulator
end
```

### Reproducing the setup on a fresh machine

```bash
# 1. Download cmdline-tools (check developer.android.com for the current build number)
mkdir -p ~/Android/Sdk/cmdline-tools
cd /tmp
curl -fL -O https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip
unzip commandlinetools-linux-14742923_latest.zip
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# 2. Export env (adapt for bash/zsh/fish)
export JAVA_HOME=/opt/android-studio/jbr       # or any JDK 17+
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

# 3. Accept licenses (interactive)
sdkmanager --licenses

# 4. Baseline packages
sdkmanager "platform-tools" "emulator" \
    "platforms;android-36" "build-tools;36.0.0" \
    "system-images;android-36;google_apis;x86_64"
```

### Note for Claude Code sessions on Gentoo

The Bash tool spawns non-login bash shells, which do **not** source `~/.config/fish/config.fish`. When invoking `gradlew`, `sdkmanager`, or `adb` via Bash, prepend:

```bash
export JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=$HOME/Android/Sdk ANDROID_SDK_ROOT=$HOME/Android/Sdk
```

(or rely on the Gradle wrapper's `JAVA_HOME` detection — it picks up `/opt/android-studio/jbr` if `JAVA_HOME` is set).

---

## Build and deployment

### Signing

Create a personal release keystore (not a Play Store one):

```bash
keytool -genkey -v -keystore ~/.android/zun-flux-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias zun-flux
```

Reference in `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "../keys/zun-flux-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "zun-flux"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false  // simpler debugging; enable later if APK size matters
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### Build commands

```bash
# Debug build (for dev iteration; installs via Android Studio Run)
./gradlew assembleDebug

# Release APK (for sideloading)
./gradlew assembleRelease

# APK path:
# app/build/outputs/apk/release/app-release.apk

# Install over USB
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` reinstalls without uninstalling, preserving app data (important once you add persisted state).

### Phone setup

1. Enable Developer Options: Settings → About phone → tap Build number 7 times
2. Enable USB debugging: Settings → Developer options → USB debugging
3. Connect phone to dev machine via USB; accept the RSA fingerprint prompt
4. Verify: `adb devices` shows your phone

For wireless install (useful with Z Fold 7 on your desk):

```bash
adb pair <phone-ip>:<port>  # get from Settings → Developer options → Wireless debugging
adb connect <phone-ip>:<port>
```

### Install unknown apps permission

Required for sideloading once. Settings → Apps → Special access → Install unknown apps → grant to whatever app you use to transfer the APK (Files, Drive, adb, etc.).

### Version management

Not critical for personal use, but nice to have:

```kotlin
android {
    defaultConfig {
        versionCode = 1    // bump on each release
        versionName = "0.1.0"
    }
}
```

Use semantic-ish versioning so you can tell installed versions apart in Settings → Apps.

---

## Notes for Claude Code sessions

When working on this Android project in Claude Code:

### Scope your requests to milestones
Use the milestones above as natural boundaries. Don't ask "build the whole app" — ask "implement Milestone 2."

### Let Claude run Gradle
Claude Code can run `./gradlew assembleDebug` and parse errors. Let it iterate. For runtime issues, Claude can't observe the device — describe the bug in text and paste any relevant logcat output.

### Foldable testing
Claude Code can't use an emulator interactively, but can run previews via `./gradlew` tasks and inspect XML layouts. Test actual fold behavior yourself on the device.

### Files to protect
- Never let Claude edit `local.properties` — it contains real secrets
- Never let Claude commit secrets to git
- Review any changes to `AndroidManifest.xml` carefully (permissions, activity config)

### Stable decisions (do not change)
- Project umbrella name: **Project ZUN**; Android applicationId: **`dev.zun.flux`**
- Kotlin + Compose, not Java or XML views
- No Hilt/Dagger (manual DI via Application)
- No Room database (server is source of truth)
- No Play Store considerations
- Fixed color palette (no dynamic color)
- SQLite on server, not local
- `JobRepository` is an interface with `FakeJobRepository` and (later) `RealJobRepository` implementations — swapped in `FluxApp`. UI code must depend on the interface, never a concrete impl.

### Style conventions
- kotlinx.serialization, not Moshi or Gson
- Coil, not Glide or Picasso
- ViewModel state as `StateFlow`, collected as `collectAsStateWithLifecycle()`
- No explicit `when` exhaustiveness branches — use `sealed class` + `@Suppress` if needed
- 4-space indentation (Kotlin default)
