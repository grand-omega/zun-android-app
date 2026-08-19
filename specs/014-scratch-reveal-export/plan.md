# Implementation Plan: Save Drag-Reveal Results

**Branch**: `014-scratch-reveal-export` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-scratch-reveal-export/spec.md`

## Summary

A new save/share action inside `ScratchRevealCompare`'s own chrome resolves the `beforeModel`/
`afterModel` Coil models to real `Bitmap`s (via Coil's `ImageLoader` — the existing
`saveToPictures`/`shareImages` utilities only byte-copy already-materialized sources, they never
decode, so this is a genuinely new capability, not a reuse of an existing one), then composites
them onto a canvas sized to the *transformed* image's own resolution, remapping the on-screen
mask — which today lives in screen-space, not source-space — through each image's actual
`ContentScale.Fit` rect within the shared on-screen container. The composite is written out through
the existing save/share utilities once flattened to a real file. Runs off the main thread with a
lightweight in-progress indicator on the triggering button (FR-009); a failure to resolve the
(never disk-cached) "before" image offline surfaces via this app's existing "needs network" framing,
not a generic error.

## Technical Context

**Language/Version**: Kotlin 2.4.0, Jetpack Compose

**Primary Dependencies**: Coil3 (`coil3.request.ImageRequest` + `SingletonImageLoader`, already a
dependency — used here for the first time to *decode* a model to a `Bitmap` rather than just
display it, following the one existing precedent for this pattern at
`PhotoViewerScreen.kt:337-338`, which pre-warms Coil's cache for a different reason); this app's
existing `saveToPictures`/`shareImages` utilities for the final write-out step, unchanged.

**Storage**: None new — the flattened composite is written through the existing MediaStore
save path, the same mechanism (and same on-device photo storage) this app's other saves already
use.

**Testing**: JUnit4 for the one genuinely pure, testable piece: mapping a screen-space
point/rect to source-image-space given a container size and an image's intrinsic size under
`ContentScale.Fit` (the same category of pure-function extraction as feature 010's
`interpolateStampPoints`). The actual Bitmap decode/composite/write pipeline is verified manually
per Constitution Principle III, same split used throughout features 010/011.

**Target Platform**: Android

**Project Type**: mobile-app (single module) — no external interface, no `contracts/`.

**Performance Goals**: FR-009's in-progress indicator exists specifically because this is *not*
assumed to be instant — resolving two Coil models to full Bitmaps plus a canvas composite is
real work, budgeted here for a few seconds worst case (cold cache, larger result image), not the
near-instant byte-copy the existing save action does.

**Constraints**: The "before" (`inputModel`) source is **never** disk-cached by this app's own
design (`RealJobRepository.kt` — `inputModel` always resolves to a plain URL, with no
`OfflineImageCache` check, unlike `previewModel`/`resultModel`) — so exporting can require a fresh
network fetch of the original photo even if the "after" side is fully cached. This is a
pre-existing characteristic of `inputModel`, not a regression this feature introduces; Constitution
Principle IV requires the resulting offline failure to surface as an explicit "unavailable
offline"/needs-network state (matching the app's existing `NeedsNetworkIcon` framing), not a
generic error.

**Scale/Scope**: One new utility function (Coil model → `Bitmap`, off-main-thread); one new pure
coordinate-mapping function (screen-space → source-space under `ContentScale.Fit`) plus its test;
compositing logic that combines the mapped mask with the two resolved Bitmaps onto the after
image's own resolution; a small save/share action row added to `ScratchRevealCompare`'s existing
chrome; reuse of `saveToPictures`/`shareImages` for the actual write-out.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Privacy & Security by Default | No new data leaves the device differently than existing save/share already do; no new permission (reuses the existing MediaStore path) | PASS |
| II. Surgical, Simplicity-First Changes | The Coil-decode-to-Bitmap step is genuinely new (research confirmed no existing precedent for multi-image compositing anywhere in this codebase — see research.md Decision 1), but it's built entirely on Coil, an existing dependency, not a new image library; the coordinate-mapping is isolated as its own small, testable function rather than inlined | PASS (see Complexity Tracking — one justified new capability) |
| III. Verify Before Claiming Done | The coordinate-mapping math gets a real unit test; the Bitmap pipeline itself is verified manually, not conflated with "tested" | PASS (planned in tasks) |
| IV. Offline-Capable by Design | This feature's read path inherits `inputModel`'s pre-existing no-disk-cache characteristic — addressed by requiring the resulting failure to surface via this app's established "needs network" framing (FR-009/spec Edge Cases), not by pretending the feature is always offline-capable when it structurally can't be | PASS (with explicit design commitment, not silently ignored) |
| V. Server Contract Fidelity | N/A — no API/DTO change | N/A |
| VI. Development/Production Environment Isolation | Not touched | N/A |

No unaddressed violations. Complexity Tracking documents the one genuinely new capability
(Coil-based Bitmap resolution + compositing) and why it's necessary rather than avoidable.

## Project Structure

### Documentation (this feature)

```text
specs/014-scratch-reveal-export/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory — purely internal to this Android app, no external interface.

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/util/
├── ImageCompositor.kt (new)   # resolveToBitmap(context, model): Bitmap? (Coil-based decode,
│                                 off-main-thread); mapRectToSource(containerSize, imageIntrinsicSize,
│                                 screenRect): Rect (the one pure, unit-tested function — see
│                                 research.md Decision 3); compositeReveal(after, before, mask,
│                                 mapping): Bitmap (the actual flatten step)

app/src/main/java/dev/zun/flux/ui/gallery/
└── ScratchRevealCompare.kt   # + a small save/share action row wired to ImageCompositor +
                                the existing saveToPictures/shareImages, + FR-009's in-progress
                                state on that row
```

**Structure Decision**: The new Bitmap-resolution/compositing logic goes in a new `util` file
(`ImageCompositor.kt`), matching this codebase's existing convention of keeping
Bitmap/file-manipulation logic in `util/` (alongside `MediaStoreSaver.kt`, `ShareUtils.kt`,
`ImageUtils.kt`) rather than inside the Compose UI file — `ScratchRevealCompare.kt` only gets the
new UI row and the calls into this utility, not the compositing math itself.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| New Coil-based "resolve model to Bitmap" + canvas-compositing utility (no existing precedent anywhere in this codebase, per research) | The feature's entire ask — a real, flattened image of the current on-screen mix — cannot exist without actually decoding both source images and compositing them; the existing `saveToPictures`/`shareImages` utilities only byte-copy already-materialized sources and never decode, so there's no way to satisfy FR-001/FR-002 by only reusing them | Screenshotting the on-screen Composable (`Modifier.graphicsLayer`/`ImageBitmap` capture of the rendered UI) was considered and rejected — it would capture at *screen* resolution only, directly violating FR-003's "quality/resolution consistent with this app's other saved images," and would also capture any UI chrome overlaid at that moment unless carefully excluded |
