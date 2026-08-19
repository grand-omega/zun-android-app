# Feature Specification: Variant Stacking & Offline Cache Cleanup

**Feature Branch**: `009-variant-stacking-cache-cleanup`

**Created**: 2026-07-07

**Status**: Draft

**Input**: User description: "Add two features: (1) Variant stacking in the gallery grid -- collapse generated images that share the same source photo (same lineage root, via the existing JobEntity.lineageRootId / JobDao.getJobsByLineageRoot / countByLineageRoot) into a single grid cell showing a stack-count badge, instead of flooding the grid with N separate tiles per source photo; tapping a stacked cell opens a horizontal filmstrip of just that stack's variants, reusing the existing photo-viewer pager. (2) Offline-cache cleanup flow with preview -- a new Settings action that computes which cached images are safe to evict (the server original is always recoverable, so eviction is never data-destructive), shows the user a preview grid of exactly what would be cleared before doing anything, and requires explicit confirmation before performing the irreversible local cache delete. Reuses the existing OfflineImageCache.stats()/delete(jobId)/clear() methods."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See one cell per source photo, not a flood of variants (Priority: P1)

A user has generated several AI edits from the same source photo (e.g. trying different prompts, or "try harder" retries). Today the gallery grid shows every one of those results as its own separate tile, so a single source photo's variants dilute the grid and make it harder to browse other, unrelated photos. Going forward, the grid collapses all variants sharing the same source photo into a single cell showing how many variants it holds.

**Why this priority**: This is the core value of the feature — without it, nothing changes for the user. It's also independently useful even before Story 2 exists (a visible stack count already tells the user "you have N edits of this photo" at a glance).

**Independent Test**: Generate 3+ edits from the same source photo, confirm the gallery grid shows exactly one cell for that photo with a count of 3 (or more), and confirm an unrelated single-edit photo still shows as its own normal cell.

**Acceptance Scenarios**:

1. **Given** a source photo has 3 generated variants, **When** the user opens Gallery, **Then** exactly one grid cell represents all 3, showing a badge with the count "3".
2. **Given** a source photo has exactly 1 generated variant, **When** the user opens Gallery, **Then** it appears as a normal, unstacked cell (no count badge) — stacking a single item adds visual noise for no benefit.
3. **Given** the currently-applied filters (prompt/tag, favorites-only, search) are active, **When** stacking is computed, **Then** the count badge only reflects variants that pass the current filter, not the photo's total variant count — so the number on screen always matches what tapping the stack would show.

---

### User Story 2 - Browse just one photo's variants in a focused filmstrip (Priority: P2)

Having seen a stacked cell (Story 1), the user wants to actually look through that photo's variants without them being interleaved with everything else in the main gallery pager.

**Why this priority**: This is what makes the stack count actionable rather than purely informational; it depends on Story 1 existing first.

**Independent Test**: Tap a stacked cell with 3 variants and confirm the viewer opens showing only those 3, swipeable, in a dedicated context — separate from the full-gallery swipe order.

**Acceptance Scenarios**:

1. **Given** a stacked cell for a source photo with 3 variants, **When** the user taps it, **Then** the photo viewer opens positioned on the first (or most recent) of those 3 variants, and swiping moves only between that photo's own variants, not into unrelated photos.
2. **Given** the user is inside a stack's filmstrip, **When** they use any existing viewer action (favorite, delete, view details, view edit history), **Then** it behaves exactly as it does in the normal full-gallery viewer today — this is the same viewer, scoped to a filtered set of jobs, not a new screen.
3. **Given** the user deletes every variant in a stack from inside its filmstrip, **When** the last one is removed, **Then** the viewer closes back to the gallery grid, which no longer shows that stack's cell.

---

### User Story 3 - Free up device storage without risking real data loss (Priority: P2)

A user's device is running low on storage, or they simply want to reclaim space used by cached AI-generated images. They want a way to clear that cache from within the app, but only after being shown exactly what will be removed and confirming — since a cache-clear is irreversible on-device even though the data itself isn't lost (the server keeps the original).

**Why this priority**: Independently valuable and independently shippable from Stories 1-2 — it touches Settings and the offline cache, not the gallery grid or viewer.

**Independent Test**: From Settings, start the cleanup flow, confirm a preview of exactly which cached images would be cleared is shown before anything happens, confirm the action, and confirm those images are gone from local cache afterward while remaining fully viewable again once re-fetched from the server.

**Acceptance Scenarios**:

1. **Given** the offline cache holds cached images, **When** the user opens the cleanup flow from Settings, **Then** they see a preview grid of exactly the images that would be evicted, before any deletion happens.
2. **Given** the preview is showing, **When** the user confirms, **Then** exactly those previewed images are cleared from local cache and no others.
3. **Given** the preview is showing, **When** the user backs out or cancels instead of confirming, **Then** nothing is deleted and the cache is left completely unchanged.
4. **Given** a cleared image is opened again after eviction, **When** the app fetches it from the server, **Then** it displays normally — confirming the eviction was never data-destructive, only a local-cache trim.

### Edge Cases

- What happens when a stack's variants span multiple pages of a paginated grid load? The stack must be computed consistently regardless of where page boundaries fall, so a variant never silently appears twice (once in its own page, once double-counted in a stack) or gets miscounted at a page seam.
- What happens if a job that's part of a stack is still processing (not yet `done`) when the grid renders? Only completed (`done`) jobs are visible in Gallery today, so an in-progress sibling isn't part of the visible stack or its count until it finishes — consistent with existing gallery behavior for any single job.
- What happens when the offline cache preview computes "safe to evict" but a network/offline-availability check changes between preview and confirm (e.g. the user goes offline mid-flow)? The confirm action re-validates against current state rather than blindly trusting the earlier preview snapshot, so nothing is evicted that would leave an image completely unavailable (see FR-010).
- What happens when there is nothing safe to evict (cache is already minimal, or everything cached is also the only available copy)? The cleanup flow tells the user there's nothing to clear rather than presenting an empty or misleading preview.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The gallery grid MUST group generated images sharing the same source photo (lineage root) into a single visual cell rather than one cell per generated image.
- **FR-002**: A stacked cell MUST display a count badge showing the number of variants it represents; a source photo with only one variant MUST NOT show a count badge.
- **FR-003**: The count shown on a stacked cell MUST reflect only variants passing the currently-applied gallery filters (prompt/tag, favorites-only, search), matching what tapping the stack would reveal.
- **FR-004**: Tapping a stacked cell MUST open the photo viewer scoped to just that stack's variants, independent of the full gallery's swipe order.
- **FR-005**: All existing per-image viewer actions (favorite, delete, details, edit history, save, etc.) MUST continue to work unchanged when reached via a stack's filmstrip.
- **FR-006**: Deleting the last remaining variant in an open stack's filmstrip MUST return the user to the gallery grid, which MUST no longer show that stack's cell.
- **FR-007**: Settings MUST expose a cache-cleanup action that computes which cached images can be safely evicted without ever making an image completely unavailable.
- **FR-008**: The cleanup flow MUST show the user a preview of exactly what would be cleared before any deletion occurs.
- **FR-009**: The cleanup flow MUST require an explicit confirmation step before performing the deletion; declining or backing out MUST leave the cache completely unchanged.
- **FR-010**: The confirm step MUST re-check eviction safety against current state rather than assuming the preview snapshot is still accurate, so a state change between preview and confirm (e.g. connectivity loss) cannot result in evicting an image's only available copy.
- **FR-011**: Evicting a cached image MUST NOT be treated as data loss — the underlying job and its ability to be re-fetched from the server MUST be unaffected.

### Key Entities

- **Variant Stack**: A grouping of generated images (jobs) that share the same source photo, identified by their existing lineage root. Represented in the grid as one cell with a count; not a new persisted entity — computed from existing job/lineage data.
- **Cache Eviction Candidate**: A cached local image file identified as safe to remove because the server retains the authoritative original. Computed at preview time from existing offline-cache accounting, not a new persisted entity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a source photo with N generated variants, the gallery grid shows exactly 1 cell for it (not N), for any N > 1.
- **SC-002**: A user can go from seeing a stack's count badge to viewing any specific variant within that stack in 2 taps or fewer.
- **SC-003**: 100% of cache-cleanup deletions are preceded by a preview the user could see before confirming — there is no path that deletes cached images without first showing what will be removed.
- **SC-004**: After a cache cleanup, 100% of the evicted images remain fully viewable again once re-fetched from the server (zero permanent data loss from the cleanup action).

## Assumptions

- "Same source photo" means the existing lineage-root grouping already used by the edit-history feature (004) — no new hashing or matching logic is introduced by this feature.
- Stacking only applies to the gallery grid's normal (non-selection-mode) browsing view; multi-select and drag-range-select continue to operate on individual jobs as they do today, unaffected by visual stacking.
- The offline-cache cleanup flow's "safe to evict" computation builds on the existing `OfflineImageCache` accounting (which already tracks what's recoverable from the server) rather than introducing new server-side bookkeeping.
- A stack's "cover" tile (the thumbnail shown for the collapsed cell) is its most recent variant, consistent with the gallery's existing newest-first default ordering.
