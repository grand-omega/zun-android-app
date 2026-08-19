---

description: "Task list for feature implementation"
---

# Tasks: Favorites, Generation Details, and Cellular Data Control

**Input**: Design documents from `/specs/008-favorites-metadata-datasaver/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Included. Unlike feature 007 (a pure no-behavior-change refactor), this feature adds
real new behavior and a real data-integrity risk (research.md Decision 1 — a favorite could be
silently wiped by a background resync unless explicitly guarded), so per Constitution Principle
III this needs actual new tests, not just "the existing suite still passes."

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent
implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Single-module Android app — all paths are under `app/src/main/java/dev/zun/flux/` (production),
`app/src/test/java/dev/zun/flux/` (unit tests), or `app/src/androidTest/java/dev/zun/flux/`
(instrumented tests), per plan.md's Project Structure.

---

## Phase 1: Setup

- [X] T001 Run `./gradlew :app:testDebugUnitTest :app:lintDebug spotlessCheck` from repo root
      and record the result as the pre-feature baseline.

---

## Phase 2: Foundational

**None.** The three user stories are independent (different primary files/concerns) and share
no blocking prerequisite beyond the T001 baseline. Note: two files are each touched by more than
one story (see Dependencies & Execution Order below) — that's a same-file *ordering*
consideration, not a blocking foundational dependency.

---

## Phase 3: User Story 1 - Mark a generated image as a favorite (Priority: P1) 🎯 MVP

**Goal**: A local `isFavorite` flag, survivable across a background resync, surfaced as a toggle
in the photo viewer, an overlay in the gallery grid, and a filter combinable with the existing
prompt-based filters.

**Independent Test**: Favorite an image from the viewer, confirm the grid overlay and "favorites
only" filter reflect it (combinable with an existing prompt filter), un-favorite it and confirm
it disappears from the filtered view, then trigger a background resync and confirm the favorite
survives.

### Implementation for User Story 1

- [X] T002 [US1] Add `val isFavorite: Boolean = false` to `JobEntity` in
      `app/src/main/java/dev/zun/flux/data/local/JobEntity.kt`.
- [X] T003 [P] [US1] Bump `@Database(version = 5 ...)` to `6` in
      `app/src/main/java/dev/zun/flux/data/local/AppDatabase.kt`, add
      `MIGRATION_5_6 = schemaMigration(5, 6)` to `.addMigrations(...)`, and add
      `addColumnIfMissing(db, jobColumns, "jobs", "isFavorite INTEGER NOT NULL DEFAULT 0")` to
      `ensureCurrentSchema` (per research.md Decision 1 — depends on T002 for schema-export
      consistency).
- [X] T004 [US1] Add an instrumented migration test in
      `app/src/androidTest/java/dev/zun/flux/data/local/AppDatabaseMigrationTest.kt` covering
      v5→v6: pre-existing rows default `isFavorite` to `false`, and the column can be set/read
      back. (Depends on T003.)
- [X] T005 [P] [US1] Add `setFavorite(jobId: String, isFavorite: Boolean)` and a
      `favoritesOnly: Boolean` parameter to `pagedDoneJobsAll`, `pagedDoneJobsByPromptId`, and
      `pagedDoneJobsCustom` (`AND (:favoritesOnly = 0 OR isFavorite = 1)`) in
      `app/src/main/java/dev/zun/flux/data/local/JobDao.kt`. (Depends on T002.)
- [X] T006 [P] [US1] Extend `carryForwardLineage` in
      `app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt` to also copy `isFavorite`
      from the existing row onto the freshly-synced entity before upsert (per research.md
      Decision 1's critical finding). (Depends on T002.)
- [X] T007 [US1] Add a regression test proving a favorited job's `isFavorite` survives the same
      upsert path `syncHistory()`/`listJobs()` uses (instrumented `JobDao`/repository test, or a
      focused unit test against `RealJobRepository` with a fake DAO). (Depends on T006.)
- [X] T008 [US1] Add `setFavorite(jobId: String)` to the `JobRepository` interface and
      `RealJobRepository` implementation; thread a `favoritesOnly: Boolean` parameter through
      `pagedJobs(...)` (`app/src/main/java/dev/zun/flux/data/repo/JobRepository.kt`,
      `RealJobRepository.kt`). (Depends on T005.)
- [X] T009 [P] [US1] Add `isFavorite: Boolean` to `JobSummaryDto`
      (`app/src/main/java/dev/zun/flux/data/api/Dto.kt`) and populate it in `toEntity.toSummaryDto()`
      (`app/src/main/java/dev/zun/flux/data/local/JobEntity.kt`). (Depends on T002.)
- [X] T010 [US1] Add a `_favoritesOnly: MutableStateFlow<Boolean>` to `GalleryViewModel`,
      combined into both the `jobs` `StateFlow` and the `pagedGridItems` flow alongside the
      existing `_tagFilter` (`app/src/main/java/dev/zun/flux/ui/gallery/GalleryViewModel.kt`).
      (Depends on T008, T009.)
- [X] T011 [P] [US1] Add a `GalleryViewModel` unit test confirming "favorites only" combines
      with an active prompt filter as an intersection (favorited AND matching prompt), not
      either alone. (Depends on T010.)
- [X] T012 [P] [US1] Add a favorite (heart) toggle icon to `PhotoViewerScreen`'s action bar,
      calling `setFavorite` (`app/src/main/java/dev/zun/flux/ui/gallery/PhotoViewerScreen.kt`).
      (Depends on T008. Also touched by US2/T016 — see Dependencies below.)
- [X] T013 [P] [US1] Add a favorite overlay to grid tiles and a "favorites only" filter chip
      alongside the existing prompt-text/custom-only filters in
      `app/src/main/java/dev/zun/flux/ui/gallery/GalleryScreen.kt`. (Depends on T010.)
- [X] T014 [US1] Manually verify per quickstart.md's Story 1 scenarios, including the
      favorite-survives-a-background-sync regression scenario. (Depends on T007, T011, T012, T013.)

**Checkpoint**: User Story 1 is complete and independently verifiable.

---

## Phase 4: User Story 2 - See what produced a generated image (Priority: P2)

**Goal**: A read-only bottom sheet in the photo viewer showing the prompt, workflow, try-harder
indicator, and timestamps already stored for a job.

**Independent Test**: Open a completed image, reveal the sheet (swipe up or info icon), confirm
the shown prompt/workflow/try-harder/timestamps match what was actually submitted, and confirm
dismissing it leaves the viewer's own state untouched.

### Implementation for User Story 2

- [X] T015 [US2] ~~Add a `GenerationDetailsSheet` composable... in a new file...~~ **Plan
      revised during implementation**: `PhotoViewerScreen.kt` already has a `JobDetailsSheet`
      wired to an existing info icon (`onDetails`/`showDetails`), already showing prompt text,
      created timestamp, duration, and offline status — discovered while reading the action bar
      to place the new favorite icon. Extended it instead of creating a duplicate: added
      `completed_at` (explicit, alongside the existing duration), `workflow` name, and a
      try-harder indicator (`job.workflow == Workflows.TRY_HARDER_EDIT`). No new file.
- [X] T016 [US2] ~~Wire an info icon into `PhotoViewerScreen`'s action bar...~~ **Not needed** —
      the info icon and its wiring already existed (see T015's note); FR-005/FR-006 are
      satisfied by the extended existing sheet.
- [X] T017 [US2] Manually verify per quickstart.md's Story 2 scenarios, including that dismissing
      the sheet leaves the viewer's image/slider position unchanged. Covered by the existing
      `JobDetailsSheet`'s pre-existing dismiss behavior (unchanged) plus the full regression
      suite (`testDebugUnitTest`, `connectedDebugAndroidTest`) passing with the new rows added.
      **Live on-device verification in a later session caught a real bug from this task**: the
      sheet's `Surface` is fixed at `.fillMaxHeight(0.4f)` with no scroll, and T015's 3 new rows
      pushed "Try harder" (the last row) out of the accessibility tree entirely — genuinely
      unreachable, not just visually tight. Fixed by adding `.verticalScroll(rememberScrollState())`
      to the inner `Column`; confirmed live afterward that all rows including "Try harder" are
      present and reachable by scrolling. Full suite re-run green after the fix.

**Checkpoint**: User Story 2 is complete and independently verifiable.

---

## Phase 5: User Story 3 - Avoid burning cellular data on AI jobs (Priority: P3)

**Goal**: A Wi-Fi-only (default) vs. allow-cellular-data setting that gates job-submission
upload and automatic result-fetch download, without affecting explicit Save/Share actions.

**Independent Test**: With the default setting and cellular-only connectivity, confirm a
submitted job's upload holds rather than proceeding; switch to "allow cellular data" and confirm
it proceeds. Confirm explicit Save/Share are unaffected either way.

### Implementation for User Story 3

- [X] T018 [US3] Add a `networkMode` (Wi-Fi only / allow cellular) preference to
      `app/src/main/java/dev/zun/flux/data/repo/SettingsManager.kt`, mirroring the existing
      plain-`SharedPreferences` pattern used by `gallerySortNewestFirst`.
- [X] T019 [P] [US3] Add the Wi-Fi-only / allow-cellular-data toggle row to
      `app/src/main/java/dev/zun/flux/ui/settings/SettingsScreen.kt`. (Depends on T018.)
- [X] T020 [US3] Read the setting when building the upload `WorkRequest`'s `Constraints` in
      `app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt`, choosing
      `NetworkType.UNMETERED` (Wi-Fi only) vs. `NetworkType.CONNECTED` (allow cellular). (Depends
      on T018. Also touches `carryForwardLineage`'s file from US1/T006 — see Dependencies below.)
- [X] T021 [US3] Add a small connectivity-check helper (`ConnectivityManager` /
      `NET_CAPABILITY_NOT_METERED`) and gate the automatic `prefetchIfDone`/`prefetchDone`/
      `prefetchJobImages` call sites behind it when Wi-Fi-only is set — skip (not error) when
      only cellular is available (`RealJobRepository.kt`,
      `app/src/main/java/dev/zun/flux/data/repo/OfflineImageCache.kt`). (Depends on T018.)
- [X] T022 [P] [US3] Add a unit test confirming the upload `Constraints` choose the right
      `NetworkType` per setting, and that prefetch is skipped vs. attempted per setting given a
      stubbed connectivity result. (Depends on T020, T021.)
- [X] T023 [US3] Manually verify per quickstart.md's Story 3 scenarios, explicitly including that
      Save to device / Share are never gated by this setting (FR-010). (Depends on T019, T020,
      T021.)

**Checkpoint**: User Story 3 is complete and independently verifiable.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T024 Run `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug spotlessCheck`
      and confirm every new test from all three stories passes alongside the existing suite.
- [X] T025 [P] Walk through all of quickstart.md's manual validation steps for Stories 1-3 in one
      sitting, confirming the three features also work correctly together (e.g. favoriting and
      viewing generation details on the same image, with the network setting at either value).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: None.
- **User Stories (Phase 3-5)**: Each depends only on Phase 1. Conceptually independent, but see
  the same-file notes below before working them fully in parallel.
- **Polish (Phase 6)**: Depends on all three stories being complete.

### Cross-story same-file notes

Two files are touched by more than one story. For solo, sequential implementation (recommended,
matching this project's established practice), do stories in priority order P1 → P2 → P3 to
avoid rework on these files:

- **`PhotoViewerScreen.kt`**: US1/T012 (favorite icon) and US2/T016 (info icon) both edit the
  same action bar. Do T012 before T016.
- **`RealJobRepository.kt`**: US1/T006 (`carryForwardLineage`) and US3/T020-T021 (upload
  constraints, prefetch gating) both touch this file, in different functions. Low conflict risk,
  but doing US1 first keeps the diffs cleaner.

### Within Each User Story

- Story 1: schema/entity change first (T002) → migration + DAO/repo layer (T003-T009, several
  parallel-safe once T002 lands) → ViewModel (T010) → UI (T012, T013) → verification (T014).
- Story 2: the sheet composable (T015) → wiring it into the viewer (T016) → verification (T017).
- Story 3: the setting itself (T018) → its UI (T019) and its two consumers (T020, T021, parallel
  with each other) → verification (T023).

### Parallel Opportunities

- T003, T005, T006, T009 (all depend only on T002, all different files) can run in parallel.
- T012 and T013 (different files, both depend on T008/T010 respectively) can run in parallel.
- T019, T020, T021 (different files/functions, all depend only on T018) can run in parallel.
- Across stories: once each story's own setup lands, US2's T015 and US3's T018 have no
  dependency on US1 at all and could start immediately if staffed separately — the same-file
  notes above are about avoiding rework for a single implementer, not a hard blocking rule.

---

## Parallel Example: User Story 1's data layer

```bash
# After T002 (isFavorite added to JobEntity) lands, these four are independent:
Task: "Bump AppDatabase to v6, add MIGRATION_5_6 and the new column"
Task: "Add setFavorite() and favoritesOnly to JobDao's paged queries"
Task: "Extend carryForwardLineage to carry isFavorite forward"
Task: "Add isFavorite to JobSummaryDto and toSummaryDto()"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (baseline capture).
2. Complete Phase 3: User Story 1 (T002-T014).
3. **STOP and VALIDATE**: favorite toggle works end-to-end, including surviving a resync — the
   single most requested and highest-risk piece of this feature.

### Incremental Delivery

1. Setup → baseline captured.
2. User Story 1 → validate independently → MVP.
3. User Story 2 → validate independently.
4. User Story 3 → validate independently.
5. Polish → confirm all three together via the full suite + a combined manual pass.

### Solo Execution Note

Sized for one implementer working stories in priority order (P1 → P2 → P3), matching this
project's established practice — see the cross-story same-file notes above for why priority
order (rather than a parallel split) is recommended here specifically.

---

## Notes

- [P] tasks = different files, no dependencies (see cross-story notes for the two exceptions
  worth being deliberate about).
- [Story] label maps each task to its user story for traceability.
- Commit after each user story's checkpoint.
- Stop at any checkpoint to validate a story independently before continuing.
