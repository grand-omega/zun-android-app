# Research: Gallery Thumbnail Reveal Animation

## Decision 1: Detect "done" transitions by diffing consecutive `activeJobIds()` emissions

**Decision**: `GalleryViewModel` collects `jobRepo.activeJobIds()` (the existing Room-backed,
reactive `Flow<List<String>>` of non-terminal job IDs — `JobDao.kt`'s `getActiveJobs()` query,
already consumed identically by `HomeViewModel.kt`). On each emission, diff it against the
*previous* emission: any ID present before and absent now just transitioned to a terminal state
(done, in this context — failed/cancelled jobs never reach the gallery grid at all). Those IDs are
added to a `revealEligibleJobIds` set. The very first emission after the collector starts is stored
as the baseline and produces zero eligible IDs (nothing to diff against yet).

**Rationale**: This single mechanism satisfies FR-001, FR-002, and FR-003 together with no extra
bookkeeping:
- FR-003 ("only a completion witnessed live, not one that already finished before Gallery was
  opened") falls out of treating the first emission as a baseline — a job already done before the
  ViewModel started observing was never in an emission this collector saw as "active," so it can
  never appear as a before→absent diff.
- Because `GalleryViewModel` is created fresh each time the user navigates to the Gallery route
  (`GalleryScaffold.kt:39`, `viewModel(key = "gallery-$repositoryVersion", ...)`) and torn down on
  navigating away, every reopen naturally gets a fresh baseline — reopening Gallery cannot replay a
  stale eligible ID from a previous visit, satisfying half of FR-002 for free (the other half — no
  replay from scroll — is Decision 2).
- No new DAO query, no new Room column, no event bus — reuses infrastructure that already exists
  for a closely related purpose (Home's "jobs still processing" banner).

**Alternatives considered**:
- *A dedicated "just completed" flag/event in Room*: rejected — a schema change for a purely
  presentational, session-scoped signal is disproportionate (Constitution Principle II).
- *Per-cell polling of job status in a `LaunchedEffect`*: rejected — would duplicate the reactive
  Flow this app already centralizes in the repository layer, and interacts badly with cells being
  disposed/recreated as the grid scrolls (see Decision 2).

## Decision 2: One `StateFlow<Set<String>>`, consumed by the cell that plays the reveal — no second "already played" set

**Decision**: `revealEligibleJobIds: StateFlow<Set<String>>` is the only piece of new state. A
`GalleryThumbnail` cell, on first composing for a given `job.id`, checks membership via
`LaunchedEffect(job.id)`; if present, it plays the reveal and immediately calls back
(`onRevealPlayed(job.id)` → `GalleryViewModel.markRevealed`) to remove that ID from the set.

**Rationale**: "Not in the eligible set" already means exactly "don't animate," regardless of
*why* (never was eligible, or was and got consumed) — a second "already played" set would track
the same outcome through a different path for no behavioral difference (Principle II). This also
correctly survives `LazyVerticalGrid` scrolling a cell out of and back into the composed window:
each time the cell is freshly composed for that key, `LaunchedEffect(job.id)` re-runs, but by then
the ID has already been removed from the ViewModel-held set (survives recomposition/disposal,
unlike `remember`), so it reliably resolves to "don't animate" on the second and later compositions.

**Alternatives considered**:
- *`remember`-scoped "have I played this" flag inside the cell itself*: rejected — `remember` is
  lost when a `LazyVerticalGrid` item scrolls out of the composed window and a fresh instance is
  created on scroll-back, which would incorrectly replay the animation (violates FR-002). State
  needs to outlive individual cell compositions, i.e., live in the ViewModel.

## Decision 3: Reveal keys off `job.id`, the same key the grid already uses — no special-casing for stacking (FR-007)

**Decision**: No additional logic is needed to make the reveal "do the right thing" for the
variant-stacking interaction (feature 009) — it falls out of the existing design.

**Rationale**: Verified directly in the codebase: `GalleryScreen.kt:522` keys each grid cell as
`"job_${item.job.id}"`, where `item.job` is the paged query's *cover* row for that stack (feature
009's `JobWithStackCount` → `toSummaryDto()`). When a newly-finished variant becomes a stack's new
cover, the row the paged query returns for that grid position *is* that variant's own row — so its
`job.id` is exactly the ID that just transitioned active→done, and the cell (now keyed by that new
ID, which Compose treats as a structurally new item) naturally plays the reveal. When a variant
finishes but isn't the new cover, its ID still transitions active→done and still enters
`revealEligibleJobIds`, but no grid cell is ever keyed with that ID (the cover's ID differs) — so
it's simply never consumed. Inert, not a bug: satisfies "a completion that doesn't change the
visible cover must not trigger a reveal" without checking stack membership anywhere in this
feature's code.

**Alternatives considered**:
- *Explicitly resolving stack cover status inside the diffing logic*: rejected — unnecessary; the
  existing id-keying already produces the correct behavior, and adding an explicit check would be
  redundant complexity for a case the design already handles.

## Decision 4: A Compose-level blur/scale/fade driven by one `animateFloatAsState`, not Coil's crossfade

**Decision**: The reveal is implemented as a single progress value (0f → 1f) from
`animateFloatAsState` (matching this codebase's one existing `animate*AsState` usage,
`common/Polish.kt:155`'s `StatusPill`, including its `label` parameter convention), lerped into a
blur radius (`Modifier.blur`), a scale and alpha (`Modifier.graphicsLayer`), wrapping the existing
`SubcomposeAsyncImage` content in `GalleryThumbnail.kt`. Coil's existing `crossfade(true)` (already
present, `GalleryThumbnail.kt` ~line 86-89) is left untouched and keeps handling ordinary
image-bytes-just-loaded fade-in.

**Rationale**: Coil's crossfade answers a different question — "did the bitmap for this request
just finish decoding" — which can be true on a plain scroll-back (image reloads from Coil's own
cache) with no job status change involved at all. Conflating that signal with "this job just
finished" would replay visual polish on ordinary scrolling, which is exactly what FR-002
prohibits. A separate, ViewModel-eligibility-gated Compose animation is the correct, decoupled
mechanism. This is the first use of `Modifier.blur`/scale-`graphicsLayer` animation in this
codebase; kept to one progress value driving all three properties (not three independent
animations) to avoid needlessly expanding the app's animation vocabulary for a small polish detail.

**Alternatives considered**:
- *Rely on Coil's `crossfade` alone*: rejected — wrong signal (see above), and it only fades alpha,
  no blur or scale.
- *`AnimatedVisibility`* (the codebase's other existing animation primitive, used in
  `PhotoViewerScreen.kt`): rejected — that's for a composable's enter/exit based on a boolean, not
  a continuous blur-to-sharp transform of content that's already present and visible.
