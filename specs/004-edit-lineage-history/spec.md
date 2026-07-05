# Feature Specification: Edit Lineage & Duplicate-Source History

**Feature Branch**: `004-edit-lineage-history`

**Created**: 2026-07-05

**Status**: Draft

**Input**: User description: "add a feature shoing the chain of edits, because if user upload a same input, if the user has uploaded this before, then we could just generate this and show all the edit of a same same input."

## Clarifications

### Session 2026-07-05

- Q: Does duplicate-source detection apply to batch submissions (multiple photos at once), not just single-photo submission? → A: Yes — each photo in a batch is checked independently, same as the single-image flow.
- Q: Should jobs that failed, were cancelled, or never completed still count toward "prior edits" detection/history for a photo? → A: No — only successfully completed jobs count; failed/cancelled attempts are excluded entirely.
- Q: When a job is hard-deleted (past the existing 30-day undo window), should it stop counting toward "you've edited this before" detection for that photo? → A: Yes — deletion removes it from detection too; no separate, longer-lived record is kept once the job itself is gone.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Duplicate source detection surfaces prior edits (Priority: P1)

As a user, when I pick a source image to edit that I've submitted before, I want to see a non-blocking indicator that I've edited this photo before (and how many times), so I can quickly check what I already tried instead of starting blind.

**Why this priority**: This is the core trigger the feature is built around — the entry point that makes the rest of the history useful and discoverable.

**Independent Test**: Submit an edit for a photo, let it complete, then later pick that exact same photo file again on the Home screen; confirm an indicator appears showing prior edits exist, and interacting with it opens the history view for that photo.

**Acceptance Scenarios**:

1. **Given** a source image that has never been submitted before, **When** the user selects it on Home, **Then** no "edited before" indicator appears.
2. **Given** a source image identical to one submitted in a previous job, **When** the user selects it on Home, **Then** a non-blocking indicator shows it's been edited before (with a count) and offers to view the history, without preventing submission of a new job.
3. **Given** the user interacts with the indicator, **When** the history view opens, **Then** it shows every past job whose source traces back to this same original photo.

---

### User Story 2 - View the full edit lineage for any result (Priority: P2)

As a user, viewing any result, I want to see the chain of edits that led to it — from the original photo through each "regenerate"/"use as new source" step, and through any independent re-uploads of the same photo — so I can understand and navigate how I got there.

**Why this priority**: Delivers the actual "chain of edits" value independent of the duplicate-detection trigger from Story 1; makes the history useful and complete, not just a one-off nudge.

**Independent Test**: From any result screen, open "View edit history" and confirm it shows an ordered chain from the original source photo through every derived edit, including the current one.

**Acceptance Scenarios**:

1. **Given** a result produced by editing an original upload directly (no prior history), **When** the user opens "View edit history," **Then** it shows just the original source and this one edit.
2. **Given** a result produced through multiple rounds of "use as new source" and/or independent re-uploads of the same original photo, **When** the user opens "View edit history," **Then** it shows every edit in the chain in the order they were created, correctly combining edits from explicit regenerate/use-as-new-source actions with edits from independently re-picking the same photo.
3. **Given** the user selects an earlier entry in the history, **When** they view it, **Then** they see that specific past result and can use the same actions already available on any result (use as new source, share, regenerate).

---

### User Story 3 - Discover edit history without a fresh detection (Priority: P3)

As a user browsing the Gallery, I want any entry to offer "View edit history" regardless of how I arrived at it, so I don't have to remember which photo triggered a duplicate indicator to see its full history.

**Why this priority**: Completes the "always available" entry point; lower priority because Stories 1 and 2 already deliver the core value — this is about consistent discoverability.

**Independent Test**: Open any gallery entry (not just a recently-flagged duplicate) and confirm a "View edit history" action is present and works.

**Acceptance Scenarios**:

1. **Given** any job in Gallery, **When** the user opens its result view, **Then** a "View edit history" action is present.
2. **Given** a job whose source photo has no other related jobs, **When** the user opens "View edit history," **Then** it shows just that single job with no artificial grouping.

---

### Edge Cases

- A user submits the same photo many times purely by independently re-picking it (never using "regenerate"/"use as new source") — all of them must still be grouped into one history, since detection matches on the source image itself, not on how it was submitted.
- Two different original photos happen to produce visually similar results — this MUST NOT cause them to be grouped together; grouping is based on matching source images only, never on result similarity.
- A recognized-duplicate source image is itself a copy of one of the app's own previous *results* (e.g., saved externally and re-picked later) — it MUST still be recognized as part of the existing history, since detection is based on image content, not on where the file came from.
- Existing job history from before this feature ships has no known lineage data (per scope decision below) — those older jobs continue to display individually with no grouping, and MUST NOT be retroactively scanned or altered.
- A job within a history is soft-deleted (still within the existing 30-day undo window) — the history view MUST continue to correctly display the remaining jobs without breaking.
- A job within a history is hard-deleted (past the 30-day undo window) — it MUST disappear from both the history view and future duplicate-source detection for that photo, as if it never existed.
- A user selects multiple photos for a batch submission where only some of them match prior uploads — each matching photo MUST show its own indicator independently; non-matching photos in the same batch MUST NOT be affected.
- A photo was previously submitted but that job failed, was cancelled, or never completed — this MUST NOT count as a "prior edit" and MUST NOT appear in the history view; only successfully completed jobs are considered.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST detect, at the moment a user selects a source image for submission — including each photo individually within a batch submission — whether that exact image has been used as a source in any previous *successfully completed* job. Failed, cancelled, or never-completed jobs MUST NOT count toward detection.
- **FR-002**: When a match is found, the system MUST show a non-blocking indicator — including how many prior edits exist — without preventing the user from submitting a new job with that image.
- **FR-003**: The system MUST provide a "view edit history" entry point from every result and gallery entry, not only at the moment a duplicate is freshly detected.
- **FR-004**: The edit history view MUST show every *successfully completed* job whose source image is the same original photo, whether reached via explicit "regenerate"/"use as new source" or via independently re-selecting the same photo file, presented in the order the edits were made.
- **FR-005**: Duplicate-source detection MUST be based on the image's content being identical to a previously used source image; visually similar but non-identical images MUST NOT be matched.
- **FR-006**: Detection and history grouping MUST apply only to jobs submitted from when this feature ships onward; existing job history MUST NOT be retroactively scanned, altered, or grouped.
- **FR-007**: From within the edit history view, the user MUST be able to open any past entry and use the same actions already available on a result (e.g., use as new source, share, regenerate).
- **FR-008**: The edit history view MUST remain usable and correctly display the remaining entries if one or more jobs in the chain have been soft-deleted (within the existing 30-day undo window).
- **FR-009**: Once a job is hard-deleted (past the existing 30-day undo window), it MUST be excluded from both future duplicate-source detection and the edit history view — no separate record of it persists.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user who re-selects a photo they've edited before sees an indicator of prior edits, before submitting, in every case where a true match exists.
- **SC-002**: Users can view the complete history of edits for any photo they've reused, in correct chronological order, regardless of whether they reused it via "regenerate" or by re-picking the original file.
- **SC-003**: Submitting a new edit for a previously-used photo is exactly as fast as submitting one for a brand-new photo — detection adds no noticeable delay.
- **SC-004**: Zero existing job history entries change appearance or grouping as a result of this feature shipping.

## Assumptions

- "Same input" is determined by exact content match (e.g., a content hash of the image file), not visual/perceptual similarity — a re-crop, rotation, or re-export of the same photo is treated as a different image in this version.
- This is a single-user, self-hosted app, so "has the user uploaded this before" only needs to consider this one user's own job history, not cross-user matching.
- The existing "regenerate" and "use as new source" actions already implicitly link jobs; this feature makes that implicit chain visible and extends it to also catch independent re-uploads of the same photo, without changing how those existing actions behave.
- No new "delete history" or "merge history" management controls are introduced; the edit history view is read-only/navigational.
