# Phase 1 Data Model: Save Drag-Reveal Results

## No new persisted entities

Per spec Assumptions, the saved composite is a one-off exported file (via the existing MediaStore
save path), not a new tracked domain entity — nothing about it is stored in Room or anywhere else
this app already models data.

## New pure/utility functions (`util/ImageCompositor.kt`)

- **`resolveToBitmap(context: Context, model: Any?): Bitmap?`** — resolves a Coil model (the same
  `Any?` type `beforeModel`/`afterModel` already are) to a decoded `Bitmap`, or `null` on failure
  (research.md Decision 1). Not pure (does real I/O/decoding), not unit-tested directly — covered
  by manual verification (plan.md's Testing section).
- **`mapRectToSource(containerSize: IntSize, imageIntrinsicSize: IntSize, rectInContainer: ...):
  ...`** — the one pure function in this feature (research.md Decision 3): given the shared
  on-screen container's size, a specific image's own intrinsic pixel size, and a rect/point in
  container-space, returns the equivalent rect/point in that image's own pixel space under
  `ContentScale.Fit`'s letterboxing rules. No Compose/Bitmap/IO dependency — this is what gets a
  real unit test.
- **`compositeReveal(after: Bitmap, before: Bitmap, mask: ImageBitmap, ...): Bitmap`** — produces
  the final flattened composite at `after`'s own resolution (research.md Decision 4), using
  `mapRectToSource` to correctly place the mapped `before` content and mask. Not pure (allocates
  and draws a real `Bitmap`), covered by manual verification.

## Transient UI state (`ScratchRevealCompare`, session-local, matches existing state-hoisting pattern)

- **Save/share in-progress flag** — a simple boolean (or small sealed state, matching this
  codebase's existing `PolishState`/`SubmitState`-style conventions elsewhere) driving FR-009's
  lightweight in-progress indicator on the triggering action. Not hoisted to `CompareOverlay` —
  unlike `maskBitmap`/`brushRadius`, there's no requirement this survive a mode switch (per spec
  Assumptions, this only applies while in drag-to-reveal mode at all), so it can live locally in
  `ScratchRevealCompare` itself without repeating research.md Decision 3 (feature 010)'s
  mask-ownership concern — there's no equivalent "this composable unmounts on mode switch" state
  loss risk here, since an in-flight save wouldn't meaningfully need to survive that anyway (spec
  doesn't require saves to survive a mode switch mid-flight).

## Relationship to existing data

- `beforeModel`/`afterModel` (already `ScratchRevealCompare` params) and `maskBitmap` (already
  hoisted in `CompareOverlay`, per feature 010) are all reused as-is — this feature adds a new way
  to *consume* them (flatten to a file) alongside the existing way (render on screen), not a new
  way to produce or store them.
