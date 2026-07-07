# Feature Specification: Scratch-to-Reveal Compare Mode

**Feature Branch**: `010-scratch-reveal-compare`

**Created**: 2026-07-07

**Status**: Draft

**Input**: User description: "Add a second before/after comparison mode to the photo viewer's existing Compare feature, alongside the current slider (both coexist -- the user can switch between them, neither replaces the other). The new mode is a finger-erase "reveal" interaction: the original (before) image sits on top, the transformed (after) image underneath; as the user drags a finger across the image, it progressively erases the top layer wherever touched, revealing the transformed image beneath in exactly those areas (not a straight wipe line -- an organic, freehand-erased area matching wherever the finger has passed). Brush size is user-adjustable. The brush must have a soft, feathered edge (a Photoshop-eraser feel, not a hard-edged circle), so erased/not-yet-erased areas blend gradually rather than with a hard line. The erased state is ephemeral -- it resets to fully covered (showing the original) each time the compare view is reopened, like a fresh scratch card, rather than being persisted."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reveal the edit by scratching it away (Priority: P1)

A user is looking at a generated edit and wants a more playful, tactile way to see what changed than sliding a divider back and forth. In the Compare view's new mode, the original photo covers the transformed result; wherever the user drags a finger, that covering is wiped away in a soft, hand-drawn area, revealing the transformed image underneath exactly where they touched.

**Why this priority**: This is the entire ask — without it there is no new feature, just the existing slider. It must feel tactile and satisfying on its own, independent of anything else.

**Independent Test**: Open Compare, switch to the new mode, drag a finger across the image, and confirm the transformed result is revealed in a soft-edged area following the finger's path, not a straight line.

**Acceptance Scenarios**:

1. **Given** the new mode is active and showing the original image fully covering the transformed one, **When** the user drags a finger across part of the image, **Then** the transformed image becomes visible in a freehand area matching where the finger passed, with a soft, gradually-blending edge rather than a hard boundary.
2. **Given** the user has partially revealed the transformed image, **When** they drag over an already-revealed area again, **Then** that area remains revealed (re-touching doesn't hide it again).
3. **Given** the user wants a bigger or smaller reveal area, **When** they adjust the brush size, **Then** subsequent strokes reveal a correspondingly larger or smaller area; already-erased areas are unaffected by the size change.
4. **Given** the user has revealed part of the image, **When** they close the Compare view and reopen it (for the same or a different image), **Then** the view starts fully covered again, with no memory of the previous reveal.

---

### User Story 2 - Switch freely between the slider and the scratch mode (Priority: P2)

Having used the existing before/after slider before, a user wants the new scratch interaction as an additional option, not a replacement — they should be able to hop between the two within the same Compare session depending on what they feel like using.

**Why this priority**: Makes the new mode additive rather than disruptive; depends on Story 1 existing to have a second mode to switch to.

**Independent Test**: Open Compare (starts in the existing slider mode), switch to the scratch mode, confirm it works, switch back to the slider, confirm the slider still works exactly as before.

**Acceptance Scenarios**:

1. **Given** the Compare view is open, **When** the user looks for a way to change modes, **Then** a clear, discoverable control lets them switch between the slider and the scratch mode without leaving Compare.
2. **Given** the user has partially revealed the image in scratch mode, **When** they switch to the slider mode and back to scratch mode within the same Compare session, **Then** their scratch progress is unaffected by the round trip (only closing Compare entirely resets it, per Story 1).
3. **Given** a user who never touches the new mode, **When** they open Compare as before, **Then** the experience is unchanged — the slider still opens by default and behaves exactly as it did before this feature existed.

### Edge Cases

- What happens on a single tap without dragging? A tap alone still reveals a small dab at that point, consistent with a real eraser touching down — dragging isn't required to see any effect at all.
- What happens if the user scratches until the entire image is revealed? Nothing special — it simply shows the fully transformed image, same as if the slider were dragged all the way across.
- What happens with two fingers touching at once? Only one active touch is tracked at a time, consistent with how the existing slider already handles dragging.
- What happens if the user resizes the brush mid-stroke (e.g., via a control that stays reachable while dragging)? The new size takes effect starting with the next touch-down, not retroactively within the same continuous stroke.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The photo viewer's Compare feature MUST offer a second mode, alongside the existing before/after slider, based on a freehand finger-erase interaction.
- **FR-002**: In the new mode, the original (unedited) image MUST be shown covering the transformed (edited) result underneath.
- **FR-003**: Dragging a finger across the image MUST reveal the transformed image beneath, exactly in the freehand area the finger has passed over — not a straight-line wipe.
- **FR-004**: The revealed area's edge MUST be soft and gradually blending rather than a hard-edged boundary.
- **FR-005**: Users MUST be able to adjust the size of the reveal brush, with the new size applying to subsequent strokes.
- **FR-006**: Touching an already-revealed area again MUST NOT re-cover it — reveals only accumulate within one viewing.
- **FR-007**: The revealed state MUST reset to fully covered (original only) every time the Compare view is closed and reopened.
- **FR-008**: Users MUST be able to switch between the slider mode and the new scratch mode from within the Compare view, without closing it.
- **FR-009**: Switching between modes within the same Compare session MUST NOT reset or otherwise affect scratch progress already made.
- **FR-010**: The existing slider mode's behavior and default availability MUST remain unchanged by this feature — it is additive, not a replacement.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can switch from the existing slider to the new scratch mode in a single action, and back again, at any time while Compare is open.
- **SC-002**: Adjusting the brush size changes the size of the very next stroke, with no perceptible delay between adjusting and seeing the new size take effect.
- **SC-003**: 100% of reveal strokes show a soft, gradually-blending edge on visual inspection — no stroke ever produces a hard-edged boundary.
- **SC-004**: Reopening Compare after closing it starts from a fully-covered state 100% of the time, regardless of how much was previously revealed.

## Assumptions

- "Compare" refers to the app's existing before/after slider feature already available from the photo viewer; this feature adds a second, coexisting mode to it rather than replacing or redesigning the existing slider.
- Compare continues to open in the existing slider mode by default, matching current behavior; the scratch mode is an additional option the user opts into during that session, not a new default.
- Scratch progress persists across mode switches within one open Compare session, but is discarded the moment Compare itself is closed — "closing and reopening" is the only reset boundary, per the feature description's "fresh scratch card" framing.
- Only single-finger/single-touch interaction is in scope, consistent with how the existing slider already handles a single drag gesture.
- Out of scope for this version: haptic feedback, sound, and any paper-grain/textured edge effect. These were discussed as possible later enhancements but are not part of this baseline — the soft edge requirement (FR-004) is about a gradual blend, not a specific grain texture.
- No new persisted data or settings are introduced — brush size and reveal state are both session-local (brush size does not need to be remembered across app restarts for this version).
