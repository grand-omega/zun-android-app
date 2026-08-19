# Feature Specification: Stack Scope Filter Alignment

**Feature Branch**: `016-stack-scope-filter-alignment`

**Created**: 2026-08-19

**Status**: Draft

**Input**: Deferred from the PR #21 review round. Copilot flagged that the gallery's stack badge and the stack viewer disagree about which jobs belong to a stack; investigation found four separate mismatches, one of which was fixed in that release and three of which were deferred here to avoid widening a 24-commit release.

## Background

Opening a stack from the gallery calls `GalleryViewModel.openJob`, which resolves the stack's members through `JobRepository.getJobsByLineageRoot(rootId)` and scopes the viewer's pager to that set. The badge on the grid tile comes from a different place: a correlated subquery inside `JobDao.pagedDoneJobsAll` / `pagedDoneJobsByPromptId` / `pagedDoneJobsCustom`.

Those two paths apply different predicates, so the count the user sees on the tile and the number of images they can swipe through can differ.

The fourth mismatch — the lineage root being excluded when its own `lineageRootId` is null — was fixed in the PR #21 release (`JobDao` gained `OR (lineageRootId IS NULL AND id = :rootId)` in both `getJobsByLineageRoot` and `countByLineageRoot`, covered by `JobDaoLineageRootTest`). This spec covers only what remains.

## Remaining mismatches

| Predicate | Grid badge subquery | `getJobsByLineageRoot` |
|---|---|---|
| `status = 'done'` | applied | applied |
| lineage root match incl. null roots | applied | applied (fixed in #21) |
| `favoritesOnly` | applied | **missing** |
| `promptId` / `customOnly` | applied | **missing** |
| `NOT IN pending_deletes` | applied | **missing** |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The stack I open matches the stack I was shown (Priority: P1)

A user filtering the gallery — by favorites, or by a prompt tag — taps a stack whose badge reads 2. The viewer must contain exactly those 2 images, not every variant in that lineage regardless of the active filter.

**Why this priority**: This is the whole feature; without it the badge is not a reliable description of what tapping does.

**Independent Test**: Favorite 2 of 3 variants in one lineage. Turn on the favorites filter. Confirm the tile reads 2 and that swiping in the viewer reaches exactly 2 images.

**Acceptance Scenarios**:

1. **Given** the favorites filter is on and a lineage has 2 favorited and 1 non-favorited variant, **When** the user opens that stack, **Then** the viewer contains only the 2 favorited variants.
2. **Given** a prompt-tag filter is active, **When** the user opens a stack, **Then** the viewer contains only variants matching that tag.
3. **Given** no filter is active, **When** the user opens a stack, **Then** the viewer contains every done variant in the lineage — the behaviour shipped in #21.

### User Story 2 - A deleted variant does not reappear inside a stack (Priority: P2)

A user deletes one variant. The deletion is queued locally and syncs in the background. Until it syncs, that variant must not be reachable by opening the stack.

**Why this priority**: The window is short — `DeleteSyncWorker` usually drains quickly — but a deleted image reappearing is alarming out of proportion to how briefly it can happen.

**Acceptance Scenarios**:

1. **Given** a variant has a pending delete, **When** the user opens its stack, **Then** that variant is absent from the viewer, matching the grid.
2. **Given** the user undoes the delete, **When** they reopen the stack, **Then** the variant is present again.

## Requirements *(mandatory)*

- **FR-001**: Stack membership resolution MUST apply the same filter predicates as the grid query that produced the badge the user tapped.
- **FR-002**: Stack membership MUST exclude jobs with a pending local delete.
- **FR-003**: The badge count and the number of pages in the viewer MUST agree for every combination of `favoritesOnly`, `promptId` and `customOnly`.
- **FR-004**: The fix MUST NOT regress the null-root handling shipped in #21 — a lineage root whose own `lineageRootId` is null stays part of its lineage.

## Design notes

The current signature cannot express this: `getJobsByLineageRoot(rootId)` takes no filter arguments, while the grid's state lives in `GalleryViewModel` as the active `GridQuery`. Two shapes are worth weighing:

1. Thread the filters through — `getJobsByLineageRoot(rootId, favoritesOnly, promptId, customOnly)`, with a DAO query mirroring the paged subquery's `WHERE`. Direct, but duplicates the predicate in a fourth place, and the predicate has now been wrong in two of its existing copies.
2. Derive the scope from the already-loaded paged data instead of a second query, so there is one predicate rather than several. Avoids the duplication entirely, but the stack may extend beyond the loaded page.

Option 2 is more likely to prevent a recurrence and should be evaluated first; the repeated divergence is a symptom of the predicate being written out more than once.

## Out of scope

- The `CacheCleanupViewModel` hardcoded-string finding from the same review round: the repo owner has explicitly deprioritised i18n work.
- Any change to how `lineageRootId` is assigned.
