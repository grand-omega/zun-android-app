---

description: "Task list for feature implementation"
---

# Tasks: Local Composite Gallery Entries

**Input**: Design documents from `/specs/015-local-composite-gallery/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Included — Constitution Principle III requires an automated test or explicit manual
verification path. The reserved-id-prefix branching (research.md Decisions 1/3/4) is pure
conditional logic, genuinely unit-testable; the actual file write/read and full gallery-grid
rendering are not, and are verified manually instead.

**Organization**: One user story (P1) — this feature has no independently-shippable smaller slice.
Phase 2 (Foundational) is the repository/data layer (where a local composite actually gets created,
resolved, and deleted); Phase 3 (US1) is the UI wiring that makes that new capability reachable and
visible to a user.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1)
- Include exact file paths in descriptions

## Phase 1: Setup

No new setup required — no new dependency, no schema migration (research.md Decision 1).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The repository-layer capability the UI wiring in Phase 3 depends on.
**⚠️ CRITICAL**: must complete before Phase 3.

- [X] T001 In `app/src/main/java/dev/zun/flux/data/repo/JobRepository.kt`, add to the `JobRepository`
  interface: `suspend fun saveLocalComposite(bitmap: Bitmap): Result<String>` — returns the new
  job's id on success.
- [X] T002 In `app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt`, implement
  `saveLocalComposite`: generate `id = "local-composite-${UUID.randomUUID()}"`, write `bitmap` to
  `File(context.filesDir, "local_composites/$id/composite.jpg")` (create parent dirs; JPEG quality
  ~95, matching `ImageCompositor.kt`'s existing `writeToTempFile` convention — deliberately a
  *sibling* of `OfflineImageCache`'s `rootDir`, never inside it, per research.md Decision 2), build
  a `JobEntity` per data-model.md's table (`status = "done"`, `promptText` = a placeholder string
  resource, `width`/`height` from `bitmap`, `createdAt = System.currentTimeMillis()`, every other
  column left at its default/null), and `dao.insertJob(entity)`. Run on `Dispatchers.IO`, wrap in
  `runCatching` to produce the `Result`. **Wrap the file-write + `dao.insertJob` pair in
  `withContext(NonCancellable) { ... }`** — the caller (`ScratchRevealCompare`'s
  `rememberCoroutineScope()`) can be cancelled by navigation mid-save; without this, a cancellation
  landing between the write and the insert leaves a file on disk with no `JobEntity` row pointing to
  it, and since `local_composites/` is deliberately never scanned by anything (that's the point of
  Decision 2), such an orphan would never get cleaned up (depends on T001).
- [X] T003 [P] In `RealJobRepository.kt`, add a reserved-prefix branch to `thumbModel`/
  `previewModel`/`resultModel` (research.md Decision 1): if `jobId.startsWith("local-composite-")`,
  return `Uri.fromFile(File(context.filesDir, "local_composites/$jobId/composite.jpg"))` directly —
  skip `offlineImageCache.localUri(...)` and the `buildUrlOrNull(...)` fallback entirely, since
  neither ever has anything valid for this id (a server URL here would 404).
- [X] T004 [P] In `RealJobRepository.kt`, add the same reserved-prefix branch to
  `offlineAvailability`: for a local-composite id, return a fully-available result *before*
  consulting `offlineImageCache.availability(jobId)` (which would otherwise report "not cached" and
  wrongly surface `NeedsNetworkIcon` in the grid) (research.md Decision 4).
- [X] T005 In `RealJobRepository.kt`, add the same reserved-prefix branch to `deleteJob`: for a
  local-composite id, delete the Room row and the `local_composites/$jobId/` directory directly —
  skip `dao.insertPendingDelete`/`DeleteSyncWorker.enqueue` entirely, so no network call ever
  happens for this id, satisfying FR-007's literal "no server round-trip at any point" rather than
  relying on the existing graceful-404 fallback (research.md Decision 3) (depends on T002 for the
  file path convention).
- [X] T006 [P] In `app/src/test/java/dev/zun/flux/data/repo/FakeJobRepository.kt`, add a
  `saveLocalComposite` stub matching this file's existing style (e.g. records the call, returns a
  synthetic id wrapped in `Result.success`).
- [X] T007 [P] In `app/src/test/java/dev/zun/flux/data/repo/RecordingRepository.kt`, add a
  `saveLocalComposite` stub matching this file's existing recording style (depends on T001).
- [X] T008 In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryViewModel.kt`, add
  `suspend fun saveLocalComposite(bitmap: Bitmap): Result<Unit>` — a thin pass-through to
  `jobRepo.saveLocalComposite(bitmap)` mapped to `Result<Unit>` (unlike `setFavorite`'s
  fire-and-forget `viewModelScope.launch`, this must be awaitable by its caller, which manages its
  own in-progress/success/failure state — see T011) (depends on T002).

**Checkpoint**: A local composite can now be created, resolved for display, correctly reported as
always-available offline, and deleted without any network call — ready for UI wiring.

---

## Phase 3: User Story 1 - Find a saved reveal composite again, inside the app (Priority: P1) 🎯 MVP

**Goal**: Saving a drag-reveal composite creates a real, browsable, favoritable, deletable gallery
entry — visually distinguishable from AI-generated results — instead of writing to system Photos.

**Independent Test**: Per spec.md — save a composite, return to the gallery, confirm it appears
there sorted by creation time, open it, favorite it, delete it, all fully offline.

### Implementation for User Story 1

- [X] T009 [US1] In `app/src/main/java/dev/zun/flux/ui/gallery/ScratchRevealCompare.kt`, add a
  required parameter `onSaveComposite: suspend (Bitmap) -> Result<Unit>`. In `export()`'s `when
  (kind)` block: for `ExportKind.Save`, replace the current `writeToTempFile` + `saveToPictures`
  call with `onSaveComposite(composite).getOrThrow()` (letting the existing outer `catch (e:
  Exception)` handle a failure the same way it already does); for `ExportKind.Share`, move
  `writeToTempFile(context, composite)` *into* this branch (it's currently computed unconditionally
  before the `when` — after this change it's only needed for Share) and call `shareImages` exactly
  as before, completely unchanged (FR-010) (depends on T008 only for the type signature to make
  sense contextually — T009 itself has no runtime dependency on T001-T008 and can be implemented in
  parallel with them).
- [X] T010 [US1] In `app/src/main/java/dev/zun/flux/ui/gallery/CompareOverlay.kt`, thread
  `onSaveComposite: suspend (Bitmap) -> Result<Unit>` through `CompareModeSwitcher`'s signature down
  into its `ScratchRevealCompare` call (depends on T009).
- [X] T011 [US1] In `CompareOverlay.kt`, thread the same param through `CompareOverlay`'s own
  signature into its `CompareModeSwitcher` call. In `PhotoViewerScreen.kt`, supply it at the
  `CompareOverlay(...)` call site (`PhotoViewerScreen.kt:398`) as
  `onSaveComposite = { bitmap -> viewModel.saveLocalComposite(bitmap) }` (depends on T008, T010).
- [X] T012 [US1] In `app/src/main/java/dev/zun/flux/ui/result/ResultScreen.kt`, supply
  `onSaveComposite` at its `CompareModeSwitcher(...)` call site (`ResultScreen.kt:317`) as
  `onSaveComposite = { bitmap -> jobs.saveLocalComposite(bitmap) }` — `ResultScreen` already holds
  `jobs: JobRepository` directly, no `GalleryViewModel` involved here (depends on T002, T010).
- [X] T013 [P] [US1] In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryThumbnail.kt`, add a
  small visual-distinguisher badge (FR-003) — e.g. an icon in a corner, matching the existing
  favorite-heart/stack-count badge placement conventions in this file — shown when
  `job.id.startsWith("local-composite-")`, with its own content-description string.
- [X] T014 [US1] **No code needed — corrected during implementation.** The `/speckit-analyze` pass
  (finding I2) concluded "Use as input" needed a new prefix-based gate since it's "enabled purely by
  `!selectingInput`." Re-reading `ViewerActionBar` at implementation time (`PhotoViewerScreen.kt:622-634`)
  shows that's incomplete: `onUseInput`'s `ActionIcon` is nested *inside* the same `if (hasInput) { ... }`
  block as "Compare," not independently gated — `enabled = !selectingInput` is an additional toggle
  on top of that outer gate, not a replacement for it. Since `hasInput = currentJob?.input_id != null`
  (`PhotoViewerScreen.kt:200`) and a local composite's `input_id` is `null` (data-model.md), all
  three actions named in FR-009 (Compare, Use as input, View edit history) are already correctly
  excluded/disabled with zero new code — confirmed by direct inspection, not assumption.
- [X] T015 [P] [US1] In `app/src/main/res/values/strings.xml`: add the placeholder `promptText`
  string used by T002 (e.g. "Saved reveal"), the badge content-description string from T013, and
  revise `compare_scratch_saved` (currently "Reveal saved to Gallery," from feature 014, now
  misleading since it no longer means the system Gallery) to something unambiguous like "Reveal
  saved."

**Checkpoint**: User Story 1 is fully functional and independently testable — saving creates a real,
distinguishable, offline-capable gallery entry with the appropriate actions available.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T016 [P] **Adjusted during implementation.** `RealJobRepositoryTest.kt`'s existing tests only
  ever exercise pure top-level functions (`networkTypeFor`, `canPrefetchGivenNetwork`, etc.) — none
  construct a full `RealJobRepository` instance (would need a mocked `Context`/`FluxApi`/
  `OfflineImageCache`, a pattern this file deliberately doesn't use). Rather than introduce that
  heavier setup just for this feature, extracted the one genuinely pure piece —
  `internal fun localCompositeRelativePath(jobId: String): String` (`RealJobRepository.kt`, the
  file-path format `previewModel`/`resultModel`/`thumbModel`/`deleteJob` all build on) — as a
  top-level function matching this file's existing convention, and added 3 cases for it plus the
  `LOCAL_COMPOSITE_ID_PREFIX` constant. The actual instance-level branching behavior (resolves to
  the right `Uri`, `offlineAvailability` reports fully available, `deleteJob` skips the server call)
  is covered by quickstart.md's manual walkthrough instead — Constitution III accepts either an
  automated test or an explicit manual path, not both, and manufacturing a mock-heavy test just to
  check a box would be worse than being direct about the tradeoff.
- [X] T017 [P] Extend `app/src/test/java/dev/zun/flux/ui/gallery/GalleryThumbnailTest.kt` with a
  case confirming the visual distinguisher badge shows for a `local-composite-*` job and not for an
  ordinary job (depends on T013).
- [X] T018 Run `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`, and `./gradlew
  spotlessCheck`; fix anything they flag.
- [ ] T019 Walk through quickstart.md's manual validation steps 1-8, including step 6 ("Clear
  offline cache" doesn't touch a saved composite) and step 8 (no accidental stacking) specifically —
  these are the two paths a purely code-level review is least likely to catch a subtle miss in.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: None.
- **Foundational (Phase 2)**: T001 → T002 → {T003, T004, T005} → T008 roughly sequential (each
  reads/depends on the file-path convention T002 establishes); T006/T007 (test fakes) can proceed
  in parallel with T003-T005 once T001's interface signature exists. Blocks Phase 3's T011/T012
  specifically (not T009/T010, which only need the callback *shape*, not a working implementation).
- **User Story 1 (Phase 3)**: T009 → T010 → T011 sequential (same call chain); T012 can proceed in
  parallel with T010/T011 (different file, same underlying dependency on T002); T013/T014/T015 can
  all proceed in parallel with each other and with T009-T012 (different files/concerns).
- **Polish (Final Phase)**: Depends on all of Phase 2 and Phase 3.

### Parallel Opportunities

- T003/T004 (both simple branches in the same file, but independent of each other) alongside T006/
  T007 (test fakes, different files entirely).
- T012 (ResultScreen) alongside T010/T011 (CompareOverlay/PhotoViewerScreen) — different call sites
  of the same underlying `CompareModeSwitcher` change, no shared file.
- T013/T014/T015 — three different files/concerns (badge rendering, action visibility, strings),
  none blocking each other.

---

## Parallel Example: Foundational

```bash
Task: "Add reserved-prefix branch to previewModel/resultModel/thumbModel"   # T003
Task: "Add reserved-prefix branch to offlineAvailability"                    # T004
Task: "Add saveLocalComposite stub to FakeJobRepository"                     # T006
Task: "Add saveLocalComposite stub to RecordingRepository"                   # T007
```

---

## Implementation Strategy

### MVP First — User Story 1 (the only story)

1. Complete Phase 2: Foundational (T001-T008) — the actual data-layer capability, including the
   three places (image resolution, offline availability, delete) that need to recognize a local
   composite by its reserved id prefix.
2. Complete Phase 3: User Story 1 (T009-T015) — wire that capability up to feature 014's existing
   save button, and make the result visible/distinguishable/appropriately-limited in the gallery UI.
3. **STOP and VALIDATE**: T018's automated checks, then T019's manual walkthrough — don't skip the
   "Clear offline cache" and "no accidental stacking" scenarios specifically.

---

## Notes

- [P] tasks = different files or independently-developable pieces, no blocking dependency.
- [US1] labels map every Phase 3 task to the single user story.
- Commit after each task or logical group, per this repo's normal practice.
- Every file path, line reference, and existing-behavior claim above was verified against the
  current codebase via a dedicated research pass (see research.md) before being written into this
  task list — `JobEntity`/`JobDao`'s actual columns and query filters, `OfflineImageCache`'s actual
  `prune()`/`listCachedJobs()` scope, `deleteJob`'s actual optimistic-delete-then-sync behavior, and
  `PhotoViewerScreen`/`ResultScreen`'s actual constructor parameters (`viewModel: GalleryViewModel`
  vs. `jobs: JobRepository` directly — these differ, and T011/T012 are written accordingly) were all
  read directly, not guessed.
