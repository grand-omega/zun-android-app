# Feature Specification: Gallery Thumbnail Reveal Animation

**Feature Branch**: `011-thumbnail-reveal-animation`

**Created**: 2026-07-07

**Status**: Draft

**Input**: User description: "Add a "materialize" reveal animation for the moment a generated image's thumbnail first appears in the gallery grid after its job finishes processing. Today a finished thumbnail just pops into the grid with no visual ceremony -- and since feature 002 removed the completion notification, the gallery grid is now the only place a user discovers a generation finished, so that first-appearance moment deserves some polish. The animation should evoke a photo "developing" feel (e.g. transitioning from blurred/soft to sharp and clear, with a gentle scale/fade-in) rather than an abrupt pop-in. This only applies to a thumbnail's first appearance right after its job completes while the user has the gallery open/visible -- it should not replay every time the grid reloads, re-scrolls, or re-renders an already-seen completed thumbnail."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Watch a generation "develop" the moment it finishes (Priority: P1)

A user submitted one or more photos for editing and is browsing the gallery while waiting. When a generation finishes, its thumbnail doesn't just abruptly pop into the grid — it plays a brief "developing" reveal, transitioning from soft and blurred to sharp and clear, giving the moment of discovery some visual weight now that there's no notification to announce it.

**Why this priority**: This is the entire feature. Since feature 002 removed the completion notification, this in-grid moment is the only place a finished generation is ever announced to the user — it's worth making it feel intentional rather than silent.

**Independent Test**: With the gallery open, submit a photo, wait for it to finish processing, and confirm its thumbnail plays the developing reveal the moment it appears — then confirm scrolling away and back, or reopening the gallery, shows it in its normal, settled state with no repeat animation.

**Acceptance Scenarios**:

1. **Given** the user has the gallery open and a submitted photo is still processing, **When** that job finishes, **Then** its thumbnail plays a brief reveal transitioning from soft/blurred to sharp and clear, rather than appearing abruptly.
2. **Given** a thumbnail has already played its reveal once, **When** the user scrolls away and back, changes a filter, reloads, or reopens the gallery later, **Then** that thumbnail appears in its normal, fully-settled state with no repeat animation.
3. **Given** a job finished while the user did not have the gallery open at all, **When** the user later opens the gallery and sees that thumbnail for the first time, **Then** it appears in its normal, settled state — the reveal only plays for a completion witnessed live, not for a thumbnail that was already done before the grid was opened.
4. **Given** several submitted photos finish processing around the same time while the gallery is open, **When** each one appears, **Then** each thumbnail plays its own reveal independently, without waiting for or being delayed by the others.

### Edge Cases

- What happens for a source photo that already has other variants displayed as a stack (per the gallery's variant-stacking feature)? If the newly-finished variant becomes that stack's newest (cover) thumbnail, the cell now shows genuinely new image content and plays the reveal, same as any other newly-finished thumbnail. If it finishes but isn't the newest in its stack, the visible cover thumbnail doesn't change, so nothing new is revealed and no animation plays — only the stack's count updates.
- What happens if the user navigates away from the gallery, or deletes the job, while its reveal is still mid-animation? The animation is simply interrupted with no error; if the thumbnail is seen again later it appears in its normal, fully-settled state.
- What happens if a generation fails instead of finishing successfully? Failed generations never appear in the gallery grid (only completed ones do), so this scenario doesn't arise for this feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When a job that was visibly still processing transitions to finished while the gallery grid is open, its thumbnail MUST play a reveal animation transitioning from soft/blurred to sharp and clear, with a gentle scale/fade-in, the first time it renders as finished.
- **FR-002**: The reveal animation MUST NOT replay for a thumbnail that has already shown it, on any later render of the same grid cell (scrolling, filtering, reloading, or reopening the gallery).
- **FR-003**: The reveal animation MUST NOT play for a thumbnail whose completion was not witnessed live in an open gallery (i.e., it was already finished before the gallery was opened).
- **FR-004**: Multiple thumbnails finishing around the same time MUST each play their own reveal independently, with no ordering dependency or added delay between them.
- **FR-005**: The reveal animation MUST complete within a short, bounded duration so it never makes the grid feel sluggish or delays the user from interacting with that thumbnail.
- **FR-006**: Interrupting a reveal in progress (navigating away, deleting the job) MUST NOT produce an error or leave the thumbnail visually stuck — it must always be shown in its normal, settled state whenever it's next rendered.
- **FR-007**: When a newly-finished variant becomes an existing stack's cover thumbnail (per the gallery's variant-stacking feature), that cell MUST play the reveal animation, since it now shows genuinely new image content; a completion that doesn't change the visible cover MUST NOT trigger a reveal.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of generations that finish while the gallery is open play the reveal animation exactly once, the first time their thumbnail appears as finished.
- **SC-002**: 0% of already-settled thumbnails replay the reveal animation across any subsequent grid render (scroll, filter change, reload, or reopening the gallery).
- **SC-003**: The reveal animation completes in under 1 second per thumbnail, so browsing the grid never feels delayed while one or more reveals are playing.
- **SC-004**: A user can tell, from the animation alone and without any other on-screen cue, that a thumbnail they're watching just finished processing.

## Assumptions

- The app already tracks which jobs are actively processing (queued/running) versus finished, and already distinguishes "gallery is currently open" as app state — this feature observes the transition from actively-processing to finished while that visibility holds, rather than introducing any new persisted tracking of "has this been revealed before." Whether a thumbnail has already played its reveal is tracked only for the current app session, not saved permanently.
- "The gallery is open" means the gallery screen is the active, visible screen — a thumbnail scrolled out of the current viewport when its job finishes still counts as long as the gallery screen itself is what's on screen; the reveal simply plays whenever that specific cell is next composed (e.g., once scrolled into view).
- This feature is scoped to the gallery grid's thumbnails only — it does not apply to the photo viewer, Home's "still processing" entry point, or any other surface where a job's status is shown.
- No new user-facing setting is introduced to disable this animation; it's a small, bounded-duration polish detail in keeping with the rest of the app's existing motion (e.g., grid fade transitions), not a persistent or intrusive effect.
