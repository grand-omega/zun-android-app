# Quickstart: Validate "Remove Completion Notifications"

Manual/automated validation scenarios proving the feature works end-to-end.
See `contracts/decommission-surface.md` for exactly what was removed and
`data-model.md` for why job status behavior changes slightly for failures.

## Prerequisites

- A running server the app is configured against (`Setup` screen completed).
- A fresh debug install is best for Scenario 1 (permission state is
  per-install); other scenarios work on any debug build.

## Scenario 1 — No permission prompt on first submit (User Story 2, P2)

1. Fresh-install the app (or clear its data) and complete `Setup`.
2. On Home, submit a single photo for the first time.
3. **Expect**: no system permission dialog appears at any point during
   submission (FR-003).

## Scenario 2 — No notification on success (User Story 1, P1)

1. Submit a photo, then background the app (Home button) before it finishes.
2. Wait for the generation to complete server-side.
3. **Expect**: no notification appears in the system tray (FR-001).
4. Reopen the app. **Expect**: the finished photo is visible in Gallery as
   normal (Acceptance Scenario 3).

## Scenario 3 — No notification on failure (User Story 1, P1)

1. Submit a photo that will fail (e.g. an unreachable/invalid workflow, or
   force a server-side failure per your test setup), then background the app.
2. Wait for the job to fail server-side.
3. **Expect**: no notification appears (FR-002).

## Scenario 4 — Accepted staleness for a silent failure (Edge Case)

1. Submit a batch, back out to Home so the "still processing" entry point
   shows a count, then background the app until one job fails server-side
   without reopening its live view.
2. Reopen the app to Home.
3. **Expect** (per the 2026-07-04 clarification): the entry point's count may
   still include the failed job — this is accepted, not a bug.
4. Tap the entry point to reopen the live batch view.
5. **Expect**: the view re-polls immediately and correctly shows the job as
   failed; the entry point's count then reflects reality.

## Scenario 5 — Processing itself is unaffected (FR-004)

1. Submit a batch of several photos.
2. **Expect**: every job still reaches `done`/`failed` normally, at the same
   pace as before this change — only the notification is gone, not the
   processing.

## Automated coverage expected from `/speckit-tasks`

- Compile-clean deletion: `./gradlew :app:compileDebugKotlin` succeeds with
  zero references to `JobNotifications`, `JobWatchWorker`,
  `ACTION_VIEW_RESULT`, or the removed `AppNavHost`/`MainActivity` parameters
  anywhere in `app/src/main`.
- Full existing unit-test suite (`./gradlew :app:testDebugUnitTest`) stays
  green with no changes needed, since no existing test references the
  removed classes (confirmed in `research.md`).
- `spotlessCheck`/lint clean after removing the four now-unused strings.
