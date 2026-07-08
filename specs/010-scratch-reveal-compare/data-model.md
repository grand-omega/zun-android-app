# Phase 1 Data Model: Scratch-to-Reveal Compare Mode

## No new persisted entities

Per spec Assumptions, nothing here is saved to Room, `SharedPreferences`, or anywhere durable.
Everything below is `CompareOverlay`-composition-scoped, in-memory Compose state (research.md
Decision 3).

## `CompareOverlay` (hoisted state)

- **`mode: CompareMode`** — a small enum/sealed type, `Slider | Scratch`. Starts at `Slider`
  (spec Assumption: unchanged default). Switching does not affect any other state below.
- **`sliderProgress: Float`** — already exists today (`progress` in the current code); unchanged
  by this feature.
- **`maskBitmap: ImageBitmap`** — the scratch mode's alpha mask (research.md Decision 1), created
  once the overlay's display bounds are known, fully opaque at creation. Persists across mode
  switches; disposed (implicitly, via `remember`) when `CompareOverlay` itself leaves composition
  (Compare closed).
- **`brushRadius: Float`** (or `Dp`) — user-adjustable (FR-005), read fresh at each new touch-down
  (not applied retroactively mid-stroke, per spec Edge Cases). A sensible default and adjustable
  range are plan-level UI details, not pinned here.

## `ScratchRevealCompare` (per-composition, non-hoisted)

- No state of its own beyond what's passed in — it reads `maskBitmap`/`brushRadius` from
  `CompareOverlay` and mutates the mask bitmap's `Canvas` directly in response to drag events. No
  `remember` inside this composable needs to survive it leaving composition, because everything
  that must survive already lives one level up.

## Pure logic (the one unit-tested piece)

- **Stamp-path interpolation** (research.md Decision 2): a function `(from: Offset, to: Offset,
  spacing: Float) -> List<Offset>` — no Compose/Canvas dependency, lives as a plain top-level
  function so it's trivially testable in isolation.

## Relationship to existing data

- `beforeModel`/`afterModel` (the `Any?` Coil models `CompareOverlay` already receives from
  `PhotoViewerScreen.kt`) are unchanged — this feature only adds a new way to render them, not a
  new way to resolve them.
