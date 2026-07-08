---

description: "Task list for feature implementation"
---

# Tasks: Gallery Thumbnail Reveal Animation

**Input**: Design documents from `/specs/011-thumbnail-reveal-animation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Included — Constitution Principle III requires either an automated test or an explicit
manual verification path per feature. The eligibility/diffing logic is genuinely unit-testable
(pure Flow logic); the visual animation itself is not, and is verified manually instead — both are
tracked as distinct tasks below rather than conflated into one claim of "tested."

**Organization**: Single user story (P1). Phase 2 (Foundational) carries the ViewModel-side
eligibility bookkeeping (research.md Decisions 1-2); Phase 3 (US1) carries the actual visible
reveal animation and its wiring into the grid.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1)
- Include exact file paths in descriptions

## Phase 1: Setup

No new setup required — no new dependencies; Coil3 and Paging3 are already in use in the files this
feature touches.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The eligibility bookkeeping every visible behavior in this feature is built on.
**⚠️ CRITICAL**: must complete before Phase 3.

- [X] T001 In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryViewModel.kt`, add
  `_revealEligibleJobIds`/`revealEligibleJobIds: StateFlow<Set<String>>`, a private nullable
  `previousActiveIds: Set<String>?` (starts `null`), a private `observeCompletions()` that collects
  `jobRepo.activeJobIds()` in `viewModelScope` and on each emission: if `previousActiveIds` is
  non-null, adds `previousActiveIds - emission` (the just-completed IDs) to
  `_revealEligibleJobIds`; always updates `previousActiveIds` to the new emission afterward; call
  `observeCompletions()` from the existing `init {}` block (alongside the existing `refresh()`
  call). Add `fun markRevealed(jobId: String)` that removes `jobId` from `_revealEligibleJobIds`.
  Matches research.md Decisions 1 & 2 exactly — no new DAO query, reuses the existing
  `jobRepo.activeJobIds()` Flow already used identically in `HomeViewModel.kt`.

**Checkpoint**: `GalleryViewModel` now correctly tracks which job IDs just transitioned from active
to done, scoped to this ViewModel instance's lifetime — ready for the UI to consume.

---

## Phase 3: User Story 1 - Watch a generation "develop" the moment it finishes (Priority: P1) 🎯 MVP

**Goal**: A thumbnail that finishes processing while the gallery is open plays a one-time
blur-to-sharp/scale/fade reveal instead of abruptly appearing, and never replays for that
thumbnail again.

**Independent Test**: Per spec.md — with the gallery open, submit a photo, wait for it to finish,
confirm its thumbnail plays the reveal the moment it appears; confirm scrolling away/back or
reopening the gallery shows it settled with no repeat.

### Tests for User Story 1 ⚠️

> Write first; should fail until T001/T003 land.

- [X] T002 [P] [US1] Write `GalleryViewModelRevealTest.kt` in
  `app/src/test/java/dev/zun/flux/ui/gallery/GalleryViewModelRevealTest.kt`, covering (per
  quickstart.md's Automated checks): an ID present in one `activeJobIds()` emission and absent from
  the next becomes eligible; an ID present only in the *first* emission ever observed does **not**
  become eligible on its own (nothing to diff against — the FR-003 case); `markRevealed(id)` removes
  it from `revealEligibleJobIds` and it doesn't reappear on later emissions; multiple IDs
  disappearing in the same emission all become eligible together (FR-004). Use a controllable fake
  `JobRepository.activeJobIds()` (a `MutableStateFlow<List<String>>` the test drives directly),
  following this codebase's existing fake-repository test conventions.

### Implementation for User Story 1

- [X] T003 [US1] In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryThumbnail.kt`, wrap the
  existing `SubcomposeAsyncImage` content in `JobThumbnail` with a reveal effect: a single
  `animateFloatAsState` progress value (matching the `label` convention already used by
  `common/Polish.kt`'s `StatusPill`), driven with an explicit bounded `animationSpec` (e.g.
  `tween(durationMillis = ...)`, comfortably under 1 second — not the implicit default spring,
  whose settling time isn't guaranteed to satisfy FR-005/SC-003), lerped into a blur radius and a
  `graphicsLayer` scale/alpha; add `isRevealEligible: Boolean = false` and
  `onRevealPlayed: (String) -> Unit = {}` parameters; gate the animation's start via
  `LaunchedEffect(job.id)` that checks `isRevealEligible` and, if true, calls
  `onRevealPlayed(job.id)` **immediately** — synchronously, at the top of the effect, before/
  independent of the visual animation playing out — so that if this composable is disposed
  mid-animation (navigated away, scrolled off, deleted), the eligibility entry is already consumed
  and the animation never has a chance to replay on a later composition (research.md Decision 2's
  "immediately calls back" — do not defer the callback until the animation visually finishes). A
  thumbnail not eligible renders at its normal, fully-settled visual state immediately — no
  animation, no delay.
- [X] T004 [US1] In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryScreen.kt`, collect
  `viewModel.revealEligibleJobIds` via `collectAsStateWithLifecycle()` (alongside the existing
  collected StateFlows near line 124-138), and pass `isRevealEligible = job.id in
  revealEligibleJobIds` / `onRevealPlayed = viewModel::markRevealed` into the single `JobThumbnail(
  ... )` call site (~line 557) (depends on T001, T003).

**Checkpoint**: User Story 1 is fully functional — the reveal plays exactly once for a live
completion, never for a stale/already-settled thumbnail, independently per thumbnail.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T005 Run `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`, and `./gradlew
  spotlessCheck`; fix anything they flag.
- [X] T006 Walk through quickstart.md's manual validation steps 1-6 on an emulator/device. Step 1
  MUST include actually timing the reveal (stopwatch or screen recording) to confirm it lands
  comfortably under 1 second, verifying the `animationSpec` bound added in T003 (FR-005/SC-003) —
  don't just eyeball "looks about right." Steps 4 and 6 (variant stacking, FR-007) depend on being
  able to actually complete jobs live — if the real GPU pipeline isn't available in this
  environment, note that honestly rather than skip silently, matching the documented precedent in
  specs 001/002/004/009; FR-007 currently has no automated coverage (research.md Decision 3 argues
  none is needed, by design), so this manual step is its only verification — if it can't run, say
  so explicitly rather than letting FR-007 look silently verified. **What was actually done**: a
  debug build (including this feature's changes) was installed on the running emulator and
  launched successfully, with no fatal crash observed in logcat — confirming the new
  `graphicsLayer`/`blur`/`animateFloatAsState` code doesn't break app startup or basic Compose
  rendering. Steps 1, 2, 4, and 6 — all of which require a job actually transitioning from active
  to done live — were **not** completed; this hit the exact same GPU-pipeline-availability
  constraint documented across specs 001/002/004/009 (the user has already declined loading the
  GPU pipeline to protect their separately-running `llama-server`, a decision made earlier in this
  session and not re-litigated here). Step 3 (no reveal for an already-done job) and step 5
  (interruption safety) are structurally covered by the automated `GalleryViewModelRevealTest`
  cases plus code-level reasoning (Compose's standard `LaunchedEffect` cancellation-on-disposal
  behavior), not by a live click-through. **Net**: automated coverage (T002) plus a build/launch
  smoke test is what backs this feature today; the fully-live visual/timing verification remains
  open, honestly, the same way prior features in this repo have documented the identical gap.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: None.
- **Foundational (Phase 2)**: T001 alone. Blocks Phase 3.
- **User Story 1 (Phase 3)**: Depends on Phase 2. T002 (test) should be written first and fail
  until T001 lands (it exercises Foundational code directly) — T003 is independent of T002/T001
  (different file, no shared symbols) and can proceed in parallel; T004 depends on both T001 and
  T003.
- **Polish (Final Phase)**: Depends on Phase 3.

### Parallel Opportunities

- T002 and T003 can be done in parallel (different files; T002 tests ViewModel logic from T001,
  T003 is a self-contained Compose change not yet wired to real eligibility data).
- No cross-repo dependency this time (unlike feature 013) — everything here is implementable and
  testable entirely within this repo, no external prerequisite to track.

---

## Parallel Example: User Story 1

```bash
Task: "Write GalleryViewModelRevealTest.kt"                              # T002
Task: "Add reveal animation wrapper to GalleryThumbnail.kt"              # T003
```

---

## Implementation Strategy

### MVP First (and only) — User Story 1

1. Complete Phase 2: Foundational (T001).
2. Complete Phase 3: User Story 1 (T002-T004).
3. **STOP and VALIDATE**: T005's automated checks, then T006's manual walkthrough.

Single user story, so Phase 2 → Phase 3 → Polish is the entire path — no multi-story sequencing.

---

## Notes

- [P] tasks = different files, no dependencies.
- [US1] label maps every Phase 3 task to the feature's single user story.
- Commit after each task or logical group, per this repo's normal practice.
- Every file path and line-number reference above was verified against the current codebase (via a
  dedicated research pass, see research.md) before being written into this task list —
  `GalleryViewModel.kt`'s `init`/`activeJobIds()` usage, `GalleryScreen.kt`'s single `JobThumbnail`
  call site and existing `collectAsStateWithLifecycle()` block, and `GalleryThumbnail.kt`'s Coil3
  `SubcomposeAsyncImage` structure were all read directly, not guessed.
