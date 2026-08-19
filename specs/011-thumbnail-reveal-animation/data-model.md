# Phase 1 Data Model: Gallery Thumbnail Reveal Animation

## No new persisted entities

Per the spec's Assumptions, nothing about this feature is saved to Room, `SharedPreferences`, or
anywhere else durable. Everything below is in-memory, `GalleryViewModel`-lifecycle-scoped state.

## `GalleryViewModel` (in-memory only)

- **`revealEligibleJobIds: StateFlow<Set<String>>`**: job IDs that transitioned from active to
  done while this ViewModel has been observing (research.md Decision 1), and haven't yet had their
  one-time reveal consumed (Decision 2). Starts empty; populated by the `activeJobIds()`-diffing
  collector started in `init{}`; entries are removed by `markRevealed(jobId)`.
- **`previousActiveIds: Set<String>?`** (private, not exposed): the last-seen `activeJobIds()`
  emission, used purely to compute the diff on the next emission. `null` until the first emission
  arrives, so that first emission establishes a baseline instead of being diffed against an assumed
  empty set (this is what makes FR-003 — no reveal for jobs already done before Gallery opened —
  correct without any separate "was Gallery open when this finished" tracking).

## `GalleryThumbnail.kt` (per-cell, transient Compose state)

- No new state held across recompositions beyond what `animateFloatAsState` already manages
  internally for the reveal progress value — deliberately not `remember`-scoped for "has this
  played," since that needs to survive scroll-driven dispose/recompose cycles and therefore lives
  in the ViewModel instead (research.md Decision 2).

## Relationship to existing data

- No change to `JobSummaryDto`, `JobWithStackCount`, or any Room entity/query. The reveal-eligible
  set is keyed by the same `job.id` the grid already uses as its Compose key
  (`GalleryScreen.kt:522`) — no new join, no new column, no new field threaded through the paging
  pipeline.
