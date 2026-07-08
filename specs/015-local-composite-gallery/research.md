# Research: Local Composite Gallery Entries

## Decision 1: Identify a client-only entry by a reserved id prefix, not a new Room column

**Decision**: A saved composite gets id `"local-composite-${UUID.randomUUID()}"`. Every place that
needs to know "is this a client-only entry" — `previewModel`/`resultModel`/`thumbModel`/
`offlineAvailability`/`deleteJob` in `RealJobRepository`, the visual badge in `GalleryThumbnail.kt`,
and the action-visibility gate in `PhotoViewerScreen`'s `ViewerActionBar` — checks
`jobId.startsWith("local-composite-")` on the id string it already has in hand. `JobEntity`'s schema
is otherwise unchanged.

**Rationale**: Confirmed by reading `JobEntity.kt`/`JobDao.kt` in full: there's no `@ForeignKey`
anywhere in this schema, `promptId` is a plain nullable `Long`, and the three paged gallery queries
only require `status = 'done'` — a row with every other column null/placeholder renders correctly
with no NPE risk anywhere downstream (confirmed across `JobDetailsSheet`, `GalleryThumbnail.kt`,
`resolvePromptLabel()` — all null-safe already). Given that, the only real design question is how to
*flag* a row as client-only, and every consumption site already holds the job id string directly —
several of them (`previewModel`, `thumbModel`, `resultModel`) are synchronous, non-suspend functions
called straight from Compose, so they can't cheaply do a live Room lookup inline anyway. A string
prefix is a zero-migration, zero-DTO-threading way to carry that one bit of information to exactly
where it's needed, in a codebase that already has real precedent for "the id told us what kind of
thing this is."

**Alternatives considered**:
- *A new `isLocalOnly: Boolean` column on `JobEntity`* (Room v6→v7 migration, matching the
  `isFavorite` v5→v6 precedent): more conventional, but real cost — it would need threading through
  `JobSummaryDto`/`JobWithStackCount` the way `stackHasFavorite` was for feature 014 (extra column in
  3 paged queries, extra DTO field, extra `toSummaryDto()` wiring) just to reach `GalleryThumbnail.kt`,
  and `previewModel`/`thumbModel`/`resultModel` would each need either a synchronous Room lookup
  (this DAO isn't currently exposed that way) or the flag passed in as an extra parameter at every
  call site. Rejected as strictly more surgery for identical behavior.
- *A separate Room table for local composites, unioned into the gallery feed at query time*:
  rejected outright — Room doesn't make ad-hoc `UNION` across differently-shaped paged sources easy
  with Paging 3, and it would require re-deriving all of `pagedDoneJobsAll`'s ordering/favorites/
  stacking logic a second time for a second table, doubling the surface area for a bug like the one
  feature 014's `/speckit-analyze` pass caught in `mapRectToSource`.

## Decision 2: Store composite bytes outside `OfflineImageCache`'s managed root — never inside it

**Decision**: A saved composite's flattened image lives at
`context.filesDir/local_composites/<id>/composite.jpg`, a directory `OfflineImageCache` never
touches. `previewModel`/`resultModel`/`thumbModel` resolve a local-composite id straight to this
file's `Uri`, never calling into `OfflineImageCache` or falling back to `buildUrlOrNull(...)` at all.

**Rationale**: Fully read `OfflineImageCache.kt` and `CacheCleanupViewModel.kt`. Two real risks would
exist if a composite's only copy lived under `OfflineImageCache`'s `rootDir`:
1. `prune()` (auto-triggered every 16 prefetches) walks the *entire* `rootDir` and LRU-evicts by
   `lastModified` with zero concept of "server-backed vs. not" — a composite could be silently
   deleted once the cache exceeds its byte budget, with no way to get it back (research.md's Edge
   Case: there is no server copy to re-fetch).
2. "Clear offline cache" (`CacheCleanupViewModel.confirmClear()`) iterates
   `OfflineImageCache.listCachedJobs()` — every subdirectory of `rootDir` — and evicts all of them
   unconditionally. Its own gating on network connectivity exists *precisely because* eviction
   assumes "this is safely re-fetchable from the server," an assumption that's false for a composite.

Storing outside `rootDir` makes FR-011 ("must be protected from Clear offline cache") true by
construction — neither `prune()` nor `confirmClear()` ever enumerates this directory — rather than
requiring new filtering logic inside `OfflineImageCache.kt` (which today has no Room dependency at
all, and filtering by "is this a real job" would have to introduce one, coupling a currently
self-contained file-cache utility to the job database).

**Alternatives considered**:
- *Store inside `rootDir` under a new `Kind.Composite`, and teach `listCachedJobs()`/`prune()` to
  skip ids matching the reserved prefix*: works, but couples a previously pure-filesystem utility to
  the id-naming convention in two more places than the "just don't put it there" approach needs, for
  no actual benefit — rejected as unnecessary complexity for the same outcome.

## Decision 3: `deleteJob` skips the server-delete-sync queue entirely for a local-composite id

**Decision**: `RealJobRepository.deleteJob(jobId)` checks the reserved prefix first. For a local
composite: delete the Room row and the `local_composites/<id>/` directory directly — no
`insertPendingDelete`, no `DeleteSyncWorker.enqueue`. For everything else: unchanged.

**Rationale**: Confirmed `deleteJob()`'s existing behavior is already optimistic/local-first
(Room row + cache removed immediately; server call happens later via `DeleteSyncWorker`) and already
degrades gracefully for an id the server has never heard of — `syncPendingDeletes()`'s existing
`catch (e: HttpException) { if (e.code() == 404) { ...clean up silently... } }` branch would handle
it with no crash and no user-visible error. So this isn't a bug fix — it's tightening the behavior to
match FR-007's literal requirement ("no server round-trip at any point"), which the graceful-404 path
technically violates once (a network call still happens, it just harmlessly fails). Skipping the
queue entirely for a local-composite id is a small, targeted addition that also removes one wasted
future network call per deleted composite.

## Decision 4: `offlineAvailability` reports "always available" for a local-composite id

**Decision**: Same prefix check, short-circuiting to a fully-available result before it would
otherwise consult `OfflineImageCache.availability(jobId)` (which, per Decision 2, was never asked to
cache anything for this id in the first place and would report "not cached").

**Rationale**: Without this, a composite would show the existing "needs network" badge
(`NeedsNetworkIcon`, `GalleryThumbnail.kt`) in the grid — actively wrong and confusing, since FR-007
guarantees a composite never needs network for anything. This directly serves FR-007/SC-002.
