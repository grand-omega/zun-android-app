---

description: "Task list for feature implementation"
---

# Tasks: Scratch-to-Reveal Compare Mode

**Input**: Design documents from `/specs/010-scratch-reveal-compare/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Included — Constitution Principle III requires an automated test or explicit manual
verification path. The stamp-interpolation function (research.md Decision 2) is genuinely
unit-testable; the mask/Canvas rendering is not, and is verified manually instead.

**Organization**: Two user stories. Phase 2 (Foundational) carries the mode-switch state, the
hoisted mask/brush state skeleton (research.md Decision 3), and the visible toggle control itself
— without which neither story is reachable to test at all. Phase 3 (US1, P1) is the actual
scratch-reveal mechanic. Phase 4 (US2, P2) turns out to need **no new code** — see its notes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup

No new setup required — no new dependencies; Coil3 and Compose's `graphicsLayer`/`drawWithContent`
are already in use in `CompareOverlay.kt`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The mode-switch scaffolding both stories are reached through.
**⚠️ CRITICAL**: must complete before Phase 3.

- [X] T001 In `app/src/main/java/dev/zun/flux/ui/gallery/CompareOverlay.kt`, add a
  `CompareMode` enum (`Slider`, `Scratch`) and `var mode by remember { mutableStateOf(CompareMode.Slider) }`
  inside `CompareOverlay` (FR-010: slider stays the default), alongside the existing
  `progress by remember { mutableFloatStateOf(0.5f) }` (research.md Decision 3 — same hoisting
  level). Render `BeforeAfterSlider(...)` when `mode == Slider`, and a placeholder call to the
  not-yet-created `ScratchRevealCompare(...)` when `mode == Scratch` (wired for real in T009).
- [X] T002 In `CompareOverlay.kt`, add a visible toggle control (an `IconButton` using
  `Icons.Default.CompareArrows`, already used elsewhere in this app's core icon set — no new
  dependency) near the existing close button, that flips `mode` between `Slider` and `Scratch`
  (FR-008, US2 Acceptance Scenario 1's "clear, discoverable control"). Also hoist
  `var brushRadius by remember { mutableFloatStateOf(...) }` and
  `var maskBitmap by remember { mutableStateOf<ImageBitmap?>(null) }` at this same level
  (data-model.md; `CompareOverlay` — not `ScratchRevealCompare` — owns this `remember`, per T004's
  correction below; the bitmap itself is lazily created here the first time `ScratchRevealCompare`
  reports its size, per research.md Decision 1/3). **Correction during implementation**: used
  `Icons.AutoMirrored.Filled.CompareArrows`, not `Icons.Default.CompareArrows` as written above —
  the non-AutoMirrored version is deprecated (caught by the compiler warning), and
  `PhotoViewerScreen.kt` already uses the AutoMirrored one elsewhere, so this also matches existing
  convention. `brushRadius` ended up typed `Dp` (not `Float`) to avoid unit-conversion at every
  call site; `DEFAULT_SCRATCH_BRUSH_RADIUS = 40.dp` is defined alongside `CompareMode`.

**Checkpoint**: `CompareOverlay` can switch between an (empty/placeholder) scratch mode and the
unchanged slider — ready for US1 to fill in the actual reveal mechanic.

---

## Phase 3: User Story 1 - Reveal the edit by scratching it away (Priority: P1) 🎯 MVP

**Goal**: Dragging a finger in scratch mode reveals the transformed image beneath in a soft-edged,
freehand area, adjustable in size, never re-covering, until Compare is closed.

**Independent Test**: Per spec.md — open Compare, switch to the new mode, drag a finger across the
image, confirm the transformed result is revealed in a soft-edged area following the finger's
path, not a straight line.

### Tests for User Story 1 ⚠️

> Write first; should fail until T006 lands.

- [X] T003 [P] [US1] Write `ScratchStampInterpolationTest.kt` in
  `app/src/test/java/dev/zun/flux/ui/gallery/ScratchStampInterpolationTest.kt`, covering (per
  quickstart.md's Automated checks): evenly-spaced points are returned between two distinct
  points at the given spacing; a tap (identical from/to points) returns a single point, not zero
  or an error; two points already closer together than the spacing still return at least the
  endpoint. Pure function, no Compose/Robolectric needed.

### Implementation for User Story 1

- [X] T004 [US1] Create `app/src/main/java/dev/zun/flux/ui/gallery/ScratchRevealCompare.kt` with
  the composable's shell: accepts `beforeModel`, `afterModel`, `brushRadius`, a nullable
  `maskBitmap: ImageBitmap?` owned and `remember`-ed by `CompareOverlay` (T001/T002 — **not** by
  this composable), and an `onSizeKnown: (IntSize) -> Unit` callback. On `onSizeChanged`, call
  `onSizeKnown` so `CompareOverlay` can lazily create the bitmap at that size exactly once and
  store it in its own hoisted state. `ScratchRevealCompare` MUST NOT `remember` the mask bitmap
  itself — since this composable only exists in composition while `mode == Scratch` (T001), any
  state `remember`-ed inside it would be lost every time the user switches to slider mode and
  back, silently violating FR-009 (research.md Decision 3 exists specifically to prevent this;
  see also spec.md Clarifications-adjacent Assumption on state hoisting). Draw `afterModel` as the
  base `AsyncImage` layer; render nothing on top yet if `maskBitmap` is still null (first frame,
  before size is known).
- [X] T005 [P] [US1] In `ScratchRevealCompare.kt`, add the top-level pure function
  `interpolateStampPoints(from: Offset, to: Offset, spacing: Float): List<Offset>` (research.md
  Decision 2) — this is what T003 tests against.
- [X] T006 [US1] In `ScratchRevealCompare.kt`, implement the mask-erasing drag handling:
  `pointerInput` + `detectDragGestures` (matching `BeforeAfterSlider`'s existing gesture-handling
  style). At drag-start (touch-down), capture the *current* value of `brushRadius` into a local
  `val strokeRadius` for that stroke — every stamp drawn during this stroke MUST use
  `strokeRadius`, never re-read the live `brushRadius` mid-stroke, per FR-005 ("new size...
  applying to subsequent strokes") and spec Edge Cases ("takes effect starting with the next
  touch-down, not retroactively within the same continuous stroke"). For each drag delta, call
  `interpolateStampPoints` between the previous and current position and draw a soft stamp (a
  `Paint` with `blendMode = BlendMode.DstOut` and a `RadialGradientShader`, opaque center →
  transparent edge, radius = `strokeRadius`) onto the mask bitmap's own `Canvas` at each
  interpolated point (research.md Decision 1). Also handle a plain tap (no drag) as a single
  stamp using `brushRadius` captured the same way at that touch-down, per spec Edge Cases
  (depends on T004, T005).
- [X] T007 [US1] In `ScratchRevealCompare.kt`, composite the before-image layer: an `AsyncImage`
  for `beforeModel` wrapped in `Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
  .drawWithContent { drawContent(); drawImage(maskBitmap, blendMode = BlendMode.DstIn) }`, drawn
  on top of the `afterModel` base layer from T004 (research.md Decision 1) (depends on T004).
- [X] T008 [US1] In `ScratchRevealCompare.kt`, add a brush-size control (e.g. a `Slider`) wired to
  the hoisted `brushRadius` from T002, and the instructional hint text (FR-011) reusing the exact
  `Surface`/`Text` pattern already at `CompareOverlay.kt:146-159` for the slider's "Before / After"
  pill, with new copy from a new string resource added near `compare_before_after`
  (`app/src/main/res/values/strings.xml:38`).
- [X] T009 [US1] In `CompareOverlay.kt`, replace T001's placeholder `ScratchRevealCompare(...)`
  call with the real one, passing the hoisted mask/brush state through (depends on T001, T002,
  T004, T006, T007, T008).

**Checkpoint**: Scratch mode is fully functional and independently testable — drag to reveal,
adjustable brush, no re-covering, soft edges.

---

## Phase 4: User Story 2 - Switch freely between the slider and the scratch mode (Priority: P2)

**Goal**: The two modes coexist cleanly — switching preserves each mode's own progress, and a user
who never touches scratch mode sees no change at all.

**Independent Test**: Per spec.md — open Compare (starts in slider mode), switch to scratch mode,
confirm it works, switch back to slider, confirm the slider still works exactly as before.

**No new implementation tasks.** This story's behavior is already structurally guaranteed by
Phase 2/3's design, not something to additionally build:
- The discoverable toggle control (Acceptance Scenario 1) is T002.
- Progress surviving a round-trip switch (Acceptance Scenario 2) is guaranteed by research.md
  Decision 3 — `mode`, `maskBitmap`, and `brushRadius` are all hoisted to `CompareOverlay`, which
  never leaves composition while Compare is open, so neither mode's state is ever disposed by
  switching away from it.
- The slider being completely unchanged for a user who never touches scratch mode (Acceptance
  Scenario 3) is true by construction — `BeforeAfterSlider`'s own code is untouched by this
  feature (T001 only adds a conditional around *which* composable renders, not to
  `BeforeAfterSlider` itself).

- [X] T010 [US2] Manually verify quickstart.md's step 5 (mode switching doesn't disturb the
  slider, slider stays default) — this is a verification task, not an implementation task,
  confirming the guarantee above actually holds on-device rather than only in reasoning. **Not
  completed live**: unlike feature 011's session, no emulator was running at implementation time —
  only a physical device (`RFCY61HAAYK`) was connected, and per this session's standing safety
  rule that device is never issued commands. Booting a fresh emulator was not attempted without
  asking first. This story's guarantee is otherwise fully backed by T004's explicit
  mask-ownership fix (F1 from `/speckit-analyze`) plus code-level reasoning (research.md Decision
  3) — but the on-device click-through itself is an honest gap, the same category as feature 011's
  step 1/2/4/6.

**Checkpoint**: Both stories verified — scratch mode works standalone (US1) and coexists cleanly
with the slider (US2).

---

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T011 Run `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`, and `./gradlew
  spotlessCheck`; fix anything they flag.
- [X] T012 Walk through quickstart.md's manual validation steps 1-6 on an emulator/device. **Not
  completed live**, same reason as T010 (no safe test device available this session — see T010's
  note). All 4 automated tests (`ScratchStampInterpolationTest`) plus the full
  `testDebugUnitTest`/`lintDebug`/`spotlessCheck` suite pass (T011), and the app compiles cleanly
  with these changes, but the actual drag-to-reveal visual/gesture behavior (steps 1-4, 6) has not
  been confirmed on a real screen. This is a genuine open item, not silently skipped.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: None.
- **Foundational (Phase 2)**: T001 → T002 (T002 adds the toggle button and brush-size state next
  to what T001 establishes). Blocks Phase 3.
- **User Story 1 (Phase 3)**: Depends on Phase 2. T003 (test) and T005 (the function it tests) can
  be developed together; T004 is independent of T003/T005 (different concerns, same file — must
  still be applied sequentially since they share `ScratchRevealCompare.kt`); T006 depends on
  T004+T005; T007 depends on T004; T008 depends on T002; T009 (wiring into `CompareOverlay`)
  depends on everything above.
- **User Story 2 (Phase 4)**: Depends on Phase 2 + Phase 3 (nothing to switch between until US1
  exists). T010 is verification-only.
- **Polish (Final Phase)**: Depends on Phase 4.

### Parallel Opportunities

- T003 and T005 touch the same file but represent test-then-implementation of the same function —
  write T003 first (it should fail), then T005 makes it pass; not truly parallel despite being
  logically separable.
- No cross-repo dependency — everything here is implementable and testable entirely within this
  repo.

---

## Parallel Example: Foundational

```bash
Task: "Add CompareMode + mode state to CompareOverlay.kt"       # T001
# T002 depends on T001 landing first (adds to the same state block) — not run in parallel with it.
```

---

## Implementation Strategy

### MVP First — User Story 1

1. Complete Phase 2: Foundational (T001-T002).
2. Complete Phase 3: User Story 1 (T003-T009). This alone is a demoable, valuable increment — the
   scratch mode works, even before US2's verification pass.
3. Complete Phase 4: User Story 2 (T010) — verification only, confirming what Phase 2's design
   already guarantees.
4. **STOP and VALIDATE**: T011's automated checks, then T012's manual walkthrough.

---

## Notes

- [P] tasks = different files or independently-developable pieces, no blocking dependency.
- [US1]/[US2] labels map every Phase 3/4 task to its user story.
- Commit after each task or logical group, per this repo's normal practice.
- Every file path and line-number reference above was verified against the current codebase
  before being written into this task list — `CompareOverlay.kt`'s `progress`/pill-`Surface`
  structure and `strings.xml`'s `compare_before_after` entry were both read directly, not guessed.
