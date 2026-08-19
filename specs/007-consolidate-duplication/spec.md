# Feature Specification: Consolidate Duplicated UI Boilerplate

**Feature Branch**: `007-consolidate-duplication`

**Created**: 2026-07-06

**Status**: Draft

**Input**: User description: "refactor the code base to more modern, elegant, less is more, code bases." — narrowed, per user selection, to: remove dead code and consolidate copy-pasted logic only. No behavior changes, no architecture changes.

**Scoping note**: An investigation pass found no genuinely dead code in the app module (no unreferenced private members, no orphaned files, no lint unused-code/unused-resource findings) — the codebase is already clean on that front. The real opportunity is duplicated boilerplate: identical or near-identical code blocks copy-pasted across multiple screens. This spec covers that consolidation only.

## User Scenarios & Testing *(mandatory)*

<!--
  This is an internal code-quality initiative with no visible change for the app's
  end users. The "user" for each story below is a developer maintaining this
  codebase — the value delivered is fewer places a future bug fix or behavior
  change has to be applied consistently by hand.
-->

### User Story 1 - One shared way to host a job's ProgressViewModel (Priority: P1)

A developer working on the progress/batch-generation UI currently has to change the same `ProgressViewModel` construction block in three separate places (`ProgressScreen`, `BatchTile`, `BatchPage`) any time its construction changes — e.g. adding a new constructor parameter, as already happened once this session when `knownSourceInputId` threading was added elsewhere in the app. Consolidating this into one shared helper means a future change is made once.

**Why this priority**: This is the only *exact* triplicate in the codebase (byte-for-byte identical block, not just similarly-shaped) and lives in the progress/batch feature area that has already seen two rounds of bug fixes this session — the area most likely to change again soon.

**Independent Test**: Can be fully tested by verifying `ProgressScreen`, `BatchTile`, and `BatchPage` all obtain their `ProgressViewModel` through the same shared function, and that the full existing `ProgressViewModel`/`BatchProgressScreen`-related test suite still passes unchanged.

**Acceptance Scenarios**:

1. **Given** the three existing call sites that each construct a `ProgressViewModel` with `viewModel(key = jobId, factory = viewModelFactory { initializer { ProgressViewModel(repository = jobs) } })`, **When** the consolidation is complete, **Then** all three call sites obtain their `ProgressViewModel` through one shared helper instead of repeating the construction block.
2. **Given** the app before and after this change, **When** any existing progress/batch-progress screen is exercised (single-job progress, batch grid, batch focused pager), **Then** its behavior is pixel-for-pixel and functionally identical — same polling, same cancel/dismiss behavior, same navigation.

---

### User Story 2 - One shared back-navigation icon button (Priority: P2)

A developer adding a new top-bar screen currently copies the same three-line "back arrow `IconButton`" block from an existing screen. Eight screens already carry this near-identical block. Consolidating it into one shared composable means the back-arrow's icon, content description, and click target only need to be right in one place.

**Why this priority**: High occurrence count (8 sites across 7 files) makes this the largest total-duplication removal in the app, but it's simple, low-risk, purely visual boilerplate — safe to do, but not as urgent as User Story 1's actively-changing area.

**Independent Test**: Can be fully tested by verifying all eight existing back-arrow `IconButton` sites are replaced with the shared composable, and that each screen's back button still navigates correctly and still exposes the same accessibility content description it did before (including the one site — `PhotoViewerScreen` — that currently tints the icon white).

**Acceptance Scenarios**:

1. **Given** the eight existing back-arrow `IconButton` blocks (in `EditHistoryScreen`, `BatchProgressScreen` ×2, `ProgressScreen`, `GalleryScreen`, `SettingsScreen`, `ResultScreen`, `PhotoViewerScreen`), **When** the consolidation is complete, **Then** every one of these sites uses the shared composable instead of its own copy of the block.
2. **Given** `PhotoViewerScreen`'s back icon is tinted white (unlike the other seven, which use the default icon tint), **When** the shared composable is used there, **Then** the white tint is still applied and is not lost or applied to the other seven screens by mistake.

---

### User Story 3 - One shared "undo deleted item" snackbar (Priority: P3)

`GalleryScreen` and `PhotoViewerScreen` each contain a byte-for-byte identical ten-line block that shows an "item deleted" snackbar with an Undo action and handles the user tapping Undo. Consolidating this into one shared function means a future change to the undo-snackbar's wording, duration, or behavior only needs to happen once.

**Why this priority**: Lowest priority — only two occurrences (versus eight and three for the other stories), so the total duplication removed is smaller, and the existing duplication has caused no reported problems so far.

**Independent Test**: Can be fully tested by verifying `GalleryScreen` and `PhotoViewerScreen` both trigger the same shared function to show the undo snackbar, and that undo-then-restore still works identically in both screens.

**Acceptance Scenarios**:

1. **Given** the two existing identical "undo deleted" snackbar blocks, **When** the consolidation is complete, **Then** both screens invoke one shared function instead of each independently duplicating the snackbar-showing and undo-handling logic.
2. **Given** a user deletes an item and then taps "Undo" in either screen, **When** this flow is exercised after the change, **Then** the deleted item is restored exactly as it was before the change, in both screens.

---

### Edge Cases

- What happens if a future fourth screen needs the same back-arrow icon but with a *different* content-description string or a disabled/loading state? The shared composable must accept these as parameters rather than hard-coding today's exact set of variations.
- What happens to the one back-arrow site (`PhotoViewerScreen`) that already differs slightly (white tint) from the other seven? It must remain visually distinguishable after consolidation — the shared composable needs a way to express that one variation without special-casing it outside the shared function.
- What happens if `ProgressViewModel`'s constructor gains a new required parameter in the future? The consolidation should make that a one-place change, not a search-and-replace across three files.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The three existing `ProgressViewModel` construction blocks (in `ProgressScreen`, `BatchTile`, `BatchPage`) MUST be replaced with calls to a single shared helper that performs the same construction.
- **FR-002**: The eight existing back-arrow navigation icon blocks MUST be replaced with calls to a single shared composable that accepts the click handler and content description as parameters, and optionally an icon tint, preserving `PhotoViewerScreen`'s white-tint variation.
- **FR-003**: The two existing "undo deleted item" snackbar blocks (`GalleryScreen`, `PhotoViewerScreen`) MUST be replaced with calls to a single shared function that performs the same snackbar-and-undo-handling behavior.
- **FR-004**: None of the consolidations in FR-001 through FR-003 MUST change any screen's visible behavior, navigation behavior, accessibility content descriptions, or test-observable behavior — this is a structural refactor only.
- **FR-005**: The full existing automated test suite (unit and instrumented) MUST continue to pass unchanged after the consolidation, with no test needing to be altered to accommodate the refactor (tests that construct or verify UI/ViewModel behavior should be unaffected by *how* a ViewModel or composable is internally wired).
- **FR-006**: The shared helper introduced for User Story 1 MUST be scoped to the three identical `ProgressViewModel` construction sites only. The four other screens that build a differently-typed ViewModel via the same `viewModel(key=..., factory=viewModelFactory{initializer{...}})` shape (`HomeRoute`, `GalleryScaffold`, `SettingsScreen`, `EditHistoryScreen`) are explicitly out of scope for this spec — that's a same-*shape*, not same-*type*, repetition, and generalizing the helper to cover it is a larger design decision than a "cleanup only" pass warrants.

### Key Entities

*(Not applicable — this feature touches only UI-layer construction/composition code, not data entities.)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The number of distinct places in the codebase that construct a `ProgressViewModel` drops from 3 to 1.
- **SC-002**: The number of distinct places in the codebase that build a back-navigation icon button drops from 8 to 1 (call sites still number 8, but each is a single-line call into the shared composable rather than a self-contained block).
- **SC-003**: The number of distinct places in the codebase that show the "undo deleted item" snackbar drops from 2 to 1.
- **SC-004**: 100% of the existing automated test suite passes without modification after the change (a test needing to change specifically *because of* this refactor would indicate a behavior change slipped in).
- **SC-005**: A developer reading any of the three consolidated call sites can find the shared implementation in exactly one place, with no remaining copy of the old duplicated block left behind anywhere in the codebase.

## Assumptions

- Per FR-006, the four other same-*shape*-but-different-*type* ViewModel-hosting sites (`HomeRoute`, `GalleryScaffold`, `SettingsScreen`, `EditHistoryScreen`) are explicitly out of scope; only the three identical `ProgressViewModel` sites are consolidated.
- No new dependencies are introduced; consolidation uses only patterns and libraries already present in the codebase (Jetpack Compose, `androidx.lifecycle.viewmodel`).
- "Consolidate" means extract to a shared, reusable function/composable in an appropriate existing or new utility file under `ui/common/` (or equivalent) — not a change to the app's overall architecture (navigation graph, DI approach, or module structure are all out of scope).
- The minor, single-line `Modifier.widthIn(max = Tuning.MAX_CONTENT_WIDTH)` repetition found across four screens is *not* in scope for this spec — it's a one-line modifier repeated verbatim, not a multi-line block, and consolidating it would be cosmetic only (no lines removed, just a differently-named call), so it doesn't meet this spec's bar for "duplication worth consolidating."
- This is a structural-only refactor: no user-facing strings, colors, spacing, or navigation destinations change as a result.
