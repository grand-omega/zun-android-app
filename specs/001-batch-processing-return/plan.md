# Implementation Plan: Return to Running Batch

**Branch**: `001-batch-processing-return` | **Date**: 2026-07-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-batch-processing-return/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Users who navigate away from `BatchProgressScreen` (`Routes.BATCH_PROGRESS = "batch/{jobIds}"`) while jobs are still processing currently have no way back — the nav route only exists with the original job-id list, and popping it off the back stack discards it for good. Background processing itself is unaffected (`JobWatchWorker` + Room already track it), only the return path is missing. The fix is additive: derive an "active jobs" list from the existing `jobs` Room table (a status filter, not a schema change), surface it as a small entry point on Home, and route taps through the existing `Routes.batch(jobIds)` destination — which already accepts an arbitrary job-id list, so one active-jobs view naturally covers single jobs and multiple concurrent batches alike.

## Technical Context

**Language/Version**: Kotlin 2.4.0

**Primary Dependencies**: Jetpack Compose + Navigation-Compose (`AppNavHost.kt`), Room (`AppDatabase`/`JobDao`), WorkManager (`JobWatchWorker`), Coil3 (image loading) — no DI framework; wiring stays manual through `FluxApp`/`Repositories`

**Storage**: Room (`jobs` table via `JobEntity`/`JobDao`) — feature reads existing data with a new query, no schema migration needed since `status` is already a column

**Testing**: JUnit (`app/src/test`) for view-model/repository logic; instrumented tests (`app/src/androidTest`, `connectedDebugAndroidTest`) for nav/UI behavior per constitution Quality Gates

**Target Platform**: Android (compileSdk 37, minSdk 36, targetSdk 36)

**Project Type**: Mobile app — single Android module

**Performance Goals**: Entry point reflects a job-status change within ~5s, matching `ProgressViewModel`'s existing poll cadence (SC-002); no additional network calls beyond what already runs while jobs are watched

**Constraints**: Must not affect background processing when the live view is left (FR-007); must work with the app fully closed and reopened (FR-005) — relies on Room + WorkManager already surviving process death; must render correct status with no network using last-synced local data (FR-008, Constitution IV)

**Scale/Scope**: One new Home-screen entry point + one new repository query; no new screens (reuses `BatchProgressScreen` via the existing `Routes.batch(jobIds)` route); no new server endpoints (Constitution V n/a — no API contract change)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Privacy & Security by Default | No new data leaves the device; no analytics/tracking added; uses existing local Room data only | PASS |
| II. Surgical, Simplicity-First Changes | Adds one repository query + one Home UI affordance; reuses `BatchProgressScreen`/`Routes.batch` as-is rather than building a parallel screen; no new DI/architecture layer | PASS |
| III. Verify Before Claiming Done | Plan requires an instrumented test driving: back out of batch → Home shows entry point → tap → live view restored, plus a repository-level unit test for the active-jobs query | PASS (enforced in tasks) |
| IV. Offline-Capable by Design | Entry point is driven by local Room state (`JobDao`), which already updates from cached data with no network — no new network dependency introduced | PASS |
| V. Server Contract Fidelity | No request/response shape changes; `FluxApi.kt` and `API_CONTRACT.md` are untouched | N/A |

No violations — Complexity Tracking table is not needed.

**Post-Phase 1 re-check**: `research.md` and `data-model.md` confirmed no
schema migration, no new module/DI, and no server-contract change are
needed — the table above still holds unchanged after design.

## Project Structure

### Documentation (this feature)

```text
specs/001-batch-processing-return/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/
├── data/
│   ├── local/
│   │   ├── JobDao.kt             # + query for non-terminal jobs, not pending-delete
│   │   └── JobEntity.kt          # unchanged — status column already exists
│   └── repo/
│       ├── JobRepository.kt      # + activeJobIds(): Flow<List<String>>
│       └── RealJobRepository.kt  # implements the new query
├── ui/
│   ├── home/
│   │   ├── HomeViewModel.kt      # + expose activeJobIds as StateFlow
│   │   ├── HomeRoute.kt          # + wire entry point tap -> onResumeBatch callback
│   │   └── HomeScreen.kt         # + small entry-point composable (banner/card)
│   ├── nav/
│   │   └── AppNavHost.kt         # + onResumeBatch = { nav.navigate(Routes.batch(it)) }
│   └── progress/
│       └── BatchProgressScreen.kt # unchanged — reused as-is for the resumed view

app/src/test/java/dev/zun/flux/
└── data/repo/                    # unit test for the new active-jobs query/mapping

app/src/androidTest/java/dev/zun/flux/
└── ui/                           # instrumented test: back out -> entry point -> resume
```

**Structure Decision**: Single-module Android app (existing `app/` module, package
`dev.zun.flux`). This feature is additive within the existing layers — no new
module, screen, or navigation route. The only new "surface" is a small entry point
on the existing Home screen, and the only new data-layer code is one filtered
query over the already-existing `jobs` Room table.

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
