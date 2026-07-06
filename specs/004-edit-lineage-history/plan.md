# Implementation Plan: Edit Lineage & Duplicate-Source History

**Branch**: `004-edit-lineage-history` | **Date**: 2026-07-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-edit-lineage-history/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

When a user picks a source photo they've submitted before (as an original upload, via "regenerate," via "use as new source," or by independently re-uploading a saved copy of a past result), the app shows a non-blocking "edited before" indicator and offers a full chronological "view edit history" for that photo — reachable from every result/gallery entry, not just at fresh-detection time. Technical approach: extend `JobEntity` with three nullable columns (`sourceSha256`, `resultSha256`, `lineageRootId`) computed from a content hash already used today for the server's upload-dedup key; one flat grouping key replaces what would otherwise require a graph/edges table, and the history view reuses existing per-job screens for every action. No server-side or contract changes.

## Technical Context

**Language/Version**: Kotlin 2.4.0, JVM target 17

**Primary Dependencies**: Jetpack Compose, Room + KSP (existing), no new dependencies added

**Storage**: Room (SQLite) — `JobEntity` extended with 3 new nullable, indexed columns; `AppDatabase` version 4 → 5 migration

**Testing**: JUnit unit tests for the pure hash-matching/`lineageRootId`-assignment logic; an instrumented migration test extending `AppDatabaseMigrationTest.kt`; manual verification (`quickstart.md`) for the on-screen banner, menu entries, and new history screen, since no Compose/ViewModel test scaffolding exists yet for those

**Target Platform**: Android (minSdk 36, targetSdk 36, compileSdk 37); single `dev.zun.flux` app module, debug/release build types

**Project Type**: mobile-app (single Android module)

**Performance Goals**: Hash computation + Room lookup at picker time must add no noticeable delay (SC-003) — SHA-256 over an already-downscaled/prepared image file is sub-100ms in practice, and the Room lookup is a single indexed equality query

**Constraints**: No server-side/`FluxApi.kt` contract changes (Principle V); no retroactive backfill of existing job history (FR-006); the history view must degrade gracefully offline like the rest of the gallery/result read path (Principle IV); no new "delete/merge lineage" management UI (per spec Assumptions)

**Scale/Scope**: 3 new nullable+indexed columns and 1 migration on `JobEntity`/`AppDatabase`; ~3 new `JobDao` query methods; 1 new "Edit History" screen; 2 existing screens (`ResultScreen`, `PhotoViewerScreen`) get one new menu/action entry each; hash-computation hooks at 2 existing points (image-selection in Home, result-prefetch in `OfflineImageCache`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Privacy & Security by Default | SHA-256 hashes of the user's own local photos, stored locally only; no new external destination — a hash of this same content is already sent to the server today for dedup. No new secrets, no new tracking. | **PASS** |
| II. Surgical, Simplicity-First | One mechanism (dual content-hash match + a flat `lineageRootId` string) covers every linking scenario in the spec, rejecting a heavier graph/edges table (see research.md). Reuses the existing hash utility, the existing eager result-cache hook, and existing per-job screens for every action — the new screen only lists and routes, it doesn't reimplement share/regenerate/use-as-source. | **PASS** |
| III. Verify Before Claiming Done | New unit tests for the hash-matching/assignment logic (pure, fully testable) plus an extended Room migration test. The banner/menu/history-screen UI is verified manually per `quickstart.md`, matching the precedent and allowance used in feature 003. | **PASS** |
| IV. Offline-Capable by Design | The history view and detection are 100% local Room queries — no network call in either path — and each history entry reuses the existing offline-capable `ResultScreen`/`PhotoViewer` image loading. This feature is a direct, deliberate extension of this principle, not a risk to it. | **PASS** |
| V. Server Contract Fidelity | No `FluxApi.kt` changes; no `zun-rust-server` changes required — detection reuses a hash already computed and sent today for an unrelated purpose (dedup), and history grouping is entirely client-side. | **PASS** |
| VI. Development/Production Environment Isolation | Not applicable — this feature doesn't touch server-address configuration. | **N/A** |

Also flag for Quality Gates: confirm the new Home-screen picker hook and the new "Edit History" screen aren't part of the generated baseline profile's covered critical user journey before merging; regenerate the baseline profile if they are (the existing Home submit flow likely already is covered, since the hash computation sits inline in that path).

No violations — Complexity Tracking table below documents the one addition (a new screen + 3 columns + 1 migration) and why it's proportionate, not because a gate failed.

## Project Structure

### Documentation (this feature)

```text
specs/004-edit-lineage-history/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory: this feature has no external interface (no new/changed server API) — it's entirely client-side data modeling and UI, so contract docs don't apply.

### Source Code (repository root)

```text
app/
├── src/main/java/dev/zun/flux/
│   ├── data/local/
│   │   ├── JobEntity.kt              # add sourceSha256, resultSha256, lineageRootId (+ indices)
│   │   ├── JobDao.kt                  # add findDoneJobByHash(), updateResultHash(), findByLineageRoot()
│   │   └── AppDatabase.kt             # version 4 → 5, MIGRATION_4_5
│   ├── data/repo/
│   │   ├── JobUploader.kt            # reuse existing sha256Hex(file) as sourceSha256 at submission
│   │   ├── OfflineImageCache.kt      # hook resultSha256 computation right after result file write
│   │   └── RealJobRepository.kt      # lineage lookup/assignment on job creation + on resultSha256 write
│   └── ui/
│       ├── home/HomeViewModel.kt     # picker-time hash check → "edited before" indicator (single + batch)
│       ├── result/ResultScreen.kt    # add "View edit history" to existing overflow menu
│       ├── gallery/PhotoViewerScreen.kt  # add "View edit history" to existing top bar
│       └── history/                  # new: EditHistoryScreen.kt + EditHistoryViewModel.kt
├── src/androidTest/java/dev/zun/flux/data/local/
│   └── AppDatabaseMigrationTest.kt   # extend with 4 → 5 case
└── src/test/java/dev/zun/flux/data/repo/
    └── LineageAssignmentTest.kt      # new: pure hash-matching/assignment logic tests
```

**Structure Decision**: Single existing Android module (`app/`), no new module/flavor. One new screen (`ui/history/`) plus additive changes to existing entities/DAOs/repositories and two existing screens' menus. This matches the "Surgical, Simplicity-First" gate — the only wholly new surface is the history screen itself; everything else is an additive column, query, or menu item on what already exists.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No Constitution gate failures — table intentionally left empty. The one new screen (`ui/history/`) and three new `JobEntity` columns are the minimum needed to deliver all three prioritized user stories with a single mechanism; `research.md`'s Alternatives Considered documents why a heavier graph/edges table and an explicit parent-pointer were both rejected in favor of this simpler design.
