# Feature Specification: Return to Running Batch

**Feature Branch**: `001-batch-processing-return`

**Created**: 2026-07-04

**Status**: Draft

**Input**: User description: "When user uploaded a batch of photos, if they sweep back, while the jobs are indeed running in background, they could not go back to the batch processing live page anymore, we need to add a feature to let user go back to the running batch page."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Return to an in-progress batch from Home (Priority: P1)

A user uploads a batch of photos and is taken to the live processing view. While the photos are still processing, the user swipes/navigates back to the Home screen. Later, they want to check on progress but currently have no way to get back to that live view — the only options are to wait for individual "job finished" notifications or resubmit. This story adds a clear way to jump back into the live view for any photos still processing.

**Why this priority**: This is the core problem reported — users lose visibility into work they already started, with no path back to it. Without this, the batch feature feels broken/lossy.

**Independent Test**: Start a batch upload, navigate back to Home before it finishes, and confirm an entry point on Home lets the user reopen the live progress view and see current status for every still-processing photo.

**Acceptance Scenarios**:

1. **Given** a batch upload is still processing and the user has navigated back to Home, **When** the user looks at the Home screen, **Then** they see a visible entry point indicating photos are still processing.
2. **Given** the entry point is visible, **When** the user taps it, **Then** they are taken to a live view showing up-to-date status for every photo that is still processing.
3. **Given** the user returns to the live view via the entry point, **When** they navigate away again (back or otherwise), **Then** the background processing continues unaffected.

---

### User Story 2 - Entry point stays accurate as jobs complete (Priority: P2)

While the user is away from the live view, individual photos in the batch keep finishing (or fail). The entry point on Home should reflect reality — updating its count as jobs complete, and disappearing entirely once nothing is left processing.

**Why this priority**: Without this, the entry point could mislead users (e.g., staying visible forever, or showing a stale count), undermining trust in the feature.

**Independent Test**: Start a batch of several photos, back out to Home, let some (not all) finish, and confirm the entry point's count updates and it disappears only once every photo in the batch has reached a final state.

**Acceptance Scenarios**:

1. **Given** some but not all photos in the batch have finished, **When** the user views Home, **Then** the entry point reflects only the photos still processing.
2. **Given** every photo has reached a final state (done, failed, or cancelled), **When** the user views Home, **Then** the entry point is no longer shown.

---

### User Story 3 - Recover access after fully closing the app (Priority: P3)

The user backs out of the live view, then fully closes the app (not just backgrounds it) while photos are still processing on the server. When they reopen the app later, they should still be able to get back to the live view for anything still in progress.

**Why this priority**: Processing happens independently of whether the app is open, so losing the return path on app restart would reintroduce the same problem this feature is meant to fix, just triggered differently.

**Independent Test**: Start a batch, back out, force-close the app, reopen it, and confirm the entry point still appears (if photos are still processing) and still leads to an accurate live view.

**Acceptance Scenarios**:

1. **Given** the app was fully closed and reopened while a batch was still processing, **When** the user views Home, **Then** the entry point is present and accurate.

---

### Edge Cases

- What happens if the user submits a second batch before the first one finishes? The entry point and live view must account for photos from both batches, not just the most recent one (see FR-006).
- What happens if every photo finishes while the app is closed? On reopening, the entry point should not appear, since nothing is left processing.
- What happens if the device has no network connectivity when the user returns to Home? The entry point must still appear based on last-known local status rather than failing silently or erroring.
- What happens if the user navigates back out of the live view immediately after reopening it from the entry point? Background processing must remain unaffected, and the entry point must remain available until all photos finish.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST show a visible entry point on the Home screen whenever one or more submitted photos are still processing in the background, including after the user has navigated away from the live view.
- **FR-002**: Tapping the entry point MUST take the user to a live view showing current status for every photo that is still processing.
- **FR-003**: The entry point MUST update to reflect the current number of processing photos as they individually complete, fail, or are cancelled, without requiring an app restart.
- **FR-004**: The entry point MUST disappear once every processing photo it represents has reached a final state (done, failed, or cancelled).
- **FR-005**: The system MUST preserve the ability to return to the live view even after the app has been fully closed and reopened, as long as processing is still ongoing.
- **FR-006**: If more than one batch is processing at the same time, the entry point and live view MUST reflect all currently processing photos across every batch, not only the most recently submitted one.
- **FR-007**: Leaving the live view (by back navigation or otherwise) MUST NOT pause, cancel, or otherwise affect background processing.
- **FR-008**: The entry point MUST reflect the correct status even if the user returns to Home with no network connectivity, using the most recently known status.

### Key Entities

- **Processing Job**: A single photo submitted for background processing; has a status that is either still processing or in a final state (done, failed, cancelled).
- **Batch**: The set of Processing Jobs created together from one upload action. A user may have more than one Batch active at the same time.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can get from the Home screen back to a live view of their still-processing photos in 2 taps or fewer, 100% of the time at least one photo is still processing.
- **SC-002**: The entry point's displayed count matches the true number of still-processing photos within 5 seconds of a status change, in line with the app's existing progress-polling behavior.
- **SC-003**: 0% of active processing jobs become unreachable from the UI after the user navigates away from the live view (compared to 100% unreachable today).
- **SC-004**: After the app is fully closed and reopened, users can still reach an accurate live view of any still-processing photos, with no loss of visibility compared to staying in the app.

## Assumptions

- The entry point lives on the Home screen, since that is where users land after backing out of the live batch view today.
- "Still processing" means any photo whose job has not yet reached a final state (done, failed, cancelled, or deleted), matching the existing job status model.
- If multiple batches are active at once, they are presented as one combined live view of all currently processing photos rather than as separate per-batch entry points, since the existing live view already supports showing an arbitrary set of jobs together.
- This feature covers returning to processing started via the batch upload flow; it does not change how per-photo "processing finished" notifications behave (tapping a finished-job notification continues to open that single result, as it does today).
- No new user-facing settings are introduced; the entry point's visibility is fully determined by whether processing jobs exist, not by user configuration.
