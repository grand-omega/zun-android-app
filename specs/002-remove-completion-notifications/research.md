# Phase 0 Research: Remove Completion Notifications

No `[NEEDS CLARIFICATION]` markers remained in the Technical Context after the
2026-07-04 `/speckit-clarify` session. Research here confirms the exact
deletion surface by tracing every caller/consumer of the code being removed.

## Decision: Delete `JobWatchWorker.kt` and `JobNotifications.kt` outright

**Decision**: Both files are removed entirely, not stripped down to a no-op.

**Rationale**: `JobWatchWorker`'s class doc (`data/worker/JobWatchWorker.kt:18-23`)
states its only purpose is "watches a submitted job to a terminal state ...
and posts a completion notification if the app is backgrounded." Its only
caller is `HomeRoute.kt` (`JobWatchWorker.enqueue(...)`, two call sites). Its
only side effects besides notifying are calls into `RealJobRepository.getJob()`
— the same method `ProgressViewModel` already calls independently while a
live screen is open, so no other code depends on `JobWatchWorker` running.
`JobNotifications`'s only caller is `JobWatchWorker` (to post) and `HomeRoute`
(to check `canNotify()` before requesting permission). Once the notification
itself is gone, both classes have zero remaining reason to exist.

**Alternatives considered**:
- *Strip the `notifyJobFinished()` calls but keep `JobWatchWorker` running as
  a silent background status-sync*: this was the explicit subject of the
  2026-07-04 clarification question and was rejected (chose "fully remove").
  Reason given: keeping an always-on background mechanism nobody asked for
  contradicts the literal, minimal scope of "remove the notification, no
  longer needed" (Constitution II).

## Decision: Remove the `POST_NOTIFICATIONS` permission end-to-end

**Decision**: Remove the runtime permission request in `HomeRoute.kt`
(the `notificationPermissionLauncher` and its two `.launch(...)` call sites)
and the `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
declaration in `AndroidManifest.xml:8`.

**Rationale**: The permission's only consumer anywhere in the app is
`JobNotifications.canNotify()` / `notifyJobFinished()`'s inline check. With
that file deleted, requesting or declaring the permission has no purpose.

**Alternatives considered**:
- *Leave the manifest declaration in case notifications return later*:
  rejected — an unused declared permission is a small but real trust/privacy
  regression (Play Store's permission listing would show a capability the
  app doesn't use), directly at odds with Constitution I's privacy-first
  positioning that this change is otherwise reinforcing. Easy to re-add if
  ever needed.

## Decision: Remove the notification-tap deep-link plumbing

**Decision**: Remove `MainActivity`'s `pendingResultJobId` state and
`resultJobId()` method, and `AppNavHost`'s `navigateToResultJobId` /
`onResultNavConsumed` parameters plus their `LaunchedEffect` block
(`AppNavHost.kt:47-49,66-73`).

**Rationale**: This plumbing exists for exactly one purpose — routing a
tapped notification's `ACTION_VIEW_RESULT` intent to `Routes.result(jobId)`.
Confirmed via search: `JobNotifications.ACTION_VIEW_RESULT` /
`.EXTRA_JOB_ID` are referenced nowhere else. With no notification ever
posted, this intent can never be delivered again.

**Alternatives considered**:
- *Keep it as a general-purpose "deep link to a result screen" mechanism for
  possible future use*: rejected as speculative flexibility nobody requested
  (CLAUDE.md: "No 'flexibility' or 'configurability' that wasn't requested").
  `Routes.result(jobId)` itself is untouched and still reachable from
  `onJobSubmitted`/gallery navigation — only the *notification-triggered*
  entry point goes away.

## Decision: Remove the four now-orphaned notification strings

**Decision**: Remove `notify_channel_job_results`, `notify_job_done_title`,
`notify_job_failed_title`, and `notify_job_tap_to_view` from `strings.xml`.

**Rationale**: These strings are referenced only from `JobNotifications.kt`.
Once that file is deleted they become unused; leaving them in would fail the
same unused-resources hygiene this repo already enforces (the immediately
prior feature removed a string it had briefly introduced for the same
reason).

**Alternatives considered**: none — mechanical cleanup with no tradeoff.

## Decision: Leave `ic_shortcut_edit` untouched

**Decision**: The drawable referenced by `JobNotifications.kt` as the
notification's small icon is not deleted.

**Rationale**: It's also declared as the launcher shortcut icon in
`res/xml/shortcuts.xml:6`, an unrelated feature. Confirmed via search this is
its only other reference, so it stays.

## Decision: Accept the failure-staleness trade-off as-is (no compensating code)

**Decision**: No new mechanism is added to keep a silently-failed job's
status fresh while the app is backgrounded.

**Rationale**: This was explicitly decided in the 2026-07-04 clarification
session and is now documented in `spec.md`'s Edge Cases and Assumptions. It's
a disclosed, accepted consequence of the chosen removal approach, not a gap
this implementation needs to close.
