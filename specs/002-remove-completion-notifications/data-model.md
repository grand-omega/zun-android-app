# Phase 1 Data Model: Remove Completion Notifications

No entities, fields, or state transitions change. This is a subtractive
feature — no schema migration, no new Room table or column, no change to the
`JobEntity`/`JobStatusDto` shape.

## Entities

### Processing Job (existing — unchanged)

The spec's one Key Entity (`spec.md` → Key Entities). Its lifecycle
(`queued → running → done | failed | cancelled`) and its Room representation
(`data/local/JobEntity.kt`) are entirely unaffected by this feature.

What changes is **not the entity, but who observes it**: today, three
independent things read a job's status —

1. `ProgressViewModel` polls while its screen is `STARTED` (unaffected by
   this change).
2. `RealJobRepository.syncHistory()` refreshes `status = "done"` jobs on
   pull-to-refresh (unaffected — still runs).
3. `JobWatchWorker` polls a specific job id in the background purely to
   detect its terminal state for notification purposes (**removed** by this
   feature).

Removing (3) means a job's local status only updates via (1) or (2) going
forward. Since (2) never fetches `"failed"` jobs, a job that fails while no
`ProgressViewModel` is polling it will not be locally corrected until a user
reopens that job's own live view — this is the accepted trade-off recorded in
`spec.md`'s Clarifications/Edge Cases, not a new entity or field to model.

## Validation Rules

None introduced. No new invariants are added to the Processing Job entity by
this feature.
