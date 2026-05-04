# FluxEdit App Design

This document is a rebuild handoff for the Project ZUN Android client. It describes the product behavior, architecture, data contracts, screen flows, and implementation choices needed for another coding agent to reproduce the app.

Related docs:

- [api-contract.md](api-contract.md) is the ground truth for the Project ZUN server API.
- [ios-app-design.md](ios-app-design.md) is the native iPhone app design.

## Product Summary

FluxEdit is a single-user Android image-editing client for a private Project ZUN backend. The user selects or captures one or more source images, chooses a server-side prompt or writes a custom prompt, submits generation jobs, watches progress, and manages completed results in a gallery.

Primary target hardware is a foldable Android phone, especially Samsung Galaxy Z Fold-class devices. The UI must work on normal phones, large unfolded displays, and during fold/unfold transitions without losing state.

Core qualities:

- Secure personal client: no server URL or API token is baked into the APK.
- Fast image submission: local upload preparation, SHA-256 probe, and multipart upload only when the server needs bytes.
- Responsive history: Room-backed local cache with pull refresh and soft-delete hiding.
- Foldable-friendly UI: adaptive home layout and list-detail gallery.
- Private viewing: biometric gate and `FLAG_SECURE` to block screenshots.

## Technical Stack

- Kotlin Android app, package `dev.zun.flux`.
- Jetpack Compose with Material 3.
- Compose Navigation for route-level screens.
- Material 3 adaptive list-detail scaffold for the gallery.
- Retrofit + OkHttp + kotlinx.serialization for API calls.
- Coil 3 with OkHttp fetcher for authenticated image loading.
- Room for local job cache and pending delete queue.
- WorkManager for retrying pending server deletes.
- CameraX for in-app capture.
- AndroidX Biometric for app unlock.
- AndroidX Security `EncryptedSharedPreferences` for settings and auth timestamps.
- Java 17 bytecode, minSdk 30, compileSdk/targetSdk 36.

## Project Shape

Important packages:

- `dev.zun.flux`: `MainActivity`, `FluxApp`, repository lifecycle.
- `data.api`: Retrofit interface and DTOs.
- `data.repo`: repository interface, real implementation, settings, upload, diagnostics, recent input cache.
- `data.local`: Room entities, DAO, database migrations.
- `data.net`: LAN/Tailscale route resolver.
- `data.worker`: pending delete sync worker.
- `ui.nav`: route constants and navigation graph.
- `ui.home`: main image/prompt composer.
- `ui.progress`: single and batch generation progress.
- `ui.result`: result comparison, regenerate, save/share/delete.
- `ui.gallery`: history grid, list-detail viewer, compare overlay, selection actions.
- `ui.settings`: first-run setup and settings.
- `ui.capture`: CameraX capture screen.
- `ui.auth`: biometric gate and lock screen.
- `ui.theme`: app color and type system.
- `util`: URL normalization, image preparation, sharing/saving, labels, time formatting.

## App Startup and Lifetime

`MainActivity` extends `FragmentActivity`, enables edge-to-edge UI, sets `FLAG_SECURE`, and installs Compose content.

Startup flow:

1. Get `FluxApp` from `application`.
2. Observe lifecycle `ON_RESUME`.
3. Ask `AuthStateHolder` whether the grace window expired.
4. If locked, launch biometric prompt.
5. Render `LockScreen` unless authenticated and the repository has been built.
6. Render `AppNavHost` inside `ZunFluxTheme` when unlocked.

`FluxApp` owns long-lived app infrastructure:

- `SettingsManager`
- `AuthStateHolder`
- `NetworkResolver`
- authenticated `OkHttpClient`
- Coil singleton `ImageLoader`
- current `RepositoryState`

The repository is rebuilt whenever the active base URL changes. `RepositoryState.version` increments on rebuild; screens use that version as a `viewModel` key so ViewModels do not keep a stale Retrofit/API instance.

## Security Model

Settings are stored in `EncryptedSharedPreferences` under `secure_settings`.

Persisted settings:

- LAN base URL
- Tailscale base URL
- active resolved base URL
- active route
- connection mode
- API token
- lockout duration
- last successful auth timestamp

Authentication:

- On app resume, `AuthStateHolder.checkLock()` compares `System.currentTimeMillis()` against `lastAuthTimestamp + lockoutDurationMs`.
- If expired, app shows `LockScreen`.
- `promptBiometric()` allows `BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL`.
- If biometric hardware is unavailable, the app marks authenticated to avoid blocking unsupported devices.
- Default lockout duration is 5 minutes.

The app is for single-user personal use. It relies on the backend bearer token and does not implement multi-account state.

## Network and Backend Selection

The app supports three connection modes:

- `AUTO`: probe LAN first; if reachable use LAN, otherwise fall back to Tailscale.
- `LAN_ONLY`: always use LAN URL.
- `TAILSCALE_ONLY`: always use Tailscale URL.

`NetworkResolver` performs a TCP probe to the LAN host/port with a 400 ms timeout. It writes the chosen URL and route to `SettingsManager`, then calls `FluxApp.rebuildRepository()`.

The active base URL is used by both Retrofit and Coil image URL builders. In release builds, cleartext HTTP is disallowed by `network_security_config.xml`; debug has its own permissive config.

Every API request adds:

```http
Authorization: Bearer <apiToken>
```

## API Contract

The authoritative server API documentation is [api-contract.md](api-contract.md). Treat that file as the ground truth for endpoint paths, request/response shapes, authentication, upload behavior, image endpoints, and compatibility rules.

This app design document only describes how the Android client uses that contract.

## Repository Boundary

All UI talks to `JobRepository`. Reproduce this interface; it is the main architectural boundary.

Responsibilities:

- health and connection diagnosis
- prompt list/create/delete
- submit job with upload progress
- poll and list jobs
- delete/restore/cancel jobs
- expose Room-backed flows for history and individual jobs
- expose locally hidden deleted job ids
- expose recent input ids and cached input URI helpers
- sync history and pending deletes
- build Coil-loadable models for input/thumb/preview/result

`RealJobRepository` combines:

- `FluxApi`
- Room `JobDao`
- `ConnectionDiagnoser`
- `JobUploader`
- `RecentInputCache`
- `DeleteSyncWorker`

Soft delete behavior is important:

1. Add job id to in-memory `localDeletedIds`.
2. Insert `PendingDeleteEntity`.
3. Delete job from local `jobs`.
4. Enqueue `DeleteSyncWorker`.
5. Hide matching ids from visible flows immediately.
6. Later call server `DELETE`.
7. If server returns 404, treat delete as complete.
8. Undo removes pending delete and calls server restore.

## Local Database

Room database name: `flux_database`.

Entities:

`jobs`

- `id` primary key
- `status`
- `inputId`
- `promptId`
- `promptText`
- `workflow`
- `seed`
- `progress`
- `error`
- `createdAt`
- `startedAt`
- `completedAt`
- `durationSeconds`
- `width`
- `height`

`pending_deletes`

- `jobId` primary key
- `createdAt`

Database version is 4. Migrations 1->2, 2->3, and 3->4 call an idempotent schema repair routine that creates missing tables and adds missing columns.

Visible history excludes ids in `pending_deletes`, ordered by `createdAt DESC`.

## Upload Design

Job submission must use the v2 two-step upload contract.

For each input image:

1. Prepare local image for upload via `prepareImageForUpload`.
2. Downscale large images, normalize to JPEG, and compute SHA-256.
3. Submit JSON job probe using hash and prompt.
4. If server returns success, mark upload progress as `1f` and use returned `job_id`.
5. If server returns `409` with `need_upload`, submit multipart.
6. Multipart upload wraps the file body with `ProgressRequestBody` when progress callback is provided.
7. Retry `IOException` failures up to 3 attempts with exponential backoff.
8. Delete the prepared temp file in `finally`.

Exactly one of `promptId` or `promptText` must be supplied. Custom prompt submissions require `workflow`.

Default custom workflow:

- `flux2_klein_edit`

Try-harder workflow:

- `flux2_klein_9b_kv_experimental`

## Navigation

Routes:

- `setup`
- `home`
- `camera`
- `gallery`
- `settings`
- `progress/{jobId}`
- `batch/{jobIds}`
- `result/{jobId}`

Start destination:

- `setup` when no URL/token is configured.
- `home` when configured.

Navigation behavior:

- Home submit single -> `progress/{jobId}`.
- Home submit batch -> `batch/{commaSeparatedIds}`.
- Progress done -> `result/{jobId}`, popping to Home.
- Result regenerate -> new `progress/{newJobId}`, popping to Home.
- Result new image -> Home.
- Result delete -> previous entry receives `deletedJobId`, then back.
- Camera capture -> returns `capturedUri` to previous Home saved state.
- Gallery "use input" -> puts original input URI into Home's `capturedUri`.
- Batch screen watches deleted ids and removes deleted jobs from local visible list.

## Home Screen

Home is the main composer.

Top app bar:

- Title `FluxEdit`.
- Health dot.
- Active route label and short health text.
- Gallery icon.
- Settings icon.

Health:

- Repeats while lifecycle is `STARTED`.
- Calls `/health` every 30 seconds.
- Maps 401 to Unauthorized.
- Maps IO errors through TCP diagnosis for clearer LAN/server messages.
- Pull refresh syncs history, prompts, and health.

Input selection:

- Android Photo Picker, single or multi depending on remaining batch capacity.
- Camera screen capture.
- Recent inputs from local job history, limited to 3.
- Input URIs are cached locally before submission.
- Duplicate selected URIs are ignored.
- Max batch size is enforced by `MAX_BATCH_IMAGES` in home UI.

Layout:

- Wide screens: source image hero on left, composer controls on right.
- Compact screens: source image hero above composer controls.
- Pull-to-refresh indicator is custom and centered near top.

Composer state:

- selected input URI list
- selected prompt id
- custom prompt text
- try-harder boolean

Prompt behavior:

- Server prompts come from `repository.promptsState`.
- A synthetic prompt with id `-1` is appended for `Write your own...`.
- Custom prompt can be saved as a server prompt with a label.
- Prompt management bottom sheet can delete server prompts.
- If the selected prompt is deleted after refresh, clear selection.

Submit state:

- `Idle`
- `InFlight`
- `Done(jobId)`
- `DoneBatch(submittedIds, failed)`
- `Failed(message)`

Single submit navigates to single progress. Batch submit uploads sequentially, tracks current image and upload percentage, then navigates to batch progress if at least one job was submitted.

## Progress Screens

`ProgressViewModel` observes both Room and the server.

For each job:

- Collect `repository.getJobFlow(jobId)` to update UI immediately from local cache.
- Collect `repository.deletedJobIds()` to stop polling and show Deleted.
- Poll `repository.getJob(jobId)` every 5 seconds while active.
- Stop polling when job becomes `done`, `failed`, or `cancelled`.

Single `ProgressScreen`:

- Top app bar title `Generating`.
- Shows dimmed input preview with spinner.
- Shows status chip and percentage when available.
- Shows prompt metadata while running.
- On `Done`, haptic feedback and navigate to Result.
- Failed state has Retry.
- Cancel button calls server cancel and returns back.

`BatchProgressScreen`:

- Grid overview of job tiles.
- Adaptive grid min tile size 130 dp.
- Done tiles show result preview and check badge.
- Running tiles show dimmed input and percent/spinner.
- Tapping a done tile opens Result.
- Tapping a running tile opens focused pager for that batch.
- Back from focused pager returns to grid.
- If every job is deleted, return Home.

## Result Screen

Result is for one completed job.

Data:

- Observes `repository.getJobFlow(jobId)`.
- Loads input, preview, and original result models from repository.
- Reads prompts for prompt labels and prompt editing.

Layout:

- Wide screens show two side-by-side images: Before and After.
- Compact screens show an interactive before/after slider when input exists; otherwise just After.
- Below image: prompt strip, Regenerate button, Save and Share buttons.

Actions:

- More menu: New image, View details, Delete.
- Save writes original result to Pictures/FluxEdit as `flux-{jobId}.jpg`.
- Share uses Android share sheet with original result.
- Delete soft-deletes via repository and pops back.
- Regenerate downloads the original input to private cache, resubmits with selected prompt/custom prompt and workflow, then navigates to progress for the new job.
- Prompt strip opens the same prompt library/manage bottom sheets used by Home.

Details dialog:

- Prompt label.
- Created timestamp.
- Duration when started and completed timestamps exist.

## Gallery

Gallery has an adaptive list-detail design.

On narrow layouts:

- Gallery grid and photo viewer behave like separate panes.

On wide/foldable layouts:

- `ListDetailPaneScaffold` shows grid and detail viewer as panes when space allows.

`GalleryViewModel` state:

- all Room-backed done jobs
- server prompts
- selected ids
- current tag filter
- loading/saving/sharing flags
- event message
- pending undo ids
- post-save-delete confirmation ids

Filtering:

- `All`
- `ByPromptId(promptId)`
- `Custom` for jobs with no prompt id but non-empty prompt text.
- Filter menu shows counts per option.

Grid:

- Jobs are grouped by formatted creation date.
- Pull refresh calls `repository.syncHistory()`.
- Empty state offers `Create an edit` or `Clear filter`.
- Long press enters selection mode.
- Drag-select selects or removes a range from the anchor tile.
- Selection app bar actions: save, share, delete.
- Delete shows undo snackbar.
- Save selected shows a post-save dialog asking whether to remove saved items from the app.

Photo viewer:

- Black immersive viewer.
- Horizontal pager across filtered jobs.
- Tap toggles top/bottom UI chrome.
- Double tap/gestures enter zoom mode; pager swiping is disabled while zoomed.
- Bottom action bar supports compare, use input, save, details, delete.
- Use input downloads original input to cache and navigates back to Home with that URI selected.
- Details overlay shows prompt, creation date, duration, and technical fields.
- Compare overlay shows before/after for jobs with input.

## Setup and Settings

First-run setup screen:

- LAN URL text field, default `http://`.
- Tailscale URL text field, default `http://`.
- API token password field with visibility toggle.
- Connect button normalizes URLs, requires at least one URL and token, persists settings, refreshes active route, validates by listing prompts, then navigates Home.
- HTTP 401 displays `Invalid API Token`.

Settings screen:

- Security section: lockout radio options from Always lock to 30 minutes.
- Connection section: mode radio buttons, editable LAN/Tailscale URLs, API token visibility toggle, Connect button, status text.
- App info section: version, build, package, mode, active route, active URL, LAN URL, Tailscale URL.

Connection settings should rebuild the repository after active URL changes.

## Camera

Camera screen uses CameraX.

Behavior:

- Requests `CAMERA` permission on demand.
- Shows fullscreen camera preview on black background.
- Top row has close button and title.
- Bottom row has centered shutter and right-side lens flip.
- Captures JPEG into `context.cacheDir` as `capture_{timestamp}.jpg`.
- Returns `Uri.fromFile(file)` to Home.
- Supports front/back camera switching.

## Visual Design

Theme name: `ZunFluxTheme`.

Style:

- Material 3, restrained utility UI.
- Primary blue `#185FA5`.
- Light background is warm off-white/white.
- Dark theme uses near-black neutral surfaces.
- Cards and controls generally use 8-12 dp radii.
- Technical numerals use monospaced typography via `tabular()`.

Important colors:

- Primary blue: `#185FA5`
- Success: `#1D9E75`
- Danger: `#A32D2D`
- Light surface: white
- Light surface variant: `#F5F5F4`
- Dark background: `#121212`
- Dark surface: `#1C1C1C`

Typography:

- Use Material 3 Typography with medium-weight displays/headlines.
- Body sizes are compact: 14-15 sp.
- Use display sizes sparingly for status moments such as generation percentage.

## Android Manifest and Platform Details

Permissions:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `USE_BIOMETRIC`
- `CAMERA`

Camera feature is not required.

`MainActivity`:

- exported launcher activity
- resizable
- handles screen size, layout, smallest width, orientation, keyboard hidden, and density config changes

`FileProvider`:

- authority `${applicationId}.fileprovider`
- used for sharing/saving helper flows.

## Rebuild Checklist

To reproduce the app:

1. Scaffold a Kotlin Android application with Compose, Material 3, Navigation, Retrofit, OkHttp, kotlinx.serialization, Coil, CameraX, Biometric, Security Crypto, Room, WorkManager, and adaptive layout dependencies.
2. Implement `FluxApp` as the owner of encrypted settings, auth state, route resolution, OkHttp, Coil, and repository rebuilds.
3. Implement `JobRepository` first; make every screen depend only on that interface.
4. Implement backend DTOs and Retrofit paths exactly as described above.
5. Implement Room cache with visible-job queries excluding pending deletes.
6. Implement JSON-probe-then-multipart upload with SHA-256 and progress callbacks.
7. Build the navigation graph and saved-state handoffs for camera/gallery/result deletion.
8. Build Home, Progress, BatchProgress, Result, Gallery, PhotoViewer, Setup, Settings, Camera, and Lock screens with the behavior in this document.
9. Add foldable/adaptive handling: width-size split in Home/Result and list-detail scaffold in Gallery.
10. Verify setup, single submit, batch submit, polling, cancel, regenerate, save/share, delete/undo, route switching, biometric lock, and fold/unfold behavior.

## Verification Targets

Minimum tests or manual checks:

- URL normalization and server URL handling.
- Room entity conversions between server DTOs and local cache.
- Home ViewModel prompt/custom prompt selection, batch cap, submit success/failure, and prompt deletion clearing.
- Job upload path: cached JSON submit and 409 multipart fallback.
- Progress polling transitions for done, failed, cancelled, deleted.
- Gallery filtering, selection, delete/undo, save/share.
- Setup validation with invalid token.
- NetworkResolver AUTO route preference and fallback.
