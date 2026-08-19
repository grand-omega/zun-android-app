# Implementation Plan: Local Composite Gallery Entries

**Branch**: `015-local-composite-gallery` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-local-composite-gallery/spec.md`

## Summary

A saved drag-reveal composite becomes a real `JobEntity` row (`status = "done"`) inserted directly
into Room — no schema change, no migration — identified purely by a reserved id prefix
(`local-composite-<uuid>`) that every consumption site already has cheap, synchronous access to via
the job id string it already holds. The composite's actual image bytes live in a new directory
outside `OfflineImageCache`'s managed root, so feature 009's LRU `prune()` and "Clear offline
cache" — both of which only ever walk that root — can never touch it, satisfying FR-011 by
construction rather than by adding exclusion logic to already-working cache code.
`ImageSourceRepository`'s `previewModel`/`resultModel`/`thumbModel`/`offlineAvailability` and
`JobRepository.deleteJob` each get one new branch: if the id carries the reserved prefix, resolve
straight to the local file (never falling back to a server URL that would 404) or skip the
server-delete-sync queue entirely (never touching the network, per FR-007's letter, not just its
spirit). `ScratchRevealCompare`'s existing save button (feature 014) is rewired from
`writeToTempFile`+`saveToPictures` to a new hoisted callback that performs this insert instead;
its share button is untouched (FR-010).

## Technical Context

**Language/Version**: Kotlin 2.4.0, Jetpack Compose, Room

**Primary Dependencies**: No new dependencies. Reuses `androidx.room` (existing `JobEntity`/`JobDao`,
unchanged schema), the existing `JobRepository`/`ImageSourceRepository` interfaces (each gains one
new method/branch), and feature 014's `ImageCompositor.kt` (`resolveToBitmap`/`snapshotMask`/
`compositeReveal` unchanged — only the final "where does the flattened bitmap go" step changes).

**Storage**: A new directory, `context.filesDir/local_composites/<id>/composite.jpg` — deliberately
*not* inside `OfflineImageCache`'s `rootDir` (research.md Decision 2 — this is what makes FR-011
hold without touching `OfflineImageCache.kt`, `CacheCleanupViewModel.kt`, or `prune()` at all).
`JobEntity` itself is unchanged: a local composite is a normal row, just with an id that starts with
a reserved prefix instead of a server-issued one.

**Testing**: JUnit4 unit tests in `RealJobRepositoryTest.kt` (already established pattern) for the
id-prefix branch in `previewModel`/`resultModel`/`thumbModel`/`offlineAvailability`/`deleteJob` —
these are pure branching logic, genuinely unit-testable without Room/instrumentation. A
`GalleryThumbnailTest.kt` case for the visual distinguisher badge (FR-003), matching that file's
existing per-badge test style. The actual file write/read and end-to-end gallery-grid rendering are
verified manually, per Constitution III's explicit-manual-path allowance — same split used
throughout features 010/011/014.

**Target Platform**: Android

**Project Type**: mobile-app (single module) — no external interface, no `contracts/`.

**Performance Goals**: Not a performance-sensitive path — a single local file write plus one Room
insert, both already-fast operations elsewhere in this codebase (matches `saveToPictures`'s existing
`Dispatchers.IO` convention).

**Constraints**: The reserved id prefix (`local-composite-`) must never collide with a real
server-issued job id — confirmed safe: server ids are opaque server-generated tokens the client
never invents, so a client-chosen, distinctly-formatted prefix carries essentially zero collision
risk. Feature 009's background sync (`syncHistory`/`listJobs`/`getJob`) only ever upserts rows the
server actually returned (research confirmed) — a locally-inserted row is never at risk of being
silently overwritten or swept by that path.

**Scale/Scope**: One new repository method (`saveLocalComposite`, added to the `JobRepository`
interface, `RealJobRepository`, and both test fakes — `FakeJobRepository`/`RecordingRepository`);
one new branch each in `previewModel`/`resultModel`/`thumbModel`/`offlineAvailability`/`deleteJob`;
one new hoisted callback threaded from `PhotoViewerScreen`/`ResultScreen` (which already hold
`jobs: JobRepository`) through `CompareOverlay`/`CompareModeSwitcher` into `ScratchRevealCompare`,
replacing its current `saveToPictures` call; a small visual-badge addition to `GalleryThumbnail.kt`;
a small action-visibility gate in `PhotoViewerScreen`'s `ViewerActionBar`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Privacy & Security by Default | No new data leaves the device; storage is a new local-only directory, not shared/exported anywhere new | PASS |
| II. Surgical, Simplicity-First Changes | The id-prefix design was chosen specifically *because* it needs zero schema migration and zero column-threading across the DTO chain — every consumption site already has the job id string in hand; the alternative (a new `isLocalOnly` Room column) was considered and rejected for being strictly more invasive for equivalent behavior (see research.md Decision 1) | PASS (see Complexity Tracking — one justified new capability) |
| III. Verify Before Claiming Done | The prefix-branching logic is genuinely unit-testable (pure conditionals, no Room/IO); file I/O and full gallery-grid rendering verified manually, not conflated with "tested" | PASS (planned in tasks) |
| IV. Offline-Capable by Design | This feature is *more* offline-resilient than existing jobs, not less — FR-007 requires zero server round-trips at any point, exceeding the principle's baseline requirement rather than merely meeting it | PASS |
| V. Server Contract Fidelity | No API/DTO change; confirmed via research that the existing sync pipeline (`syncHistory`, `listJobs`, `getJob`) only ever acts on ids the server itself returned, so a locally-inserted row is never pushed to or silently reconciled against the server | PASS |
| VI. Development/Production Environment Isolation | Not touched | N/A |

No unaddressed violations. Complexity Tracking documents the one genuinely new capability (a
client-only gallery entry, identified by a reserved id prefix) and why the simpler of two considered
designs was chosen.

## Project Structure

### Documentation (this feature)

```text
specs/015-local-composite-gallery/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory — purely internal to this Android app, no external interface.

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/data/repo/
├── JobRepository.kt          # + saveLocalComposite(bitmap: Bitmap): Result<String> (interface)
├── RealJobRepository.kt      # + saveLocalComposite impl; + id-prefix branch in
│                                previewModel/resultModel/thumbModel/offlineAvailability/deleteJob
└── ImageSourceRepository.kt  # (no signature change — same methods, RealJobRepository's impl
                                 gains the new branch)

app/src/main/java/dev/zun/flux/ui/gallery/
├── ScratchRevealCompare.kt   # save button calls the new hoisted onSaveComposite callback instead
│                                of writeToTempFile+saveToPictures; share button unchanged (FR-010)
├── CompareOverlay.kt         # threads onSaveComposite through CompareModeSwitcher
├── PhotoViewerScreen.kt      # supplies onSaveComposite (already holds jobs: JobRepository);
│                                ViewerActionBar hides history/compare/use-as-input for a
│                                local-composite job.id
└── GalleryThumbnail.kt       # + small visual-distinguisher badge (FR-003) gated on the job id's
                                 reserved prefix

app/src/main/java/dev/zun/flux/ui/result/
└── ResultScreen.kt           # supplies onSaveComposite (already holds jobs: JobRepository)

app/src/test/java/dev/zun/flux/data/repo/
├── FakeJobRepository.kt      # + saveLocalComposite stub
└── RecordingRepository.kt    # + saveLocalComposite stub + recording, matching its existing style
```

**Structure Decision**: No new files beyond tests — this extends five existing files along one
consistent seam (the reserved id prefix) rather than introducing a parallel "local gallery" subsystem.
`ImageCompositor.kt` (feature 014) is untouched; only where its output *goes* changes.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| A reserved id-prefix convention for identifying client-only `JobEntity` rows, used across 5 call sites (`previewModel`/`resultModel`/`thumbModel`/`offlineAvailability`/`deleteJob`) | The feature's entire ask — a saved composite must appear as a real, browsable, favoritable, deletable gallery entry — requires *some* way to distinguish a client-only row from a server-backed one at each of these points, and none of the existing schema gives that for free | A new `isLocalOnly: Boolean` Room column (with a v6→v7 migration) was seriously considered — it's more conventional, but every one of the 5 consumption sites already has the job id string in hand and none of them are in a position to cheaply do a live Room lookup inline (several are synchronous, non-suspend functions called directly from Compose); a column would need either an extra DB round-trip at each site or threading the flag through the whole `JobSummaryDto`/`JobWithStackCount` chain the way `stackHasFavorite` was for feature 014 — real, working precedent, but strictly more surgery for identical behavior here |
