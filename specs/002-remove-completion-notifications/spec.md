# Feature Specification: Remove Completion Notifications

**Feature Branch**: `002-remove-completion-notifications`

**Created**: 2026-07-04

**Status**: Draft

**Input**: User description: "remove the notification of when image done feature, no longer needed."

## Clarifications

### Session 2026-07-04

- Q: Once the notification-triggering background watcher (FR-005) is removed, nothing else discovers a job that fails while the app is backgrounded (the only other background refresh, pull-to-refresh's `syncHistory`, only ever catches jobs that reached "done"). Should a silent, notification-free background sync be kept so status stays automatically accurate, or should the watcher be fully removed and this staleness accepted? → A: Fully remove the background watcher; a silently-failed job's status only updates when the user reopens that job's own live view.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - No more completion notifications (Priority: P1)

A user submits one or more photos for processing. Today, when a generation finishes (or fails) while the app isn't in the foreground, the system shows a notification. Going forward, users should not see any such notification — they check on progress and results from inside the app itself instead.

**Why this priority**: This is the entire ask — the notification is explicitly called out as no longer needed. Removing it is the core deliverable.

**Independent Test**: Submit a photo (or batch) and background the app until it finishes. Confirm no notification appears, and confirm the generation still completed successfully and is visible in the app (Gallery, or the in-progress entry point on Home while still running).

**Acceptance Scenarios**:

1. **Given** a submitted photo is processing in the background, **When** it finishes successfully, **Then** no notification is shown.
2. **Given** a submitted photo is processing in the background, **When** it fails, **Then** no notification is shown.
3. **Given** a notification would previously have appeared, **When** the user later opens the app, **Then** the finished (or failed) generation is still visible through the app's normal screens (Gallery for done work; the existing "still processing" entry point on Home for anything not yet finished).

---

### User Story 2 - No more notification permission prompt (Priority: P2)

Today, submitting a photo for the first time asks the user to grant notification permission, solely so the completion notification can be shown. Since that notification is going away, users should no longer be asked for this permission when submitting photos.

**Why this priority**: A permission prompt for a feature that no longer exists is confusing and erodes trust; this is a direct, low-effort consequence of removing the notification.

**Independent Test**: On a fresh install, submit a photo for the first time and confirm no notification-permission prompt appears.

**Acceptance Scenarios**:

1. **Given** a user has never been asked for notification permission, **When** they submit a photo for the first time, **Then** they are not prompted to grant it.

---

### Edge Cases

- What happens to notification permission a user already granted in the past? Nothing changes at the OS level — the app simply stops requesting or using it going forward; the app does not attempt to revoke a previously granted permission.
- What happens to a generation that is already in flight when this change ships? It keeps processing and completing normally; the only difference is that no notification is shown when it finishes.
- What happens if a user taps on an old completion notification still present in their notification shade from before this change shipped? Out of scope — this only affects notifications generated going forward.
- What happens if a job fails while the app is backgrounded and the user never reopens its live view? Its local status can remain stale (still appearing as "processing") until the user manually reopens that job's own live view — no background mechanism discovers failures once the notification-triggering watcher is removed (see Clarifications). This is an accepted trade-off of fully removing that watcher rather than a defect.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST NOT show a notification when a photo generation completes successfully.
- **FR-002**: System MUST NOT show a notification when a photo generation fails.
- **FR-003**: System MUST NOT request notification permission from the user as part of submitting photos for processing.
- **FR-004**: Removing the notification MUST NOT change how photo generation itself is processed — submitted jobs continue to run and complete normally in the background regardless of whether a notification would have been shown.
- **FR-005**: The background mechanism that watches an in-flight job purely in order to trigger a completion notification MUST be removed, since it no longer serves any purpose once the notification itself is gone.
- **FR-006**: Any UI that exists solely to handle a tapped completion notification (e.g. deep-linking straight to a result screen) MUST be removed along with the notification.

### Key Entities

- **Processing Job**: A single photo submitted for background processing. Its completion/failure previously triggered a notification; going forward it does not, but the job's own lifecycle (queued → processing → done/failed) is unaffected.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 0% of completed or failed photo generations produce a system notification, going forward.
- **SC-002**: 0% of first-time photo submissions trigger a notification-permission prompt.
- **SC-003**: Photo generation success rate and completion time are unaffected — removing the notification introduces no regression in processing itself.

## Assumptions

- Removing the notification also means removing the permission request tied to it — prompting for a permission the app no longer uses would be worse than not prompting at all.
- Users will discover completed generations by checking the app directly: the Gallery for finished work, and the existing "still processing" entry point on Home (from the recently added return-to-running-batch feature) for anything still in progress. That entry point is driven by locally stored job status, not by the notification mechanism, so it continues to work for successes (refreshed via pull-to-refresh). For failures specifically, its accuracy is best-effort while the app is backgrounded, per the Clarifications entry above.
- There is no existing Settings toggle for notifications in this app, so there is nothing to remove from Settings UI.
- No replacement in-app alert (banner, sound, badge, etc.) is being requested — the notification is being removed outright, not swapped for a different mechanism.
