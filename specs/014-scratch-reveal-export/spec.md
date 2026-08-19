# Feature Specification: Save Drag-Reveal Results

**Feature Branch**: `014-scratch-reveal-export`

**Created**: 2026-07-08

**Status**: Draft

**Input**: User description: "need a feature to be able to save the drag-reveal results, like PhotoShop" — while using the scratch/drag-to-reveal comparison mode (feature 010), let the user save or share the current partially-revealed composite (a mix of the original and transformed image, exactly as currently shown) as its own image, similar to flattening and exporting a layered composite in Photoshop.

## Clarifications

### Session 2026-07-08

- Q: Should there be a visible in-progress indicator while the composite is being saved/shared, given this composites two source images (potentially at higher resolution than the live on-screen mask) rather than copying an already-materialized file? → A: Yes — show a lightweight in-progress indicator (e.g. on the triggering action itself) while saving/sharing, then confirm once complete.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Save the current reveal as its own image (Priority: P1)

A user has been dragging their finger across a photo in drag-to-reveal mode and has landed on a partial reveal they like — part original, part transformed, in an organic shape they created. They want to keep that exact combined image, not just the plain "before" or plain "after."

**Why this priority**: This is the entire ask — without it, whatever composite the user creates by scratching is only ever visible on screen and disappears the moment Compare closes (per feature 010's ephemeral-by-design behavior); there's currently no way to keep it.

**Independent Test**: Reveal part of a photo in drag-to-reveal mode, trigger the save action, and confirm a new image appears in the device's photo storage showing exactly that same mix of original/transformed content.

**Acceptance Scenarios**:

1. **Given** the user has partially revealed a photo in drag-to-reveal mode, **When** they trigger the save action, **Then** a new image is saved to the device containing exactly the same partial-reveal composite that was on screen at that moment.
2. **Given** a composite has just been saved, **When** the user keeps dragging to reveal more (or less, via reset), **Then** their in-progress scratch session is unaffected — saving doesn't reset, lock, or otherwise disturb it.
3. **Given** the user saves two different partial reveals of the same photo in the same session (dragging further between saves), **When** they check their saved images, **Then** both are present as distinct images, not one overwriting the other.
4. **Given** the user has just triggered the save action, **When** the composite is still being assembled, **Then** a lightweight in-progress indicator is visible so they know the tap registered, followed by a confirmation once it's done.

---

### User Story 2 - Share the current reveal directly (Priority: P2)

Having created a partial reveal they like, a user wants to send it to someone else right away, the same way they can already share other images in this app, without first saving and then separately finding it to share.

**Why this priority**: A natural companion to saving locally, but saving is the more essential capability — a user can always save first and share from their device's own gallery app as a fallback, so this is additive rather than blocking.

**Independent Test**: Reveal part of a photo in drag-to-reveal mode, trigger the share action, and confirm the system share sheet opens with exactly that partial-reveal composite as the shared image.

**Acceptance Scenarios**:

1. **Given** the user has partially revealed a photo, **When** they trigger the share action, **Then** the system share sheet opens with an image matching exactly the current on-screen composite.

### Edge Cases

- What happens if the user saves before making any reveal at all (fully covered, showing only the original)? Saving still works — the result is simply an image identical to the original, since that's genuinely what's on screen. No special restriction.
- What happens if the user saves after fully revealing the whole photo? Same as above — the result is identical to the fully transformed image. No special restriction.
- What happens if saving fails (e.g., a storage error)? The user gets a clear failure message and can try again; their in-progress reveal is untouched either way.
- How is this distinguished from the existing "save the result" action already available for a photo? This is a separate, clearly distinct action specific to drag-to-reveal mode — it must not be confusable with, or silently replace, the existing action that saves the plain, unmodified result image.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: While in drag-to-reveal mode, users MUST be able to save the currently-visible partial-reveal composite as a new image.
- **FR-002**: The saved image MUST reflect exactly the reveal state that was on screen at the moment the save was triggered — not an earlier or later state.
- **FR-003**: The saved image MUST be at a quality/resolution consistent with this app's other saved images, not a degraded or low-resolution capture.
- **FR-004**: Saving MUST NOT alter, reset, or interrupt the user's in-progress reveal — they can keep dragging and save again afterward.
- **FR-005**: Saving the same photo's composite multiple times at different reveal states MUST produce separate, distinct saved images, never overwriting a previous save.
- **FR-006**: The save action MUST be clearly distinct from this app's existing "save the plain result" action, so users are never confused about which image (the composite vs. the plain result) they are saving.
- **FR-007**: Users MUST also be able to share the current partial-reveal composite directly, via the same sharing mechanism this app already uses for other images.
- **FR-008**: Saving MUST work regardless of how much of the photo has been revealed, including the extremes of nothing revealed or everything revealed.
- **FR-009**: While a save or share is in progress, the app MUST show a lightweight, non-blocking in-progress indicator (e.g. on the action itself) so the user knows their action registered, then confirm once it completes — the user isn't left guessing whether their tap worked while the composite is being assembled.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can save the current partial-reveal composite in a single action from within drag-to-reveal mode.
- **SC-002**: 100% of saved composites are pixel-for-pixel consistent with what was on screen at the moment of saving — no visible drift or mismatch.
- **SC-003**: Saving never disrupts an in-progress reveal — 100% of the time, a user can continue dragging immediately after a save with no visible interruption.
- **SC-004**: A user can distinguish, from the UI alone, the "save this composite" action from the existing "save the plain result" action, without needing to guess which one they're pressing.
- **SC-005**: 100% of save/share actions show a visible in-progress state for as long as the operation takes, so a user never has to wonder whether their tap was registered.

## Assumptions

- This applies only to drag-to-reveal (scratch) mode, not the existing before/after slider — the user specifically asked about "drag-reveal results." Saving a snapshot of the slider's current split position is a plausible future extension but out of scope here.
- The saved/shared composite is a single, flattened image — a snapshot of what's currently visible, not the underlying reveal shape/mask data itself. There's no expectation of re-editing a previously-saved composite's reveal boundary later.
- No confirmation dialog is required before saving — this matches the immediate, single-tap save behavior this app's other save actions already use.
- No new storage permission is required — this reuses the same on-device photo storage mechanism this app's existing save action already has access to.
- The save/share actions for the composite live within drag-to-reveal mode's own UI, not repurposed from the existing single-image action bar, so they're available consistently everywhere drag-to-reveal mode itself is available (both the gallery viewer and the result screen), and are never confused with the existing plain-result save/share actions per FR-006.
