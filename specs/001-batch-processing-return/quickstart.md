# Quickstart: Validate "Return to Running Batch"

Manual/instrumented validation scenarios proving the feature works
end-to-end. See `data-model.md` for the active-jobs query and
`contracts/internal-interfaces.md` for the exact interfaces exercised here.

## Prerequisites

- A running server the app is configured against (`Setup` screen completed),
  reachable over the network for job submission and polling.
- App installed in a debug build on an emulator/device (API 36+).

## Scenario 1 — Basic return path (User Story 1, P1)

1. On Home, select 2+ photos and submit a batch.
2. Confirm the app navigates to the live batch view (`BatchProgressScreen`).
3. Press back (or swipe back) before any photo finishes.
4. **Expect**: you land on Home, and Home shows an entry point indicating
   photos are still processing.
5. Tap the entry point.
6. **Expect**: you're back in the live view, with current status shown for
   every photo that was still processing (matching FR-001, FR-002).
7. Leave the live view again (back). **Expect**: processing is unaffected —
   confirm by returning again and seeing progress has advanced (FR-007).

## Scenario 2 — Entry point accuracy as jobs complete (User Story 2, P2)

1. Submit a batch of 3+ photos, back out to Home immediately.
2. Wait for some (not all) photos to finish (watch for the existing
   per-job completion notification/logic as a signal).
3. **Expect**: the entry point's count decreases to match only the
   still-processing photos (FR-003).
4. Wait for every photo to reach done/failed/cancelled.
5. **Expect**: the entry point disappears entirely (FR-004).

## Scenario 3 — Survives a full app restart (User Story 3, P3)

1. Submit a batch, back out to Home.
2. Force-stop the app (not just background it) while jobs are still
   processing server-side.
3. Reopen the app.
4. **Expect**: the entry point is present and accurate (FR-005), and tapping
   it opens a live view with correct current status.

## Scenario 4 — Concurrent batches (Edge case / FR-006)

1. Submit a batch, back out before it finishes.
2. Submit a second batch (of different photos) before the first finishes.
3. Back out to Home.
4. **Expect**: the entry point's count covers photos from both batches, and
   tapping it shows all of them in one live view — not just the second.

## Scenario 5 — Offline return (Edge case / FR-008)

1. Submit a batch, back out to Home.
2. Turn off network connectivity on the device.
3. **Expect**: the entry point is still shown, using last-known local status
   (no crash, no error state, no indefinite spinner).

## Automated coverage expected from `/speckit-tasks`

- Unit test: `JobRepository.activeJobIds()` / underlying `JobDao` query
  correctly excludes `done`/`failed`/`cancelled` and pending-delete ids.
- Instrumented test (`connectedDebugAndroidTest`): drives Scenario 1
  end-to-end through `AppNavHost` (submit → back → entry point → resume).
