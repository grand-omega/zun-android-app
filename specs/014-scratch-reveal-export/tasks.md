---

description: "Task list for feature implementation"
---

# Tasks: Save Drag-Reveal Results

**Input**: Design documents from `/specs/014-scratch-reveal-export/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Included — Constitution Principle III requires an automated test or explicit manual
verification path. `mapRectToSource` (research.md Decision 3) is genuinely unit-testable; the
Coil-decode/composite/file-write pipeline is not, and is verified manually instead.

**Organization**: Two user stories. Phase 2 (Foundational) carries the entire compositing pipeline
(`ImageCompositor.kt`) — neither story can save or share anything without it, so it isn't
story-specific. Phase 3 (US1, save) does the bulk of the UI wiring; Phase 4 (US2, share) turns out
to be small, since it reuses US1's pipeline end-to-end except for the final call.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup

No new setup required — Coil3 is already a dependency (`app/src/main/java/dev/zun/flux/FluxApp.kt`
already configures `SingletonImageLoader`), and `saveToPictures`/`shareImages` already exist.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The compositing pipeline both stories are built on.
**⚠️ CRITICAL**: must complete before Phase 3.

- [X] T001 Create `app/src/main/java/dev/zun/flux/util/ImageCompositor.kt` with
  `suspend fun resolveToBitmap(context: Context, model: Any?): Bitmap?` (research.md Decision 1):
  builds an `ImageRequest.Builder(context).data(model).build()`, executes it via
  `SingletonImageLoader.get(context).execute(request)` (same pattern already used at
  `PhotoViewerScreen.kt:337-338`, which only checks success there — this task additionally
  extracts the decoded `Bitmap` from a `SuccessResult`, returning `null` on any other result), run
  on `Dispatchers.IO`.
- [X] T002 [P] In `ImageCompositor.kt`, add the pure function `mapRectToSource(containerSize:
  IntSize, imageIntrinsicSize: IntSize, rectInContainer: Rect): Rect` (research.md Decision 3):
  computes `ContentScale.Fit`'s uniform scale + centered-offset letterboxing between
  `containerSize` and `imageIntrinsicSize`, and remaps `rectInContainer` into the image's own pixel
  space accordingly. No Compose/Bitmap/IO — this is what T003 tests.
- [X] T003 [P] Write `app/src/test/java/dev/zun/flux/util/ImageCompositorTest.kt` covering (per
  quickstart.md's Automated checks): same-aspect-ratio container/image (simple uniform scale, no
  letterboxing); a wider container (left/right letterbox bars); a taller container (top/bottom
  letterbox bars); the container's exact center always maps to the image's own center regardless
  of scale/letterboxing (depends on T002).
- [X] T004 In `ImageCompositor.kt`, add `fun snapshotMask(mask: ImageBitmap): ImageBitmap` — copies
  the mask's current pixels into a new, independent `ImageBitmap` (e.g. draw it into a fresh
  `Canvas`-backed bitmap). This exists specifically so the composite reflects the mask *at the
  instant save/share was triggered*, not whatever it happens to look like whenever the async
  pipeline (T001's network/decode work) finally gets around to reading it — `maskBitmap` is a live,
  mutable object the user keeps drawing into via `stampAt` (`ScratchRevealCompare.kt:106-120`)
  while dragging, and FR-004 explicitly requires dragging to keep working uninterrupted during a
  save. Without this snapshot, a user who drags while a slow save is in flight would get a
  composite reflecting a mix of pre- and mid-save strokes — breaking FR-002/SC-002's "exactly the
  state at the moment triggered." **Callers MUST invoke this synchronously, before any `suspend`
  call begins** (see T007/T010).
- [X] T005 In `ImageCompositor.kt`, add `fun compositeReveal(after: Bitmap, before: Bitmap, mask:
  ImageBitmap, containerSize: IntSize): Bitmap` (research.md Decision 4): creates the output
  canvas at `after`'s own width/height — **capped at `maxDimension = 2048` (matching
  `ImageUtils.kt`'s existing `prepareImageForUpload` convention), downscaling proportionally if
  `after` exceeds it** (research.md Decision 4's "Alternatives considered" flagged this as a
  needed safety bound for uncapped-resolution result PNGs; this task is where it actually lands).
  Draws `after` (downscaled if capped) as the base, then draws `before` and the
  **already-snapshotted** `mask` (from T004 — never the live `maskBitmap` reference) each remapped
  via `mapRectToSource(containerSize, <that image's own size>, ...)` before compositing them on top
  (same `BlendMode.DstIn` masking approach `ScratchRevealCompare.kt:164-169` already uses on
  screen, just applied to real `Bitmap`s instead of live Compose content) (depends on T001, T002,
  T004).
- [X] T006 In `ImageCompositor.kt`, add `suspend fun writeToTempFile(context: Context, bitmap:
  Bitmap): Uri` — compresses to PNG into the app's cache directory and returns `Uri.fromFile(file)`.
  This `Uri` is deliberately handed to the *existing, unmodified* `saveToPictures`/`shareImages`
  (`util/MediaStoreSaver.kt`, `util/ShareUtils.kt`) via their existing `is Uri ->` branch (both
  already `resolver.openInputStream`/`FileProvider.getUriForFile` a `file://` `Uri` correctly) —
  no changes needed to either utility (depends on T005).

**Checkpoint**: Given the two source models and the current mask, the app can now produce a real,
correctly-mapped, size-bounded, race-free flattened `Bitmap` and hand it to the existing save/share
utilities as a plain file `Uri` — ready for UI wiring.

---

## Phase 3: User Story 1 - Save the current reveal as its own image (Priority: P1) 🎯 MVP

**Goal**: From drag-to-reveal mode, save the exact on-screen composite as a new image, with clear
in-progress feedback and a failure message distinct from a generic error when the (never
disk-cached) before-image can't be resolved offline.

**Independent Test**: Per spec.md — reveal part of a photo, trigger save, confirm a new image
appears in device photo storage matching exactly that composite.

### Implementation for User Story 1

- [X] T007 [US1] In `app/src/main/java/dev/zun/flux/ui/gallery/ScratchRevealCompare.kt`, add local
  save-in-progress state (a small sealed type or boolean, matching this codebase's existing
  `PolishState`/`SubmitState` style — see data-model.md: this state does *not* need hoisting to
  `CompareOverlay`, unlike `maskBitmap`/`brushRadius`, since nothing requires an in-flight save to
  survive a mode switch).
- [X] T008 [US1] In `ScratchRevealCompare.kt`, add a save `IconButton` at `Alignment.TopEnd` (the
  one corner not already occupied — top-center has the hint pill, bottom-center the brush slider,
  and the mode-toggle/reset buttons live one level up in `CompareModeSwitcher` at
  bottom-start/bottom-end). On click, **synchronously call `snapshotMask(maskBitmap)` first, before
  launching the coroutine** (T004 — this ordering is the actual fix, not optional), then inside the
  coroutine: set the in-progress state, call `resolveToBitmap` for both `beforeModel` and
  `afterModel`, then `compositeReveal` with the snapshot (not the live `maskBitmap`), then
  `writeToTempFile`, then the *existing* `saveToPictures(context, uri, displayName)`. Use a
  **distinctly-patterned display name that is both unique per save and never collides with the
  plain-result save's existing `"flux-${job.id}.jpg"` pattern** (`PhotoViewerScreen.kt:285`) — e.g.
  `"flux-${job.id}-reveal-${System.currentTimeMillis()}.jpg"` — satisfying FR-005 (distinct files,
  never overwritten) and FR-006 (never confused with the existing save, including at the saved-file
  level, not just the in-app button) without depending on unverified MediaStore auto-dedup
  behavior. Clear the in-progress state and show a success message on completion (depends on T001,
  T004, T005, T006, T007).
- [X] T009 [US1] In the same save flow, if `resolveToBitmap` returns `null` for the before-image
  specifically, surface a failure message using this app's existing "needs network"/offline
  framing (matching `NeedsNetworkIcon`'s existing language in `GalleryThumbnail.kt`), not a
  generic failure string — per research.md Decision 2 / Constitution Principle IV. A failure for
  the after-image, or any other step, uses this app's ordinary save-failure messaging (matching
  `R.string.viewer_save_failed`'s existing pattern) (depends on T008).
- [X] T010 [P] [US1] Add new string resources near the existing `compare_scratch_*` entries in
  `app/src/main/res/values/strings.xml`: a content description for the save action, a success
  message, a generic failure message (distinct wording from the existing
  `viewer_save_failed`/`viewer_saved_to_gallery`, per FR-006's "must not be confused with the
  existing action"), and the offline-specific failure message from T009.

**Checkpoint**: User Story 1 is fully functional and independently testable — save works, shows
progress, captures the mask at the moment triggered even if the user keeps dragging during a slow
save, never collides with the plain-result save's filename, and distinguishes an offline
before-image failure from any other failure.

---

## Phase 4: User Story 2 - Share the current reveal directly (Priority: P2)

**Goal**: Share the same composite directly via the system share sheet, without saving first.

**Independent Test**: Per spec.md — reveal part of a photo, trigger share, confirm the system share
sheet opens with exactly that composite.

**Minimal new work.** This story reuses T001-T006's entire pipeline unchanged — the only new piece
is the final call and its own trigger button.

### Implementation for User Story 2

- [X] T011 [US2] In `ScratchRevealCompare.kt`, add a share `IconButton` next to the save button
  from T008 (same `TopEnd`-area row), reusing the identical
  snapshot → resolve → composite → writeToTempFile pipeline (same synchronous-snapshot-first
  ordering as T008), calling the existing `shareImages(context, listOf(uri))` instead of
  `saveToPictures` as the final step. Reuses T007's in-progress state and T009's offline-specific
  failure framing as-is (depends on T004, T005, T006, T007, T009).
- [X] T012 [P] [US2] Add the share action's content-description string resource alongside T010's.

**Checkpoint**: Both stories verified — save and share both work from the same underlying
composite, independently of each other.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T013 Run `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`, and `./gradlew
  spotlessCheck`; fix anything they flag.
- [ ] T014 Walk through quickstart.md's manual validation steps 1-6 on an emulator/device,
  including step 5's offline/uncached-before-image scenario specifically (don't skip it just
  because it's the least convenient to set up — it's the one path most likely to be silently
  wrong). Also specifically test: start a save, keep dragging while it's in flight, and confirm
  the saved image reflects the state *at the moment save was tapped*, not a later, mid-drag state
  (T004's fix).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: None.
- **Foundational (Phase 2)**: T001 → T004 → T005 → T006 sequential (each depends on the previous);
  T002 → T003 can proceed in parallel with T001/T004, since `mapRectToSource` has no dependency on
  `resolveToBitmap`/`snapshotMask`. Blocks Phase 3.
- **User Story 1 (Phase 3)**: Depends on Phase 2. T007 → T008 → T009 sequential (same file,
  building on each other); T010 (strings) can proceed in parallel with T007-T009.
- **User Story 2 (Phase 4)**: Depends on Phase 2 and T007/T009 specifically (reuses that state and
  messaging) — not on T008 itself, though in practice it'll be written right after it in the same
  file region.
- **Polish (Final Phase)**: Depends on Phase 4.

### Parallel Opportunities

- T002/T003 (the pure mapping function + its test) alongside T001/T004 (the Coil resolve +
  snapshot functions) — different concerns, no shared dependency.
- T010 and T012 (string resources) can be done alongside their respective story's code changes.
- No cross-repo dependency — everything here is implementable and testable entirely within this
  repo.

---

## Parallel Example: Foundational

```bash
Task: "Add resolveToBitmap + snapshotMask to ImageCompositor.kt"   # T001, T004
Task: "Add mapRectToSource + its test"                              # T002 + T003
```

---

## Implementation Strategy

### MVP First — User Story 1

1. Complete Phase 2: Foundational (T001-T006). This is the hard part — the actual compositing
   math, the mask-snapshot-timing fix, and the offline-handling decisions research.md worked
   through.
2. Complete Phase 3: User Story 1 (T007-T010). Save alone is already the feature's full value per
   spec.md's own priority framing.
3. Complete Phase 4: User Story 2 (T011-T012) — small, reuses everything from Phase 2/3.
4. **STOP and VALIDATE**: T013's automated checks, then T014's manual walkthrough — don't skip the
   offline scenario or the drag-during-save scenario.

---

## Notes

- [P] tasks = different files or independently-developable pieces, no blocking dependency.
- [US1]/[US2] labels map every Phase 3/4 task to its user story.
- Commit after each task or logical group, per this repo's normal practice.
- Every file path and API reference above was verified against the current codebase (via a
  dedicated research pass, see research.md) before being written into this task list —
  `ScratchRevealCompare.kt`'s current chrome layout, `MediaStoreSaver.kt`/`ShareUtils.kt`'s
  `Uri`-branch handling, the existing `"flux-${job.id}.jpg"` save-name convention
  (`PhotoViewerScreen.kt:285`), and the `SingletonImageLoader.get(context).execute(...)` precedent
  at `PhotoViewerScreen.kt:337-338` were all read directly, not guessed.
- This task list was passed through `/speckit-analyze` once; three findings (a mask-snapshot race
  condition, a saved-filename collision risk, and a missing resolution safety cap) were found and
  fixed directly in T004/T005/T008 above rather than left as follow-up work.
