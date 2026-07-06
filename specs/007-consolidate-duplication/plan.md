# Implementation Plan: Consolidate Duplicated UI Boilerplate

**Branch**: `007-consolidate-duplication` | **Date**: 2026-07-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-consolidate-duplication/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Extract three shared helpers to remove verified duplication with zero behavior change:
(1) a `rememberProgressViewModel(jobId, jobs)` composable replacing the byte-identical
`ProgressViewModel` construction block in `ProgressScreen`, `BatchTile`, and `BatchPage`;
(2) a `BackNavigationIcon(onBack, contentDescription, tint)` composable replacing the
back-arrow `IconButton` block duplicated across 8 sites in 7 screens; (3) a shared
`showUndoDeletedSnackbar(...)` suspend function replacing the identical 10-line
undo-snackbar block in `GalleryScreen` and `PhotoViewerScreen`. No new dependencies, no
architecture change — pure extract-and-call-site-replace, verified by the existing test
suite passing unmodified (per constitution Principle II and FR-004/FR-005).

## Technical Context

**Language/Version**: Kotlin 2.4.0

**Primary Dependencies**: Jetpack Compose (BOM 2026.06.01), `androidx.lifecycle.viewmodel.compose` (`viewModel()`, `viewModelFactory`), Material 3

**Storage**: N/A — no data-layer changes; this feature touches only UI-layer composition code

**Testing**: JUnit4 + Robolectric (unit, `app/src/test`), instrumented tests on an API 36 emulator (`app/src/androidTest`) — existing suite only, no new test framework introduced

**Target Platform**: Android (compileSdk 37, minSdk 36, targetSdk 36)

**Project Type**: mobile-app (single-module Android app)

**Performance Goals**: N/A — no behavior or performance-sensitive path is touched; this is a structural, compile-time-only consolidation

**Constraints**: Zero behavior change (FR-004); existing automated test suite MUST pass unmodified (FR-005); no new DI framework or architectural layer (constitution Principle II)

**Scale/Scope**: 3 shared helpers extracted; 3 + 8 + 2 = 13 call sites updated across 7 files (`ProgressScreen.kt`, `BatchProgressScreen.kt`, `EditHistoryScreen.kt`, `GalleryScreen.kt`, `SettingsScreen.kt`, `ResultScreen.kt`, `PhotoViewerScreen.kt`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Privacy & Security by Default | No storage, network, secrets, or permission code is touched | PASS |
| II. Surgical, Simplicity-First Changes | Every extracted helper has a concrete, current call site (no speculative params); FR-006 explicitly excludes generalizing beyond the 3 identical `ProgressViewModel` sites; no new DI framework or architectural layer | PASS |
| III. Verify Before Claiming Done | FR-005 requires the existing suite to pass unmodified; since there is no new behavior, the existing suite passing unmodified *is* the verification — no new test is fabricated to "prove" a behavior that didn't change | PASS |
| IV. Offline-Capable by Design | Not touched — no gallery/result read-path or `OfflineImageCache` code is in scope | N/A |
| V. Server Contract Fidelity | Not touched — no `FluxApi.kt` or server-facing contract change | N/A |
| VI. Development/Production Environment Isolation | Not touched — no server-URL or debug/release config code is in scope | N/A |

No violations. Complexity Tracking table below is empty (nothing to justify).

**Post-Phase-1 re-check**: The concrete design in `research.md` (Decisions 1-3)
adds no new files, no new dependencies, and no generalization beyond each
helper's current call sites (Decision 3's discovery that `GalleryScreen` and
`PhotoViewerScreen` aren't fully identical was handled by keeping the
divergent guard at the call site rather than parameterizing it into the
shared function — the simpler option, per Principle II). Gate still PASSES
with no changes to the table above.

## Project Structure

### Documentation (this feature)

```text
specs/007-consolidate-duplication/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory — this feature is purely internal to a single-module
Android app (no library API, CLI schema, or service endpoint is exposed),
matching the precedent set by features 003/005/006.

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/ui/
├── progress/
│   ├── ProgressViewModel.kt        # + rememberProgressViewModel() helper (Decision 1)
│   ├── ProgressScreen.kt           # call site: single-job progress
│   └── BatchProgressScreen.kt      # call sites: BatchTile, BatchPage, BatchGrid back icon, BatchFocused back icon
├── common/
│   └── Polish.kt                   # + BackNavigationIcon() composable (Decision 2)
├── history/EditHistoryScreen.kt    # call site: back icon
├── gallery/
│   ├── GalleryScreen.kt            # + showUndoDeletedSnackbar() extension fn (Decision 3); call sites: back icon, undo snackbar
│   └── PhotoViewerScreen.kt        # call sites: back icon (white tint), undo snackbar
├── settings/SettingsScreen.kt      # call site: back icon
└── result/ResultScreen.kt         # call site: back icon

app/src/test/java/dev/zun/flux/ui/progress/
├── ProgressViewModelTest.kt        # existing suite — must pass unmodified
└── (BatchProgressScreen has no dedicated unit test file today; covered by
    ProgressViewModelTest + manual/instrumented smoke pass)
```

**Structure Decision**: Single-module Android app (existing structure, no new
modules/packages). All three helpers are added to files that already exist in
the areas they serve — no new files are created. This directly follows from
Decision 1/2/3 in `research.md`: co-locate each shared helper with its most
natural existing home rather than introducing a new abstraction layer or
directory, consistent with constitution Principle II.

## Complexity Tracking

N/A — no Constitution Check violations to justify.
