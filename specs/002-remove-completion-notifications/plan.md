# Implementation Plan: Remove Completion Notifications

**Branch**: `002-remove-completion-notifications` | **Date**: 2026-07-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-remove-completion-notifications/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Users no longer want a system notification when a photo generation finishes or fails. Per code inspection, exactly one class (`JobNotifications`) ever posts this notification, and exactly one background worker (`JobWatchWorker`) exists solely to watch a job to a terminal state in order to trigger it — nothing else depends on either. The fix is a clean deletion: remove both files, the `POST_NOTIFICATIONS` permission request and manifest declaration, the notification-tap deep-link plumbing in `MainActivity`/`AppNavHost` that only a tapped notification could ever trigger, and the now-orphaned notification strings. Per the 2026-07-04 clarification session, this is a full removal (not a "silent status-sync" variant) — the accepted trade-off is that a job which fails while the app is backgrounded may show stale status in Home's "still processing" entry point until the user reopens that job's live view.

## Technical Context

**Language/Version**: Kotlin 2.4.0

**Primary Dependencies**: No new dependencies. Removes this feature's only usage of `androidx.work` (`JobWatchWorker`) and `androidx.core.app.NotificationCompat`/`NotificationManagerCompat` (`JobNotifications`) — WorkManager itself stays in the project (`JobUploadWorker`, `DeleteSyncWorker` are unrelated and unaffected)

**Storage**: N/A — no Room schema or query changes; `RealJobRepository.getJob()` (which persists status to Room) is called by other code paths (`ProgressViewModel`) independent of `JobWatchWorker`, so removing the worker does not change how job status is persisted, only how often it's refreshed while backgrounded

**Testing**: No existing automated test references `JobNotifications`, `JobWatchWorker`, or the notification deep-link plumbing (confirmed via repo-wide search), so there is nothing to update there. Verification is: (a) the project compiles with zero dangling references after deletion, (b) the full existing unit-test suite still passes unchanged, and (c) a manual on-device check for the two behaviors that can only be observed at the OS level — no permission prompt, no notification — since Robolectric doesn't exercise real notification posting

**Target Platform**: Android (compileSdk 37, minSdk 36, targetSdk 36) — unchanged

**Project Type**: Mobile app — single Android module

**Performance Goals**: N/A — no explicit target; incidental effect is one fewer `WorkManager` job enqueued per submission (worker previously ran up to ~8 minutes watching each job)

**Constraints**: Must not change how photo generation is processed (FR-004) — deleting `JobWatchWorker` must not touch `RealJobRepository`/`FluxApi` job-submission or polling logic used elsewhere; must not affect the "return to running batch" Home entry point's accuracy for successes (still refreshed via `syncHistory`'s pull-to-refresh); the entry point's reduced accuracy for silently-failed jobs is an accepted, documented trade-off, not something to compensate for in this change

**Scale/Scope**: Delete 2 files (`JobNotifications.kt`, `JobWatchWorker.kt`); edit 3 files (`HomeRoute.kt`, `MainActivity.kt`, `AppNavHost.kt`) to remove now-dead call sites and parameters; remove 1 manifest permission line; remove 4 now-orphaned string resources. No new files, screens, or routes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Privacy & Security by Default | Removing a runtime permission request and its declaration *reduces* the app's footprint — no new tracking/permissions added; reinforces the "privacy-first" positioning rather than risking it | PASS |
| II. Surgical, Simplicity-First Changes | Every deleted/changed line traces directly to this one feature; no drive-by refactors. Orphaned strings/imports made unused by this change are cleaned up, per this repo's own precedent (the immediately prior feature dropped an unused string the same way) | PASS |
| III. Verify Before Claiming Done | Plan requires: compile-clean deletion (no dangling references), full existing test suite green, plus an explicit manual verification path (`quickstart.md`) for the two OS-level behaviors automated tests can't observe | PASS (enforced in tasks) |
| IV. Offline-Capable by Design | N/A — this feature doesn't touch the gallery/result offline-read path; the one related surface (Home's active-jobs entry point) is unaffected for its offline behavior, per Clarifications | N/A |
| V. Server Contract Fidelity | N/A — no request/response shape change; `JobWatchWorker` only ever called the existing `GET /jobs/{id}` polling endpoint that `ProgressViewModel` already calls independently. `FluxApi.kt` and `API_CONTRACT.md` are untouched | N/A |

No violations — Complexity Tracking table is not needed.

**Post-Phase 1 re-check**: `research.md` and `data-model.md` confirmed this is pure deletion with no new abstractions, no schema change, and no server-contract impact — the table above still holds unchanged after design.

## Project Structure

### Documentation (this feature)

```text
specs/002-remove-completion-notifications/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/
├── java/dev/zun/flux/
│   ├── util/
│   │   └── JobNotifications.kt          # DELETE — whole file
│   ├── data/worker/
│   │   └── JobWatchWorker.kt            # DELETE — whole file
│   ├── ui/home/
│   │   └── HomeRoute.kt                 # - permission launcher, canNotify() checks,
│   │                                     #   JobWatchWorker.enqueue() calls
│   ├── ui/nav/
│   │   └── AppNavHost.kt                # - navigateToResultJobId / onResultNavConsumed
│   │                                     #   param pair + its LaunchedEffect
│   └── MainActivity.kt                  # - pendingResultJobId state, resultJobId(),
│                                         #   the two params passed into AppNavHost
├── AndroidManifest.xml                  # - <uses-permission POST_NOTIFICATIONS>
└── res/values/strings.xml               # - notify_channel_job_results,
                                          #   notify_job_done_title,
                                          #   notify_job_failed_title,
                                          #   notify_job_tap_to_view

app/src/main/res/xml/shortcuts.xml       # UNCHANGED — ic_shortcut_edit is shared with
                                          # the app shortcut icon, not notification-only
```

**Structure Decision**: Single-module Android app (existing `app/` module, package
`dev.zun.flux`). This feature is subtractive only — two files deleted outright, three
files trimmed of now-dead code paths, one manifest permission removed, four orphaned
strings removed. No new module, screen, route, or abstraction is introduced.

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
