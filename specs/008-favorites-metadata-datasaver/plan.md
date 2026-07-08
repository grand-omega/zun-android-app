# Implementation Plan: Favorites, Generation Details, and Cellular Data Control

**Branch**: `008-favorites-metadata-datasaver` | **Date**: 2026-07-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-favorites-metadata-datasaver/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Three independent, additive features: (1) a local-only `isFavorite` flag on `JobEntity` (schema
v5→v6) surfaced as a heart toggle in the photo viewer and a grid overlay, plus a combinable
"favorites only" filter threaded alongside the existing `TagFilter`/`customOnly` dimension in
`GalleryViewModel`/`JobDao`'s paged queries; (2) a read-only `ModalBottomSheet` (reusing the
pattern already in `PromptSheets.kt`) in the photo viewer showing fields `JobEntity` already
stores (`promptText`, `workflow`, `seed`/try-harder, `createdAt`/`completedAt`); (3) a Settings
toggle that sets `NetworkType.UNMETERED` vs `NetworkType.CONNECTED` on `JobUploadWorker`'s
`Constraints` for uploads, and gates the inline (non-WorkManager) `OfflineImageCache.prefetch`
calls behind a manual connectivity check for automatic result-fetch downloads.

## Technical Context

**Language/Version**: Kotlin 2.4.0

**Primary Dependencies**: Jetpack Compose (BOM 2026.06.01) + Material 3 `ModalBottomSheet`, Room
(schema v5, `schemaMigration()`/`addColumnIfMissing` idempotent-DDL pattern), Paging 3, WorkManager
(`Constraints.NetworkType`), `ConnectivityManager` (ad hoc connectivity check for non-WorkManager
downloads)

**Storage**: Room — one new column (`jobs.isFavorite INTEGER NOT NULL DEFAULT 0`), one schema
migration (v5→v6); one new app-wide preference (network mode) alongside existing Settings-stored
preferences

**Testing**: JUnit4 + Robolectric (unit, `app/src/test`), instrumented tests on an API 36 emulator
(`app/src/androidTest`) — existing suite plus new migration/DAO/ViewModel tests for this feature

**Target Platform**: Android (compileSdk 37, minSdk 36, targetSdk 36)

**Project Type**: mobile-app (single-module Android app)

**Performance Goals**: N/A — no new performance-sensitive path; favorite/metadata are cheap
reads over already-loaded data, network gating only changes *when* existing transfers run, not
their throughput

**Constraints**: Local-only favorite flag MUST survive a server resync (`OnConflictStrategy.REPLACE`
on `insertJobs` would otherwise wipe it — same class of problem already solved once for
`sourceSha256`/`lineageRootId` via `carryForwardLineage`, see research.md); network-mode setting
MUST NOT block explicit user actions (Save to device, Share) per spec FR-010

**Scale/Scope**: 1 schema migration, 1 new Room column, ~4 files touched for favorites (`JobEntity`,
`JobDao`, `AppDatabase`, `GalleryViewModel`/`GalleryScreen`, `PhotoViewerScreen`), ~2 files for the
metadata sheet (`PhotoViewerScreen`, a new sheet composable), ~3 files for the network toggle
(`SettingsScreen`/`SettingsManager`, `RealJobRepository`'s upload-enqueue constraint, `OfflineImageCache`
call sites)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Privacy & Security by Default | No secrets, no new permissions, no analytics touched. Favorite flag and network preference are plain local state (not tokens/credentials) — plain storage is fine here | PASS |
| II. Surgical, Simplicity-First Changes | Each of the 3 features has a concrete spec requirement driving it; no speculative params. Metadata sheet reuses the existing `ModalBottomSheet` pattern from `PromptSheets.kt` rather than inventing a new UI mechanism | PASS |
| III. Verify Before Claiming Done | New migration needs an instrumented migration test (matches existing `AppDatabaseMigrationTest` pattern); favorite/filter logic and network-gating logic each get unit tests; no UI-only claim without a test or explicit manual-verification note | PASS (planned in tasks) |
| IV. Offline-Capable by Design | Favorite flag and metadata sheet are pure Room/already-cached reads — no network dependency, no regression. Network-mode gating deliberately *defers* transfers rather than erroring, consistent with graceful degradation | PASS |
| V. Server Contract Fidelity | Not touched — no `FluxApi.kt`/request-response shape changes; favorite status and network preference are local-only, never sent to the server | N/A |
| VI. Development/Production Environment Isolation | Not touched — no server-URL/debug-release config code in scope | N/A |

No violations. Complexity Tracking table below is empty (nothing to justify).

## Project Structure

### Documentation (this feature)

```text
specs/008-favorites-metadata-datasaver/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory — purely internal to a single-module Android app, matching precedent
from features 003/005/006/007.

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/data/local/
├── JobEntity.kt                  # + isFavorite: Boolean = false
├── JobDao.kt                     # + favorite-filtered paging queries, setFavorite()
└── AppDatabase.kt                # version 5 -> 6, MIGRATION_5_6, isFavorite in ensureCurrentSchema

app/src/main/java/dev/zun/flux/data/repo/
├── RealJobRepository.kt          # carryForwardLineage -> also carries isFavorite;
│                                   upload-enqueue Constraints read network-mode setting
├── JobRepository.kt              # + setFavorite(jobId), pagedJobs() gains favoritesOnly param
├── OfflineImageCache.kt          # prefetch() call sites gated by a connectivity check when
│                                   network-mode is Wi-Fi-only
└── SettingsManager.kt            # + networkMode preference (mirrors existing gallerySortNewestFirst)

app/src/main/java/dev/zun/flux/ui/gallery/
├── GalleryViewModel.kt           # + favoritesOnly StateFlow, combined into jobs/pagedGridItems
├── GalleryScreen.kt              # + favorites-only filter chip, grid tile favorite overlay
└── PhotoViewerScreen.kt          # + favorite heart icon in action bar, + metadata sheet trigger

app/src/main/java/dev/zun/flux/ui/common/
└── (new) GenerationDetailsSheet.kt   # ModalBottomSheet, reusing PromptSheets.kt's pattern

app/src/main/java/dev/zun/flux/ui/settings/
└── SettingsScreen.kt             # + Wi-Fi-only / allow-cellular-data toggle
```

**Structure Decision**: Single-module Android app (existing structure, no new modules). All
three features extend existing files rather than introducing new architectural layers — the one
new file (`GenerationDetailsSheet.kt`) is a straightforward composable following an established
in-repo pattern (`PromptSheets.kt`'s `ModalBottomSheet` usage), not a new mechanism.

## Complexity Tracking

N/A — no Constitution Check violations to justify.
