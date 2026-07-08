# Quickstart: Validating Variant Stacking & Offline Cache Cleanup

## Prerequisites

- A debug build on a device/emulator with several completed generated images, including at
  least one source photo with 3+ generated variants (submit the same photo multiple times, or
  use "use as new source"/regenerate — same lineage-building mechanism as feature 004).
- For Story 3: some images already downloaded into the offline cache (opening them once in
  Gallery is enough — prefetch caches thumb/preview/result automatically).
- A way to toggle network connectivity off (airplane mode, or the emulator's network controls)
  to validate the offline-safety re-check.

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
./gradlew spotlessCheck
```

Per Constitution Principle III, this feature should add:
- Instrumented `JobDao` tests for the new grouped/count query: a source photo with 3 done
  variants returns exactly one row with `stackCount = 3`; a photo with exactly 1 variant returns
  a row with `stackCount = 1` (no badge shown by the UI, per FR-002); a `pending_deletes` row and
  a non-`done` sibling are correctly excluded from the count; the count changes correctly when
  the active filter (prompt/favorites-only/search) narrows the underlying set (FR-003).
- A `GalleryViewModel`/`PhotoViewerScreen` test confirming that opening a stack scopes the
  pager to just that stack's job ids, and that deleting the last one closes back to the grid
  with the stack's cell gone (FR-004, FR-006).
- A unit test for `OfflineImageCache.listCachedJobs()` confirming it reports the right jobs/kinds
  from a set of files written to a temp `rootDir`.
- A unit test for the cache-cleanup confirm gate: confirm proceeds when connectivity is present,
  and is blocked (with nothing deleted) when a stubbed connectivity check reports offline,
  regardless of what the earlier preview showed (FR-010).

## Manual validation

1. **See one stacked cell, not a flood (Story 1 / FR-001-003)**
   Generate 3 variants from the same source photo. Open Gallery — confirm exactly one cell
   represents all 3, with a "3" badge. Confirm an unrelated single-edit photo still shows as a
   normal cell with no badge. Apply a prompt filter that only 2 of the 3 variants match — confirm
   the badge updates to "2".

2. **Browse a stack in its own filmstrip (Story 2 / FR-004-006)**
   Tap the stacked cell from step 1. Confirm the viewer opens on one of that stack's variants and
   swiping only moves between the 3 of them — not into unrelated gallery photos. Try favorite,
   details, and edit-history from inside this filmstrip — confirm each behaves exactly as in the
   normal full-gallery viewer. Delete all 3 variants one by one — confirm the viewer closes back
   to the grid on the last deletion, and the stack's cell is gone.

3. **Cache cleanup shows a preview before deleting anything (Story 3 / FR-007-009)**
   From Settings, open the cache-cleanup flow. Confirm a preview grid of currently-cached images
   appears before anything is deleted. Cancel/back out — confirm nothing was removed (re-open
   the flow, same preview appears). Re-open and confirm this time — confirm exactly the
   previewed images are gone from local cache, and re-opening one from Gallery re-fetches it
   normally from the server (not broken/missing).

4. **Cleanup is blocked while offline, not silently unsafe (Story 3 / FR-010)**
   Open the cleanup preview while online, then turn on airplane mode before confirming. Tap
   confirm — expect the app to block the deletion with a clear "needs connectivity" message
   rather than proceeding — and confirm nothing was actually deleted. Turn connectivity back on
   and confirm again — expect it to proceed normally this time.
