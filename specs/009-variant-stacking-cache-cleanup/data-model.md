# Phase 1 Data Model: Variant Stacking & Offline Cache Cleanup

## Variant Stack (computed, not persisted)

- **Represents**: a group of `done` jobs sharing the same effective lineage root
  (`COALESCE(lineageRootId, id)`), presented in the gallery grid as a single cell.
- **Key**: `COALESCE(lineageRootId, id)` of any member job — matches the existing key used by
  `JobDao.getJobsByLineageRoot`/`countByLineageRoot` (feature 004), reused rather than duplicated.
- **Fields exposed to the UI** (per grid row, computed in SQL, not stored):
  - `cover`: the stack's most-recently-created member job (matches the gallery's newest-first
    default) — this is what renders as the tile's thumbnail/metadata.
  - `stackCount`: number of member jobs passing the *currently applied* gallery filters
    (prompt/tag, favorites-only, search) — per spec FR-003, this must match what tapping the stack
    reveals, so it's computed under the same filter predicate as the grid query itself, not a
    global count.
- **Lifecycle**: purely derived at query time from existing `jobs` rows; nothing to create,
  migrate, or clean up. A stack of size 1 (no lineage siblings) is not visually a "stack" — no
  badge — per spec FR-002, even though it's the same underlying grouped-query shape (count = 1).
- **Validation**: none beyond what already constrains `JobEntity`/`lineageRootId` — this feature
  adds no new invariants, only a new read shape over existing data.

## Cached Job Summary (computed, not persisted)

- **Represents**: one job's entry in the offline cache-cleanup preview — i.e., "this job has
  something cached locally, and clearing it would free approximately N bytes."
- **Fields**:
  - `jobId`: which job's cache entry this is.
  - `cachedKinds`: which of thumb/preview/result are present (`OfflineImageCache.Kind`).
  - `bytes`: on-disk size of this job's cached files, for the preview's "frees ~X MB" framing.
- **Source**: computed by a new `OfflineImageCache.listCachedJobs()` walking the same
  `rootDir` the existing `stats()`/`prune()` methods already walk — grouped by the per-job
  subdirectory instead of flattened into one aggregate total.
- **Lifecycle**: a point-in-time snapshot for the preview screen; not persisted, not cached itself.
  Recomputed fresh each time the cleanup flow is opened.
- **Validation/safety**: eviction of a `CachedJobSummary` is only performed if a connectivity
  re-check at confirm time succeeds (see research.md Decision 3) — this is a *runtime* gate on the
  action, not a stored field on the summary itself.

## No schema changes

Both features are read shapes over data that already exists (`jobs.lineageRootId` from feature
004; the offline-cache filesystem `OfflineImageCache` already manages). No Room migration, no new
table, no new persisted preference.
