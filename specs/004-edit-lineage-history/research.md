# Phase 0 Research: Edit Lineage & Duplicate-Source History

## Current-state findings

- `JobEntity` (`app/src/main/java/dev/zun/flux/data/local/JobEntity.kt:9-26`, table `jobs`, `AppDatabase` version 4) has no `parentJobId`, no locally-persisted content hash, and no soft-delete column. Migrations 1→2→3→4 are idempotent "ensure current schema" converge-to-current migrations (`AppDatabase.kt:37-86`), tested by hand-building an old-version DB in `app/src/androidTest/java/dev/zun/flux/data/local/AppDatabaseMigrationTest.kt`.
- A SHA-256 content hash is **already computed** today, but only transiently: `sha256Hex(file)` (`util/ImageUtils.kt:107-118`) runs in `JobUploader.submitStagedJob` (`data/repo/JobUploader.kt:51`) purely as the server's dedup key (`input_sha256`) — never persisted client-side, discarded after the network call.
- Neither "regenerate" (`ui/result/ResultScreen.kt:170-206`) nor "use as source" (lines 240-256, wired through `AppNavHost.kt:180-183`) persists any backward link — both are fresh submissions with zero recorded relationship to the prior job. Lineage tracking is being added from scratch, not extended from an existing mechanism.
- Batch submissions have no shared batch entity — `HomeViewModel.submitBatch()` (`ui/home/HomeViewModel.kt:293-321`) submits each image independently, so **each image in a batch is already its own `Job` row**; per-image detection needs no batch-level joins.
- Two existing entry points need a "View edit history" action: `ResultScreen`'s overflow menu (`ui/result/ResultScreen.kt:232-271`) and `PhotoViewerScreen`'s top bar (`ui/gallery/PhotoViewerScreen.kt:29-32`, reached via the Material3 adaptive list-detail pane in `GalleryScaffold.kt:50-51`).
- Job status is a raw `String`, not an enum; `"done"` is the exact, already-pervasively-used filter for "successfully completed" (`JobDao.kt` lines 30-31, 44-45, 59-60, 77-78, 91-92).
- Soft/hard delete is server-side: `RealJobRepository.deleteJob()` (`data/repo/RealJobRepository.kt:238-244`) deletes the local Room row **immediately** (before the server's 30-day grace window even starts) and queues a `PendingDeleteEntity` + `DeleteSyncWorker` to tell the server; `restoreJob()` re-fetches from the server to repopulate Room if still within its window. There is no local table of "soft-deleted-but-undoable" jobs at all.
- Result images are cached **eagerly**, not on-view: `OfflineImageCache.prefetch(jobId, Kind.Result, url)` (`data/repo/OfflineImageCache.kt:82-120`) is invoked the moment a job's status is first observed as `"done"`, via `RealJobRepository.prefetchIfDone` → `prefetchJobImages` (lines 407-437), triggered from both live polling (`ProgressViewModel.kt:87` → `getJob`) and history sync (`listJobs`/`syncHistory`). The cached file lands at `filesDir/offline_images/{sanitized_jobId}/result.jpg`, durably available right after the atomic rename (`OfflineImageCache.kt:106`).
- `JobDao` has no partial-update method today — all writes are full-row `insertJob`/`insertJobs` (`REPLACE` conflict strategy) or `deleteJobById`. A new narrow `UPDATE jobs SET ... WHERE id = :jobId` method will be the first of its kind in this DAO.

## Decisions

### Decision: One data-model mechanism — dual content-hash matching + a flat `lineageRootId` grouping key — covers every linking scenario in the spec

Add three nullable columns to `JobEntity`: `sourceSha256` (hash of the bytes submitted as this job's source), `resultSha256` (hash of this job's cached result, once available), and `lineageRootId` (the grouping key for "view edit history").

At submission time, look up Room for any `status = 'done'` job whose `sourceSha256` **or** `resultSha256` equals the newly-picked image's hash. If found, the new job inherits that match's `lineageRootId` (or its `id`, if the match predates this feature and has no `lineageRootId` yet); otherwise the new job is its own root (`lineageRootId = id`). "View edit history" becomes a single indexed query: `WHERE lineageRootId = :root AND status = 'done' ORDER BY createdAt`.

**Rationale**: This single mechanism naturally satisfies every case in the spec without special-casing:
- Independent re-upload of the same original photo → same `sourceSha256` as a prior job.
- "Regenerate" → re-downloads and re-submits the exact same original input bytes → same `sourceSha256` as the job it regenerated from, with zero new plumbing.
- "Use as new source" (button) → the new job's source bytes equal the prior job's **result** bytes → matches via `resultSha256`.
- The `resultSha256` explicitly required by the spec's edge case ("a photo saved externally and re-picked later" without ever tapping "use as new source") → still matches via `resultSha256`, since matching is based on content, not on which UI action was used.

Storing these as plain columns on `JobEntity` (not a separate side-table) also satisfies FR-009 for free: when a job row is hard-deleted, its hash/lineage data disappears with it — there's no separate index to purge.

**Alternatives considered**:
- An explicit `derivedFromJobId` pointer written only when "use as new source" is tapped — rejected: it would miss the spec's explicit "saved externally, re-picked later" edge case, since that path never goes through the button at all.
- A separate lineage/edges table (graph model, one row per relationship) — rejected as more machinery than this needs (Principle II); the flat `lineageRootId` gives the same grouping with a single indexed column and no join/recursion at read time.
- Matching only on `sourceSha256` (no `resultSha256`) — rejected: doesn't cover "use as new source" or the saved-and-re-picked edge case at all, failing FR-004/Story 2 outright.

### Decision: `resultSha256` is computed once, eagerly, at the existing result-prefetch hook — not lazily on first view

Hook the hash computation into `OfflineImageCache.prefetch()` right after the atomic rename completes (`OfflineImageCache.kt:106`), for `Kind.Result` only, then persist it via a new `JobDao.updateResultHash(jobId, hash)` partial-update query.

**Rationale**: This point already runs eagerly for every job the instant it completes (via polling and history sync), not just when the user opens it — so `resultSha256` becomes available for matching as early as possible, maximizing how often a later re-upload/reuse is correctly detected. It also means no new network fetch is introduced; the file is already being written for the existing offline-cache purpose.

**Alternatives considered**: Computing lazily when the user first opens a result (e.g., in `ResultScreen`/`PhotoViewerScreen`) — rejected: it would leave `resultSha256` null for any completed job the user hasn't viewed yet, weakening detection for no real benefit, since the file is already being written regardless.

### Decision: Matching happens only at new-job submission time — never retroactively re-scanned

When a job's `resultSha256` is computed (asynchronously, after completion) or when `sourceSha256` is computed (at submission), the lineage lookup runs once, at that moment, against the jobs that exist *then*. It is never re-run to retroactively re-link jobs that were created earlier and might now match a hash that became available later.

**Rationale**: Consistent with FR-006's "no retroactive backfill" decision from `/speckit-clarify` — applying that same forward-only philosophy internally keeps the mechanism simple and avoids a background re-scan job. In practice this is a non-issue: a user can only plausibly save-and-re-pick a result *after* it has completed and already been hashed, so the ordering naturally works out.

**Alternatives considered**: A background reconciliation worker that periodically re-scans for newly-matchable hashes — rejected as unnecessary complexity for a scenario that doesn't arise in practice given the ordering above (Principle II).

### Decision: New "Edit History" screen reuses existing per-job navigation; no new mini-gallery component

The history view is a simple chronological list (existing Gallery-style list item components) of jobs sharing a `lineageRootId`. Tapping any entry navigates to the *existing* `Routes.result(jobId)` (or the existing gallery detail pane) for that specific job — reusing `ResultScreen`/`PhotoViewerScreen` and every action they already support (share, regenerate, use as new source), directly satisfying FR-007 with no new action-handling code.

**Rationale**: Principle II — the existing result/detail screens already do everything FR-007 requires; a new screen only needs to *list and route*, not reimplement actions.

## Testing approach

- Unit-test the pure hash-matching/`lineageRootId`-assignment logic in isolation (given a candidate hash and a list of existing jobs, does it pick the right root?) — mirrors the `ServerUrlsTest.kt` precedent (pure function, fully unit-testable, no Android framework dependency).
- Extend `AppDatabaseMigrationTest.kt` with the new 4→5 migration case, following its existing hand-built-old-schema pattern.
- The on-screen banner, "View edit history" menu entries, and the new history screen itself have no existing Compose/ViewModel test scaffolding in this repo (same situation as feature 003) — verified manually per `quickstart.md`, per Constitution Principle III's explicit-manual-verification-path allowance.
