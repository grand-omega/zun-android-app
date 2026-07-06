# Feature Specification: Fix Setup Screen Keyboard Layout Squish

**Feature Branch**: `005-fix-setup-keyboard-layout`

**Created**: 2026-07-05

**Status**: Draft

**Input**: User description: "DEBUG: on my samsung zfold7, at the login page, when I am about to type API token, my keyboard shows up, but that shows will make my UI squized to the op, very ugly and bad"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Type the API token without the layout squishing (Priority: P1)

A user opens the app for the first time (or after a data reset) on a Samsung Galaxy Z Fold and lands on the Setup screen. They tap the API token field to type it. The on-screen keyboard appears and currently crushes the entire screen's content into the remaining sliver of space above the keyboard, distorting logo, text, and fields into an unreadable, ugly layout. Instead, the content should reflow sensibly — the field being edited stays visible and reachable, and nothing above it is squeezed into a deformed size.

**Why this priority**: This is the very first screen a new user (or anyone after `just` clearing app data, or a fresh install) sees. If the token field is unusable or the screen looks broken here, the user cannot get any further into the app at all — it fully blocks onboarding.

**Independent Test**: On a Z Fold (both folded/cover-screen and unfolded states) and on a standard-aspect phone, open a fresh Setup screen, tap the API token field, and confirm the keyboard's appearance never compresses any on-screen element below its normal size — the screen scrolls or resizes gracefully instead.

**Acceptance Scenarios**:

1. **Given** the Setup screen is open on a Z Fold in the folded (cover-screen) state, **When** the user taps the API token field, **Then** the keyboard appears and the visible content scrolls/reflows so the token field remains visible and every visible element renders at its normal, undistorted size.
2. **Given** the Setup screen is open on a Z Fold in the unfolded state, **When** the user taps the API token field, **Then** the same graceful reflow behavior applies (no squish), accounting for the wider/taller unfolded aspect ratio.
3. **Given** the keyboard is showing and the API token field is focused, **When** the user scrolls the Setup screen's content, **Then** any content above/below the field (logo, server-URL field, buttons) is reachable by scrolling rather than being permanently squeezed off-screen or shrunk.
4. **Given** the keyboard is dismissed (user taps outside the field or presses back), **When** the keyboard closes, **Then** the Setup screen returns to its normal full-height layout with no leftover extra spacing or jump.

---

### User Story 2 - Consistent behavior across other text-entry screens (Priority: P2)

Other screens in the app that take text input while the keyboard is showing (e.g., the prompt composer on Home) should not exhibit the same squish behavior, so the fix isn't a one-off patch that leaves the same class of bug elsewhere.

**Why this priority**: Lower priority than fixing the onboarding-blocking Setup screen, but worth confirming the same underlying cause doesn't recur on other keyboard-triggering screens, since a partial fix would just move the bug report to a different screen next.

**Independent Test**: Focus a text field on another keyboard-triggering screen (e.g., Home's prompt input) on the same Z Fold states used in User Story 1, and confirm no squish occurs there either.

**Acceptance Scenarios**:

1. **Given** a screen other than Setup with a focused text field and the keyboard showing, **When** compared against the Setup screen's fixed behavior, **Then** it already handles the keyboard gracefully (no change needed) or is confirmed to need the same fix.

---

### Edge Cases

- What happens when the user rotates/folds the device (folded ↔ unfolded) while the keyboard is already showing and the token field is focused?
- What happens on a very short/cramped window (e.g., multi-window split-screen mode on the Z Fold) combined with the keyboard showing?
- What happens if the user pastes a very long token (longer than the field's visible width) while the keyboard is showing — does the field itself still render at normal height?
- What happens with a physical/Bluetooth keyboard attached (no on-screen keyboard to show) — layout must remain unaffected in that case.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Setup screen MUST allow its content to scroll vertically when the on-screen keyboard reduces the available vertical space, instead of compressing/shrinking any element to fit.
- **FR-002**: When the API token field (or any other Setup screen field) is focused and the keyboard is showing, the Setup screen MUST keep that field visible on screen (auto-scrolled into view if needed).
- **FR-003**: No element on the Setup screen (logo, text, input fields, buttons) MUST render below its normal/default size or with distorted proportions while the keyboard is showing.
- **FR-004**: Dismissing the keyboard MUST return the Setup screen to its normal pre-keyboard layout without residual empty space or a visible layout jump/flash.
- **FR-005**: The fix MUST apply correctly in both the Z Fold's folded (cover-screen, narrow/tall aspect ratio) and unfolded (wide, near-square aspect ratio) states.
- **FR-006**: Any other screen in the app confirmed to share the same underlying cause (content not scrollable / keyboard insets not handled) MUST receive the same treatment.

### Key Entities

*(No data entities involved — this is a layout/presentation fix with no new data model.)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a Z Fold in both folded and unfolded states, tapping the API token field with the keyboard showing never reduces any Setup screen element to less than its normal rendered size.
- **SC-002**: 100% of the Setup screen's fields and buttons remain reachable (via scroll if necessary) while the keyboard is showing, verified across folded and unfolded states.
- **SC-003**: No visible layout defect (squish, overlap, clipped text) is observable on the Setup screen with the keyboard open, on both a Z Fold and a standard-aspect-ratio phone/emulator.

## Assumptions

- "Login page" in the user's report refers to the app's existing Setup screen (`SetupScreen.kt`), where the server URL and API token are entered — there is no separate authentication/login screen in this app.
- The squish is caused by the Setup screen's content not being wrapped in a scrollable container and/or not accounting for the keyboard's inset, which is the standard cause of this class of bug in Compose — confirming the exact root cause is deferred to the planning/implementation phase.
- Fixing this only requires layout/UI changes within the Android client; no server-side or data-model changes are needed.
- "Very ugly and bad" is treated as a functional layout defect (elements squeezed/distorted) rather than a purely subjective visual-design complaint.
