---

description: "Task list template for feature implementation"
---

# Tasks: Remove Completion Notifications

**Input**: Design documents from `/specs/002-remove-completion-notifications/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/decommission-surface.md, quickstart.md

**Tests**: Not requested in the spec, and there is nothing to unit-test — this is a pure deletion with no new logic (confirmed in `research.md`: no existing test references any of the removed classes). Verification instead follows Constitution Principle III via compile-cleanliness checks and the manual `quickstart.md` scenarios, since real notification/permission-prompt behavior can't be observed by Robolectric.

**Organization**: Tasks are grouped by user story (from `spec.md`), with a caveat noted below.

**Important caveat on independence**: `JobNotifications.kt` houses both the notification-posting logic (User Story 1) and the `canNotify()` check that gates the permission prompt (User Story 2), inside the same `LaunchedEffect` block in `HomeRoute.kt`, and `MainActivity`/`AppNavHost` share one interdependent parameter pair. Kotlin will not compile with half of this removed — so the code edits satisfying both stories must land together as one atomic change (Phase 2: Foundational). What genuinely separates per-story afterward is: (a) the one story-specific cleanup each doesn't share (orphaned strings for US1, the manifest permission line for US2), and (b) each story's own independent verification. This is called out explicitly rather than presenting a false picture of independently shippable code changes — see `plan.md`'s Constraints for why the removal is intentionally atomic.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Every task lists its exact file path

## Path Conventions

Single-module Android app. All paths are relative to the repo root:
`app/src/main/java/dev/zun/flux/...`, `app/src/main/res/...`.

---

## Phase 1: Setup

**Purpose**: Establish a clean baseline before making changes.

- [X] T001 Run `./gradlew :app:testDebugUnitTest` at the repo root and confirm it passes on `dev` before starting, so any later failure is attributable to this feature's changes.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The atomic set of deletions/edits needed for the project to compile with notifications and their permission prompt fully gone. Because of the shared-file coupling described above, this single phase satisfies the substance of both FR-001/002/005/006 (User Story 1) and FR-003 (User Story 2) — the User Story phases that follow are mostly story-specific cleanup and verification, not new implementation.

**⚠️ CRITICAL**: No user story verification can begin until this phase compiles cleanly.

- [X] T002 [P] Delete `app/src/main/java/dev/zun/flux/data/worker/JobWatchWorker.kt` entirely (its only purpose was watching a job to trigger a notification; its only caller, `HomeRoute.kt`, is fixed in T004).
- [X] T003 [P] Delete `app/src/main/java/dev/zun/flux/util/JobNotifications.kt` entirely (its only callers — `JobWatchWorker`, `HomeRoute.kt`, `MainActivity.kt` — are fixed in T002, T004, T005).
- [X] T004 In `app/src/main/java/dev/zun/flux/ui/home/HomeRoute.kt`: remove the `notificationPermissionLauncher` (`rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` block around line 221), both `if (!JobNotifications.canNotify(context)) { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }` blocks, and both `JobWatchWorker.enqueue(context, ...)` calls inside the `LaunchedEffect(state)` block (the `SubmitState.Done` and `SubmitState.DoneBatch` branches, lines ~225-253). Leave `viewModel.acknowledgeDone()`, `onJobSubmitted(s.jobId)`, `onBatchSubmitted(s.submittedIds)`, and the failed-upload snackbar logic untouched — they drive navigation and are unrelated to notifications. Remove the now-unused `android.Manifest`, `dev.zun.flux.data.worker.JobWatchWorker`, and `dev.zun.flux.util.JobNotifications` imports (depends on T002, T003).
- [X] T005 In `app/src/main/java/dev/zun/flux/MainActivity.kt`: remove the `pendingResultJobId` state property, the `resultJobId(intent)` private method, its call sites in `onCreate` and `onNewIntent`, the `navigateToResultJobId = pendingResultJobId` / `onResultNavConsumed = { pendingResultJobId = null }` arguments passed into the `AppNavHost(...)` call, and the now-unused `dev.zun.flux.util.JobNotifications` import. Must land in the same change as T006 — `AppNavHost`'s parameter list and this call site must agree (depends on T003).
- [X] T006 In `app/src/main/java/dev/zun/flux/ui/nav/AppNavHost.kt`: remove the `navigateToResultJobId: String? = null` / `onResultNavConsumed: () -> Unit = {}` parameters and their `// Completion-notification tap → the finished job's result screen` `LaunchedEffect(navigateToResultJobId) { ... }` block (lines ~48-49, 66-73). Leave `Routes.result(jobId)` itself and every other navigation call untouched. Must land in the same change as T005 (depends on T003).
- [X] T007 Run `grep -rn "JobNotifications\|JobWatchWorker" app/src/main` and confirm zero matches, then run `./gradlew :app:compileDebugKotlin` and confirm it succeeds (depends on T002-T006).

**Checkpoint**: The app compiles with no notification ever posted and no permission ever requested. Both user stories' core behavior already works — the phases below verify and clean up around the edges.

---

## Phase 3: User Story 1 - No more completion notifications (Priority: P1)

**Goal**: Confirm no notification is ever shown for a finished or failed generation, and clean up the resources that existed only to support it.

**Independent Test**: Submit a photo (or batch), background the app until it finishes, confirm no notification appears, and confirm the result is still visible through the app's own screens (quickstart.md Scenarios 2, 3, 5).

### Implementation for User Story 1

- [X] T008 [P] [US1] Remove the four now-orphaned strings from `app/src/main/res/values/strings.xml`: `notify_channel_job_results`, `notify_job_done_title`, `notify_job_failed_title`, `notify_job_tap_to_view`. Leave every other string, including `ic_shortcut_edit`'s references elsewhere, untouched (depends on T003, T007).
- [ ] T009 [US1] Manually run quickstart.md Scenario 2 (no notification on success) and Scenario 3 (no notification on failure) on a device/emulator against a real server; confirm no notification appears in either case, and that the finished/failed job is still visible through Gallery or Home's "still processing" entry point as appropriate (depends on T007). **Partially done**: built and installed the debug APK on the connected device (`SM-F966U1`); confirmed via `aapt dump permissions` on the actual built APK that `POST_NOTIFICATIONS` is gone, and confirmed `MainActivity` launches with no crash in logcat. The device's screen was locked/off and couldn't be driven interactively from here (same constraint as the prior feature), so the visual "no notification appears" / "still visible in Gallery" checks still need a human to run.
- [ ] T010 [US1] Manually run quickstart.md Scenario 5, confirming a submitted batch still reaches `done`/`failed` for every job at the same pace as before this change — the removal must not affect processing itself (FR-004) (depends on T007). **Not run** — needs the same live-device, live-server interaction as T009.
- [ ] T011 [US1] Manually run quickstart.md Scenario 4 (accepted staleness for a silent failure), confirming the Home entry point's documented best-effort behavior for a job that fails unattended in the background matches what's recorded in spec.md's Clarifications/Edge Cases (depends on T007). **Not run** — needs the same live-device, live-server interaction as T009.

**Checkpoint**: User Story 1 is fully verified — no notification ever appears, and orphaned resources are cleaned up.

---

## Phase 4: User Story 2 - No more notification permission prompt (Priority: P2)

**Goal**: Confirm users are never asked to grant notification permission when submitting photos, and remove the now-unused permission declaration.

**Independent Test**: On a fresh install (or cleared app data), submit a photo for the first time and confirm no permission dialog appears (quickstart.md Scenario 1).

### Implementation for User Story 2

- [X] T012 [US2] Remove `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` from `app/src/main/AndroidManifest.xml` (depends on T004 — `HomeRoute.kt` must no longer request this permission first).
- [ ] T013 [US2] Manually run quickstart.md Scenario 1 (fresh install or cleared data, submit first photo) and confirm no notification-permission dialog appears (depends on T004, T012). **Not run** — confirmed at the build level (`aapt dump permissions` shows `POST_NOTIFICATIONS` is no longer declared, and the `.launch(POST_NOTIFICATIONS)` call site is deleted from `HomeRoute.kt`), but the actual on-device dialog-absence check needs a human with an unlocked device.

**Checkpoint**: User Story 2 is fully verified — the permission is neither requested nor declared.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final regression pass across both stories.

- [X] T014 [P] Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` and confirm both succeed with no regressions from the baseline in T001.
- [X] T015 [P] Run `./gradlew spotlessCheck` and confirm it's clean after the string removal in T008 (no unused-resource or formatting issues introduced). Also ran `./gradlew :app:lintDebug`: 3 pre-existing warnings unrelated to this change (a stale baseline entry, a dependency-version notice, and a pre-existing Modifier-factory style warning from the prior feature) — no unused-resource warning for the removed strings.
- [X] T016 Per the constitution's Quality Gates (baseline profiles regenerated when a change touches a startup-path or benchmark-covered code path): reviewed `baselineprofile/src/main/java/dev/zun/flux/baselineprofile/BaselineProfileGenerator.kt` — grepped for "submit"/"notification"/"permission", zero matches. Its scripted run never submits a photo, so it never exercises the removed permission-request/notification code paths. **Conclusion: no baseline-profile regeneration needed.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS both user stories' verification.
- **User Story 1 (Phase 3)**: Depends on Foundational. Independent of User Story 2's remaining task (T012/T013 touch only the manifest).
- **User Story 2 (Phase 4)**: Depends on Foundational (specifically T004). Independent of User Story 1's remaining task (T008-T011 touch only strings.xml and manual verification).
- **Polish (Phase 5)**: Depends on both user stories being complete.

### Within Each Phase

- T002 and T003 can run in parallel (different files, no interdependency at delete-time).
- T004 depends on T002 and T003; the {T005, T006} pair depends on T003 and must land together, but is independent of T004 (different files) — can proceed in parallel with T004.
- T007 depends on T004, T005, T006 all being complete.
- T008 can run in parallel with T004-T007 once T003 is done (different file, no compile dependency).
- T009-T011 (US1 verification) and T012-T013 (US2's manifest edit + verification) can proceed in parallel once T007 passes.

### Parallel Opportunities

- T002 + T003 (Foundational deletions).
- T004 in parallel with {T005, T006} (Foundational edits, disjoint files).
- T008 (strings cleanup) in parallel with the rest of Foundational, once T003 lands.
- Phase 3 and Phase 4 verification tasks in parallel with each other once Phase 2's checkpoint (T007) passes.

---

## Parallel Example: Phase 2 (Foundational)

```bash
Task: "Delete app/src/main/java/dev/zun/flux/data/worker/JobWatchWorker.kt"
Task: "Delete app/src/main/java/dev/zun/flux/util/JobNotifications.kt"
```

---

## Implementation Strategy

### MVP First

Because both stories' substantive behavior lands in Phase 2 (Foundational), there
isn't a meaningful "User Story 1 only" MVP milestone distinct from "User Story 2
only" the way additive features have — completing Foundational already delivers
the entire user-facing ask (no notification, no permission prompt). The story
split exists to organize verification and the two small pieces of story-specific
cleanup (orphaned strings vs. the manifest permission line), not to stage the
rollout.

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational — this is the real deliverable.
3. Complete Phase 3 and Phase 4 (independent of each other) to verify and clean up.
4. Complete Phase 5: Polish.

## Notes

- [P] tasks touch different files with no unmet dependencies.
- No new screens, routes, or server contract changes are introduced anywhere in this task list — every task deletes or trims existing code.
- Commit after each task or logical group, per this repo's usual practice. Given the atomic-compile requirement, T002-T007 will likely land as one commit in practice.
