# Feature Specification: Fix Custom Prompt Field Rendering When Unfolded

**Feature Branch**: `006-fix-custom-prompt-unfolded`

**Created**: 2026-07-05

**Status**: Draft

**Input**: User description: "BUG: The write your own... text field when in \"open\" mode (when you open your fold phone), the text field looks wired, you cant also see the text you entered. but when \"close\" it is normal"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Type a custom prompt while the phone is unfolded (Priority: P1)

A user opens the prompt picker and selects "Write your own…" to enter a custom prompt, with their Samsung Galaxy Z Fold unfolded (the large inner display, "open" mode). The text field renders incorrectly and the text they type isn't visible, making it impossible to tell what they've written or confirm it before submitting. The same field works correctly when the phone is folded (cover-screen, "close" mode).

**Why this priority**: The custom prompt is one of the two primary ways to describe an edit (the other being picking a saved prompt). If a user can't see what they're typing, they can't confidently use this feature at all while unfolded — a large fraction of Z Fold usage, since unfolding is the main reason to own the device.

**Independent Test**: On a Z Fold, unfold the device, open the prompt picker, select "Write your own…", type text, and confirm the field renders normally (correct colors/borders/sizing) and the typed text is clearly visible and readable — matching the folded-state appearance.

**Acceptance Scenarios**:

1. **Given** the phone is unfolded, **When** the user selects "Write your own…" and types a custom prompt, **Then** the text field renders with normal appearance (no visual distortion) and the entered text is clearly visible.
2. **Given** the phone is folded, **When** the user does the same thing, **Then** the field continues to render correctly exactly as it does today (no regression from this fix).
3. **Given** the user has typed a custom prompt while unfolded, **When** they proceed to submit or save that prompt, **Then** the correct text (matching what they actually typed) is used — the bug is visual only, not a data-loss/corruption issue, unless investigation finds otherwise.

---

### User Story 2 - Fix applies everywhere this field appears (Priority: P2)

The same custom-prompt field is reachable from more than one place in the app (at least Home's prompt picker; possibly also when regenerating from a completed result). The fix must cover every place this field appears, not just the first one found.

**Why this priority**: Lower priority than the core visual fix, but a partial fix that only covers one entry point would leave the same bug reproducible elsewhere, generating a near-identical follow-up report.

**Independent Test**: Locate every screen/flow where the "Write your own…" custom prompt field appears, and confirm each one renders correctly unfolded.

**Acceptance Scenarios**:

1. **Given** the custom prompt field appears on more than one screen, **When** each is opened unfolded, **Then** all of them render correctly, not just the one from the original bug report.

---

### Edge Cases

- What happens if the user starts typing while folded, then unfolds the device mid-entry (or vice versa)? The field should recover to a correct appearance without losing what was already typed or requiring the sheet to be reopened.
- Does the same issue reproduce in intermediate "flex mode" (partially folded, e.g. laptop-like angles) or only in the two extremes ("closed"/cover-screen and fully "open")? Flex-mode behavior is nice-to-have, not required, since the user only reported the two extremes.
- Does the same defect reproduce on other wide-screen/large-window-size-class devices (e.g., a tablet, or a phone rotated to landscape), suggesting the cause is about window size rather than folding specifically? Worth checking during investigation, but the reported bug is scoped to the Z Fold's fold states.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The custom "Write your own…" prompt text field MUST render with normal, undistorted appearance when the device is unfolded.
- **FR-002**: Text entered into that field MUST be clearly visible and readable while the device is unfolded, matching how it already renders correctly while folded.
- **FR-003**: The already-correct folded-state appearance MUST NOT regress as a result of this fix.
- **FR-004**: If the custom prompt field appears in more than one screen/flow, the fix MUST apply to every occurrence, not just the one from the original report.
- **FR-005**: Whatever the user has already typed MUST be preserved correctly (not lost or corrupted) through a fold/unfold transition while the field is open, even though the visual bug itself is scoped to rendering, not data.

### Key Entities

*(No data entities involved — this is a client-side rendering fix with no new data model.)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a Z Fold unfolded, typing in the custom prompt field results in fully visible, readable text and a normal-looking field, verified against the already-correct folded-state appearance.
- **SC-002**: Every screen/flow containing the custom prompt field renders it correctly in both fold states.
- **SC-003**: No visual regression in the folded state is observable after the fix ships.

## Assumptions

- "Open" mode refers to the Z Fold unfolded (using the large inner display); "close" mode refers to the Z Fold folded (using the small cover/outer display) — consistent with how prior features in this app (003, 004) have described the device's two states.
- The affected control is the "Write your own…" custom prompt entry field reachable from the prompt picker (at minimum from Home; possibly also reachable when regenerating from a completed result — confirming the exact set of entry points is deferred to planning).
- The exact technical root cause (e.g., a color/contrast issue, a layout-measurement issue, or something else) is not yet known and is deferred to the planning phase; this spec defines only the required end state.
- This is a client-side Jetpack Compose rendering fix; no server-side or data-model changes are needed.
- The bug is assumed to be visual/rendering-only (the underlying typed text is preserved correctly) unless investigation during planning finds evidence of actual data loss or corruption.
