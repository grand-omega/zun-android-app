# Quickstart: Validating Save Drag-Reveal Results

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew spotlessCheck
```

Per Constitution Principle III, this feature should add a unit test for `mapRectToSource`
(research.md Decision 3) covering: a container/image pair with the *same* aspect ratio (no
letterboxing — mapping should be a simple uniform scale); a container wider than the image's aspect
ratio (letterboxed left/right — a point near the container's edge should map outside the image's
own bounds or be recognized as landing in the letterbox region, not silently misplaced); the
reverse (letterboxed top/bottom); and a point exactly at the container's center, which must always
map to the image's own center regardless of scale/letterboxing.

The `resolveToBitmap`/`compositeReveal` pipeline itself is not meaningfully unit-testable (real
decode/IO/Canvas work) — verified manually below, per Constitution III's explicit-manual-path
allowance, same split used throughout features 010/011/014.

## Manual validation

1. **Save the current composite (Story 1 / FR-001-005)**
   Open drag-to-reveal mode on a photo, reveal part of it, trigger save. Confirm a new image
   appears in the device's photo storage matching exactly the on-screen composite at that moment —
   open it and visually compare against a screenshot taken just before saving.

2. **In-progress feedback (FR-009)**
   Trigger save/share and confirm a lightweight in-progress indicator shows on the action itself
   while it's working, followed by a clear success confirmation. If this app's dev server/network
   is artificially slowed or the before-image isn't yet cached, confirm the indicator persists
   through the longer wait rather than appearing to hang with no feedback.

3. **Doesn't disturb the session (FR-004, Acceptance Scenario 2)**
   After saving, confirm you can keep dragging to reveal more (or use the reset button) with no
   visible disruption, then save again — confirm both saved images exist as separate files (FR-005).

4. **Distinct from the existing save action (FR-006, Edge Cases)**
   Compare the new save/share action's placement and labeling against the existing plain-result
   save/share actions elsewhere in the photo viewer — confirm there's no ambiguity about which one
   a user is pressing.

5. **Offline / uncached before-image (FR from Constitution IV, research.md Decision 2)**
   With the device offline and the "before" image not already in Coil's memory cache (e.g. after
   force-stopping and reopening the app so the cache is cold), trigger save. Confirm the failure
   surfaces with this app's existing "needs network"/offline-unavailable framing, not a generic or
   silent failure, and that the in-progress reveal itself is left completely untouched.

6. **Full/empty reveal extremes (Edge Cases)**
   Save with nothing revealed yet (fully covered) and again with everything revealed — confirm
   both produce a valid image (identical to the original and to the plain transformed result,
   respectively) with no special-case failure.
