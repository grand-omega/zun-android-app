# Feature Specification: Local Composite Gallery Entries

**Feature Branch**: `015-local-composite-gallery`

**Created**: 2026-07-08

**Status**: Draft

**Input**: User description: "Show saved drag-reveal composites in this app's own in-app gallery grid (alongside AI-generated results), instead of only saving them to the system photo gallery. The composite export feature (spec 014) currently uses saveToPictures/shareImages to write into the device's shared system Pictures folder via MediaStore. Instead, a saved composite should become a new browsable entry in this app's existing gallery grid (the same paged grid that shows server-generated jobs), even though it has no corresponding server-side generation job (no prompt/workflow/seed/server id) — it's a purely local, on-device composite." Follows directly from feature 014 (Save Drag-Reveal Results); the user explicitly chose "visible in the existing gallery grid" over a private-storage-only or separate-section alternative.

## Clarifications

### Session 2026-07-08

- Q: This app already has a "Clear offline cache" action (feature 009) that deletes locally-cached, server-re-fetchable image content. Should saved composites be protected from it, or included in what it can clear? → A: Always protected — "Clear offline cache" must never delete a saved composite, since composites have no server copy to re-fetch and clearing one would be permanent, irreversible data loss, unlike clearing an ordinary cache entry.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find a saved reveal composite again, inside the app (Priority: P1)

A user saves a drag-reveal composite (per feature 014). Later, they want to look at it again, favorite it, or delete it — without leaving the app to dig through their phone's separate Photos app.

**Why this priority**: This is the entire ask. Without it, a saved composite exists somewhere on the device but is effectively invisible from within the app that created it — the user has no way to revisit their own saved work through the app itself.

**Independent Test**: Save a drag-reveal composite, return to the app's main gallery, and confirm the composite appears there as its own entry, sorted alongside AI-generated results by when it was created — open it, favorite it, and delete it, all without needing network connectivity.

**Acceptance Scenarios**:

1. **Given** a user has just saved a drag-reveal composite, **When** they return to the app's gallery, **Then** the composite appears there as a new entry, positioned by creation time alongside AI-generated results — not in a separate section.
2. **Given** a saved composite is showing in the gallery, **When** the user looks at the grid, **Then** they can tell at a glance that this entry is a saved composite rather than a fresh AI-generated result.
3. **Given** a user taps a saved composite entry, **When** it opens, **Then** they see it full-screen and can favorite it or delete it, the same as they could with any other gallery entry.
4. **Given** the device has no network connection, **When** the user views, favorites, or deletes a saved composite, **Then** all of it works — nothing about a saved composite ever requires reaching the server.
5. **Given** a user deletes a saved composite, **When** they check the gallery afterward, **Then** it's gone, and nothing about the deletion touches any server-side record (there isn't one).

### Edge Cases

- What happens to composites already saved before this feature existed, sitting in the system Photos app from feature 014's prior behavior? They're left exactly where they are — this feature only changes where newly-saved composites go from now on; it does not retroactively import anything already saved externally.
- What happens if the user tries actions that assume a server-side generation — viewing edit history, using the composite as a fresh generation input, or comparing a before/after — on a saved composite? These aren't available for a saved composite, since there's no generation record behind it to act on; this isn't a bug, it's outside this entry's nature.
- What happens if the app's local data is cleared or the app is uninstalled? Saved composites are lost along with everything else the app stores locally — the same as any other on-device-only content in this app; there's no separate backup path.
- What happens if a user saves the same reveal twice? Each save is its own distinct new gallery entry (per feature 014's existing guarantee), never overwriting a previous save.
- What happens if the user runs "Clear offline cache" (feature 009) while saved composites exist? Nothing about a saved composite is touched — that action only clears content re-fetchable from the server, and composites aren't (FR-011).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A saved drag-reveal composite MUST appear as its own browsable entry in this app's existing gallery grid.
- **FR-002**: Saving a composite MUST no longer also write it to the device's shared system photo storage — from this feature forward, a saved composite lives only within this app's own gallery.
- **FR-003**: A saved composite entry MUST be visually distinguishable from AI-generated results in the gallery grid, without needing to open it first.
- **FR-004**: Tapping a saved composite entry MUST open it for full-screen viewing.
- **FR-005**: A saved composite MUST support the same favorite and delete actions already available on other gallery entries.
- **FR-006**: Saved composites MUST be sorted into the gallery's existing chronological order together with AI-generated results, by creation time — not segregated into a separate tab or section.
- **FR-007**: Viewing, favoriting, and deleting a saved composite MUST work fully offline, with no server round-trip involved at any point.
- **FR-008**: Deleting a saved composite MUST only affect this device — there is no server-side record to keep in sync.
- **FR-009**: Actions that inherently require a server-side generation record — viewing edit history, using the composite as a new generation's input, or comparing a before/after — are outside this feature's scope and MUST NOT be offered for a saved composite entry.
- **FR-010**: Sharing a saved composite via the system share sheet (feature 014's existing share path) is UNCHANGED by this feature — only where a *saved* composite ends up is affected, not how it's shared.
- **FR-011**: A saved composite MUST be protected from feature 009's "Clear offline cache" action — that action only clears content this app can re-fetch from the server, and a saved composite has no server copy to re-fetch, so clearing it would be permanent, irreversible data loss rather than an ordinary cache clear.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After saving a composite, a user can find it again inside the app's own gallery without ever leaving the app.
- **SC-002**: 100% of saved-composite viewing, favoriting, and deleting works with the device fully offline.
- **SC-003**: A user can distinguish a saved composite from an AI-generated result in the gallery grid at a glance, without opening it, 100% of the time.
- **SC-004**: A newly-saved composite appears in the gallery the very next time it's viewed — no manual refresh or separate sync step needed.

## Assumptions

- This replaces, rather than adds to, feature 014's save destination: a saved composite goes into this app's own gallery instead of the system's shared photo storage, not both — per the user's explicit instruction ("do not save to system gallery, save to this app's gallery").
- The *share* action from feature 014 is untouched — sharing still hands the composite to the system share sheet as a real file, since that's inherent to how sharing works on the platform; only the *save* destination changes.
- No data migration for composites already saved under feature 014's old behavior — this is a forward-only change.
- The visual distinguisher for a saved composite (FR-003) is left as an implementation-level design choice (e.g., a small badge or icon), not prescribed here — the requirement is that one exists and is reliably visible at a glance, not what it specifically looks like.
