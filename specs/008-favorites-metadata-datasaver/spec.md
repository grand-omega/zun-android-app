# Feature Specification: Favorites, Generation Details, and Cellular Data Control

**Feature Branch**: `008-favorites-metadata-datasaver`

**Created**: 2026-07-06

**Status**: Draft

**Input**: User description: "Add three small features: (1) a favorite/keep toggle on generated
images -- a heart icon in the photo viewer action bar and a small overlay on gallery grid tiles,
plus a 'favorites only' filter chip alongside the existing prompt-text/custom-only filters in
Gallery; (2) a generation-metadata bottom sheet in the photo viewer, reachable via swipe-up or an
info icon, showing the prompt text, whether it was a 'try harder' variant, the workflow used, and
the job's created/completed timestamps; (3) a 'Wi-Fi only' vs 'allow cellular data' toggle in
Settings (Wi-Fi-only by default) that gates both job-submission upload and result download over
cellular connections."

**Scoping note**: Three small, independent features bundled into one spec because they were
selected together from the same round of feature research (comparing against the immich mobile
app) — none depends on any of the others, and each is independently testable/shippable.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mark a generated image as a favorite (Priority: P1)

A user submits a batch of AI edits from the same source photo, gets back several variants, and
wants to mark the one they actually like so it's easy to find again without deleting the rest.

**Why this priority**: Solves a concrete, immediate gap — there is currently no way to
distinguish "the one I want" from "the other four variants I generated" other than remembering
or manually deleting the rest. Smallest, lowest-risk of the three, and the one most directly
requested.

**Independent Test**: From the photo viewer or the gallery grid, mark an image as a favorite,
confirm it's visually marked in both places, then use the "favorites only" filter in Gallery and
confirm only favorited images appear. Un-favorite it and confirm it disappears from that filter
immediately.

**Acceptance Scenarios**:

1. **Given** a completed generated image open in the photo viewer, **When** the user taps the
   favorite (heart) icon, **Then** the image is marked as a favorite and the icon reflects the
   new state immediately.
2. **Given** a favorited image, **When** the user views the Gallery grid, **Then** that image's
   tile shows a small favorite indicator overlay.
3. **Given** the Gallery screen with the "favorites only" filter active, **When** the user views
   the grid, **Then** only favorited images are shown, and this filter can be combined with the
   existing prompt-text/custom-only filters.
4. **Given** a favorited image, **When** the user taps the favorite icon again, **Then** the
   image is un-favorited and immediately drops out of the "favorites only" filter view.
5. **Given** a favorited image, **When** the user deletes it (existing delete/undo flow),
   **Then** it is deleted exactly as any other image would be — favoriting does not add extra
   confirmation steps or protection against deletion.

---

### User Story 2 - See what produced a generated image (Priority: P2)

A user looking at a generated image wants to know exactly what prompt, workflow, and timing
produced it, without having to remember or hunt through their prompt history.

**Why this priority**: Purely informational and additive — no risk of confusing existing flows,
but slightly less urgent than being able to mark a keeper (Story 1), since the information is
also visible, in less convenient form, elsewhere in the app already (the prompt library, edit
history).

**Independent Test**: Open a generated image in the photo viewer, reveal the generation-details
sheet (swipe up or tap an info icon), and confirm it shows the prompt text, whether it was a
"try harder" generation, the workflow used, and when the job was created and completed —
compare against the same job's known submission details to confirm accuracy.

**Acceptance Scenarios**:

1. **Given** a completed generated image open in the photo viewer, **When** the user swipes up
   (or taps an info icon), **Then** a bottom sheet appears showing the prompt text used, whether
   "try harder" was enabled, the workflow name, and the created/completed timestamps for that
   job.
2. **Given** the generation-details sheet is open, **When** the user dismisses it (swipe down,
   tap outside, or back gesture), **Then** it closes without affecting the photo viewer's state.
3. **Given** a generated image whose prompt came from the prompt library (not custom-typed),
   **When** the sheet is shown, **Then** it displays that saved prompt's text, matching what's
   shown elsewhere in the app for the same prompt.

---

### User Story 3 - Avoid burning cellular data on AI jobs (Priority: P3)

A user on a limited mobile data plan wants to make sure submitting photos for AI editing, and
getting results back, only happens over Wi-Fi unless they explicitly allow otherwise.

**Why this priority**: Real, protective value for a subset of users, but it's a one-time setting
change rather than a recurring interaction, and no current reports of anyone actually hitting
this problem — lowest urgency of the three, though still small and self-contained.

**Independent Test**: With the setting at its default (Wi-Fi only) and the device on cellular
data only, attempt to submit a job and confirm it waits rather than immediately uploading over
cellular; switch to "allow cellular data" and confirm the same submission now proceeds over
cellular.

**Acceptance Scenarios**:

1. **Given** the app's default settings (Wi-Fi only) and a device connected only to cellular
   data, **When** the user submits an image for editing, **Then** the upload is held until a
   Wi-Fi connection is available, rather than proceeding over cellular.
2. **Given** the same default setting, **When** a submitted job finishes and its result would
   normally be fetched automatically, **Then** that automatic result fetch is likewise held
   until Wi-Fi is available.
3. **Given** the user has switched the setting to "allow cellular data", **When** they submit a
   job or a result is ready to fetch while on cellular only, **Then** the upload/fetch proceeds
   immediately over cellular.
4. **Given** either setting, **When** the user explicitly taps "Save to device" or "Share" for
   an already-downloaded image, **Then** that explicit, one-time action is not blocked by this
   setting (it governs only automatic background upload/fetch traffic).

---

### Edge Cases

- What happens if a user favorites an image and then it's deleted from the server independently
  (e.g. via the existing "server no longer knows about this job" cleanup)? The favorite flag is
  local-only metadata on a row that gets removed along with the rest of that row — no orphaned
  state to clean up separately.
- What happens if the generation-details sheet is opened for an image whose job is not yet
  fully complete (e.g. viewed while still "running")? The sheet still shows the fields already
  known at submission time (prompt, workflow, try-harder flag, created time) and indicates the
  completed time isn't available yet, rather than showing blank or incorrect data.
- What happens when a user is on Wi-Fi with the setting at "Wi-Fi only" and Wi-Fi drops mid
  transfer, leaving only cellular available? The transfer holds/retries rather than silently
  falling back to cellular, consistent with the setting's intent.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to mark any completed generated image as a favorite, and
  un-mark it, from the photo viewer.
- **FR-002**: The Gallery grid MUST visually indicate which images are currently favorited.
- **FR-003**: Gallery MUST offer a "favorites only" filter that can be applied together with the
  existing prompt-text and custom-only filters (a user can narrow to, for example, "favorited
  images from this specific prompt").
- **FR-004**: Favoriting/un-favoriting MUST take effect immediately in both the photo viewer and
  the Gallery grid/filter, with no additional confirmation step, and MUST NOT change how
  deletion of that image behaves.
- **FR-005**: Users MUST be able to view a generation-details sheet for any generated image from
  the photo viewer, showing: the prompt text used, whether the "try harder" variant was used,
  the workflow name, and the job's created and completed timestamps.
- **FR-006**: The generation-details sheet MUST be dismissible without affecting the photo
  viewer's own state (e.g. still on the same image, before/after slider position unchanged).
- **FR-007**: Settings MUST offer a "Wi-Fi only" vs "allow cellular data" choice for AI-job
  network traffic, defaulting to Wi-Fi only.
- **FR-008**: When "Wi-Fi only" is selected, both job-submission upload and automatic
  result-fetch download MUST be held until a Wi-Fi connection is available, rather than
  proceeding over cellular or failing outright.
- **FR-009**: When "allow cellular data" is selected, job-submission upload and automatic
  result-fetch download MUST proceed over cellular when Wi-Fi is unavailable.
- **FR-010**: Explicit, user-initiated one-time actions (Save to device, Share) MUST NOT be
  gated by the Wi-Fi/cellular setting — it governs only automatic background upload/fetch
  traffic.

### Key Entities

- **Generated image (job) — favorite status**: A new boolean attribute on each already-tracked
  generated-image record, indicating whether the user has marked it as a favorite. Local to the
  device; no change to what's sent to or stored by the server.
- **Generation details**: Not a new entity — this is a read-only presentation of data already
  captured for each job at submission/completion time (prompt text, try-harder flag, workflow
  name, created/completed timestamps).
- **Network preference**: A single app-wide setting (Wi-Fi only / allow cellular), alongside the
  app's other existing connection-related settings.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can mark and later locate a favorited image (via the favorites filter) in
  under 10 seconds, without needing to remember which image it was among several similar
  variants.
- **SC-002**: 100% of generated images show accurate generation details (prompt, workflow,
  try-harder flag, timestamps) matching what was actually submitted/completed for that job.
- **SC-003**: With "Wi-Fi only" active and only cellular data available, 0% of job
  submissions or automatic result downloads occur over cellular; with "allow cellular data"
  active, submissions and downloads proceed normally regardless of connection type.
- **SC-004**: None of the three features changes the behavior of any existing Gallery, photo
  viewer, or Settings interaction that isn't directly part of this feature (no regressions).

## Assumptions

- "Try harder" refers to the existing workflow-variant toggle already present when submitting a
  job (`tryHarderAvailable`/try-harder workflow) — this feature only surfaces that existing
  fact after the fact, it does not change how try-harder itself works.
- The "favorites only" filter is an independent, combinable toggle alongside the existing
  prompt-based filters, not a mutually-exclusive alternative to them.
- "Result download" for the Wi-Fi/cellular setting means the automatic fetch of a completed
  job's result image into the app's offline cache — not explicit user-initiated actions like
  Save to device or Share, which remain unaffected (FR-010).
- No server-side changes are required for any of these three features — favorite status and
  the network preference are local-only, and generation details are already returned by the
  existing job-status API.
- These three features are independent and may be implemented/shipped in any order or
  individually; they are bundled in this spec only because they were selected together.
