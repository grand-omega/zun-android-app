# Implementation Plan: Variant Stacking & Offline Cache Cleanup

**Branch**: `009-variant-stacking-cache-cleanup` | **Date**: 2026-07-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-variant-stacking-cache-cleanup/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Two independent, additive features: (1) collapse gallery grid cells that share a `lineageRootId`
into one "stack" cell with a count badge, computed in SQL via `GROUP BY COALESCE(lineageRootId,
id)` on the existing paged/aggregate `JobDao` queries (not a client-side, page-boundary-fragile
grouping) — tapping a stack opens the existing photo viewer scoped to just that lineage group's
jobs; (2) a Settings "Clear offline cache" flow that lists every job with something cached
(`OfflineImageCache` has no such per-job listing today — new), shows it as a preview grid, and on
confirm re-checks connectivity before evicting (a state change between preview and confirm, e.g.
going offline, must not leave a "last-known" image with zero available copies).

## Technical Context

**Language/Version**: Kotlin 2.4.0

**Primary Dependencies**: Jetpack Compose (BOM 2026.06.01) + Material 3, Room, Paging 3
(`PagingSource<Int, JobEntity>`), `ConnectivityManager` (reusing the connectivity-check pattern
introduced in feature 008 for the cellular-data toggle)

**Storage**: Room — no schema change. Stacking reads the existing `lineageRootId` column (added in
feature 004); cache cleanup reads the filesystem under `OfflineImageCache.rootDir`, no new
persisted entity.

**Testing**: JUnit4 + Robolectric (unit, `app/src/test`), instrumented tests on an API 36 emulator
(`app/src/androidTest`) — new DAO/query tests for stack grouping and counting, new tests for the
cache-listing/eviction-safety logic

**Target Platform**: Android (compileSdk 37, minSdk 36, targetSdk 36)

**Project Type**: mobile-app (single-module Android app)

**Performance Goals**: Stacking must not add a second query pass over the grid's data — it's
computed by changing the existing paged/count `@Query`s, not by post-processing pages client-side.
Cache listing is a filesystem walk already done today by `stats()`/`prune()`; the new listing
reuses that same walk, not a second one.

**Constraints**: Stacking MUST be correct across Paging 3 page boundaries — grouping happens once,
in SQL, before pagination, so a stack's members never split across two pages by construction (see
research.md for why client-side/page-local grouping was rejected). Cache eviction MUST NOT be
performed on stale connectivity information — the confirm step re-checks state rather than trusting
the preview snapshot (spec FR-010).

**Scale/Scope**: ~3 files touched for stacking (`JobDao`, `RealJobRepository`/`JobRepository`,
`GalleryViewModel`/`GalleryScreen`/`GalleryThumbnail`, `PhotoViewerScreen`'s entry point); ~4 files
for cache cleanup (`OfflineImageCache` new listing method, `SettingsScreen`, a new
`CacheCleanupScreen`/dialog composable, nav wiring)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Privacy & Security by Default | No secrets, no new permissions, no analytics. Stacking and cache listing are local, already-authorized data (job metadata, cache file listing) | PASS |
| II. Surgical, Simplicity-First Changes | Stacking reuses `lineageRootId` (feature 004) and the paging infrastructure (feature 008's `GridQuery` pattern) rather than inventing a new grouping mechanism; cache cleanup reuses `OfflineImageCache.delete()`/`stats()` rather than a new deletion path | PASS |
| III. Verify Before Claiming Done | New DAO grouping/count queries get instrumented tests against real Room; cache-listing and eviction-safety logic get unit tests; UI reached via manual verification per quickstart.md | PASS (planned in tasks) |
| IV. Offline-Capable by Design | Stacking is a pure Room read, no network dependency. Cache cleanup explicitly strengthens this principle — its whole purpose is to never evict an image that would become completely unavailable, re-checking connectivity at confirm time | PASS |
| V. Server Contract Fidelity | Not touched — no `FluxApi.kt` changes. Both features are entirely client-local | N/A |
| VI. Development/Production Environment Isolation | Not touched | N/A |

No violations. Complexity Tracking table below is empty (nothing to justify).

## Project Structure

### Documentation (this feature)

```text
specs/009-variant-stacking-cache-cleanup/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory — purely internal to a single-module Android app, matching precedent
from features 003/005/006/007/008.

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/data/local/
└── JobDao.kt                     # paged/count queries grouped by COALESCE(lineageRootId, id);
                                     + stack member lookup query

app/src/main/java/dev/zun/flux/data/repo/
├── JobRepository.kt              # pagedJobs() return type carries stack membership/count
├── RealJobRepository.kt          # implementation wired to the new grouped DAO queries
└── OfflineImageCache.kt          # + listCachedJobs(): List<CachedJobSummary> (new; everything
                                     else — delete/stats/clear — reused as-is)

app/src/main/java/dev/zun/flux/ui/gallery/
├── GalleryViewModel.kt           # GalleryGridItem gains a Stack case; stack-scoped jobs() query
│                                   for PhotoViewerScreen when opened from a stack
├── GalleryThumbnail.kt           # stack count badge on the cover tile
└── GalleryScreen.kt              # tapping a stack cell opens the viewer scoped to the stack

app/src/main/java/dev/zun/flux/ui/settings/
└── SettingsScreen.kt             # + "Clear offline cache" action opening the cleanup flow

app/src/main/java/dev/zun/flux/ui/settings/ (new)
└── CacheCleanupScreen.kt         # preview grid + confirm, reusing Gallery's thumbnail-grid look
```

**Structure Decision**: Single-module Android app (existing structure, no new modules). Stacking
extends the existing `JobDao`/`GalleryViewModel`/`GalleryScreen` trio already touched by features
004 and 008 rather than introducing a new architectural layer. Cache cleanup adds one new screen
(`CacheCleanupScreen.kt`) following the existing Settings-flow pattern, plus one new read-only
listing method on `OfflineImageCache` — no new persisted entity, no new worker, no new permission.

## Complexity Tracking

N/A — no Constitution Check violations to justify.
