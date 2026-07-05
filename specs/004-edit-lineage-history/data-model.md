# Phase 1 Data Model: Edit Lineage & Duplicate-Source History

## `JobEntity` (extended) — `app/src/main/java/dev/zun/flux/data/local/JobEntity.kt`, table `jobs`

Three new nullable columns added to the existing entity; `AppDatabase` version 4 → 5.

| Column | Type | Notes |
|---|---|---|
| `sourceSha256` | `String?` | SHA-256 of the bytes submitted as this job's source image. Reuses the hash already computed by `JobUploader.submitStagedJob` (`data/repo/JobUploader.kt:51`) for the server dedup key — no new hashing algorithm, just persisting a value already being computed. `null` for jobs created before this feature shipped (per FR-006, never backfilled). |
| `resultSha256` | `String?` | SHA-256 of this job's cached result file, computed once at the existing `OfflineImageCache.prefetch(..., Kind.Result, ...)` hook, immediately after the file is durably written (`OfflineImageCache.kt:106`). `null` until the result has been cached, and always `null` for non-`"done"` jobs. |
| `lineageRootId` | `String?` | Grouping key for "view edit history." Assigned once, at the moment `sourceSha256` or `resultSha256` is first computed for this job (see Validation Rules below). `null` only for legacy jobs that predate this feature. |

New indices: `@Index("sourceSha256")`, `@Index("resultSha256")`, `@Index("lineageRootId")` — all lookups are equality queries against `status = 'done'` plus one of these three columns.

### Validation / assignment rule (applies identically whether triggered by `sourceSha256` or `resultSha256` becoming known)

```
candidateHash = the newly-computed sourceSha256 or resultSha256
match = SELECT * FROM jobs
        WHERE status = 'done'
          AND (sourceSha256 = candidateHash OR resultSha256 = candidateHash)
          AND id != <this job's id>
        ORDER BY createdAt ASC LIMIT 1

if match found:
    lineageRootId = match.lineageRootId ?: match.id   -- fall back to match's own id if it predates this feature
else:
    lineageRootId = <this job's own id>                -- this job becomes a new root
```

This assignment is idempotent and forward-only (Decision: matching happens only at submission/completion time, never retroactively re-scanned — see `research.md`).

### State transitions

No new lifecycle states. `sourceSha256` is set once, at submission (job creation). `resultSha256` is set once, asynchronously, when the result is first cached after the job reaches `"done"`. `lineageRootId` is set once, at whichever of those two events happens first for a given job (normally `sourceSha256`, set at creation) — it is not recomputed afterward.

### Deletion behavior (FR-008 / FR-009)

- **Soft-deleted** (job still recoverable, per existing behavior): the local Room row is already deleted immediately by `RealJobRepository.deleteJob()` today (see `research.md`) — this is unchanged by this feature. Other jobs sharing the same `lineageRootId` are unaffected, since `lineageRootId` is a plain string value, not a foreign key requiring the referenced row to exist.
- **Hard-deleted / permanently gone**: since `sourceSha256`/`resultSha256`/`lineageRootId` are columns on the `jobs` row itself (not a separate table), deleting the row removes all three automatically — satisfying FR-009 with no additional cleanup code.

## No new entities

No new Room tables, no new server-side entities, no `FluxApi.kt` contract changes. This feature is entirely additive columns on the existing `JobEntity` plus new query methods on the existing `JobDao`.
