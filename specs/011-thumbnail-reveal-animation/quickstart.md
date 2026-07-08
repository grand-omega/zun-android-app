# Quickstart: Validating Gallery Thumbnail Reveal Animation

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew spotlessCheck
```

Per Constitution Principle III, this feature should add a `GalleryViewModel` unit test for the
diffing logic specifically (data-model.md's `previousActiveIds` → `revealEligibleJobIds`
transitions), independent of Compose:
- An ID present in the first `activeJobIds()` emission and absent from the second becomes eligible.
- An ID present in the *first* emission ever observed and never seen active before that does **not**
  become eligible on its own (there's no "before" to diff against — this is the FR-003 case).
- Calling `markRevealed(id)` removes it from `revealEligibleJobIds` and it does not reappear on
  later emissions.
- Multiple IDs disappearing in the same emission all become eligible together (FR-004).

The visual animation itself (blur→sharp, scale, fade) is **not** meaningfully covered by this
unit test — it verifies the eligibility bookkeeping only. The actual visual is verified manually
below, per Constitution III's explicit-manual-verification-path allowance; don't claim the unit
test proves the animation looks right, only that it fires at the right time and only once.

## Manual validation

1. **Live completion plays the reveal (Story 1 / FR-001, timing FR-005/SC-003)**
   With the gallery screen open, submit a photo for editing. Watch the grid — when the job
   finishes, confirm its thumbnail transitions from blurred/soft to sharp with a gentle scale/fade,
   rather than abruptly appearing. Time it (stopwatch or screen recording) — it must complete
   comfortably under 1 second; don't just eyeball it.

2. **No replay on scroll/reload/reopen (FR-002)**
   After step 1's thumbnail has revealed, scroll it out of view and back, pull-to-refresh the grid,
   and fully leave and re-enter the Gallery screen. Confirm the thumbnail shows in its normal,
   settled state every time — the reveal never plays a second time for it.

3. **No reveal for a job finished while Gallery was closed (FR-003)**
   Submit a photo, then navigate away from Gallery (e.g. back to Home) before it finishes. Wait for
   it to finish in the background, then open Gallery. Confirm the thumbnail appears directly in its
   settled state — no reveal plays.

4. **Multiple simultaneous completions (FR-004)**
   Submit a small batch (2-3 photos) with Gallery open. Confirm each thumbnail reveals
   independently as its own job finishes, with no waiting on or blocking by the others.

5. **Interrupting a reveal (FR-006)**
   Trigger a reveal (per step 1) and immediately navigate away from Gallery mid-animation, or
   delete that job. Confirm no crash/error, and that the thumbnail (if it's ever shown again) is in
   a normal, fully-settled state — never visually stuck mid-transition.

6. **Stack cover interaction (FR-007, requires feature 009)**
   With a stack of variants already open in Gallery, submit a new edit of the same source photo so
   it becomes a new variant. If it becomes the stack's newest (cover) thumbnail, confirm the reveal
   plays for that cell. This scenario is easiest to observe if the workflow naturally produces a
   fast completion; if the real GPU pipeline isn't available in this environment (see specs
   001/002/004/009's documented live-verification constraints), this step may need to be deferred
   and honestly noted as such, same as prior features.
