# Quickstart: Validating Favorites, Generation Details, and Cellular Data Control

## Prerequisites

- A debug build on a device/emulator with at least one completed generated image already in the
  gallery (needed for all three stories).
- For Story 3's live validation: a way to toggle the device between Wi-Fi and cellular-only (or
  airplane mode + cellular), e.g. the emulator's network profile controls or a physical device.

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
./gradlew spotlessCheck
```

Per Constitution Principle III, this feature should add:
- An instrumented migration test (`AppDatabaseMigrationTest` pattern) covering v5 → v6 and
  confirming `isFavorite` defaults to `false` for pre-existing rows and can be set/read back.
- A `JobDao`/repository-level test confirming a favorite survives a simulated resync (insert a
  favorited job, run the same upsert path `syncHistory()`/`listJobs()` uses, assert
  `isFavorite` is still `true` afterward) — this is the regression test for research.md
  Decision 1's critical finding.
- A `GalleryViewModel` unit test confirming "favorites only" combines correctly with an active
  prompt filter (favorited + specific-prompt narrows to the intersection, not either alone).
- A unit test for the network-mode gating logic (uploads get `NetworkType.UNMETERED` when
  Wi-Fi-only is selected, `NetworkType.CONNECTED` otherwise; prefetch is skipped vs. attempted
  based on the same setting and a stubbed connectivity check).

## Manual validation

1. **Favorite a variant (Story 1 / FR-001-004)**
   Open a completed image in the photo viewer, tap the heart icon. Confirm it fills/highlights
   immediately. Go to Gallery — confirm the tile shows a favorite overlay. Enable "favorites
   only" — confirm only favorited images show, and this can be combined with an existing
   prompt filter. Un-favorite from the grid or viewer — confirm it disappears from the filtered
   view immediately. Delete a favorited image — confirm the exact same delete/undo flow as any
   other image (no extra confirmation).

2. **Favorite survives a background sync (Story 1 regression)**
   Favorite an image, then trigger whatever causes `syncHistory()`/`listJobs()` to run again
   (e.g. pull-to-refresh Gallery, or background job-watch completing another job). Confirm the
   favorite is still set afterward — this is the scenario research.md Decision 1 specifically
   calls out as previously-broken-if-not-handled.

3. **Generation details (Story 2 / FR-005-006)**
   Open a completed image, swipe up (or tap the info icon). Confirm the sheet shows the correct
   prompt text, workflow, whether try-harder was used, and created/completed times — cross-check
   against what was actually submitted for that job. Dismiss the sheet and confirm the viewer is
   exactly where it was (same image, same before/after slider position).

4. **Wi-Fi-only blocks cellular upload (Story 3 / FR-007-009)**
   With the setting at its default (Wi-Fi only) and the device on cellular only, submit an
   image for editing. Confirm the job stays queued/pending rather than uploading immediately.
   Reconnect to Wi-Fi — confirm the upload proceeds without resubmitting. Switch the setting to
   "allow cellular data", repeat on cellular-only — confirm it uploads immediately.

5. **Explicit actions are never blocked (Story 3 / FR-010)**
   With Wi-Fi-only active and only cellular available, use "Save to device" or "Share" on an
   already-visible image. Confirm this works immediately regardless of the network setting.
