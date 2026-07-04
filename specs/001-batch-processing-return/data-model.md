# Phase 1 Data Model: Return to Running Batch

No schema changes or migrations are required. This feature reads the existing
`jobs` Room table (`app/src/main/java/dev/zun/flux/data/local/JobEntity.kt`)
through one new filtered query; it does not add, remove, or alter any column.

## Entities

### Processing Job (existing — `JobEntity`)

Represents a single photo submitted for background processing. Already
persisted by `RealJobRepository` whenever job status is fetched (on submit,
while a progress screen polls, and by `JobWatchWorker` in the background).

| Field | Type | Relevance to this feature |
|---|---|---|
| `id` | String | Identifies the job; used to build `Routes.batch(jobIds)` |
| `status` | String | Drives inclusion in "active" (see State below) |
| `createdAt` | Long | Not used by this feature (no new ordering requirement) |

No new fields are added to `JobEntity`.

### Active Job Set (derived — not a stored entity)

A read-only, always-current view computed from `JobEntity` rows, not a new
table:

```
activeJobIds = SELECT id FROM jobs
               WHERE status NOT IN ('done', 'failed', 'cancelled')
               AND id NOT IN (SELECT jobId FROM pending_deletes)
```

This mirrors the existing `getVisibleJobs()` visibility rule (excluding
pending local deletes) already used elsewhere in `JobDao`, just with the
status filter inverted from "done only" to "not yet terminal."

### Batch

Not a stored concept — there is no batch identifier column anywhere in the
data model, and this feature does not introduce one (see `research.md`,
"Aggregate all active jobs across concurrent batches"). A "batch," for this
feature's purposes, is simply "the current set of active job ids" at the
moment the user taps the entry point.

## State Transitions

Each `Processing Job` has exactly one current `status`, sourced from the
server response (`JobStatusDto.status` / `JobSummaryDto.status`):

```
queued/running (non-terminal, counted as "active")
        │
        ▼
  done | failed | cancelled   (terminal, excluded from "active")
```

- A job also becomes excluded immediately if it is locally deleted (present
  in `pending_deletes` or `localDeletedIds`), independent of its `status`.
- Transitions are one-directional (no terminal status reverts to
  non-terminal) — consistent with existing `PollState` handling in
  `ProgressViewModel.kt:58-65`.

## Validation Rules

- FR-004 (entry point disappears once nothing is active) is satisfied purely
  by `activeJobIds` becoming empty — no separate "dismissed" flag is needed
  or introduced.
- FR-008 (correct status with no network) is satisfied because the query
  reads only local Room state; no network call is on the path of showing or
  updating the entry point.
