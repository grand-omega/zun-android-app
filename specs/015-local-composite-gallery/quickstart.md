# Quickstart: Validating Local Composite Gallery Entries

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew spotlessCheck
```

Per Constitution Principle III, this feature should extend `RealJobRepositoryTest.kt` with cases
covering the reserved-id-prefix branch (research.md Decisions 1/3/4):
- `previewModel`/`resultModel`/`thumbModel` for a `local-composite-*` id resolve to the local file
  `Uri`, never a `buildUrlOrNull(...)` server URL.
- `offlineAvailability` for a `local-composite-*` id always reports fully available.
- `deleteJob` for a `local-composite-*` id never calls the API's delete endpoint and never enqueues
  `DeleteSyncWorker` — only the Room row and local file are removed.

And extend `GalleryThumbnailTest.kt` with a case confirming a `local-composite-*` job shows the
visual distinguisher badge (FR-003) that an ordinary AI-generated job does not.

The actual file write/read (`saveLocalComposite`) and full gallery-grid rendering are not
meaningfully unit-testable (real I/O/Compose rendering) — verified manually below, same split used
throughout features 010/011/014/015.

## Manual validation

1. **Save and find again (Story 1 / FR-001, FR-006)**
   Save a drag-reveal composite (feature 014's save action), then return to the app's main gallery
   without touching anything else. Confirm the composite appears as its own cell, positioned by
   creation time among the existing AI-generated results — not in a separate section or tab.

2. **Visual distinguisher (FR-003)**
   With the composite visible in the grid, confirm it's identifiable as a saved composite at a
   glance, without tapping it, distinct from how an AI-generated result's cell looks.

3. **Full-screen view and actions (FR-004, FR-005, FR-009)**
   Tap the composite entry. Confirm it opens full-screen, and that favorite/delete work normally.
   Confirm "view edit history," "use as input," and "compare" are *not* offered for it.

4. **Fully offline (FR-007, SC-002)**
   With the device offline, repeat steps 1-3 in full (save, browse, view, favorite, delete). Confirm
   every step works with no error, spinner-hang, or "needs network" indicator anywhere in the flow.

5. **Delete is local-only (FR-008)**
   Delete a saved composite, then check `adb logcat` (or the equivalent) briefly around the deletion
   — confirm no network request fires as a result. Confirm the entry is gone from the gallery
   immediately.

6. **Protected from "Clear offline cache" (FR-011)**
   Save a composite, then run Settings' "Clear offline cache" (feature 009) while online. Confirm
   the composite is still present and viewable in the gallery afterward, and that "Clear offline
   cache"'s own preview/confirmation UI doesn't list it as something that will be removed.

7. **Sharing is unaffected (FR-010)**
   Confirm the share action on a fresh drag-reveal composite (before saving it) still opens the
   system share sheet exactly as it did before this feature — this feature only changes where a
   *saved* composite ends up, not the share path.

8. **No accidental stacking (data-model.md, research.md Decision 1)**
   Save a composite derived from a photo that already has other AI-generated variants stacked
   together in the gallery (feature 009). Confirm the new composite does *not* get pulled into that
   stack — it renders as its own standalone cell, with no stack-count badge.
