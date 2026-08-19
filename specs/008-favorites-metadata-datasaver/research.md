# Research: Favorites, Generation Details, and Cellular Data Control

No `NEEDS CLARIFICATION` markers remain in the Technical Context. This document records the
concrete technical decisions for each of the three stories, grounded in direct inspection of the
current code (not assumed).

## Decision 1: Favorite flag — schema, and surviving a server resync

- **Decision**: Add `val isFavorite: Boolean = false` to `JobEntity`. Bump
  `@Database(version = 5 ...)` to `6`, add `MIGRATION_5_6 = schemaMigration(5, 6)` to
  `AppDatabase`'s `.addMigrations(...)` list, and add
  `addColumnIfMissing(db, jobColumns, "jobs", "isFavorite INTEGER NOT NULL DEFAULT 0")` to
  `ensureCurrentSchema` — this is the exact idempotent-DDL pattern the four prior migrations
  (1→2 through 4→5) already use, not a new mechanism.
- **Critical finding**: `JobDao.insertJob(s)` uses `@Insert(onConflict = OnConflictStrategy.REPLACE)`.
  Every server resync (`syncHistory()`, `listJobs()`) re-derives a fresh `JobEntity` via
  `it.toEntity()` and upserts with REPLACE — a full-row replace. `RealJobRepository` already hit
  this exact problem once for `sourceSha256`/`resultSha256`/`lineageRootId` (local-only fields
  the server doesn't return) and solved it with a `carryForwardLineage(entities)` helper that
  fetches the existing rows by id and copies those three fields onto the fresh entity before
  the upsert (`RealJobRepository.kt:439-449`). `isFavorite` is the same class of local-only
  field — without the same treatment, a favorite would silently disappear the next time the
  gallery does a background sync. **`carryForwardLineage` must be extended to also carry
  `isFavorite` forward** (rename to something like `carryForwardLocalFields` if that reads
  better, but the mechanism is identical — no new pattern needed).
- **Alternatives considered**: A separate "favorites" table keyed by job id (avoids touching
  `JobEntity` at all). Rejected — adds a join to every gallery query for one boolean, and still
  needs the exact same "don't let sync wipe it" handling, just against a different foreign key;
  a plain column is simpler per constitution Principle II.

## Decision 2: Combinable "favorites only" filter

- **Decision**: `GalleryViewModel` already threads one filter dimension (`TagFilter.All` /
  `ByPromptId` / `Custom`) into two places: the in-memory `jobs` `StateFlow` (used by
  `PhotoViewerScreen`'s pager) and the Paging 3 `pagedGridItems` flow, which calls
  `jobRepo.pagedJobs(promptId, customOnly, newestFirst)`. Add a second, independent
  `_favoritesOnly: MutableStateFlow<Boolean>`, combine it into both existing `combine(...)`
  chains alongside `_tagFilter`, and add a `favoritesOnly: Boolean` parameter to
  `JobRepository.pagedJobs(...)` and each of `JobDao.pagedDoneJobsAll` /
  `pagedDoneJobsByPromptId` / `pagedDoneJobsCustom` (`AND (:favoritesOnly = 0 OR isFavorite = 1)`
  in each query's `WHERE` clause). `JobSummaryDto` also needs an `isFavorite` field added
  (mirroring `toSummaryDto()`) so the in-memory `jobs` flow used by the photo-viewer pager can
  filter identically without a second DB round-trip.
- **Rationale**: This is additive to an existing, already-combinable filter mechanism — favorites
  becomes a second independent axis, not a replacement for or a mode within `TagFilter`, matching
  the spec's FR-003 requirement that it combine with prompt-based filters.
- **Alternatives considered**: Folding "favorites" into the `TagFilter` sealed interface as a new
  case (`TagFilter.Favorites`). Rejected — `TagFilter`'s cases are mutually exclusive by
  construction (`when` over one filter), which would make "favorited + this specific prompt"
  impossible to express, directly contradicting FR-003.

## Decision 3: Generation-details bottom sheet

- **Decision**: A new composable (`GenerationDetailsSheet`, in `ui/common/` since it's a
  presentation-only component with no feature-specific state, similar to how `Polish.kt`'s
  shared composables live there) using `ModalBottomSheet` — the same Compose component already
  used in `ui/home/PromptSheets.kt`, so this introduces no new UI mechanism to the codebase.
  Triggered from `PhotoViewerScreen`'s existing action bar (an info icon, alongside delete/save/
  share) and reads directly from the already-loaded `JobSummaryDto`/`JobStatusDto` for the
  current image: `prompt_text`, `workflow`, `seed` (as the try-harder indicator — see below),
  `created_at`, `completed_at`.
- **Try-harder indicator**: There is no separate boolean field for "was this a try-harder job" —
  `tryHarderAvailable`/the try-harder toggle (`HomeRoute.kt`, `HomeScreen.kt`) selects which
  *workflow* name gets submitted (`TRY_HARDER_WORKFLOW` vs the default). The sheet shows this by
  checking whether the job's `workflow` field equals the try-harder workflow constant, not by
  reading a dedicated flag — no new field needed.
- **Alternatives considered**: A full-screen details page instead of a sheet. Rejected — this is
  quick-glance metadata for an image already being viewed; a sheet keeps the viewer's own state
  (before/after position, current pager index) untouched underneath, matching FR-006 directly.

## Decision 4: Wi-Fi-only vs. allow-cellular-data

- **Decision, uploads**: `RealJobRepository`'s upload-enqueue path already builds a
  `Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)` for `JobUploadWorker`.
  Change this to read the new `SettingsManager` preference and set
  `NetworkType.UNMETERED` when Wi-Fi-only is selected, `NetworkType.CONNECTED` when cellular is
  allowed. This is WorkManager's standard, built-in mechanism for exactly this kind of
  preference — no manual connectivity polling needed for the upload path, and WorkManager
  automatically re-attempts once the required network type becomes available (matching spec
  Edge Case 3: a mid-transfer Wi-Fi drop holds/retries rather than falling back to cellular).
- **Decision, automatic result downloads**: `OfflineImageCache.prefetch(...)` is called
  *inline* (suspend function, not via WorkManager) from `RealJobRepository`'s
  `prefetchIfDone`/`prefetchDone`/`prefetchJobImages` — these run as part of the same coroutine
  that just synced job state, not as an independently-constrained background job. Gate these
  call sites behind a manual `ConnectivityManager.getNetworkCapabilities(...)` check
  (`NET_CAPABILITY_NOT_METERED`) when the setting is Wi-Fi-only, skipping the prefetch (not
  erroring) when only cellular is available — consistent with the existing "unavailable
  offline" degradation path already used elsewhere for uncached content (constitution
  Principle IV).
- **Decision, explicit actions**: "Save to device" and "Share" already operate on a URI the app
  either already has cached or resolves through `ImageSourceRepository`/`MediaStoreSaver` — these
  call sites are left untouched, satisfying FR-010 by simply not adding a connectivity check to
  them.
- **Alternatives considered**: Wrapping `prefetch()` calls in a one-off `WorkManager` job with a
  `NetworkType` constraint instead of a manual check. Rejected — these prefetches are
  opportunistic, best-effort, and already running inline as part of a larger sync operation;
  deferring them to a separately-scheduled worker would be a bigger structural change than the
  feature calls for, and a manual check achieves the same user-facing behavior (skip on
  cellular, proceed on Wi-Fi) with far less code.
