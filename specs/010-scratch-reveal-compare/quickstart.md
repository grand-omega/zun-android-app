# Quickstart: Validating Scratch-to-Reveal Compare Mode

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew spotlessCheck
```

Per Constitution Principle III, this feature should add a unit test for the one pure piece of
logic (research.md Decision 2): given two points and a spacing value, the interpolation function
returns evenly-spaced points between them (including a sensible result when the two points are
identical — a tap, not a drag — and when they're already closer together than the spacing).

The mask/Canvas rendering itself (blend modes, gradients, actual erased pixels) is **not**
meaningfully covered by that unit test — it verifies the stamp-spacing math only. The actual visual
is verified manually below, per Constitution III's explicit-manual-verification-path allowance.

## Manual validation

1. **Scratch reveal itself (Story 1 / FR-001-004, FR-006)**
   Open Compare on a generated edit, switch to scratch mode, drag a finger across part of the
   image. Confirm the transformed result is revealed in a soft-edged, freehand area matching the
   finger's path — not a straight line, not a hard-edged circle. Drag back over an already-revealed
   area and confirm nothing changes (it doesn't re-cover).

2. **Brush size (FR-005, SC-002)**
   Adjust the brush size control, then drag again. Confirm the new stroke is visibly
   larger/smaller with no perceptible delay between adjusting and the next stroke reflecting it.
   Confirm already-erased areas from before the adjustment are unaffected.

3. **Full reveal and single tap (Edge Cases)**
   Scratch until the entire image is revealed — confirm it just shows the full transformed result,
   nothing special happens. Try a single tap without dragging — confirm it still reveals a small
   dab at that point.

4. **Reset on close, not on mode switch (FR-007, FR-009, Story 1 scenario 4, Story 2 scenario 2)**
   Partially reveal the image, switch to the slider mode, switch back to scratch — confirm the
   partial reveal is exactly as it was. Then close Compare entirely and reopen it (same or a
   different image) — confirm it starts fully covered, no memory of the previous session.

5. **Mode switching doesn't disturb the slider, and slider stays the default (FR-008, FR-010,
   Story 2 scenarios 1 and 3)**
   Open Compare fresh — confirm it opens in slider mode, behaving exactly as before this feature.
   Find the mode-switch control, switch to scratch and back — confirm the slider still works
   exactly as it did.

6. **Instructional hint (FR-011)**
   Switch to scratch mode and confirm a brief instructional hint is visible (matching the
   slider's "Before / After" pill's position/styling), so a first-time user isn't left guessing
   what to do.
