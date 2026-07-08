# Research: Consolidate Duplicated UI Boilerplate

No `NEEDS CLARIFICATION` markers remain in the Technical Context — this is a small,
well-understood internal refactor within an already-explored codebase, so this
document records the concrete extraction decisions rather than resolving unknowns.

## Decision 1: `ProgressViewModel` hosting helper

- **Decision**: Add a top-level `@Composable fun rememberProgressViewModel(jobId: String, jobs: JobRepository): ProgressViewModel` at the bottom of `ui/progress/ProgressViewModel.kt` (the file that already defines the class), returning `viewModel(key = jobId, factory = viewModelFactory { initializer { ProgressViewModel(repository = jobs) } })`. `ProgressScreen`, `BatchTile`, and `BatchPage` each replace their own copy of this block with a call to it.
- **Rationale**: Co-locating the helper with the class it constructs means there's exactly one file to read to understand both what a `ProgressViewModel` is and how one is obtained — no new file needed, no cross-file indirection.
- **Alternatives considered**:
  - A generic `rememberVm<VM : ViewModel>(key: String?, create: () -> VM)` helper reusable by the 4 other, differently-typed ViewModel-hosting sites (`HomeRoute`, `GalleryScaffold`, `SettingsScreen`, `EditHistoryScreen`). Rejected per spec FR-006 — explicitly out of scope for this pass.
  - A Hilt/Dagger-provided ViewModel. Rejected — constitution's Technology & Architecture Constraints section forbids introducing a DI framework.

## Decision 2: Back-navigation icon composable

- **Decision**: Add `@Composable fun BackNavigationIcon(onBack: () -> Unit, contentDescription: String, tint: Color = LocalContentColor.current)` to `ui/common/Polish.kt` (the existing home for small shared UI atoms — `StatusPill`, `LoadingScrim`, `PanelShape`, etc.), wrapping `IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = contentDescription, tint = tint) }`. All 8 call sites (`EditHistoryScreen`, `BatchProgressScreen` ×2, `ProgressScreen`, `GalleryScreen`, `SettingsScreen`, `ResultScreen`, `PhotoViewerScreen`) replace their own `IconButton` block with a call to it, passing their own `onBack`/content-description string; only `PhotoViewerScreen` passes `tint = Color.White` explicitly.
- **Rationale**: `tint: Color = LocalContentColor.current` matches `Icon`'s own default exactly, so the 7 sites that don't currently specify a tint keep pixel-identical output by simply omitting the parameter — no behavior change, no forced verbosity.
- **Alternatives considered**:
  - Always requiring an explicit tint. Rejected — would force 7 call sites to redundantly restate the default for no benefit, working against "less is more."
  - Adding a new single-purpose file (`ui/common/BackNavigationIcon.kt`). Rejected in favor of extending the existing `Polish.kt` grab-bag file, consistent with how `StatusPill`/`LoadingScrim` already live there — avoids fragmenting `ui/common/` into many one-composable files.

## Decision 3: Undo-deleted-item snackbar function

- **Correction to initial scoping**: the two call sites are *not* byte-for-byte identical as first assumed. `GalleryScreen`'s `LaunchedEffect(pendingUndo, showUndoSnackbars)` has an extra guard (`if (!showUndoSnackbars) return@LaunchedEffect`) driven by its own `showUndoSnackbars: Boolean = true` parameter (used when `GalleryScreen` is embedded somewhere its own snackbar would be wrong); `PhotoViewerScreen`'s `LaunchedEffect(pendingUndo)` has no such guard and always shows it. The snackbar-showing-and-undo-handling body itself *is* identical between the two.
- **Decision**: Add a `suspend fun SnackbarHostState.showUndoDeletedSnackbar(message: String, actionLabel: String, onUndo: () -> Unit, onDismiss: () -> Unit)` extension function (co-located in `ui/gallery/GalleryScreen.kt`, the more "primary" of the two screens, since both already live under `ui/gallery/`). Each call site keeps its own `LaunchedEffect` (with its own key list and, for `GalleryScreen`, its own `showUndoSnackbars` guard) and delegates only the shared body — the snackbar call and the undo/dismiss branching — to this function.
- **Rationale**: The guard condition is a real behavioral difference specific to `GalleryScreen`'s embedding use case, not incidental duplication — it must not be silently added to or removed from either site. Keeping each `LaunchedEffect`'s trigger keys and guard at the call site, while sharing only the common body, preserves both behaviors exactly (satisfies FR-004).
- **Alternatives considered**:
  - Passing `showUndoSnackbars` as a parameter into the shared function itself. Rejected — `PhotoViewerScreen` has no such concept, so this would leak a `GalleryScreen`-specific parameter into a shared function that shouldn't know about it.
