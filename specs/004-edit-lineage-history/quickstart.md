# Quickstart: Validating Edit Lineage & Duplicate-Source History

## Prerequisites

- A reachable `zun-rust-server` (local dev or production, per feature 003's isolation) to actually run edit jobs.
- A debug or release build installed on a device/emulator, with at least two distinct source photos available to pick from.

## Automated checks

```bash
./gradlew :app:testDebugUnitTest --tests "*LineageAssignmentTest*"
./gradlew :app:connectedDebugAndroidTest --tests "*AppDatabaseMigrationTest*"
```

Expected: the pure hash-matching/`lineageRootId`-assignment logic passes for match/no-match/self-root cases, and the 4→5 migration test confirms existing rows survive with the three new columns defaulted to `null`.

## Manual validation

1. **Fresh photo, no indicator (Story 1 / AC1)**
   Pick a photo you've never submitted before on Home. Confirm no "edited before" indicator appears.

2. **Re-upload the same photo shows the indicator (Story 1 / SC-001)**
   Submit an edit for a photo and let it complete. Later, pick that exact same photo file again on Home. Confirm a non-blocking indicator appears (e.g. "Edited before — 1 time") and that submitting a new job still works normally without any extra confirmation step.

3. **"Use as new source" and independent re-upload both land in the same history (Story 2 / SC-002)**
   From a completed result, tap "use as new source" and submit a second edit. Then, save that second result externally (e.g. share/export) and re-pick the saved file from your gallery as a brand-new Home submission (not via the button). Open "View edit history" from any of the three jobs and confirm all three appear together, in chronological order.

4. **View edit history from any entry, not just a fresh detection (Story 3)**
   Open a Gallery entry that has no related jobs. Confirm "View edit history" is still present and shows just that one entry.

5. **Batch submission, partial match (Edge Case)**
   Select a batch of 3 photos where only 1 has been edited before. Confirm only that one photo shows the indicator; the other two are unaffected.

6. **Failed job doesn't count (Edge Case)**
   Submit a photo, then force the job to fail or cancel it before completion. Re-pick that same photo. Confirm no "edited before" indicator appears, and that failed attempt does not appear in any later history view.

7. **Hard-deleted job disappears from detection (FR-009)**
   Delete a job and wait past the 30-day undo window (or otherwise confirm server-side permanent deletion), then re-pick that same photo. Confirm no indicator appears referencing the deleted job. (If a full 30-day wait isn't practical, verify at the code/data level that a hard-deleted row's `sourceSha256`/`resultSha256`/`lineageRootId` are gone along with the row, per `data-model.md`.)

8. **Offline behavior (Constitution Principle IV)**
   With no network connectivity, open "View edit history" for a photo with existing local history. Confirm it still renders from local data — no error, no indefinite spinner.

9. **No regression to existing history (SC-004)**
   Confirm job history predating this feature's install still displays exactly as before — no new grouping, no indicator, no crash.
