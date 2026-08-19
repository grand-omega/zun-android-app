# Implementation Plan: Gallery Thumbnail Reveal Animation

**Branch**: `011-thumbnail-reveal-animation` | **Date**: 2026-07-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-thumbnail-reveal-animation/spec.md`

## Summary

`GalleryViewModel` diffs consecutive emissions of the already-existing, Room-backed
`JobRepository.activeJobIds()` Flow (the same one `HomeViewModel` already uses) to detect job IDs
that just transitioned from active to done — the first emission after the ViewModel starts is
treated as a baseline (not diffed), so nothing is falsely eligible for jobs that finished before
Gallery was opened, and every fresh Gallery visit starts a fresh baseline for free. Newly-done IDs
become a `revealEligibleJobIds` set exposed to the grid; `GalleryThumbnail`'s cell composable plays
a one-time blur→sharp/scale/fade reveal via `LaunchedEffect(job.id)` when its own id is in that set,
then immediately reports it consumed. No new persisted state, no new API surface, no cross-repo
dependency — purely internal to this Android app.

## Technical Context

**Language/Version**: Kotlin 2.4.0, Jetpack Compose (Material3)

**Primary Dependencies**: Coil3 (`coil3.compose`, v3.5.0 — already used in `GalleryThumbnail.kt`
via `SubcomposeAsyncImage`); existing Paging3 (`LazyPagingItems`) grid already in `GalleryScreen.kt`

**Storage**: None — no new persisted entity or schema change (matches spec Assumptions: reveal
eligibility is session/ViewModel-lifecycle-scoped only, held in memory)

**Testing**: JUnit4 for `GalleryViewModel`'s active-id diffing logic (pure Flow logic, no Compose
needed — genuinely unit-testable). The visual animation itself (blur/scale/fade progression) is not
meaningfully unit-testable; verified manually per Constitution Principle III's "explicit manual
verification path" allowance, documented honestly in quickstart.md/tasks.md rather than claimed via
a test that wouldn't actually catch a visual regression.

**Target Platform**: Android

**Project Type**: mobile-app (single module) — purely internal to the Gallery screen, no external
interface, so (unlike feature 013) no `contracts/` directory for this one.

**Performance Goals**: Reveal completes in under 1 second per thumbnail (SC-003); the diffing
itself is a cheap `Set` operation over the small number of concurrently-active jobs, run once per
`activeJobIds()` emission (not per-frame, not per-scroll).

**Constraints**: Must never replay for an already-revealed thumbnail (FR-002) even across
scroll-driven compose/dispose cycles of the same grid cell; must never fire for a job that finished
before Gallery was opened this visit (FR-003); each thumbnail animates independently with no
ordering/delay coupling (FR-004); an interrupted reveal must never leave a stuck or errored visual
state (FR-006); a stack's cover-thumbnail change must trigger the reveal, a non-cover completion
must not (FR-007) — resolved with no special-casing, see research.md Decision 3.

**Scale/Scope**: New state in `GalleryViewModel.kt` (one `StateFlow<Set<String>>` + the diffing
collector + a consume function); an animated wrapper in `GalleryThumbnail.kt` around the existing
`SubcomposeAsyncImage` content; threading the new state/callback through `GalleryScreen.kt` to the
`JobThumbnail` call site.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Privacy & Security by Default | No new data collection, no new permission, no analytics — purely a local presentation-layer animation | PASS |
| II. Surgical, Simplicity-First Changes | Reuses the existing `activeJobIds()` Flow (already used identically in `HomeViewModel`) rather than adding a new DAO query or event mechanism; one `StateFlow<Set<String>>`, no new persisted entity, no new architectural layer | PASS |
| III. Verify Before Claiming Done | The diffing logic is unit-tested (pure, no Compose); the visual animation is verified manually on-device — both are called out explicitly, not conflated | PASS (planned in tasks) |
| IV. Offline-Capable by Design | Not a new offline concern — `activeJobIds()` is already Room-backed/local-only (no network call), and this feature adds no new read path, only a presentation animation gated on data that's already loaded offline-safely | PASS |
| V. Server Contract Fidelity | N/A — no API/DTO change, no cross-repo dependency this time (unlike feature 013) | N/A |
| VI. Development/Production Environment Isolation | Not touched | N/A |

No violations; no Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/011-thumbnail-reveal-animation/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory — this feature has no external interface (no new API endpoint, no new
data persisted anywhere), unlike feature 013's cross-repo dependency.

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/ui/gallery/
├── GalleryViewModel.kt   # + revealEligibleJobIds: StateFlow<Set<String>>, markRevealed(jobId),
│                           the activeJobIds()-diffing collector (started in init{})
├── GalleryThumbnail.kt   # + reveal animation wrapper (blur/scale/fade via animateFloatAsState +
│                           Modifier.blur/graphicsLayer), gated by LaunchedEffect(job.id) checking
│                           membership in revealEligibleJobIds
└── GalleryScreen.kt      # threads revealEligibleJobIds/onRevealPlayed from the ViewModel down to
                            each JobThumbnail call site
```

**Structure Decision**: Single-module Android app (existing structure), entirely additive within
the already-established Gallery screen files — no new files, no new screen, no new navigation
route.

## Complexity Tracking

*No entries — no Constitution Check violations to justify.*
