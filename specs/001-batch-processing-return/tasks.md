---

description: "Task list template for feature implementation"
---

# Tasks: Return to Running Batch

**Input**: Design documents from `/specs/001-batch-processing-return/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/internal-interfaces.md, quickstart.md

**Tests**: Included. Constitution Principle III ("Verify Before Claiming Done") requires either an automated test or an explicit manual verification path for every feature; this plan uses automated Robolectric/JVM unit tests (matching this repo's existing convention — see `JobDaoOrderingTest.kt`, `HomeViewModelTest.kt`, `GalleryScaffoldTest.kt`) plus the manual `quickstart.md` scenarios for what automation doesn't cover (a true force-stop/reopen).

**Organization**: Tasks are grouped by user story (from `spec.md`) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task lists its exact file path

## Path Conventions

Single-module Android app. All paths are relative to the repo root:
`app/src/main/java/dev/zun/flux/...`, `app/src/test/java/dev/zun/flux/...`.

---

## Phase 1: Setup

**Purpose**: Establish a clean baseline before making changes. No new project scaffolding is needed — this feature is additive within the existing `app` module.

- [X] T001 Run `./gradlew :app:testDebugUnitTest` at the repo root and confirm it passes on `dev` before starting, so any later failure is attributable to this feature's changes.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared "active jobs" data plumbing every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Add `fun getActiveJobs(): Flow<List<JobEntity>>` to `app/src/main/java/dev/zun/flux/data/local/JobDao.kt`: `SELECT * FROM jobs WHERE status NOT IN ('done', 'failed', 'cancelled') AND id NOT IN (SELECT jobId FROM pending_deletes) ORDER BY createdAt ASC, id ASC` (oldest-submitted first, with an id tiebreak matching the style of the existing paged queries).
- [X] T003 [P] Add `fun activeJobIds(): Flow<List<String>>` to the `JobRepository` interface in `app/src/main/java/dev/zun/flux/data/repo/JobRepository.kt`, with a KDoc comment matching `contracts/internal-interfaces.md` section 1 (local-only, no network call, ids of non-terminal/non-deleted jobs).
- [X] T004 Implement `activeJobIds()` in `app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt` by mapping `dao.getActiveJobs()` (from T002) to `entities.map { it.id }` (depends on T002, T003).
- [X] T005 [P] Add a compiling `override fun activeJobIds(): Flow<List<String>> = MutableStateFlow(emptyList())` stub to `app/src/test/java/dev/zun/flux/data/repo/FakeJobRepository.kt`, consistent with that fake's existing minimal-stub style for methods it doesn't otherwise model (depends on T003).
- [X] T006 [P] Add a functional `activeJobIds()` to `app/src/test/java/dev/zun/flux/data/repo/RecordingRepository.kt`, backed by a settable `MutableStateFlow<List<String>>` (with a small test helper like `fun setActiveJobIds(ids: List<String>)`) so `HomeViewModelTest` can seed values (depends on T003).
- [X] T007 [P] Create `app/src/test/java/dev/zun/flux/data/local/JobDaoActiveJobsTest.kt` (Robolectric + in-memory Room, following the pattern in `JobDaoOrderingTest.kt`) with tests asserting: jobs with status `queued`/`running` are included; jobs with status `done`/`failed`/`cancelled` are excluded; a job present in `pending_deletes` is excluded regardless of status (depends on T002).

**Checkpoint**: Data layer complete — both test fakes compile, and the underlying query is verified correct. User story work can now begin.

---

## Phase 3: User Story 1 - Return to an in-progress batch from Home (Priority: P1) 🎯 MVP

**Goal**: Give the user a visible way back into the live batch view from Home after backing out while jobs are still processing.

**Independent Test**: Submit a batch, back out to Home before it finishes, confirm an entry point appears, tap it, and confirm it opens a live view showing current status for every still-processing photo (quickstart.md Scenario 1).

### Implementation for User Story 1

- [X] T008 [US1] In `app/src/main/java/dev/zun/flux/ui/home/HomeViewModel.kt`, add `val activeJobIds: StateFlow<List<String>>` sourced from `jobRepo.activeJobIds()` via `.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())`, following the existing `prompts` property's pattern (`HomeViewModel.kt:110-118`) (depends on T003, T004).
- [X] T009 [P] [US1] Add an entry-point action-label string (`home_resume_active_jobs_action` = "View") to `app/src/main/res/values/strings.xml` near the existing `progress_batch_*` entries. Reuse the existing `progress_batch_n_generations_format` plural for the count text. (A separate content-description string was dropped during implementation: `Surface(onClick = ...)` already merges its child text into one accessible node via Compose's `clickable` semantics, so a duplicate description would be redundant and untestable via visible text.)
- [X] T010 [US1] Add a small entry-point composable (e.g. `ActiveJobsBanner(count: Int, onClick: () -> Unit)`) to `app/src/main/java/dev/zun/flux/ui/home/HomeScreen.kt`. Render nothing when `count == 0`; otherwise show the count via `pluralStringResource(R.plurals.progress_batch_n_generations_format, count, count)` and the action label from T009, clickable to invoke `onClick` (depends on T009).
- [X] T011 [US1] In `app/src/main/java/dev/zun/flux/ui/home/HomeRoute.kt`: collect `viewModel.activeJobIds` with `collectAsStateWithLifecycle()`, render `ActiveJobsBanner` (T010) inside the existing `Scaffold` body when the list is non-empty, add a new `onResumeBatch: (List<String>) -> Unit` parameter to `HomeRoute`, and call `onResumeBatch(activeJobIds)` from the banner's `onClick` (depends on T008, T010).
- [X] T012 [US1] In `app/src/main/java/dev/zun/flux/ui/nav/AppNavHost.kt`, add `onResumeBatch = { jobIds -> nav.navigate(Routes.batch(jobIds)) }` to the `HomeRoute(...)` call in the `Routes.HOME` composable, mirroring the existing `onBatchSubmitted` wiring immediately above it (depends on T011).
- [X] T013 [P] [US1] Add a test to `app/src/test/java/dev/zun/flux/ui/home/HomeViewModelTest.kt` asserting `viewModel.activeJobIds` reflects the ids seeded via `RecordingRepository.setActiveJobIds(...)` (T006) (depends on T006, T008).

**Checkpoint**: User Story 1 is fully functional and independently testable — run quickstart.md Scenario 1.

---

## Phase 4: User Story 2 - Entry point stays accurate as jobs complete (Priority: P2)

**Goal**: The entry point's count updates as jobs individually finish/fail, and it disappears once every job it represents is terminal.

**Independent Test**: Submit a multi-photo batch, back out, let some (not all) finish, confirm the count shrinks; let all finish, confirm the entry point disappears (quickstart.md Scenario 2).

**Note**: This story adds no new production code path — it depends on the same reactive `activeJobIds` flow from Phase 2/3 already updating and emptying correctly. Its tasks exist to *verify* that behavior explicitly, per Constitution III.

### Tests for User Story 2

- [X] T014 [P] [US2] Extend `app/src/test/java/dev/zun/flux/data/local/JobDaoActiveJobsTest.kt` (from T007) with a test that seeds several non-terminal jobs, updates one at a time to `done`/`failed`/`cancelled` via `dao.insertJob(...)` (Room `REPLACE` conflict strategy), and asserts `getActiveJobs()` shrinks after each update and returns an empty list once all are terminal (depends on T007).
- [X] T015 [P] [US2] Create `app/src/test/java/dev/zun/flux/ui/home/HomeActiveJobsBannerTest.kt` (Robolectric + `createComposeRule`, following the pattern in `GalleryScaffoldTest.kt`) that renders `ActiveJobsBanner` (T010) with a mutable count, asserting: it is not displayed at `count = 0`, is displayed with the correct text at `count > 0`, and updates its displayed text when the count changes (depends on T010).

**Checkpoint**: User Stories 1 AND 2 both work independently — run quickstart.md Scenario 2.

---

## Phase 5: User Story 3 - Recover access after fully closing the app (Priority: P3)

**Goal**: The return path survives the app being fully closed (not just backgrounded) while jobs are still processing.

**Independent Test**: Submit a batch, back out, force-close the app, reopen it, confirm the entry point is still present and accurate (quickstart.md Scenario 3).

### Tests for User Story 3

- [X] T016 [US3] Create `app/src/test/java/dev/zun/flux/data/local/JobDaoPersistenceTest.kt` (Robolectric): build a `Room.databaseBuilder` (not `inMemoryDatabaseBuilder`) pointing at a temp file, insert non-terminal jobs, call `db.close()`, then open a **new** `AppDatabase`/`JobDao` instance against the same file path and assert `getActiveJobs()` still returns the seeded ids — proving the active-jobs source of truth is durable storage, not in-memory state, which is what makes FR-005 possible (depends on T002).

**Checkpoint**: All three user stories are independently functional. A true "force-stop the app" pass is covered manually via quickstart.md Scenario 3, since this repo has no existing instrumented-test convention for that (only `AppDatabaseMigrationTest.kt` exists under `androidTest`, and adding a new instrumented-test harness for one scenario would be disproportionate to this feature per Constitution II).

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification across all three stories.

- [X] T017 [P] Run `./gradlew :app:testDebugUnitTest` at the repo root and confirm all tests pass, including the new ones from T007, T013, T014, T015, T016.
- [X] T018 Per the constitution's Quality Gates ("Baseline profiles MUST be regenerated when a change touches a startup-path or benchmark-covered code path"): review whether the `ActiveJobsBanner` addition to `HomeScreen.kt` (on the app's cold-start screen) is material enough to require regenerating `app/src/main/baseline-prof.txt` per the steps in `docs/build.md` ("Baseline profile" section); regenerate if so. **Reviewed, not regenerated**: `BaselineProfileGenerator.kt` never submits a batch during its scripted cold-start/gallery run, so `activeJobIds` is always empty and `HomeRoute`'s `if (activeJobIds.isNotEmpty())` guard means `ActiveJobsBanner`'s body never executes during profile capture — the only change on the always-profiled path is one cheap, already-covered `Column` wrapper. Not material enough to warrant regeneration.
- [ ] T019 Manually run quickstart.md Scenarios 1-5 on an emulator or device against a real server and confirm each passes, including Scenario 3 (full app close/reopen) and Scenario 5 (offline return), which are not covered by the automated tests above. **Partially done**: built and installed the debug APK on the connected device (`SM-F966U1`) and confirmed the app launches and reaches `MainActivity` with no crash (`dumpsys activity` shows it resumed; no `FATAL`/`AndroidRuntime` entries in logcat). Screenshots came back black — the device's screen was off/locked and could not be confirmed visually or driven further from here (this app is biometric-locked per `BaselineProfileGenerator.kt`'s comments, and no server URL is known to be configured). The full 5 scenarios — which need a live server, an unlocked device, and visual confirmation of the banner/live view — still need a human to run per `quickstart.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2/US3.
- **User Story 2 (Phase 4)**: Depends on Foundational; its Compose test (T015) also depends on T010 from US1. Functionally independent of US1's navigation wiring (T011, T012).
- **User Story 3 (Phase 5)**: Depends on Foundational (T002) only — independent of US1 and US2.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Within Each Phase

- T002/T003/T005/T006/T007 in Phase 2 can run in parallel (different files); T004 waits on T002+T003.
- In Phase 3, T009 can run in parallel with T002-T007; T010 waits on T009; T011 waits on T008+T010; T012 waits on T011; T013 waits on T006+T008.
- In Phase 4, T014 and T015 can run in parallel with each other once their respective dependencies (T007, T010) are done.
- Phase 5's single task (T016) can run any time after T002 — in practice, in parallel with all of Phase 3 and Phase 4.

### Parallel Opportunities

- All of Phase 2 except T004 can be worked on in parallel.
- Once Phase 2 is done, Phase 5 (T016) can proceed fully in parallel with Phase 3 and Phase 4 — it only touches a new, independent test file.
- T009 (strings) and T013 (view-model test) in Phase 3 can run in parallel with the rest of that phase's sequential UI-wiring chain (T010 → T011 → T012).

---

## Parallel Example: Phase 2 (Foundational)

```bash
Task: "Add getActiveJobs() query to app/src/main/java/dev/zun/flux/data/local/JobDao.kt"
Task: "Add activeJobIds() to the JobRepository interface in app/src/main/java/dev/zun/flux/data/repo/JobRepository.kt"
Task: "Add activeJobIds() stub to app/src/test/java/dev/zun/flux/data/repo/FakeJobRepository.kt"
Task: "Add functional activeJobIds() to app/src/test/java/dev/zun/flux/data/repo/RecordingRepository.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (blocks everything else).
3. Complete Phase 3: User Story 1.
4. **STOP and VALIDATE**: run quickstart.md Scenario 1 on a device/emulator.
5. This alone fixes the reported problem — a user can always get back to their running batch from Home.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. User Story 1 → validate independently → this is the MVP fix.
3. User Story 2 → validate independently → confirms the entry point doesn't lie or overstay.
4. User Story 3 → validate independently → confirms the fix survives a full app restart, not just backgrounding.
5. Polish → full regression pass + manual quickstart scenarios.

## Notes

- [P] tasks touch different files with no unmet dependencies.
- No new screens, routes, or server contract changes are introduced anywhere in this task list — every task is additive within existing files (or a new test file).
- Commit after each task or logical group, per this repo's usual practice.
