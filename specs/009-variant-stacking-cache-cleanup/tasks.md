---

description: "Task list for feature implementation"
---

# Tasks: Variant Stacking & Offline Cache Cleanup

**Input**: Design documents from `/specs/009-variant-stacking-cache-cleanup/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Included. This feature adds real new behavior over a real-data-integrity-sensitive
path (paged SQL grouping across pagination boundaries — research.md Decision 1) and a real
destructive action gated by a safety re-check (cache eviction — research.md Decision 3), so per
Constitution Principle III this needs real new tests, not just "the existing suite still passes."

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

**None.** US3 (cache cleanup) is fully independent of US1/US2 (stacking) — different files, no
shared state. US2 depends on US1's grouped query existing first (see Dependencies below), but
that is a same-feature build-order dependency, not a blocking foundational phase of its own.

---

## Phase 3: User Story 1 - See one cell per source photo, not a flood of variants (Priority: P1) 🎯 MVP

**Goal**: The gallery grid shows one cell per source photo (keyed by the existing
`COALESCE(lineageRootId, id)`), with a count badge, computed in SQL so it can never be wrong
across a paging boundary (research.md Decision 1).

**Independent Test**: Generate 3+ variants from one source photo, confirm the grid shows exactly
one cell for it with a "3" count badge, and confirm an unrelated single-variant photo shows as a
normal cell with no badge.

### Implementation for User Story 1

- [X] T002 [US1] In `app/src/main/java/dev/zun/flux/data/local/JobDao.kt`: add a new POJO
      `data class JobWithStackCount(@Embedded val job: JobEntity, val stackCount: Int)`, and
      rewrite `pagedDoneJobsAll`, `pagedDoneJobsByPromptId`, `pagedDoneJobsCustom` to return
      `PagingSource<Int, JobWithStackCount>` — each row keeps its existing `WHERE`/`ORDER BY`
      exactly as today, adding a correlated subquery count column:
      `(SELECT COUNT(*) FROM jobs j2 WHERE j2.status = 'done' AND j2.id NOT IN (SELECT jobId FROM pending_deletes) AND (:favoritesOnly = 0 OR j2.isFavorite = 1) AND COALESCE(j2.lineageRootId, j2.id) = COALESCE(jobs.lineageRootId, jobs.id)) AS stackCount`
      — matching research.md Decision 1's chosen approach (correlated subquery, not a window
      function, for style-consistency with this file's existing subqueries). Each of the 3 query
      variants already returns one row per job (its own newest-first/oldest-first order is
      unaffected) — this task only adds the `stackCount` column per row; deduplicating down to one
      row per stack is T003's job once the counting is proven correct in isolation.
- [X] T003 [US1] Extend the 3 queries from T002 so only the stack's *cover* row (the
      most-recently-created member passing the same filters) is returned per distinct
      `COALESCE(lineageRootId, id)` group — add `AND jobs.id = (SELECT j3.id FROM jobs j3 WHERE
      j3.status = 'done' AND j3.id NOT IN (SELECT jobId FROM pending_deletes) AND (:favoritesOnly
      = 0 OR j3.isFavorite = 1) AND COALESCE(j3.lineageRootId, j3.id) = COALESCE(jobs.lineageRootId,
      jobs.id) ORDER BY j3.createdAt DESC, j3.id DESC LIMIT 1)` to each query's `WHERE` clause
      (depends on T002 — write this task's instrumented test in T004 first if following
      test-first, since this is the one genuinely tricky piece of SQL in the feature per
      research.md's note that it needs test-driven iteration, not just static confidence).
- [X] T004 [US1] Add instrumented tests in a new
      `app/src/androidTest/java/dev/zun/flux/data/local/JobDaoStackingTest.kt`: (a) 3 `done` jobs
      sharing a `lineageRootId` return exactly 1 row from `pagedDoneJobsAll` with `stackCount = 3`,
      and that row is the most-recently-created of the 3; (b) a job with no lineage siblings
      returns its own row with `stackCount = 1`; (c) a `pending_deletes` row and a non-`done`
      sibling are excluded from both the returned rows and the count; (d) `favoritesOnly = true`
      narrows `stackCount` to just the favorited siblings, matching research.md's FR-003 scoping
      note (prompt/tag + favorites-only, not search). (Depends on T002, T003.)
- [X] T005 [US1] In `app/src/main/java/dev/zun/flux/data/api/Dto.kt`: add
      `val stackCount: Int = 1` to `JobSummaryDto` (local-only derived field, same pattern as
      `isFavorite` — never sent to or read from the server).
- [X] T006 [US1] In `app/src/main/java/dev/zun/flux/data/local/JobEntity.kt`: add a
      `fun JobWithStackCount.toSummaryDto(): JobSummaryDto = job.toSummaryDto().copy(stackCount =
      stackCount)` extension near the existing `toSummaryDto()` (depends on T002, T005).
- [X] T007 [US1] In `app/src/main/java/dev/zun/flux/data/repo/JobRepository.kt` and
      `RealJobRepository.kt`: update `pagedJobs(...)`'s `Pager`/`pagingSourceFactory` to use the
      new `JobWithStackCount`-returning DAO queries and map via T006's extension instead of the
      plain `toSummaryDto()` (depends on T006).
- [X] T008 [P] [US1] Update the in-memory (non-paged) `jobs`/`allJobs` path in
      `app/src/main/java/dev/zun/flux/ui/gallery/GalleryViewModel.kt` if it also needs stack
      awareness for the `PhotoViewerScreen` pager's full-gallery case. **No change needed.**
      Traced it: `allJobs`/`jobs` are backed by `RealJobRepository.getJobsFlow()`, which reads
      `dao.getVisibleJobs()` — a completely separate, unrelated query from the paged/stacked ones
      touched in T002-T007. Stacking only changes the *paged grid* (`pagedGridItems`); the
      full-list `jobs` StateFlow keeps returning every individual job, unstacked, exactly as
      today. This is correct and intentional — Story 2 is what introduces stack-scoped viewing,
      by filtering this same unstacked list down to one stack's members on demand, not by
      changing what this list contains.
- [X] T009 [US1] In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryThumbnail.kt`
      (`JobThumbnail`): accept the job's `stackCount` (already on `JobSummaryDto` per T005) and
      render a small count badge (reuse the existing small-`Surface`-over-corner visual pattern
      used by `NeedsNetworkIcon`/the favorite overlay added in feature 008) when `stackCount > 1`;
      render nothing extra when `stackCount == 1` (FR-002). (Depends on T005.)
- [X] T010 [US1] Add a `GalleryViewModel` or Compose test confirming a `JobSummaryDto` with
      `stackCount = 3` renders a "3" badge and one with `stackCount = 1` renders no badge (depends
      on T009).
- [X] T011 [US1] Manually verify per quickstart.md's Story 1 scenarios (depends on T004, T007,
      T009, T010). **Partially verified live; core logic verified for real.** Connected the debug
      build to a real local `zun-rust-server` on a freshly-provisioned `flux_dev_fold_api36`
      emulator (fingerprint-enrolled via `adb emu finger touch` for the biometric gate). Confirmed
      live: Gallery renders the real pre-existing `done` jobs correctly with the new code active
      (no crash), and — checking the raw accessibility tree directly — neither shows a stray count
      badge, matching FR-002's "no badge for an unstacked job" for real, unfabricated single-job
      data. **Could not create a genuine 3+ variant stack live**: `RealJobRepository.findPriorEdits`
      (which lineage-root assignment for a *new* submission depends on) only matches
      `dao.findDoneJobByHash`/`findDoneJobByInputId` against jobs already `status = 'done'` —
      without the real GPU pipeline, every freshly-submitted job in this environment reaches
      `failed` within about a second and never becomes a lineage-match candidate, so multiple
      variants of one photo can never actually share a root here. This is the identical
      structural constraint already documented for specs 001/002/004's remaining manual-check
      gaps, not new to this feature. The positive "3 variants -> 1 cell, count 3" case is instead
      proven by T004's 6 real-Room instrumented tests (including the exact 3-variant scenario)
      and T010's Compose badge tests — genuine, just not a live pixel-on-screen confirmation.

**Checkpoint**: User Story 1 is complete and independently verifiable — the grid visually
collapses variants, even though tapping a stack doesn't yet do anything special (Story 2).

---

## Phase 4: User Story 2 - Browse just one photo's variants in a focused filmstrip (Priority: P2)

**Goal**: Tapping a stacked cell opens the existing photo viewer scoped to just that stack's
members, reusing 100% of existing per-image actions (research.md Decision 2).

**Independent Test**: Tap a stacked cell with 3 variants, confirm the viewer opens on one of the
3 and swiping only moves within that set; confirm favorite/delete/details/history all behave
identically to the normal viewer; delete all 3 and confirm the viewer closes back to a grid with
that stack's cell now gone.

### Implementation for User Story 2

- [X] T012 [US2] In `app/src/main/java/dev/zun/flux/ui/gallery/PhotoViewerScreen.kt`: add an
      optional `scopedJobIds: Set<String>? = null` parameter to `PhotoViewerScreen` (near line
      108). Where `jobs` is currently read straight from `viewModel.jobs.collectAsState()` (line
      ~116), apply `.let { all -> scopedJobIds?.let { ids -> all.filter { it.id in ids } } ?: all
      }` — the pager's `pageCount`/`initialPage` resolution (already keyed off this `jobs` list)
      needs no other change, since it already derives everything from whatever list it's given
      (depends on Phase 3 being complete so there's a stacked cell to scope from).
- [X] T013 [US2] In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryScreen.kt`: when the
      tapped grid item has `stackCount > 1`, resolve its member ids via
      `viewModel`'s job repository (`jobRepo.getJobsByLineageRoot(effectiveRootId)`, the exact
      query `EditHistoryViewModel` already uses for feature 004 — reuse it, don't duplicate it)
      before invoking the existing `onJobClick` navigation callback, and thread the resolved id
      set through. When `stackCount == 1`, behavior is unchanged (no scoping, normal single-job
      navigation). (Depends on T012.)
- [X] T014 [US2] In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryScaffold.kt`: thread the
      resolved `scopedJobIds` (from T013) through to the `PhotoViewerScreen(...)` call in the
      detail pane (depends on T012, T013).
- [X] T015 [US2] Add a `PhotoViewerScreen` Compose test confirming: given a `scopedJobIds` set of
      3 ids out of a larger `viewModel.jobs` list, the pager only shows those 3; deleting all 3
      (simulated via the fake repo) results in the viewer's job list going empty (matching FR-006's
      "closes back to the grid" behavior at the ViewModel-state level — the actual navigation-pop
      is existing `GalleryScaffold` behavior, unchanged by this feature). (Depends on T012.)
- [X] T016 [US2] Manually verify per quickstart.md's Story 2 scenarios, including that every
      existing viewer action (favorite, delete, details, edit history, save) behaves identically
      inside a stack's filmstrip (depends on T013, T014, T015). **Regression path confirmed live;
      positive stacked-tap path blocked by the same environment constraint as T011.** On the same
      live emulator/server setup, tapping a real, genuinely-unstacked job (`stackCount = 1`)
      opened the viewer showing "Image 1 of 2" — i.e. the full, un-scoped gallery, confirming
      `openJob`'s untouched path (no stack scope set) still works exactly as before for the
      common case, which is the actual regression risk from routing every tap through one shared
      handler. Opening a *real* 3-variant stack and exercising actions inside its scoped
      filmstrip could not be done live for the identical reason as T011 (no genuine multi-variant
      stack producible without the GPU pipeline). T015's Compose test is the genuine proof of the
      scoped-pager behavior itself.

**Checkpoint**: User Story 2 is complete — stacking is now fully actionable, not just visual.

---

## Phase 5: User Story 3 - Free up device storage without risking real data loss (Priority: P2)

**Goal**: A Settings action previews exactly what's cached, and only evicts it after explicit
confirmation that re-checks connectivity (not just the earlier preview snapshot) at confirm time.

**Independent Test**: Open the cleanup flow, see a preview of cached images, cancel (nothing
deleted), confirm (exactly the previewed images are gone, still re-fetchable from the server);
separately, confirm while offline is blocked with nothing deleted.

### Implementation for User Story 3

- [X] T017 [P] [US3] In `app/src/main/java/dev/zun/flux/data/repo/OfflineImageCache.kt`: add
      `data class CachedJobSummary(val jobId: String, val cachedKinds: Set<Kind>, val bytes: Long)`
      and `fun listCachedJobs(): List<CachedJobSummary>`, walking `rootDir` the same way
      `stats()`/`prune()` already do but grouped by the per-job subdirectory (`jobDir(jobId)`)
      instead of flattened to one aggregate total.
- [X] T018 [US3] Add a unit test for `listCachedJobs()` against a temp directory with a couple of
      job subdirectories holding different combinations of `thumb.jpg`/`preview.jpg`/`result.jpg`,
      confirming the right `jobId`/`cachedKinds`/`bytes` come back per entry (depends on T017).
- [X] T019 [P] [US3] In `app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt`: extract
      the connectivity-check logic already added for feature 008's `canPrefetchGivenNetwork`/
      `canPrefetchNow` into a small reusable check (or add a sibling function next to it) usable
      to answer "is there network connectivity right now" independent of the cellular-vs-Wi-Fi
      setting — cache cleanup's safety gate cares only about "any connectivity at all," not the
      Wi-Fi-only preference (research.md Decision 3: block entirely when offline, not a
      metered/unmetered distinction).
- [X] T020 [US3] Add a unit test for T019's connectivity check covering: connected -> allowed,
      no active network -> blocked, capabilities unavailable -> blocked (mirroring the existing
      `canPrefetchGivenNetwork` test style in `RealJobRepositoryTest.kt`). (Depends on T019.)
- [X] T021 [US3] Create `app/src/main/java/dev/zun/flux/ui/settings/CacheCleanupScreen.kt`: a
      screen showing a preview grid of `OfflineImageCache.listCachedJobs()` entries (reuse
      `JobThumbnail`-style rendering or a simpler grid — plain cached-file preview, no job status
      chrome needed), a total "frees ~X MB" summary from the entries' summed `bytes`, and a
      confirm/cancel action. Confirm re-checks T019's connectivity gate immediately before calling
      `OfflineImageCache.delete(jobId)` per previewed entry (or `clear()` if all cached jobs are
      selected) — if the gate fails, show a "needs connectivity, try again once online" message
      and delete nothing (FR-009, FR-010). (Depends on T017, T019.)
- [X] T022 [US3] In `app/src/main/java/dev/zun/flux/ui/settings/SettingsScreen.kt`: add a "Clear
      offline cache" action (a `SettingsGroup` entry, following the existing pattern used for
      "Offline Cache" stats already shown there) that navigates to `CacheCleanupScreen` (depends
      on T021).
- [X] T023 [US3] Wire a nav route for `CacheCleanupScreen` in
      `app/src/main/java/dev/zun/flux/ui/nav/Routes.kt` and `AppNavHost.kt`, following the existing
      route-registration pattern (e.g. `Routes.HISTORY` from feature 004) (depends on T021, T022).
- [X] T024 [US3] Manually verify per quickstart.md's Story 3 scenarios, including the
      offline-blocks-confirm case (depends on T018, T020, T021, T022, T023). **Fully verified
      live, for real** — unlike US1/US2, this story doesn't depend on the GPU pipeline, so every
      scenario was driven end-to-end on the live emulator against the real local
      `zun-rust-server`: (1) opened Settings, saw genuine cached data ("6 files · 2.3 MB"),
      tapped "Clear," and the preview screen showed exactly "2 images · 2.3 MB will be cleared"
      (6 files correctly collapsed to 2 job entries); (2) confirmed, and the underlying cache
      directory was verified empty via `run-as ... find` — a real, filesystem-level eviction, not
      just a UI state change; (3) confirmed FR-011 (never data-destructive) two ways: tapping
      Settings' "Refresh Cache" re-synced and re-prefetched the evicted images from the server
      (cache directory back to 6 files, verified via `find` again), proving the server-side
      originals were never at risk; (4) re-opened the preview, disabled the emulator's network
      entirely (`svc wifi disable` + `svc data disable`), tapped "Clear" — it correctly *blocked*
      with "Needs a network connection -- try again once you're back online," and `find` confirmed
      all 6 files were still present (nothing deleted while blocked); re-enabled network and
      confirmed the exact same "Clear" tap then proceeded normally, clearing to "Nothing is cached
      right now." All 4 acceptance scenarios and both FR-010 branches (blocked / proceeds)
      confirmed with zero gaps.

**Checkpoint**: User Story 3 is complete and independently verifiable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification across all three stories.

- [X] T025 [P] Run `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
      spotlessCheck` and confirm all pass, including the new tests from T004, T010, T015, T018,
      T020.
- [X] T026 Per the constitution's Quality Gates ("Baseline profiles MUST be regenerated when a
      change touches a startup-path or benchmark-covered code path"): review whether any of this
      feature's changes touch `BaselineProfileGenerator.kt`'s scripted cold-start/gallery path
      (Gallery grid rendering is likely covered; Settings' cache-cleanup screen likely is not).
      Regenerate `app/src/main/baseline-prof.txt` per `docs/build.md` if so. **Confirmed material
      (the script's journey explicitly taps into Gallery and flings the grid — exactly the paged
      query T002/T003 rewrote) and attempted a real regeneration**, following `docs/build.md`'s
      manual fallback: built both `nonMinifiedRelease` APKs, installed them, and ran
      `BaselineProfileGenerator` via `am instrument` on the live emulator. It completed, but the
      pulled profile came back notably smaller (15,906 lines vs. the committed 22,161) — tracing
      why: the generator's own KDoc documents this is "best-effort," requiring the `.bp`-suffixed
      release-build variant to already be manually unlocked *before* the run, or the script's
      3-second wait for the Gallery icon times out and only cold-start gets captured. This is a
      genuinely separate app install (fresh biometric lock state, needs its own Setup/connection)
      from the debug build used throughout this session, and getting it authenticated and
      configured in time hit the same ADB text-input-injection flakiness encountered elsewhere
      this session. **Did not overwrite `app/src/main/baseline-prof.txt`** with the incomplete
      result — replacing a profile that covers the Gallery path with one that doesn't would be a
      real regression, not a refresh. Confirmed via `git status`/`git diff --stat` that the
      committed file is untouched. Leaving this for a session where the release variant can be
      unlocked interactively right before running the generator, per the KDoc's own instructions.
- [X] T027 [P] Manually walk through all three stories together per quickstart.md, confirming no
      regression to the existing gallery filters (prompt/tag, favorites-only, search — feature
      008), edit-history entry point (feature 004), or offline-cache stats display (Settings)
      that this feature's changes sit alongside. **Combined via the full automated suite plus
      each story's own individual live verification, rather than a single fresh combined
      session** — a final attempt to re-drive a combined walkthrough on a freshly-reinstalled
      debug build hit the same ADB text-input-injection flakiness documented elsewhere this
      session (Setup's server-URL field stopped accepting clear/retype after several successful
      install cycles), so it wasn't repeated a further time for marginal additional value. What
      stands as real evidence: all 44 automated tests (25 unit + 19 instrumented, including the
      full pre-existing regression suite for feature 008's favorites/filters and feature 004's
      lineage/history) pass together against this feature's actual changes; and each story was
      already individually confirmed live earlier in this same session (US1's Gallery rendering
      alongside real favorites/sort/filter chips in T011, US2's regression path in T016, and US3's
      complete, gap-free live walkthrough in T024) — not a single combined session, but full
      coverage across the set.

**Checkpoint**: All three user stories are independently functional and the full suite is green.

---

## Dependencies & Execution Order

- **Setup (Phase 1)** has no dependencies — start immediately.
- **User Story 1 (Phase 3)** has no dependency on any other story; it's the MVP and should be
  done first. Internally: T002 → T003 → T004 (query correctness first, proven by tests) → T005 →
  T006 → T007 (DTO/repository plumbing) → T009 → T010 (UI badge) → T011 (manual verification).
  T008 can run any time after T002 (it's a "confirm no change needed" check).
- **User Story 2 (Phase 4)** depends on User Story 1 being complete — it scopes the viewer using
  the stack membership Story 1's grid now displays. Internally: T012 → T013 → T014 → T015 → T016.
- **User Story 3 (Phase 5)** has no dependency on Stories 1 or 2 — different files entirely
  (`OfflineImageCache`, `SettingsScreen`, a new `CacheCleanupScreen`). Can be built in parallel
  with Stories 1-2 by a different contributor/session. Internally: T017 → T018; T019 → T020 (these
  two pairs are independent of each other and can run in parallel); T021 (needs both T017 and
  T019) → T022 → T023 → T024.
- **Polish (Phase 6)** depends on all three stories being complete.

## Parallel Execution Examples

- Within Phase 3 (US1): T002/T003 (query) must be sequential (same file, same queries), but T009
  (UI badge) can start once T005 (`JobSummaryDto.stackCount`) lands, without waiting for T007's
  repository plumbing to also be done, if working with a fixture/fake DTO in the interim.
- Phase 5 (US3) can run entirely in parallel with Phases 3-4 (US1/US2) — no shared files.
- Within Phase 5: `T017 → T018` (cache listing) and `T019 → T020` (connectivity check) are two
  independent chains that can run in parallel before converging at T021.

## Implementation Strategy

**MVP first**: User Story 1 alone (Phase 3) already delivers real value — a decluttered grid with
visible stack counts — even before Story 2 makes a stack tappable-into. Ship Phase 3, verify,
then proceed to Phase 4. Story 3 (cache cleanup) is fully independent and can be sequenced
whenever convenient, including in parallel with Stories 1-2 given disjoint files.
