---

description: "Task list for feature implementation"
---

# Tasks: Consolidate Duplicated UI Boilerplate

**Input**: Design documents from `/specs/007-consolidate-duplication/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: No new test tasks are included. Per FR-005 and the plan's Constitution Check
(Principle III), this is a zero-behavior-change refactor — the existing automated suite
passing unmodified *is* the verification; there is no new behavior to write a new test
for. Each story ends with a task that runs the existing suite and expects it to pass
without any test file being edited.

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent
implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Single-module Android app — all paths are under `app/src/main/java/dev/zun/flux/` (production) or `app/src/test/java/dev/zun/flux/` (tests), per plan.md's Project Structure.

---

## Phase 1: Setup

**Purpose**: Capture a baseline so the later "existing suite passes unmodified" claim is verifiable, not assumed.

- [X] T001 Run `./gradlew :app:testDebugUnitTest :app:lintDebug spotlessCheck` from repo root and record the result (all green, per the repo's current state) as the pre-refactor baseline to diff against after each story.

---

## Phase 2: Foundational

**None.** The three user stories touch fully disjoint files (`ProgressViewModel.kt`/`ProgressScreen.kt`/`BatchProgressScreen.kt` for US1; `Polish.kt` plus 7 screens for US2; `GalleryScreen.kt`/`PhotoViewerScreen.kt` for US3) and share no blocking prerequisite beyond the T001 baseline capture. All three stories can start immediately after Phase 1.

---

## Phase 3: User Story 1 - One shared way to host a job's ProgressViewModel (Priority: P1) 🎯 MVP

**Goal**: Replace the three identical `ProgressViewModel` construction blocks with calls to one shared helper.

**Independent Test**: `ProgressScreen`, `BatchTile`, and `BatchPage` all obtain their `ProgressViewModel` through `rememberProgressViewModel(...)`; the existing `ProgressViewModel`/`BatchProgressScreen`-related test suite passes unchanged.

### Implementation for User Story 1

- [X] T002 [US1] Add `@Composable fun rememberProgressViewModel(jobId: String, jobs: JobRepository): ProgressViewModel` to `app/src/main/java/dev/zun/flux/ui/progress/ProgressViewModel.kt`, returning `viewModel(key = jobId, factory = viewModelFactory { initializer { ProgressViewModel(repository = jobs) } })` (per research.md Decision 1).
- [X] T003 [P] [US1] In `app/src/main/java/dev/zun/flux/ui/progress/ProgressScreen.kt`, replace `ProgressScreen`'s own `viewModel(key = jobId, factory = viewModelFactory { initializer { ProgressViewModel(repository = jobs) } })` block with `rememberProgressViewModel(jobId, jobs)`.
- [X] T004 [US1] In `app/src/main/java/dev/zun/flux/ui/progress/BatchProgressScreen.kt`, replace `BatchTile`'s own construction block with `rememberProgressViewModel(jobId, jobs)`.
- [X] T005 [US1] In the same file, replace `BatchPage`'s own construction block with `rememberProgressViewModel(jobId, jobs)`. (Sequential with T004 — same file.)
- [X] T006 [US1] Run `./gradlew :app:testDebugUnitTest --tests "dev.zun.flux.ui.progress.*"` and confirm the full existing suite (including `ProgressViewModelTest`) passes with zero test-file edits.

**Checkpoint**: User Story 1 is complete and independently verifiable — `grep -rn "ProgressViewModel(repository" app/src/main/java/dev/zun/flux/ui/progress/` returns exactly one match (per quickstart.md SC-001 check).

---

## Phase 4: User Story 2 - One shared back-navigation icon button (Priority: P2)

**Goal**: Replace the eight near-identical back-arrow `IconButton` blocks with calls to one shared composable, preserving `PhotoViewerScreen`'s white-tint variation.

**Independent Test**: All eight sites use `BackNavigationIcon(...)`; each screen's back button still navigates correctly; `PhotoViewerScreen`'s icon is still white while the other seven are unchanged.

### Implementation for User Story 2

- [X] T007 [US2] Add `@Composable fun BackNavigationIcon(onBack: () -> Unit, contentDescription: String, tint: Color = LocalContentColor.current)` to `app/src/main/java/dev/zun/flux/ui/common/Polish.kt`, wrapping `IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = contentDescription, tint = tint) }` (per research.md Decision 2).
- [X] T008 [P] [US2] In `app/src/main/java/dev/zun/flux/ui/history/EditHistoryScreen.kt`, replace the back-arrow `IconButton` block with `BackNavigationIcon(onBack = onBack, contentDescription = stringResource(R.string.common_back))`.
- [X] T009 [US2] In `app/src/main/java/dev/zun/flux/ui/progress/BatchProgressScreen.kt`, replace `BatchGrid`'s back-arrow `IconButton` block with the shared composable.
- [X] T010 [US2] In the same file, replace `BatchFocused`'s back-arrow `IconButton` block with the shared composable. (Sequential with T009 — same file.)
- [X] T011 [P] [US2] In `app/src/main/java/dev/zun/flux/ui/progress/ProgressScreen.kt`, replace the back-arrow `IconButton` block with the shared composable.
- [X] T012 [P] [US2] In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryScreen.kt`, replace the back-arrow `IconButton` block with the shared composable.
- [X] T013 [P] [US2] In `app/src/main/java/dev/zun/flux/ui/settings/SettingsScreen.kt`, replace the back-arrow `IconButton` block with the shared composable.
- [X] T014 [P] [US2] In `app/src/main/java/dev/zun/flux/ui/result/ResultScreen.kt`, replace the back-arrow `IconButton` block with the shared composable.
- [X] T015 [P] [US2] In `app/src/main/java/dev/zun/flux/ui/gallery/PhotoViewerScreen.kt`, replace the back-arrow `IconButton` block with `BackNavigationIcon(onBack = ..., contentDescription = ..., tint = Color.White)`, explicitly preserving the white tint.
- [X] T016 [US2] Run `./gradlew :app:testDebugUnitTest :app:lintDebug` and confirm zero test-file edits; manually confirm back navigation still works on all seven touched screens and `PhotoViewerScreen`'s icon is still visibly white.

**Checkpoint**: User Story 2 is complete — `grep -rn "fun BackNavigationIcon" app/src/main/java/dev/zun/flux/ui/common/` returns exactly one match, and all 8 call sites use it (per quickstart.md SC-002 check).

---

## Phase 5: User Story 3 - One shared "undo deleted item" snackbar (Priority: P3)

**Goal**: Replace the two "undo deleted item" snackbar blocks with calls to one shared function, preserving `GalleryScreen`'s extra `showUndoSnackbars` suppression guard (research.md Decision 3 — the two sites are *not* fully identical).

**Independent Test**: Both screens invoke `showUndoDeletedSnackbar(...)`; deleting then tapping Undo restores the item exactly as before in both screens; `GalleryScreen`'s `showUndoSnackbars = false` suppression still works and `PhotoViewerScreen` is unaffected by it.

### Implementation for User Story 3

- [X] T017 [US3] Add `suspend fun SnackbarHostState.showUndoDeletedSnackbar(message: String, actionLabel: String, onUndo: () -> Unit, onDismiss: () -> Unit)` to `app/src/main/java/dev/zun/flux/ui/gallery/GalleryScreen.kt`, containing the shared `showSnackbar(...)` + `ActionPerformed` branching body (per research.md Decision 3).
- [X] T018 [US3] In the same file, update `GalleryScreen`'s `LaunchedEffect(pendingUndo, showUndoSnackbars)` block to delegate its body to `showUndoDeletedSnackbar(...)`, keeping its own `if (!showUndoSnackbars) return@LaunchedEffect` guard and key list exactly as they are today. (Sequential with T017 — same file.)
- [X] T019 [P] [US3] In `app/src/main/java/dev/zun/flux/ui/gallery/PhotoViewerScreen.kt`, update `PhotoViewerScreen`'s `LaunchedEffect(pendingUndo)` block to delegate its body to `showUndoDeletedSnackbar(...)`, with no guard added (it has none today).
- [X] T020 [US3] Run `./gradlew :app:testDebugUnitTest` and confirm zero test-file edits; manually verify delete-then-undo restores correctly in both the Gallery grid and Photo viewer, and that `GalleryScreen`'s undo-suppression flag still behaves as before.

**Checkpoint**: User Story 3 is complete — `grep -rn "fun.*showUndoDeletedSnackbar" app/src/main/java/dev/zun/flux/ui/gallery/` returns exactly one match, and both call sites delegate to it while keeping their own distinct guard behavior (per quickstart.md SC-003 check).

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Confirm the whole refactor holds together and nothing was missed.

- [X] T021 Run `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug spotlessCheck` (the full suite from quickstart.md) and confirm it passes with zero test-file modifications across all three stories combined (FR-005/SC-004).
- [X] T022 [P] Run the SC-001/SC-002/SC-003 grep checks from quickstart.md's "Verifying the three consolidation counts" section and confirm each returns exactly one match for its respective shared helper.
- [X] T023 [P] Run `grep -rn "viewModelFactory { initializer { ProgressViewModel" app/src/main/java/dev/zun/flux/ui/` and confirm it returns nothing outside `rememberProgressViewModel`'s own definition (SC-005 — no leftover copy of the old block anywhere).
- [X] T024 Run `git diff --stat` against the pre-refactor baseline and confirm no file under `app/src/test/` or `app/src/androidTest/` appears in the diff (reinforcing FR-005: the existing suite needed zero edits).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: None — all three stories can start right after Phase 1.
- **User Stories (Phase 3-5)**: Each depends only on Phase 1 (baseline capture). They touch disjoint files and can proceed in parallel or in priority order (P1 → P2 → P3).
- **Polish (Phase 6)**: Depends on all three stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: No dependency on US2/US3.
- **User Story 2 (P2)**: No dependency on US1/US3.
- **User Story 3 (P3)**: No dependency on US1/US2.

### Within Each User Story

- The helper/shared-function task comes first (it's what the call-site replacements call into).
- Call-site replacement tasks in different files are parallel-safe; two tasks touching the *same* file (T004/T005, T009/T010, T017/T018) are sequential relative to each other only.
- Each story ends with a task that runs the existing suite and expects zero test-file edits.

### Parallel Opportunities

- T003, T011, T012, T013, T014, T015 (US1's `ProgressScreen.kt` site, and US2's five single-occurrence screens) can all run in parallel with each other and across stories — every one is a different file.
- T008 (US2, `EditHistoryScreen.kt`) and T019 (US3, `PhotoViewerScreen.kt`) are likewise parallel-safe against everything else.
- All three stories' helper-definition tasks (T002, T007, T017) touch different files and can be done in parallel before their respective call-site tasks begin.

---

## Parallel Example: User Story 2

```bash
# After T007 (BackNavigationIcon added to Polish.kt) lands, these six call-site
# replacements are all in different files and can be done in any order/in parallel:
Task: "Replace back-arrow IconButton in EditHistoryScreen.kt"
Task: "Replace back-arrow IconButton in ProgressScreen.kt"
Task: "Replace back-arrow IconButton in GalleryScreen.kt"
Task: "Replace back-arrow IconButton in SettingsScreen.kt"
Task: "Replace back-arrow IconButton in ResultScreen.kt"
Task: "Replace back-arrow IconButton in PhotoViewerScreen.kt (with tint = Color.White)"

# BatchProgressScreen.kt's two sites (BatchGrid, BatchFocused) are sequential
# relative to each other only, since they share one file.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (baseline capture).
2. Complete Phase 3: User Story 1 (T002-T006).
3. **STOP and VALIDATE**: `grep` confirms exactly 1 `ProgressViewModel` construction site; full suite passes with zero test edits.
4. This alone already delivers the highest-priority consolidation (the only byte-identical triplicate, in the feature area most likely to change again per spec.md's "Why this priority").

### Incremental Delivery

1. Setup → baseline captured.
2. User Story 1 → validate independently → this is the MVP.
3. User Story 2 → validate independently (largest total duplication removed).
4. User Story 3 → validate independently (smallest, lowest-risk).
5. Polish → confirm all three together via the full suite + all SC greps.

Each story is a strict subset of the total diff — stopping after any one of them leaves the codebase in a fully working, fully tested state; none depend on the others landing first.

### Solo Execution Note

This is sized for one implementer working stories in priority order (P1 → P2 → P3), not a multi-developer parallel split — the "Parallel Team Strategy" pattern from the tasks template doesn't add value here given the small total scope (24 tasks, no cross-story coordination needed).

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps each task to its user story for traceability.
- No test-file tasks are included anywhere in this list — see the **Tests** note at the top.
- Commit after each user story's checkpoint (matches this repo's existing practice of one logical commit per fix/feature slice).
- Stop at any checkpoint to validate a story independently before continuing.
