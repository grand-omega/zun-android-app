# Implementation Plan: Scratch-to-Reveal Compare Mode

**Branch**: `010-scratch-reveal-compare` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-scratch-reveal-compare/spec.md`

## Summary

`CompareOverlay.kt` gains a second mode alongside the existing `BeforeAfterSlider`: a
finger-erase reveal built from a persistent, off-screen alpha mask `ImageBitmap`, sized to the
overlay's own display bounds. The mask starts fully opaque (before-image fully covers); each drag
stamps soft, radial-gradient circles into it via `BlendMode.DstOut` ("erase," never "re-cover"),
interpolated along the drag path so fast strokes don't leave gaps. The before-image layer
composites against that mask via `BlendMode.DstIn` on an offscreen `graphicsLayer`, so it's only
visible where the mask is still opaque. All new state (mask bitmap, brush size, active mode) is
hoisted to `CompareOverlay` itself — the same composable that already hoists the slider's
`progress: Float` — so switching modes doesn't dispose either mode's progress, and closing Compare
(which disposes `CompareOverlay` entirely) is what naturally resets everything for free.

## Technical Context

**Language/Version**: Kotlin 2.4.0, Jetpack Compose (Material3)

**Primary Dependencies**: Coil3 (`coil3.compose.AsyncImage`, already used in `CompareOverlay.kt`);
Compose UI's `graphicsLayer`/`drawWithContent`/`Canvas` drawing primitives — the same category of
API `BeforeAfterSlider` already uses for its own `drawWithContent { clipRect(...) }` compositing,
extended here to `BlendMode`-based masking rather than a simple clip.

**Storage**: None — matches spec Assumptions; brush size and mask state are both
`CompareOverlay`-composition-scoped only, never persisted.

**Testing**: JUnit4 for the one genuinely pure piece of logic — interpolating evenly-spaced stamp
positions along a drag segment (so fast strokes don't leave gaps, FR-003). The mask/Canvas
rendering itself (blend modes, gradients, actual pixels) is not meaningfully unit-testable;
verified manually per Constitution Principle III, same split used in feature 011.

**Target Platform**: Android

**Project Type**: mobile-app (single module) — purely internal to the photo viewer's existing
Compare feature, no external interface, no `contracts/` directory.

**Performance Goals**: SC-002 (brush size takes effect with no perceptible delay) and general
60fps drag responsiveness — achieved by drawing incrementally into a persistent mask bitmap (only
the new stroke segment each frame), never replaying the full stroke history, matching this
codebase's existing "keep expensive work off the hot path" instincts.

**Constraints**: Reveals must only ever erase, never re-cover (FR-006) — satisfied structurally by
`BlendMode.DstOut` only ever reducing mask alpha, never increasing it, so no extra "already
revealed" tracking is needed. Mode switches must not disturb either mode's progress (FR-009) —
satisfied by hoisting state to `CompareOverlay`, never inside a conditionally-composed branch.
Closing Compare must fully reset (FR-007) — satisfied for free by `remember`'s normal
disposal-on-leaving-composition behavior, no explicit reset code required.

**Scale/Scope**: One new composable (the scratch-reveal mode) in a new file or appended to
`CompareOverlay.kt`; mode-switch state and control added to `CompareOverlay`; a brush-size control;
a new string resource for the instructional hint (FR-011); one small pure function (stamp-path
interpolation) that's actually unit-tested.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Privacy & Security by Default | No new data, permission, or network call | PASS |
| II. Surgical, Simplicity-First Changes | Extends the existing `CompareOverlay.kt`/`BeforeAfterSlider` state-hoisting pattern rather than introducing a new architecture; no new persisted entity; the mask technique is a direct extension of the `drawWithContent`/blend-mode drawing this same file already does for the slider's clip | PASS |
| III. Verify Before Claiming Done | The one pure, testable piece of logic (stamp interpolation) gets a real unit test; the Canvas/blend-mode visual itself is verified manually, not conflated with "tested" | PASS (planned in tasks) |
| IV. Offline-Capable by Design | Not a new read-path concern — both `beforeModel`/`afterModel` are already-resolved image models passed in from `PhotoViewerScreen.kt` (`images.inputModel`/`images.previewModel`), which already handle offline resolution; this feature only adds a new way to *display* those same already-resolved images | PASS |
| V. Server Contract Fidelity | N/A — no API/DTO change | N/A |
| VI. Development/Production Environment Isolation | Not touched | N/A |

No violations; no Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/010-scratch-reveal-compare/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory — purely internal to this Android app, no external interface.

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/ui/gallery/
├── CompareOverlay.kt     # + CompareMode (Slider/Scratch) state hoisted here, mode-switch control,
│                           mask ImageBitmap + brush-size state hoisted here (survives mode
│                           switches, disposed when Compare itself closes); renders whichever mode
│                           is active, passing the hoisted state down
└── ScratchRevealCompare.kt (new)  # the finger-erase composable: mask creation/sizing, drag
                                     handling (interpolated stamping via BlendMode.DstOut), the
                                     before-layer's DstIn composite, the instructional hint text,
                                     the brush-size control

app/src/main/res/values/strings.xml
└── + compare_scratch_hint (FR-011's instructional text)
```

**Structure Decision**: Single-module Android app (existing structure). The new composable gets
its own file (`ScratchRevealCompare.kt`) rather than growing `CompareOverlay.kt` further, since the
mask/drag-handling logic is substantial enough to warrant separation — `CompareOverlay.kt` stays
focused on hosting/switching between the two modes, matching this codebase's existing
one-composable-concern-per-file convention in this same directory (`GalleryThumbnail.kt`,
`GalleryScreen.kt`, `GalleryViewModel.kt` are already split this way).

## Complexity Tracking

*No entries — no Constitution Check violations to justify.*
