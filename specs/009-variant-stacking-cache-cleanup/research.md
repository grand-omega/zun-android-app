# Research: Variant Stacking & Offline Cache Cleanup

## Decision 1: Stack grouping happens in SQL, not client-side, and keys off `COALESCE(lineageRootId, id)`

**Decision**: The paged/aggregate `JobDao` queries group rows by `COALESCE(lineageRootId, id)`
directly in SQL (one row returned per stack, plus a `stackCount`), rather than fetching individual
job rows and grouping them in Kotlin after paging.

**Rationale**:
- Spec's Edge Cases explicitly call out the risk: "What happens when a stack's variants span
  multiple pages of a paginated grid load?" Paging 3's `PagingSource` loads fixed-size pages; any
  client-side grouping applied *after* a page loads would be wrong whenever a stack's members
  don't all land in the same page — a variant could appear in its own right on page 2 while
  already counted into a stack shown on page 1. Grouping in SQL *before* pagination makes each
  page contain N distinct stacks, not N distinct jobs, so this failure mode doesn't exist by
  construction.
- Traced `lineageRootId`'s actual assignment (`LineageAssignment.kt`:
  `assignLineageRoot(newJobId, match) = match?.lineageRootId ?: match?.id ?: newJobId`) — every
  job that goes through `recordSourceLineage` (`JobUploader.kt`) gets a *non-null*, self-referencing
  `lineageRootId` even as the first job in its own chain (root jobs point at their own id, not
  `null`). `COALESCE(lineageRootId, id)` is still used as the grouping key rather than
  `lineageRootId` alone, purely as a defensive fallback for any job whose lineage recording never
  ran (the "best-effort" `runCatching` in `recordSourceLineage`, or a legacy pre-004 row) — such a
  job simply becomes a stack of one, keyed by its own id.
- A representative "cover" row per stack is selected as the most-recently-created member,
  matching the gallery's existing newest-first default ordering (`Assumptions` in spec.md).
- **Scoping note on FR-003** ("count reflects the currently-applied filters"): traced how the
  *existing* gallery filters are actually applied. Prompt/tag and favorites-only are SQL `WHERE`
  clauses in these same queries — the new `stackCount` naturally reflects both. The free-text
  search filter, however, is *not* a SQL clause today — `GalleryViewModel.pagedGridItems` applies
  it client-side, as a `PagingData.filter { it.matchesQuery(...) }` *after* a page is fetched
  (`GalleryViewModel.kt`). This is a pre-existing characteristic of the grid, not something this
  feature introduces: a page can already under-fill when search narrows it, independent of
  stacking. `stackCount` is therefore computed over the prompt/tag + favorites-only predicates
  only; when a search query is also active, the badge may not exactly match what search further
  narrows down to. Bringing search into SQL (e.g. joining prompt labels) would be a materially
  larger, unrelated change to how search works app-wide — out of scope for this feature per
  Principle II. Tasks should implement FR-003 against prompt/tag + favorites-only, and quickstart's
  manual check for FR-003 is scoped to a prompt filter, not a search string, accordingly.

**Alternatives considered**:
- *Client-side grouping after `Pager` loads a page*: rejected — exactly the page-boundary bug
  the spec calls out; would require abandoning Paging 3's page-at-a-time model or fetching much
  larger pages to "usually" avoid splits, which is not a real fix.
- *A new `stack_id` column maintained by a migration + backfill*: rejected as unnecessary
  complexity — `lineageRootId` already *is* a stable stack identifier; a duplicate column would be
  redundant with the existing lineage-tracking work from feature 004, violating Principle II
  (surgical, no speculative new state).
- *Window functions (`COUNT(*) OVER (PARTITION BY ...)`)*: SQLite has supported these since 3.25,
  and Android's bundled SQLite on a minSdk-36 device is well past that — technically viable, but a
  correlated subquery (`SELECT COUNT(*) FROM jobs j2 WHERE ...`) achieves the same result with SQL
  that's been used elsewhere in this DAO already (see `pagedDoneJobsCustom`'s existing subqueries)
  and needs no assumption about window-function support. Chosen for consistency with the existing
  query style in this file, not because window functions wouldn't work.
- The exact query text (representative-row selection alongside a correlated count) is something
  the implementation phase should nail down against real instrumented tests (Principle III), not
  something to over-specify here from static reading alone — Room/SQLite edge cases (tie-breaking
  two same-`createdAt` rows, `NOT IN (SELECT ... pending_deletes)` interaction) are exactly the
  kind of thing worth a red-then-green test rather than an assumption.

**Follow-up (PR review)**: the first implementation's join predicate was
`COALESCE(j2.lineageRootId, j2.id) = COALESCE(jobs.lineageRootId, jobs.id)` — correct, but wrapping
`j2`'s own columns in `COALESCE` prevents SQLite from using the index on `lineageRootId` (feature
004 already indexes it) for the inner-table lookup, forcing a full scan of `j2`/`j3` per outer row.
Rewritten as `(j2.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j2.lineageRootId IS
NULL AND j2.id = COALESCE(jobs.lineageRootId, jobs.id)))` — semantically identical (still handles
both the normal self-referencing-root case and the legacy/no-lineage-recorded fallback), but only
wraps the *outer* row's columns (evaluated once per outer row, not once per inner row), leaving
`j2.lineageRootId`/`j2.id` bare so the index can actually be used. All 6 `JobDaoStackingTest` cases
still pass unchanged after the rewrite — same behavior, cheaper query.

## Decision 2: A stack's filmstrip reuses the existing viewer, scoped by job-id set — not a new screen or ViewModel

**Decision**: Tapping a stacked cell opens the *existing* `PhotoViewerScreen`/`GalleryViewModel`
pair, with the pager's item set narrowed to just that stack's member job ids, computed on tap via
the same `JobRepository.getJobsByLineageRoot(rootId)` query the edit-history feature (004) already
uses (see `EditHistoryViewModel`, which does exactly this: one query, `stateIn`'d, no other
machinery).

**Rationale**: Spec FR-005 requires every existing per-image viewer action (favorite, delete,
details, edit history, save) to behave identically inside a stack's filmstrip as in the full
gallery. The cheapest way to guarantee identical behavior is to *literally reuse the same
mechanism* — filter which jobs the pager shows, don't fork the actions themselves. This also
satisfies FR-006 for free: deleting the last member updates the same underlying `jobs` flow the
grid also reads, so the stack's cell disappearing from the grid and the viewer closing both fall
out of the existing reactive `StateFlow` plumbing, not new bespoke logic.

**Alternatives considered**:
- *A dedicated `StackViewModel` + separate route*, mirroring `EditHistoryScreen`: rejected — that
  screen exists because History has fundamentally different content (a list, not a swipeable
  pager) and its own navigation entry point reachable from multiple places. A stack's filmstrip is
  the *exact same interaction* as the normal viewer, just pre-filtered; introducing a second
  ViewModel class for identical behavior would duplicate logic Principle II says not to duplicate.
- *Adding `lineageRootId` to `JobSummaryDto` so the UI can filter client-side without a fetch*:
  rejected for now — the on-tap `getJobsByLineageRoot` fetch is already a proven, existing,
  cheap query (used today for History); adding a DTO field purely to avoid one already-cheap query
  is speculative surface area this feature doesn't need.

## Decision 3: Cache cleanup needs one new read-only listing method; eviction re-validates at confirm time

**Decision**: Add `OfflineImageCache.listCachedJobs(): List<CachedJobSummary>` (job id + which
kinds are cached + total bytes for that job), built from the same `rootDir.walkTopDown()` pattern
`stats()`/`prune()` already use — grouped by the per-job subdirectory instead of flattened to a
byte/file total. Everything else (`delete(jobId)`, `clear()`) is reused unchanged. The confirm step
re-runs a connectivity check (reusing the pattern behind feature 008's `canPrefetchGivenNetwork`)
immediately before evicting, rather than trusting the preview snapshot.

**Rationale**:
- `OfflineImageCache.stats()` only returns an aggregate `(bytes, fileCount)` — there is no existing
  way to list *which* jobs are cached, which the preview grid (FR-008) needs. This is genuinely
  new surface, not a duplicate of anything existing.
- The spec's "safe to evict" framing (Edge Cases: "a network/offline-availability check changes
  between preview and confirm") is really about *availability*, not data loss: the server always
  has the authoritative copy, so eviction is never destructive in the durable sense — but if the
  user has gone offline between opening the preview and tapping confirm, evicting a cached image
  would leave it with *zero* currently-reachable copies until connectivity returns. Re-checking
  connectivity at confirm time (not just preview time) is the one piece of real "safety" logic
  this feature needs, and it's a near-exact reuse of a check this codebase already has one
  instance of.

**Alternatives considered**:
- *No re-check — trust the preview*: rejected — this is precisely the edge case spec.md calls out
  by name; skipping it would leave FR-010 unimplemented.
- *Evict anyway while offline, just with a warning*: rejected. `OfflineImageCache.delete(jobId)`
  removes a job's entire cache directory in one call — thumb, preview, and result together, not
  selectively — so while offline (no server fetch possible), evicting *any* cached job leaves it
  with zero reachable copies until connectivity returns, which is exactly what FR-010 rules out.
  There's no partial/safe subset to still allow here.
- **Chosen instead**: the confirm action *blocks* (doesn't silently skip or partially proceed)
  when offline at confirm time, with a clear message that cleanup needs connectivity and can be
  retried once back online. Previewing what's cached remains available offline — only the
  destructive step is gated.
- *A new persisted "eviction candidates" table*: rejected — this is a point-in-time computation
  over existing filesystem state, not something that needs its own storage or migration.
