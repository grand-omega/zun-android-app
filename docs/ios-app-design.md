# FluxEdit iOS Product Design

This document describes the intended iOS experience for FluxEdit, a native iPhone client for the Project ZUN image-editing stack. It is a product and UX handoff for an iOS implementation agent.

The server API contract is [api-contract.md](api-contract.md). That file is the source of truth for endpoint paths, request/response shapes, upload semantics, authentication, and compatibility rules.

## Product Intent

FluxEdit is a private, single-user image editing app. It should feel like a focused iPhone tool for quickly choosing source images, applying a saved or custom edit prompt, watching generation progress, and keeping the best results.

The app should be iOS-first:

- Use native iOS navigation, gestures, controls, permissions, and system affordances.
- Prefer clarity and speed over dense cross-platform feature matching.
- Let the iOS implementation choose idiomatic layouts when they better fit iPhone.
- Keep the server workflow consistent with Project ZUN, but do not copy non-iOS UI patterns just because another client uses them.

Primary target:

- iPhone.
- SwiftUI native app.
- iOS 17 or newer is a reasonable baseline if the implementation uses SwiftData and Observation.

## Core User Experience

The main loop:

1. The user unlocks the app if the privacy grace window has expired.
2. The user chooses or captures one or more source images.
3. The user chooses a saved prompt or writes a custom prompt.
4. The app submits jobs to the ZUN server.
5. The user sees clear upload and generation progress.
6. Completed results can be viewed, compared, regenerated, saved, shared, reused, or deleted.

The experience should optimize for repeated use:

- The Home screen should be the working surface, not a landing page.
- Recent source images and recent prompts should be easy to reuse.
- Progress should be understandable without forcing the user to stay on one screen.
- Gallery should make it fast to browse, filter, select, save, share, and clean up generations.
- Failures should be recoverable with plain-language explanations and retry paths.

## Design Principles

- **Native first:** Use SwiftUI, system sheets, PhotosPicker, iOS menus, toolbar items, haptics, Dynamic Type, and SF Symbols where they fit.
- **Private by default:** Treat source images, prompts, generated images, URLs, and tokens as sensitive.
- **Fast to act:** The path from opening the app to submitting an edit should be short.
- **Progressive detail:** Show simple status by default; put technical fields in details views.
- **Recoverable state:** Local cache should make the app feel responsive, but server history remains authoritative.
- **Implementation freedom:** This document defines required capabilities and constraints. It should not prevent better iOS-specific interaction design.

## Suggested Native Stack

The implementation should use current stable Apple-native technologies unless there is a clear reason not to:

- SwiftUI for app UI.
- Observation for feature state.
- Swift concurrency for async work.
- URLSession for JSON, multipart upload, binary downloads, and authenticated image requests.
- SwiftData or another Apple-native persistence layer for local cache.
- Keychain for tokens and sensitive settings.
- LocalAuthentication for Face ID, Touch ID, or passcode unlock.
- PhotosPicker for photo selection.
- AVFoundation or a high-quality system-compatible camera implementation for capture.
- Photos framework for saving results.
- Network framework or URLSession-compatible diagnostics for route checks.

These are recommendations, not rigid file-structure requirements. The iOS agent may choose a different internal architecture if it preserves the product behavior, security posture, and API contract.

## Server and Connectivity

The app connects to a private ZUN server using the API in [api-contract.md](api-contract.md).

Required server behavior:

- All requests include the bearer token.
- Setup collects at least one server URL and an API token.
- The app supports a LAN URL and a Tailscale/remote URL.
- The app offers connection modes conceptually equivalent to Auto, LAN only, and Tailscale only.
- Auto should prefer the local route when it is clearly reachable and fall back gracefully when it is not.
- Health and setup validation should distinguish invalid token, unreachable host, server down, and generic network failure where practical.

iOS-specific connectivity guidance:

- Include `NSLocalNetworkUsageDescription` if direct LAN probing or local server access can trigger local network privacy prompts.
- Avoid broad `NSAllowsArbitraryLoads`; prefer HTTPS for production/TestFlight and narrow ATS allowances for local development.
- If local-network permission is denied, Auto can fall back to the remote route; LAN-only should show an actionable permission/settings message.

## Privacy and Security

The app is private-use software, so the design should protect user content even though it is not multi-account enterprise software.

Sensitive storage:

- Store API token and private server URLs in Keychain.
- Prefer device-only Keychain accessibility for token and private URL values.
- Store the last successful unlock timestamp securely enough that the privacy grace window survives process death.
- Do not log tokens, prompts, private image paths, or generated image URLs.

Local files:

- Apply iOS file protection to private image caches and local persistence where feasible.
- Exclude recoverable image caches and temporary upload/share files from backup.
- Keep cache cleanup safe: never delete Keychain settings as part of cache cleanup.
- Prepared upload files should be deleted as soon as they are no longer needed.

App privacy:

- Use LocalAuthentication with device owner authentication for unlock.
- If device authentication is unavailable, do not make the app unusable; show a warning and allow access.
- Use a privacy overlay when the scene becomes inactive so app switcher snapshots do not expose images.

Required privacy strings:

- `NSFaceIDUsageDescription`
- `NSLocalNetworkUsageDescription` when local networking is used
- `NSCameraUsageDescription`
- `NSPhotoLibraryAddUsageDescription` for saving results

Use PhotosPicker for choosing images so the app receives access only to selected assets rather than broad photo-library read permission.

## Home Experience

Home is the primary working surface.

It should provide:

- A clear source-image area.
- Actions to pick photos, capture from camera, and reuse recent inputs.
- A prompt selector for saved prompts.
- A custom prompt path.
- A high-quality or experimental mode if the server workflows support it.
- Submit action with disabled/ready/in-flight states.
- Connection health and current route status in a compact location.
- Entry points to Gallery and Settings.

Input handling:

- Support one or multiple selected images.
- Prevent accidental duplicate selections.
- Use a reasonable batch cap; 20 images matches the current ZUN client behavior, but the iOS implementation may choose a lower default if needed for iPhone ergonomics and server load.
- Copy selected assets into private app storage before upload so PhotosPicker permission lifetimes do not break the workflow.

Prompt handling:

- List prompts from the server.
- Support writing a one-off custom prompt.
- Support saving a custom prompt with a user-facing label.
- Support deleting saved prompts.
- If a selected prompt disappears after refresh, clear that selection and ask the user to choose again.

## Job Submission

The upload semantics are defined by [api-contract.md](api-contract.md). The iOS app should preserve these user-visible properties:

- The app tries the lightweight JSON submit first.
- If the server needs the image bytes, the app performs multipart upload.
- Upload progress is visible when bytes are being sent.
- Network failures are retryable.
- Batch submission should continue after individual failures and clearly report partial success.

Image preparation should be high quality and memory-safe:

- Normalize image orientation before upload.
- Downsample large images before encoding.
- Use a common RGB color space suitable for server processing.
- Hash the exact bytes that will be uploaded.
- Avoid full-resolution in-memory decoding when ImageIO/CoreGraphics downsampling can do the job.

Current ZUN client defaults that can guide the iOS implementation:

- Max upload long edge: 2048 px.
- JPEG quality: 90.
- Default custom workflow: `flux2_klein_edit`.
- High-quality/experimental workflow: `flux2_klein_9b_kv_experimental`.

The iOS implementation can choose whether uploads are foreground-only or background-capable. For a first iPhone release, foreground-only uploads are acceptable if interrupted uploads have a clear retry path. If background uploads are implemented, use iOS background URLSession patterns correctly.

## Progress Experience

Generation progress should make the server state understandable without overloading the user.

Single job progress should show:

- Source image context when available.
- Current status.
- Percent progress when the server provides it.
- Prompt label or short prompt context.
- Cancel where server cancellation is available.
- Retry or refresh when polling fails.

Batch progress should show:

- An overview of all submitted items.
- Which jobs are running, done, failed, cancelled, or deleted.
- Easy transition from a completed item to its result.
- A way to leave and later recover completed jobs through Gallery.

Polling cadence can follow the existing server-friendly behavior of about every 5 seconds while a progress view is active. The app may also refresh on foreground and gallery pull-to-refresh.

## Result Experience

A completed result screen should help the user decide what to do next.

Core capabilities:

- Show the generated result prominently.
- Compare before/after when an original input exists.
- Regenerate from the same input with the same or edited prompt.
- Edit the prompt before regenerating.
- Save the original result to Photos.
- Share the original result.
- Delete the result from the app/server history.
- View details such as prompt, created time, duration, seed, workflow, and image dimensions when available.

iOS interaction guidance:

- A before/after slider is a good compact iPhone pattern.
- Use native share and Photos save flows.
- Request add-only Photos permission for saving.
- Put details and destructive actions behind native menus/sheets where appropriate.

## Gallery Experience

Gallery is the user's history and cleanup area.

It should support:

- Grid browsing of completed generations.
- Refreshing server history.
- Filtering by prompt or custom prompt.
- Multi-select for save, share, and delete.
- Undo for deletes when possible.
- A full-screen viewer for focused browsing.
- Reusing an original input from a prior generation.

The full-screen viewer should feel native:

- Swipe between results.
- Pinch to zoom.
- Tap to show/hide chrome.
- Use a bottom action area or toolbar for compare, use input, save, details, and delete.

Local cache should hide pending deletes immediately, then sync with the server. If server delete later reports the item is already gone, treat that as success.

## Setup and Settings

Setup should be short and confidence-building.

Setup fields:

- LAN URL.
- Tailscale/remote URL.
- API token.

Setup behavior:

- Require at least one URL and a token.
- Normalize entered URLs.
- Resolve the active route.
- Validate connection and token by calling an authenticated server endpoint.
- Show invalid-token separately from network/server problems.

Settings should include:

- Lockout duration.
- Connection mode.
- Editable LAN and remote URLs.
- Editable API token.
- Connection test/status.
- App version/build details.
- Active route and active URL diagnostics.

## Camera and Photos

Photo input should feel like a standard iPhone media workflow.

Picking:

- Use PhotosPicker for privacy-preserving image selection.
- Load selected assets into private app storage before using them in the composer.
- Handle iCloud-backed assets that may fail to download.

Camera:

- Provide an in-app camera if it materially improves speed.
- Request camera permission only when needed.
- If permission is denied, explain the issue and offer Open Settings.
- Captured files should be private, protected, and cleaned up when no longer needed.

Saving:

- Save generated results using add-only Photos authorization.
- If saving fails or permission is denied, show a clear explanation and recovery action.

## Local State

The app should keep enough local state to feel fast and resilient:

- Job summaries and details needed for Gallery and Progress.
- Pending delete queue.
- Recent input references or cached files.
- Prompt list cache if useful.

Local state should not become a second source of truth:

- Server history is authoritative.
- Pull-to-refresh and foreground refresh should repair stale cache state.
- Cache reset is acceptable if migrations fail, but Keychain settings must survive.

The implementation may use SwiftData, Core Data, SQLite, or another suitable local store. SwiftData is the recommended default for a modern iOS 17+ app.

## Visual Direction

FluxEdit should feel like a serious, compact creative utility, not a marketing app.

Visual qualities:

- Native iOS typography with Dynamic Type.
- SF Symbols for standard actions.
- Clear image-first composition.
- Compact controls and readable status.
- Light and dark mode support.
- Monospaced digits for percentages, durations, and counters.
- Destructive actions should be visually clear and confirmable where appropriate.

Suggested palette:

- Primary blue: `#185FA5`.
- Success: `#1D9E75`.
- Danger: `#A32D2D`.
- Neutral light and dark backgrounds that let images dominate.

The iOS designer/agent can adapt spacing, navigation, sheets, and toolbar placement to feel right on iPhone.

## Acceptance Scenarios

The design is complete when an iOS implementation can satisfy these scenarios:

- First launch setup succeeds with valid server details.
- Invalid token is clearly distinguished from network failure.
- Auto connection chooses a reachable local route or falls back to remote.
- The app locks after the configured privacy grace window.
- A user can pick one image, choose a prompt, submit, watch progress, and view a result.
- A user can submit multiple images and understand partial failures.
- A user can write, save, choose, and delete prompts.
- A user can regenerate a result with an edited prompt.
- A user can save and share generated images.
- A user can browse, filter, select, delete, and undo delete in Gallery.
- A user can reuse an original input from a prior generation.
- Camera capture works or fails with a clear permission recovery path.
- Local cached images and server credentials are protected according to iOS privacy expectations.
