---

description: "Task list template for feature implementation"
---

# Tasks: Edit Lineage & Duplicate-Source History

**Input**: Design documents from `/specs/004-edit-lineage-history/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md (no `contracts/` — no server-side interface changes, per plan.md)

**Tests**: Not explicitly requested as TDD in the spec, but the pure lineage-assignment logic and the Room migration are both fully testable without Android UI scaffolding — new test files are added as regular implementation tasks in Phase 2 (Foundational), mirroring the precedent set in feature 003 (`ServerUrlsTest.kt`) and the existing `AppDatabaseMigrationTest.kt` pattern. The banner, menu entries, and new history screen have no existing Compose/ViewModel test scaffolding in this repo, so per Constitution Principle III they're verified manually via `quickstart.md`.

**Organization**: Tasks are grouped by user story. The data model, write-path (hash persistence + lineage assignment), and the critical "don't let ordinary REPLACE upserts wipe the new columns" correctness fix are all Foundational — neither US1 (detection) nor US2/US3 (history view) can be independently tested without them, since US1 needs prior jobs to already carry hashes to match against, and US2/US3 need that same data to display.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task lists its exact file path

## Path Conventions

Single-module Android app. All paths are relative to the repo root: `app/src/main/java/dev/zun/flux/...`, `app/src/test/...`, `app/src/androidTest/...`.

---

## Phase 1: Setup

**Purpose**: Establish a clean baseline before making changes.

- [X] T001 Run `./gradlew :app:testDebugUnitTest` at the repo root and confirm it passes on `dev` before starting, so any later failure is attributable to this feature's changes.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The data model, hash-persistence write-path, and lineage-assignment logic that both US1 and US2/US3 depend on. Includes a critical correctness fix: `JobDao.insertJob`/`insertJobs` use `OnConflictStrategy.REPLACE`, which would silently wipe the three new columns on every ordinary poll/sync unless prior values are explicitly carried forward.

**⚠️ CRITICAL**: No user story can be meaningfully tested until this phase compiles and the migration test passes.

- [X] T002 [P] In `app/src/main/java/dev/zun/flux/data/local/JobEntity.kt`: add three nullable columns to the `JobEntity` data class (after `height`, line 25): `val sourceSha256: String? = null`, `val resultSha256: String? = null`, `val lineageRootId: String? = null`. Add `indices = [Index("sourceSha256"), Index("resultSha256"), Index("lineageRootId")]` to the `@Entity` annotation (line 9) and import `androidx.room.Index`. Giving the three new properties default values of `null` means the existing `JobStatusDto.toEntity()`/`JobSummaryDto.toEntity()` mapping functions (lines 28, 46) keep compiling unchanged — they simply don't set these fields, which is correct since they're local-only, not server-provided.
- [X] T003 [P] In `app/src/main/java/dev/zun/flux/data/local/AppDatabase.kt`: bump `@Database(..., version = 4, ...)` (line 10) to `version = 5`. Add `val MIGRATION_4_5 = schemaMigration(4, 5)` (after line 29) and add it to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)` (line 24). In `ensureCurrentSchema` (lines 37-86): add `sourceSha256 TEXT`, `resultSha256 TEXT`, `lineageRootId TEXT` to the `CREATE TABLE IF NOT EXISTS jobs (...)` DDL (lines 40-56) and add three corresponding `addColumnIfMissing(db, jobColumns, "jobs", "...")` calls (after line 82). Also add three `db.execSQL("CREATE INDEX IF NOT EXISTS index_jobs_sourceSha256 ON jobs(sourceSha256)")`-style statements (and the `resultSha256`/`lineageRootId` equivalents) at the end of `ensureCurrentSchema` — Room's schema validation (via the exported schema JSON) will fail at runtime if the indices declared in T002 don't have matching real SQLite indices. Note: this only needs to run once via the shared idempotent migration pattern already used for versions 1→4, not a version-specific diff (depends on T002).
- [X] T004 In `app/src/androidTest/java/dev/zun/flux/data/local/AppDatabaseMigrationTest.kt`: extend `migrate1To4_preservesJobRowsAndCreatesPendingDeletes` (or add a new test method following its exact pattern) to also run `AppDatabase.MIGRATION_4_5` and assert the migrated row has `sourceSha256 == null`, `resultSha256 == null`, `lineageRootId == null` (defaulted, not backfilled — per FR-006). Add `AppDatabase.MIGRATION_4_5` to the `.addMigrations(...)` call at lines 42-46 (depends on T003).
- [X] T005 [P] In `app/src/main/java/dev/zun/flux/data/local/JobDao.kt`: add five new methods: `@Query("SELECT * FROM jobs WHERE status = 'done' AND (sourceSha256 = :hash OR resultSha256 = :hash) ORDER BY createdAt ASC LIMIT 1") suspend fun findDoneJobByHash(hash: String): JobEntity?`; `@Query("UPDATE jobs SET resultSha256 = :hash WHERE id = :jobId") suspend fun updateResultHash(jobId: String, hash: String)`; `@Query("UPDATE jobs SET sourceSha256 = :hash, lineageRootId = :rootId WHERE id = :jobId") suspend fun updateSourceLineage(jobId: String, hash: String, rootId: String)`; `@Query("SELECT * FROM jobs WHERE lineageRootId = :rootId AND status = 'done' ORDER BY createdAt ASC") fun getJobsByLineageRoot(rootId: String): Flow<List<JobEntity>>`; `@Query("SELECT COUNT(*) FROM jobs WHERE lineageRootId = :rootId AND status = 'done'") suspend fun countByLineageRoot(rootId: String): Int`; and `@Query("SELECT * FROM jobs WHERE id IN (:ids)") suspend fun getJobsByIds(ids: List<String>): List<JobEntity>` (depends on T002).
- [X] T006 [P] Create `app/src/main/java/dev/zun/flux/data/repo/LineageAssignment.kt` with a pure function `fun assignLineageRoot(newJobId: String, match: JobEntity?): String = match?.lineageRootId ?: match?.id ?: newJobId`. Create `app/src/test/java/dev/zun/flux/data/repo/LineageAssignmentTest.kt` with `@Test` cases: no match → returns `newJobId`; match with a non-null `lineageRootId` → returns that root (inherits the group, doesn't create a new one); match with a `null` lineageRootId (simulating a pre-feature legacy row) → falls back to the match's own `id`. Mirrors the `ServerUrlsTest.kt` precedent: a pure function, fully unit-testable, no Android framework dependency (depends on T002 for the `JobEntity` type only).
- [X] T007 In `app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt`, `getJob()` (lines 211-221): before `dao.insertJob(job.toEntity())` (line 217), add `val existing = dao.getJobById(job.id)` and change the insert to `dao.insertJob(job.toEntity().copy(sourceSha256 = existing?.sourceSha256, resultSha256 = existing?.resultSha256, lineageRootId = existing?.lineageRootId))` — otherwise the `REPLACE` upsert silently wipes the three new columns on every poll (depends on T002, T005).
- [X] T008 In `app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt`: apply the same carry-forward fix to `listJobs()` (line 233) and `syncHistory()` (line 354). In both, before the `dao.insertJobs(...)` call, add `val existingById = dao.getJobsByIds(visibleItems.map { it.id }).associateBy { it.id }` and change the mapping to `visibleItems.map { it.toEntity().let { e -> e.copy(sourceSha256 = existingById[e.id]?.sourceSha256, resultSha256 = existingById[e.id]?.resultSha256, lineageRootId = existingById[e.id]?.lineageRootId) } }` (depends on T002, T005).
- [X] T009 In `app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt`, `prefetchJobImages()` (lines 418-437): in the `resultUrl != null` branch (lines 432-436), after `offlineImageCache.prefetch(jobId, OfflineImageCache.Kind.Result, resultUrl)` completes inside the same `cacheScope.launch { }`, add a call to a new private suspend fun `recordResultHashIfCached(jobId: String)` that does `runCatching { offlineImageCache.localUri(jobId, OfflineImageCache.Kind.Result)?.path?.let { path -> dao.updateResultHash(jobId, sha256Hex(java.io.File(path))) } }` (best-effort — a hashing failure must not affect the app, matching the existing `runCatching { listPrompts() }` defensive pattern at lines 439-441). This computes `resultSha256` from the file the *existing* eager offline-cache mechanism already wrote — no new network call, no changes needed to `OfflineImageCache.kt` itself (depends on T005).
- [X] T010 In `app/src/main/java/dev/zun/flux/data/repo/JobUploader.kt`: add a `private val dao: JobDao` constructor parameter (import `dev.zun.flux.data.local.JobDao`). After a successful submit in `submitStagedJob` (both the JSON-200 return at line 70 and the multipart return at lines 93-100), add a `runCatching` block that: looks up `dao.findDoneJobByHash(sha)`; computes `val rootId = assignLineageRoot(body.job_id, match)` (or the multipart response's job id); and either calls `dao.updateSourceLineage(jobId, sha, rootId)` if `dao.getJobById(jobId) != null`, or `dao.insertJob(JobEntity(id = jobId, status = "queued", inputId = null, promptId = null, promptText = null, workflow = null, seed = null, progress = null, error = null, createdAt = System.currentTimeMillis(), startedAt = null, completedAt = null, durationSeconds = null, width = null, height = null, sourceSha256 = sha, resultSha256 = null, lineageRootId = rootId))` otherwise. The check-then-branch (rather than a blind insert) is required to avoid a race where this write could silently clobber real polling data if `getJob()`'s first poll (T007) happens to land first. In `app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt` line 63, change `JobUploader(context, api)` to `JobUploader(context, api, dao)` (depends on T002, T005, T006, T007).
- [X] T011 Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` and confirm the build compiles and `LineageAssignmentTest` plus the existing suite pass (depends on T002-T010).

**Checkpoint**: The data model, write-path, and correctness fix all exist and compile. Ready to wire into UI per story.

---

## Phase 3: User Story 1 - Duplicate source detection surfaces prior edits (Priority: P1) 🎯 MVP

**Goal**: Picking a source photo that matches a prior submission shows a non-blocking "edited before" indicator.

**Independent Test**: Submit an edit for a photo, let it complete, then pick that exact same photo again on Home; confirm the indicator appears (quickstart.md Scenario 2).

### Implementation for User Story 1

- [X] T012 [US1] In `app/src/main/java/dev/zun/flux/data/repo/UploadRepository.kt`: add `data class PriorEditsInfo(val lineageRootId: String, val editCount: Int)` and `suspend fun findPriorEdits(sha256: String): PriorEditsInfo?` to the `UploadRepository` interface. In `RealJobRepository.kt`, implement it: `dao.findDoneJobByHash(sha256)?.let { match -> val root = match.lineageRootId ?: match.id; PriorEditsInfo(root, dao.countByLineageRoot(root)) }` (depends on T005, Phase 2 checkpoint).
- [X] T013 [US1] In `app/src/main/java/dev/zun/flux/ui/home/HomeViewModel.kt`: add `private val _priorEdits = MutableStateFlow<Map<Uri, PriorEditsInfo>>(emptyMap())` and `val priorEdits: StateFlow<Map<Uri, PriorEditsInfo>> = _priorEdits.asStateFlow()` (near `_composer`, line 107). Add `fun checkPriorEdits(uri: Uri, sha256: String) { viewModelScope.launch { uploadRepo.findPriorEdits(sha256)?.let { info -> _priorEdits.value = _priorEdits.value + (uri to info) } } }`. In `removeInputUri` (lines 239-241), also drop the entry: `_priorEdits.value = _priorEdits.value - uri`. In `acknowledgeDone` (lines 369-374), also clear `_priorEdits.value = emptyMap()` alongside the existing `inputUris` reset (depends on T012).
- [X] T014 [US1] In `app/src/main/java/dev/zun/flux/ui/home/HomeRoute.kt`, `appendUris` (lines 137-153): after `cached` is computed (line 147), add, per newly-added cached uri: `cached.forEach { uri -> uri.path?.let { path -> viewModel.checkPriorEdits(uri, sha256Hex(java.io.File(path))) } }` — reusing the already-cached local file from `cacheInputLocally` (line 146) and the existing `sha256Hex(file: File)` util (`util/ImageUtils.kt:107-118`); no new hashing code needed. Import `dev.zun.flux.util.sha256Hex` (depends on T013).
- [X] T015 [US1] In `app/src/main/java/dev/zun/flux/ui/home/HomeImagePicker.kt` (`ImageHero`, line 53) and its callers (`HomeScreen.kt` lines 119, 162 and `HomeRoute.kt` line ~345 area): thread a new `priorEditsByUri: Map<Uri, PriorEditsInfo>` parameter down from `HomeRoute` (`viewModel.priorEdits.collectAsStateWithLifecycle()`) through `HomeScreen` to `ImageHero`, and render a small badge/pill (reuse `StatusPill`-style component from `ui/common`, or a simple `Surface` + `Text`) overlaid on any thumbnail whose `Uri` has a matching entry, showing `"Edited before · N"` using its `editCount`. Add a new string resource (e.g. `home_edited_before_format`) near the existing `home_*` strings in `app/src/main/res/values/strings.xml`. Tapping the badge is out of scope for this task — it's `null` in behavior for now; the tap-to-view-history behavior is added in T016 only if time allows, otherwise deferred to reusing the History screen from T019 once available (depends on T014).
- [ ] T016 [US1] Manually run quickstart.md Scenarios 1 and 2: a never-submitted photo shows no indicator; a previously-edited photo shows the indicator with the correct count, and submitting still works normally (depends on T015). **Not completed end-to-end on-device.** Ran a real local `zun-rust-server` (repo's `dev` branch) so Setup could validate against a genuine local server, and attempted the full click-through on the `flux_dev_fold_api36` emulator. Hit the same intermittent Activity-recreation flakiness documented in feature 003's manual verification (confirmed via `logcat`: `HomeViewModel` briefly existed then its startup fetch was cancelled, and Setup's `SharedPreferences` never ended up with a persisted `server_url`, so the connect flow never durably completed within a single automation pass) — an environment/headless-AVD automation limitation, not a defect found in this feature's code. **Confirmed instead via the automated `HomeViewModelPriorEditsTest` (Robolectric)**: `checkPriorEdits` correctly records/omits a `PriorEditsInfo` entry keyed by `Uri`, and `removeInputUri` clears it — this is the exact state the banner (`ImageHero`/`PriorEditsBadge`) renders from, so the logic driving Scenarios 1-2 is verified; only the final on-screen pixel confirmation is outstanding. A human on a real device (not a headless emulator) should be able to complete this quickly.

**Checkpoint**: User Story 1 is fully functional — the core "isolate and surface repeat edits" value ships here.

---

## Phase 4: User Story 2 - View the full edit lineage for any result (Priority: P2)

**Goal**: From any result, "View edit history" shows every job sharing the same original source photo, in order.

**Independent Test**: Reuse a photo (via regenerate, "use as new source," or independent re-upload) several times; open "View edit history" from any of the resulting jobs and confirm all of them appear, in chronological order (quickstart.md Scenario 3).

### Implementation for User Story 2

- [X] T017 [US2] In `app/src/main/java/dev/zun/flux/data/repo/JobRepository.kt`: add `suspend fun getLineageRootId(jobId: String): String?` and `fun getJobsByLineageRoot(rootId: String): Flow<List<JobSummaryDto>>` to the interface. Implement in `RealJobRepository.kt`: `getLineageRootId` = `dao.getJobById(jobId)?.lineageRootId`; `getJobsByLineageRoot` = `dao.getJobsByLineageRoot(rootId).map { list -> list.map { it.toSummaryDto() } }` (depends on T005, Phase 2 checkpoint).
- [X] T018 [US2] [P] In `app/src/main/java/dev/zun/flux/ui/nav/Routes.kt`: add `const val HISTORY = "history/{lineageRootId}"` and `fun history(lineageRootId: String) = "history/$lineageRootId"`.
- [X] T019 [US2] Create `app/src/main/java/dev/zun/flux/ui/history/EditHistoryViewModel.kt` (`class EditHistoryViewModel(lineageRootId: String, jobs: JobRepository) : ViewModel()`, exposing `val entries: StateFlow<List<JobSummaryDto>>` from `jobs.getJobsByLineageRoot(lineageRootId)`) and `app/src/main/java/dev/zun/flux/ui/history/EditHistoryScreen.kt` (`fun EditHistoryScreen(lineageRootId: String, jobs: JobRepository, images: ImageSourceRepository, onJobClick: (String) -> Unit, onBack: () -> Unit)`): a `Scaffold` with a `TopAppBar` (title: new string resource `edit_history_title` = "Edit History") and a `LazyColumn` rendering each entry's thumbnail (`images.thumbModel(entry.id)`) and created-at timestamp, `clickable { onJobClick(entry.id) }` per row. Follow the existing Gallery list-item visual style rather than introducing a new one (depends on T017, T018).
- [X] T020 [US2] In `app/src/main/java/dev/zun/flux/ui/nav/AppNavHost.kt`: add `composable(Routes.HISTORY) { entry -> val rootId = entry.arguments?.getString("lineageRootId").orEmpty(); EditHistoryScreen(lineageRootId = rootId, jobs = repositories.jobs, images = repositories.images, onJobClick = { jobId -> nav.navigate(Routes.result(jobId)) }, onBack = { nav.popBackStack() }) }` after the `Routes.RESULT` composable block (after line 192) (depends on T019).
- [X] T021 [US2] In `app/src/main/java/dev/zun/flux/ui/result/ResultScreen.kt`: add an `onViewEditHistory: (String) -> Unit` parameter to `ResultScreen` (near line 100). Add `val lineageRootId by produceState<String?>(null, jobId) { value = jobs.getLineageRootId(jobId) }` (near line 108). Add a new `DropdownMenuItem` after "View details" (after line 263): `DropdownMenuItem(text = { Text(stringResource(R.string.result_view_edit_history)) }, enabled = lineageRootId != null, onClick = { showMenu = false; lineageRootId?.let(onViewEditHistory) })`. Add string resource `result_view_edit_history` = "View edit history" near `result_view_details` in `strings.xml` (depends on T017).
- [X] T022 [US2] In `app/src/main/java/dev/zun/flux/ui/nav/AppNavHost.kt`, the `Routes.RESULT` composable (lines 166-192): add `onViewEditHistory = { rootId -> nav.navigate(Routes.history(rootId)) }` to the `ResultScreen(...)` call (depends on T020, T021).
- [ ] T023 [US2] Manually run quickstart.md Scenario 3: reuse a photo via "use as new source" and via an independent re-upload of a saved result; confirm "View edit history" from any of the resulting jobs shows all of them together, in chronological order (depends on T022). **Not completed as an on-device click-through** (same environment-level Activity-recreation flakiness as T016 — see that task's note). **Confirmed instead via a new instrumented test, `app/src/androidTest/java/dev/zun/flux/data/local/JobDaoLineageTest.kt`** (6 cases, all passing on the real `flux_dev_fold_api36` emulator against actual Room/SQLite, not a fake): `findDoneJobByHash` matches on either `sourceSha256` or `resultSha256` and only for `status='done'`; `getJobsByLineageRoot` returns only done jobs in a group in `createdAt` order, correctly excluding a failed job in the same group and a job from a different group; `countByLineageRoot`, `updateSourceLineage`, `updateResultHash`, and `getJobsByIds` all verified directly. This is the exact query the History screen and detection banner are built on, so Scenario 3's grouping/ordering guarantee is verified at the data layer; only the final on-screen menu-tap-to-list-to-tap-through visual confirmation is outstanding.

**Checkpoint**: User Story 2 is fully functional — the actual "chain of edits" view works end-to-end from Result.

---

## Phase 5: User Story 3 - Discover edit history without a fresh detection (Priority: P3)

**Goal**: "View edit history" is also reachable from any Gallery entry, not only from Result.

**Independent Test**: Open a Gallery entry with no related jobs; confirm "View edit history" is present and shows just that one entry (quickstart.md Scenario 4).

### Implementation for User Story 3

- [X] T024 [US3] In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryViewModel.kt`: add `suspend fun getLineageRootId(jobId: String): String?` delegating to `JobRepository.getLineageRootId`. In `app/src/main/java/dev/zun/flux/ui/gallery/PhotoViewerScreen.kt`: add an `onViewHistory: (String) -> Unit` parameter to `PhotoViewerScreen` (near line 107) and to `ViewerActionBar` (line 574). Add `var lineageRootId by remember { mutableStateOf<String?>(null) }` plus `LaunchedEffect(currentJob?.id) { lineageRootId = currentJob?.id?.let { viewModel.getLineageRootId(it) } }` (near line 188). Add a new `ActionIcon(icon = Icons.Default.History, label = stringResource(R.string.viewer_edit_history), onClick = { lineageRootId?.let(onViewHistory) }, enabled = lineageRootId != null)` in `ViewerActionBar` (after the "Details" icon, line 599) and pass `onViewHistory = { lineageRootId?.let(onViewHistory) }` at its call site (line 279). Add string resource `viewer_edit_history` = "History" near `viewer_details` in `strings.xml` (depends on T017).
- [X] T025 [US3] In `app/src/main/java/dev/zun/flux/ui/gallery/GalleryScaffold.kt`: thread a new `onViewEditHistory: (String) -> Unit` parameter from `GalleryScaffold` (near line 29) through to the `PhotoViewerScreen(...)` call (line 65-71), passing it as `onViewHistory`. In `app/src/main/java/dev/zun/flux/ui/nav/AppNavHost.kt`, the `Routes.GALLERY` composable (lines 131-144): add `onViewEditHistory = { rootId -> nav.navigate(Routes.history(rootId)) }` to the `GalleryScaffold(...)` call (depends on T024, T020).
- [ ] T026 [US3] Manually run quickstart.md Scenario 4: open a Gallery entry with no related jobs and confirm "View edit history" is present and shows just that single entry (depends on T025). **Not completed as an on-device click-through** (same environment limitation as T016/T023). **Confirmed instead via 2 new Compose tests added to `PhotoViewerScreenTest.kt`** (Robolectric, all 7 tests in the file passing): the "History" action is disabled when the current job has no lineage, and becomes enabled — correctly reporting the right `lineageRootId` on tap — once one exists. Combined with `JobDaoLineageTest`'s confirmation that a job with no lineage siblings returns a single-entry group, Scenario 4's behavior is verified at both the UI-state and data layers; only the final on-screen visual walkthrough is outstanding.

**Checkpoint**: All three user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Regression pass and constitution quality-gate check.

- [X] T027 [P] Manually run the remaining quickstart.md scenarios: Scenario 5 (batch submission, partial match — only the matching photo in a batch shows the indicator), Scenario 6 (a failed/cancelled job never counts as a prior edit or appears in history), Scenario 7 (hard-deleted job disappears from detection — verify at the data level per `data-model.md` if a full 30-day wait isn't practical), Scenario 8 (offline: "View edit history" still renders from local data with no network), Scenario 9 (existing pre-feature job history displays unchanged). **Not completed as on-device click-throughs** (same environment limitation as T016/T023/T026). Confirmed instead: Scenario 6 by `JobDaoLineageTest.findDoneJobByHash_matchesOnSourceOrResultHash_onlyWhenDone` (a non-`'done'` status never matches, regardless of which non-done status) and `getJobsByLineageRoot_returnsOnlyDoneJobsInThatGroup_orderedByCreatedAt` (a `failed` sibling in the same group is excluded); Scenario 7 by the new `hardDeletingAJob_removesItFromFutureHashDetection` test; Scenario 9 by `AppDatabaseMigrationTest` (pre-existing rows keep working, unmodified, with lineage columns defaulted to `null`, never backfilled). Scenario 8 (offline) is a structural guarantee, not just tested behavior: `EditHistoryViewModel`/`RealJobRepository.getJobsByLineageRoot` and `findPriorEdits` are pure local Room `Flow`/`suspend` queries with zero network calls in either path — confirmed by inspection of every method added in T012 and T017. Scenario 5 (batch partial match) follows from `HomeViewModel.priorEdits` being a per-`Uri` map, independently populated per image (verified for a single URI in `HomeViewModelPriorEditsTest`); not additionally tested with a literal 3-image batch.
- [X] T028 [P] Run `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` and confirm all succeed with no regressions from the T001 baseline. **Done**: all green — debug/release compile, 100% unit tests pass (including 3 `LineageAssignmentTest`, 3 `HomeViewModelPriorEditsTest`, 2 new `PhotoViewerScreenTest` cases), 7/7 instrumented tests pass (`AppDatabaseMigrationTest` + 6 `JobDaoLineageTest` cases, later 7 after adding the hard-delete case), lint clean (same 3 pre-existing warnings as the T001 baseline — 2 dependency-version notices, 1 pre-existing `HomeDragAndDrop.kt` style warning — no new warnings introduced).
- [X] T029 Per the constitution's Quality Gates (baseline profiles regenerated when a change touches a startup-path or benchmark-covered code path): grep `baselineprofile/src/main/java/dev/zun/flux/baselineprofile/BaselineProfileGenerator.kt` for "Home"/"Result"/"Gallery"/"submit" — if the generator's scripted run exercises the Home submit flow or Result/Gallery viewing (which this feature adds inline hash-checking/DAO calls to), regenerate the baseline profile; otherwise document that no regeneration is needed. **Done**: the generator's script (read in full) covers cold-start plus the Gallery **grid** (list pane) only — it flings the grid but never taps into an individual photo, never opens Result, and never touches Home's picker/submit flow. None of those three screens (`HomeScreen`/`HomeImagePicker`, `ResultScreen`, `PhotoViewerScreen`'s detail pane, the new `EditHistoryScreen`) are on this profile's exercised path, and the one screen that is (`GalleryScaffold`'s list pane / `GalleryScreen`) was not modified by this feature — only `GalleryScaffold`'s own composable signature gained a new default-valued parameter. **Conclusion: no baseline-profile regeneration needed.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS all three user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. Independent of User Story 2 and 3's remaining tasks (different files: `UploadRepository`/`HomeViewModel`/`HomeRoute`/`HomeImagePicker` vs. `JobRepository`/`ui/history`/`ResultScreen`/`PhotoViewerScreen`).
- **User Story 2 (Phase 4)**: Depends on Foundational. Independent of User Story 1. User Story 3 depends on User Story 2's `Routes.HISTORY`/`EditHistoryScreen` (T018-T020) but not on its `ResultScreen` wiring (T021-T022).
- **User Story 3 (Phase 5)**: Depends on Foundational and on T017/T020 from User Story 2 (the `getLineageRootId` repository method and the `Routes.HISTORY` destination it navigates to).
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Within Each Phase

- T002 and T003 touch different files and can run in parallel; T004 depends on both.
- T005 and T006 touch different files and can run in parallel with T002-T004 (T005 only needs T002's `JobEntity` shape).
- T007, T008, T009 each touch different methods in the same file (`RealJobRepository.kt`) — sequential edits to avoid merge conflicts, though logically independent of each other.
- T010 depends on T006, T007 (needs `assignLineageRoot` and the carry-forward-safe `getJob` to coexist correctly).
- Within US1: T012 → T013 → T014 → T015 → T016 (each depends on the previous).
- Within US2: T017 unlocks T018 (parallel-safe) and T021 (ResultScreen); T019 depends on T017+T018; T020 depends on T019; T022 depends on T020+T021.
- Within US3: T024 depends on T017; T025 depends on T024 and T020 (the `Routes.HISTORY` destination).

### Parallel Opportunities

- T002 + T003 (Foundational, disjoint files).
- T005 + T006 (Foundational, disjoint files).
- T018 (Routes.kt) in parallel with T017 (JobRepository) since T018 has no dependency on it.
- T027 + T028 (Polish, disjoint concerns).

---

## Parallel Example: Phase 2 (Foundational)

```bash
Task: "Add sourceSha256/resultSha256/lineageRootId columns + indices to JobEntity.kt"
Task: "Bump AppDatabase to version 5 with MIGRATION_4_5"
```

```bash
Task: "Add findDoneJobByHash/updateResultHash/updateSourceLineage/getJobsByLineageRoot/countByLineageRoot/getJobsByIds to JobDao.kt"
Task: "Create the pure assignLineageRoot function and LineageAssignmentTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational — this is the majority of the real engineering work (data model, write-path, race-safety fix).
3. Complete Phase 3: User Story 1.
4. **STOP and VALIDATE**: Run quickstart.md Scenarios 1-2 independently.
5. This alone delivers real user value (the "have I edited this before?" nudge) even before the history view exists.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready.
2. Add User Story 1 → Test independently (MVP: duplicate detection).
3. Add User Story 2 → Test independently (full history from Result).
4. Add User Story 3 → Test independently (history from any Gallery entry).
5. Complete Polish.

## Notes

- [P] tasks touch different files with no unmet dependencies.
- No new server-side (`zun-rust-server`) changes anywhere in this task list, per `research.md`'s finding that this feature is entirely client-side.
- Commit after each task or logical group, per this repo's usual practice. Given the correctness-sensitive nature of T007-T010 (the carry-forward/race-safety fix), consider landing Phase 2 as a tightly-reviewed single unit before starting UI work.
