# Phase 1 Contracts: Remove Completion Notifications

This feature has no external/server-facing interface change — `FluxApi.kt`
and `../zun-rust-server/docs/API_CONTRACT.md` are unaffected (Constitution V:
N/A). There are no new internal interfaces either, since this is a pure
removal. Instead, this document is the **decommission surface**: the exact
internal contracts being retired, and what must confirm no one else depends
on them before deletion — verified once during Phase 0 research, to be
re-verified by the implementer at task time.

## 1. `JobNotifications` (object, whole file removed)

- **Public surface being retired**: `ACTION_VIEW_RESULT`, `EXTRA_JOB_ID`,
  `canNotify(context)`, `notifyJobFinished(context, jobId, succeeded)`.
- **Confirmed callers** (all being removed in the same change):
  `JobWatchWorker` (posts), `HomeRoute` (`canNotify` check),
  `MainActivity` (`ACTION_VIEW_RESULT` / `EXTRA_JOB_ID` constants).
- **Confirmed non-callers**: no test file in `app/src/test` or
  `app/src/androidTest` references this class.

## 2. `JobWatchWorker` (object + class, whole file removed)

- **Public surface being retired**: `JobWatchWorker.enqueue(context, jobId)`.
- **Confirmed callers**: `HomeRoute.kt`, two call sites (single-job submit,
  batch submit forEach) — both removed in the same change.
- **Side-effect dependency check**: `JobWatchWorker` calls
  `RealJobRepository.getJob()`, which also persists to Room. This is *not* a
  unique capability — `ProgressViewModel` already calls the same repository
  method independently while its screen is open. No other code relies on
  `JobWatchWorker` specifically for Room updates.

## 3. `HomeRoute` permission-request flow (call sites removed, function stays)

- **Retired**: the `notificationPermissionLauncher`
  (`rememberLauncherForActivityResult(RequestPermission())`) and its two
  `.launch(Manifest.permission.POST_NOTIFICATIONS)` call sites inside the
  `SubmitState.Done` / `SubmitState.DoneBatch` branches.
- **Unaffected in the same branches**: `onJobSubmitted(s.jobId)` /
  `onBatchSubmitted(s.submittedIds)` and the existing failed-upload snackbar
  — these drive navigation to the progress/batch screens and are unrelated
  to notifications; they must remain.

## 4. `MainActivity` / `AppNavHost` notification deep-link (parameter pair removed)

- **Retired**: `MainActivity.pendingResultJobId`, `MainActivity.resultJobId()`,
  and the `navigateToResultJobId` / `onResultNavConsumed` parameters on
  `AppNavHost` plus their `LaunchedEffect`.
- **Confirmed this is the only caller of `Routes.result(jobId)` being
  removed**: `onJobSubmitted`/gallery/result-screen navigation to
  `Routes.result(...)` elsewhere in `AppNavHost` is untouched — only the
  notification-triggered entry point goes away, not the route itself.

## 5. `AndroidManifest.xml` — `POST_NOTIFICATIONS` permission

- **Retired**: `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`.
- **Confirmed sole consumer**: `JobNotifications` (via item 1 above).

## 6. `strings.xml` — four notification strings

- **Retired**: `notify_channel_job_results`, `notify_job_done_title`,
  `notify_job_failed_title`, `notify_job_tap_to_view`.
- **Confirmed sole consumer**: `JobNotifications.kt`.
- **Explicitly NOT retired**: `ic_shortcut_edit` (drawable) — shared with
  `res/xml/shortcuts.xml`, an unrelated feature.
