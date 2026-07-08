# Quickstart: Validating the Duplication Consolidation

## Prerequisites

- No device/emulator needed for the automated checks below — this is a
  structural refactor with no new UI to exercise.
- A debug build on any device/emulator is only needed for the optional manual
  smoke pass (step 4).

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
./gradlew spotlessCheck
```

Per FR-005/SC-004, **none of these should require editing a single existing
test** to pass — if a test needs to change to accommodate this refactor, that
is a signal a behavior slipped in, not just a structure change.

## Verifying the three consolidation counts (SC-001, SC-002, SC-003)

```bash
# SC-001: exactly 1 place constructs a ProgressViewModel (was 3)
grep -rn "ProgressViewModel(repository" app/src/main/java/dev/zun/flux/ui/progress/

# SC-002: exactly 1 place builds the back-arrow IconButton block (was 8 call sites,
# now 8 one-line calls into the shared composable, plus its single definition)
grep -rln "ArrowBack" app/src/main/java/dev/zun/flux/ui/ | wc -l
grep -rn "fun BackNavigationIcon" app/src/main/java/dev/zun/flux/ui/common/

# SC-003: exactly 1 place shows the "undo deleted item" snackbar body (was 2)
grep -rn "fun.*showUndoDeletedSnackbar" app/src/main/java/dev/zun/flux/ui/gallery/
```

Expected after the refactor: the `ProgressViewModel(repository` construction
call and the `showUndoDeletedSnackbar` function definition each appear exactly
once; `BackNavigationIcon`'s definition appears exactly once, and the two
`LaunchedEffect` guards in `GalleryScreen`/`PhotoViewerScreen` (documented in
`research.md` Decision 3) are still intact at their respective call sites —
only the shared body moved, not the per-screen trigger logic.

## Confirming no remaining copy of the old blocks (SC-005)

```bash
# Should return nothing outside the new shared helper's own definition —
# no screen should still have its own inline copy of the old block.
grep -rn "viewModelFactory { initializer { ProgressViewModel" app/src/main/java/dev/zun/flux/ui/
```

## Optional manual smoke pass

Since FR-004 requires zero visible behavior change, a manual pass is a
sanity check, not new verification — the automated suite is the real
evidence per Constitution Principle III (there's no new behavior to
hand-verify).

1. **Single-job progress, batch grid, and batch focused pager** all still
   poll, show progress, and support Cancel/Dismiss exactly as before (Story 1).
2. **Every screen with a back arrow** (History, Progress, Batch grid/focused,
   Gallery, Settings, Result, Photo viewer) still navigates back correctly,
   and **Photo viewer's back icon is still white** while the other seven are
   unchanged (Story 2).
3. **Deleting a photo in the Gallery grid** still shows the Undo snackbar and
   restores correctly on Undo; **deleting from the Photo viewer** does the
   same. If `GalleryScreen` is ever shown with `showUndoSnackbars = false`
   (e.g. an embedded picker context), confirm it still suppresses its own
   snackbar while Photo viewer's is unaffected (Story 3).
